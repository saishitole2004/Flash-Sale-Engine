package com.flashsale.flash_sale_engine.controller;

import com.flashsale.flash_sale_engine.dto.OrderRequestDTO;
import com.flashsale.flash_sale_engine.entity.Order;
import com.flashsale.flash_sale_engine.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // 1. Place a new order
    @PostMapping
    public ResponseEntity<Order> placeOrder(@RequestBody OrderRequestDTO request) {
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
}