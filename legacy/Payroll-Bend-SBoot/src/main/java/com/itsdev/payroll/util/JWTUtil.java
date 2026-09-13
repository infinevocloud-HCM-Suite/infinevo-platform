package com.itsdev.payroll.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.HashMap;
import java.util.Map;

public class JWTUtil {

    public static Map<String, String> getUserIdAndEmailFromToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            throw new RuntimeException("Invalid authentication context");
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Map<String, String> claims = new HashMap<>();

        String userId = jwt.getClaimAsString("sub");
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");

        if (userId == null || email == null) {
            throw new RuntimeException("Required claims are missing in JWT");
        }

        claims.put("userId", userId);
        claims.put("email", email);
        claims.put("name", name);

        return claims;
    }

    // NEW METHOD: Get just the current user's name
    public static String getCurrentUserName() {
        try {
            Map<String, String> claims = getUserIdAndEmailFromToken();
            return claims.get("name");
        } catch (Exception e) {
            // Fallback to "System" if can't get name
            return "System";
        }
    }
}
