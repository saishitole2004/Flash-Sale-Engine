package com.flashsale.flash_sale_engine.controller;

import com.flashsale.flash_sale_engine.dto.OrderEvent;
import com.flashsale.flash_sale_engine.entity.Order;
import com.flashsale.flash_sale_engine.service.KafkaProducerService;
import com.flashsale.flash_sale_engine.service.OrderService;
import com.flashsale.flash_sale_engine.service.RedisStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final KafkaProducerService kafkaProducerService;
    private final RedisStockService redisStockService;

    // 1. Place a new order
    @PostMapping
    public ResponseEntity<Order> placeOrder(@RequestBody OrderEvent request) {
        Order newOrder = orderService.placeOrder(request);
        return new ResponseEntity<>(newOrder, HttpStatus.CREATED);
    }

    // 2. Fetch all orders
    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrder());
    }

    // 3. Fetch orders by User ID
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Order>> getOrdersByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(orderService.getOrderByUserId(userId));
    }

    // 4. Flash sale order - ADD @PostMapping HERE!
    @PostMapping("/flash-sale")  // ← ADD THIS
    public ResponseEntity<String> placeFlashSaleOrder(@RequestBody OrderEvent req) {
        // 1. Atomic Redis check
        int result = redisStockService.deductStock(req.getProductId(), req.getUserId());

        switch (result) {
            case 1:
                kafkaProducerService.sendOrderEvent(req);
                return ResponseEntity.ok("Order placed successfully! Processing in background.");

            case -1:
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Duplicate request: You have already purchased this item.");

            case 0:
            default:
                return ResponseEntity.status(HttpStatus.GONE)
                        .body("Flash sale sold out!");
        }
    }
}