package com.flashsale.flash_sale_engine.config;

import com.flashsale.flash_sale_engine.entity.User;
import com.flashsale.flash_sale_engine.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TestLoader implements CommandLineRunner {

    private final UserRepo userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // Only run if no test users exist
        if (userRepository.count() == 0) {
            System.out.println("🚀 Creating 1000 test users...");

            List<User> users = new ArrayList<>();
            String password = passwordEncoder.encode("password123");

            for (int i = 1; i <= 1000; i++) {
                User user = new User();
                user.setUsername("testuser" + i);
                user.setPassword(password);  // Same password for all
                user.setEmail("testuser" + i + "@test.com");
                users.add(user);

                // Save in batches of 100 for performance
                if (users.size() == 100) {
                    userRepository.saveAll(users);
                    users.clear();
                    System.out.println("✅ Created 100 users");
                }
            }

            // Save remaining
            if (!users.isEmpty()) {
                userRepository.saveAll(users);
            }

            System.out.println("✅ Total 1000 users created!");
        }
    }
}
