package com.flashsale.flash_sale_engine.repository;

import com.flashsale.flash_sale_engine.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepo extends JpaRepository<Product, Long> {
}
