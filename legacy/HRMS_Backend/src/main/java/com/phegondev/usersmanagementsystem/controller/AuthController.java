package com.phegondev.usersmanagementsystem.controller;

import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.repository.UsersRepo;
import com.phegondev.usersmanagementsystem.service.JWTUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UsersRepo usersRepo;
    
    @Autowired
    private JWTUtils jwtUtils;

    private static final Set<String> tokenBlacklist = new HashSet<>();

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No token provided");
        }

        String token = authHeader.substring(7);
        
        // Add token to in-memory blacklist
        tokenBlacklist.add(token);
        
        // Also store in user entity for persistence
        String username = jwtUtils.extractUsername(token);
        OurUsers user = usersRepo.findByEmail(username).orElse(null);
        if (user != null) {
            user.setBlacklistedToken(token);
            usersRepo.save(user);
        }

        return ResponseEntity.ok("Logged out successfully");
    }

    public boolean isTokenBlacklisted(String token) {
        // Check in-memory blacklist
        if (tokenBlacklist.contains(token)) {
            return true;
        }
        
        // Check database blacklist
        String username = jwtUtils.extractUsername(token);
        OurUsers user = usersRepo.findByEmail(username).orElse(null);
        return user != null && token.equals(user.getBlacklistedToken());
    }
}