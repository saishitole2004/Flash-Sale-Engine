package com.flashsale.flash_sale_engine.service;

import com.flashsale.flash_sale_engine.entity.User;
import com.flashsale.flash_sale_engine.repository.UserRepo;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class UserService {
    private final UserRepo userRepo;

    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepo userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }
    public User registerUser(User user){
        if (userRepo.existsByUsername(user.getUsername()) || userRepo.existsByEmail(user.getEmail())){
            throw new  IllegalArgumentException("Username is already taken or email is taken");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // 2. Set default "USER" role if none provided
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            user.setRoles(Arrays.asList("USER"));
        }
            return userRepo.save(user);
    }
    public User registerUserAdmin(User user){
        if (userRepo.existsByUsername(user.getUsername()) || userRepo.existsByEmail(user.getEmail())){
            throw new  IllegalArgumentException("Username is already taken or email is taken");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        user.setRoles(Arrays.asList("USER", "ADMIN"));

        return userRepo.save(user);
    }

    public List<User> getAllUser(){
        return userRepo.findAll();
    }
    public User findById(Long id){
        return userRepo.findById(id).orElseThrow(()->new IllegalArgumentException("user not found by id"));
    }
}
