package com.flashsale.flash_sale_engine.repository;

import com.flashsale.flash_sale_engine.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepo extends JpaRepository<Order, Long> {

    // Find all orders placed by a specific user
    List<Order> findByUserId(Long userId);
}