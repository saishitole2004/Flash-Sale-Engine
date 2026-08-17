package com.flashsale.flash_sale_engine.controller;

import com.flashsale.flash_sale_engine.entity.User;
import com.flashsale.flash_sale_engine.service.RedisStockService;
import com.flashsale.flash_sale_engine.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    @Autowired
    private UserService userService;


    // PRIVATE: Only accessible by an existing Admin
    @PostMapping("/create-admin")
    public ResponseEntity<?> createAdminUser(@RequestBody User user) {
        try {
            userService.registerUserAdmin(user);
            return new ResponseEntity<>(user, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>("Failed to create admin: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }


}