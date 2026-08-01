package com.flashsale.flash_sale_engine.service;

import com.flashsale.flash_sale_engine.dto.OrderRequestDTO;
import com.flashsale.flash_sale_engine.entity.Order;
import com.flashsale.flash_sale_engine.entity.Product;
import com.flashsale.flash_sale_engine.entity.User;
import com.flashsale.flash_sale_engine.repository.OrderRepo;
import com.flashsale.flash_sale_engine.repository.ProductRepo;
import com.flashsale.flash_sale_engine.repository.UserRepo;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepo orderRepo;
    private final ProductRepo productRepo;
    private final UserRepo userRepo;

    public OrderService(OrderRepo orderRepo, ProductRepo productRepo, UserRepo userRepo) {
        this.orderRepo = orderRepo;
        this.productRepo = productRepo;
        this.userRepo = userRepo;
    }

    @Transactional
    public Order placeOrder(OrderRequestDTO req) {
        // 1. Validate user
        User user = userRepo.findById(req.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + req.getUserId()));

        // 2. Validate product
        Product product = productRepo.findById(req.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + req.getProductId()));

        // 3. Check stock
        if (product.getStock() < req.getQuantity()) {
            throw new IllegalArgumentException("Insufficient stock available");
        }

        // 4. Deduct stock
        product.setStock(product.getStock() - req.getQuantity());

        // 5. Save order
        Order order = Order.builder()
                .user(user)
                .product(product)
                .quantity(req.getQuantity())
                .totalPrice(req.getQuantity() * product.getPrice())
                .build();

        return orderRepo.save(order);
    }

    public List<Order> getAllOrder() {
        return orderRepo.findAll();
    }

    public List<Order> getOrderByUserId(Long userId) {
        return orderRepo.findByUserId(userId);
    }

    // Get order by single order ID
    public Order getOrderById(Long id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with id: " + id));
    }
}