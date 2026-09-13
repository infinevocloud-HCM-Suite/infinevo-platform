package com.phegondev.usersmanagementsystem.service;

import com.phegondev.usersmanagementsystem.entity.OurUsers;
import com.phegondev.usersmanagementsystem.entity.useraccess.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JWTUtils {

    // 🔑 Keep your original secret string
    private final SecretKey key;
    private static final long EXPIRATION_TIME = 86400000L; // 24 hours

    public JWTUtils() {
        String secretString = "673567893696976453275974432697R634967R738467R678T3486576834R8763T4783876764538745673865";
        byte[] keyBytes = Arrays.copyOf(secretString.getBytes(StandardCharsets.UTF_8), 32); // 32 bytes for HS256
        this.key = new SecretKeySpec(keyBytes, SignatureAlgorithm.HS256.getJcaName());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + EXPIRATION_TIME);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateToken(UserDetails userDetails, List<String> actionList) {
        Map<String, Object> claims = new HashMap<>();
        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().toLowerCase())
                .orElse("user");
        claims.put("role", role);
        claims.put("actions", actionList == null ? List.of() : actionList);
        return createToken(claims, userDetails.getUsername());
    }

    public String generateRefreshToken(HashMap<String, Object> claims, UserDetails userDetails) {
        return createToken(claims, userDetails.getUsername());
    }

    public String generateIdentityToken(OurUsers user, List<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("roles", roles == null ? List.of() : roles);
        return createToken(claims, user.getEmail());
    }

    public String generateTokenForRole(OurUsers user, String activeRole, List<String> actions) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("roles", user.getRoles() == null ? List.of() :
                user.getRoles().stream().map(Role::getRoleName).collect(Collectors.toList()));
        claims.put("activeRole", activeRole);
        claims.put("actions", actions == null ? List.of() : actions);
        return createToken(claims, user.getEmail());
    }

    // ✅ Parsing method updated for JJWT 0.12.5 with your secret key
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public <T> T extractClaims(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String extractUsername(String token) {
        return extractClaims(token, Claims::getSubject);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractActions(String token) {
        Object claim = extractAllClaims(token).get("actions");
        return claim == null ? Collections.emptyList() : (List<String>) claim;
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Object claim = extractAllClaims(token).get("roles");
        return claim == null ? Collections.emptyList() : (List<String>) claim;
    }

    public String extractActiveRole(String token) {
        Object claim = extractAllClaims(token).get("activeRole");
        return claim == null ? null : claim.toString();
    }

    public boolean isTokenExpired(String token) {
        Date expiry = extractClaims(token, Claims::getExpiration);
        return expiry.before(new Date());
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }
}
