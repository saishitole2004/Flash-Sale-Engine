package com.flashsale.flash_sale_engine.service;

import com.flashsale.flash_sale_engine.entity.Product;
import com.flashsale.flash_sale_engine.repository.ProductRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisStockService {

    private final StringRedisTemplate redisTemplate;
    private final ProductRepo productRepository;

    // Lua Script: Checks duplicate user, checks stock, deducts 1 item, records user
    // Returns: 1 = Success, 0 = Sold Out, -1 = Duplicate User
    private static final String LUA_DEDUCT_SCRIPT =
            "local stockKey = KEYS[1]\n" +           // Redis key for stock (e.g., "product:stock:123")
                    "local userSetKey = KEYS[2]\n" +         // Redis key for user set (e.g., "product:users:123")
                    "local userId = ARGV[1]\n" +             // User ID of buyer
                    "\n" +
                    "-- Check if user already purchased\n" +
                    "if redis.call('sismember', userSetKey, userId) == 1 then\n" +
                    "    return -1\n" +                      // DUPLICATE: User already bought
                    "end\n" +
                    "\n" +
                    "-- Check if stock available (at least 1)\n" +
                    "local current = tonumber(redis.call('get', stockKey) or '0')\n" +
                    "if current < 1 then\n" +
                    "    return 0\n" +                       // SOLD OUT: No stock left
                    "end\n" +
                    "\n" +
                    "-- Deduct 1 item and record user atomically\n" +
                    "redis.call('decrby', stockKey, 1)\n" +  // Decrease stock by 1
                    "redis.call('sadd', userSetKey, userId)\n" +  // Add user to purchased set
                    "return 1";                               // SUCCESS

    /**
     * Admin Action: Load product stock from MySQL into Redis
     * Called before flash sale starts
     */
    public void preheatStock(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with ID: " + productId));

        String stockKey = "product:stock:" + product.getId();

        // Store stock count in Redis as String
        redisTemplate.opsForValue().set(stockKey, String.valueOf(product.getStock()));

        log.info(" [Admin] Pre-heated Product ID [{}] - Name: [{}] with Stock: [{}] into Redis",
                product.getId(), product.getName(), product.getStock());
    }

    /**
     * Deducts 1 item from stock and records user (prevents duplicate purchases)
     * All operations happen atomically in Redis using Lua script
     *
     * @param productId ID of the product
     * @param userId ID of the user trying to purchase
     * @return 1 = Success, 0 = Sold Out, -1 = Duplicate Purchase
     */
    public int deductStock(Long productId, Long userId) {
        // Redis keys
        String stockKey = "product:stock:" + productId;
        String userSetKey = "product:users:" + productId;

        // Prepare Lua script
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(LUA_DEDUCT_SCRIPT);
        redisScript.setResultType(Long.class);

        // Execute script atomically in Redis
        Long result = redisTemplate.execute(
                redisScript,
                Arrays.asList(stockKey, userSetKey),   // KEYS[1] & KEYS[2]
                String.valueOf(userId)                  // ARGV[1]
        );

        // Convert result to int (null safety)
        int resultCode = result != null ? result.intValue() : 0;

        // Log based on result
        switch (resultCode) {
            case 1:
                log.info("[Redis] 1 item reserved for User [{}] on Product [{}]", userId, productId);
                break;
            case 0:
                log.warn("[Redis] Flash Sale SOLD OUT for Product [{}]!", productId);
                break;
            case -1:
                log.warn("[Redis] Duplicate purchase blocked - User [{}] already bought Product [{}]",
                        userId, productId);
                break;
        }

        return resultCode;
    }

    /**
     * Get remaining stock from Redis
     */
    public Long getRemainingStock(Long productId) {
        String stockKey = "product:stock:" + productId;
        String stock = redisTemplate.opsForValue().get(stockKey);
        return stock != null ? Long.parseLong(stock) : 0L;
    }

    /**
     * Check if user has already purchased
     */
    public boolean hasUserPurchased(Long productId, Long userId) {
        String userSetKey = "product:users:" + productId;
        Boolean isMember = redisTemplate.opsForSet().isMember(userSetKey, String.valueOf(userId));
        return Boolean.TRUE.equals(isMember);
    }

    /**
     * Clear flash sale data from Redis (after sale ends)
     */
    public void clearFlashSaleData(Long productId) {
        String stockKey = "product:stock:" + productId;
        String userSetKey = "product:users:" + productId;
        redisTemplate.delete(Arrays.asList(stockKey, userSetKey));
        log.info("[Admin] Cleared Redis data for Product [{}]", productId);
    }
}