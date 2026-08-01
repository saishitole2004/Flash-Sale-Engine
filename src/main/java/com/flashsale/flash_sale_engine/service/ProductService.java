package com.flashsale.flash_sale_engine.service;

import com.flashsale.flash_sale_engine.entity.Product;
import com.flashsale.flash_sale_engine.repository.ProductRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepo productRepo;

    public ProductService(ProductRepo productRepo) {
        this.productRepo = productRepo;
    }

    // 1. Create Product (Admin)
    public Product createProduct(Product product) {
        return productRepo.save(product);
    }

    // 2. Fetch All Products (Public)
    public List<Product> getAllProducts() {
        return productRepo.findAll();
    }

    // 3. Fetch Product by ID
    public Product getProductById(Long id) {
        return productRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + id));
    }

    // 4. Update Product Details & Stock (Admin)
    public Product updateProduct(Long id, Product updatedProduct) {
        Product existing = getProductById(id);
        existing.setName(updatedProduct.getName());
        existing.setDescription(updatedProduct.getDescription());
        existing.setPrice(updatedProduct.getPrice());
        existing.setStock(updatedProduct.getStock());
        return productRepo.save(existing);
    }

    // 5. Delete Product (Admin)
    public void deleteProduct(Long id) {
        if (!productRepo.existsById(id)) {
            throw new IllegalArgumentException("Product not found with id: " + id);
        }
        productRepo.deleteById(id);
    }
}