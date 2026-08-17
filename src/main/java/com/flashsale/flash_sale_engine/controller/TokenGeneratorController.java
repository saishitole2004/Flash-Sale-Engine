package com.flashsale.flash_sale_engine.controller;

import com.flashsale.flash_sale_engine.entity.User;
import com.flashsale.flash_sale_engine.repository.UserRepo;
import com.flashsale.flash_sale_engine.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TokenGeneratorController {

    private final UserRepo userRepo;
    private final JwtUtil jwtUtil;

    // This will create tokens.csv file with all tokens
    @GetMapping("/generate-tokens-file")
    public String generateTokensFile() {
        try {
            List<User> users = userRepo.findAll();
            PrintWriter writer = new PrintWriter(new FileWriter("tokens.csv"));

            writer.println("username,token");

            for (User user : users) {
                String token = jwtUtil.generateToken(user.getUsername());
                writer.println(user.getUsername() + "," + token);
            }

            writer.close();
            return "✅ Created tokens.csv with " + users.size() + " tokens!";

        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }
}