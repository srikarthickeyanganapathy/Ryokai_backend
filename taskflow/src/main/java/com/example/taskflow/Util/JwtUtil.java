package com.example.taskflow.util;

import java.util.Date;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.taskflow.domain.Role;
import com.example.taskflow.domain.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final SecretKey refreshKey;
    private final long jwtExpirationMs;
    private final long refreshExpirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secretString,
            @Value("${jwt.refreshSecret}") String refreshSecretString,
            @Value("${jwt.expirationMs:900000}") long jwtExpirationMs,
            @Value("${jwt.refreshExpirationMs:604800000}") long refreshExpirationMs) {
        if (secretString.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters long");
        }
        if (refreshSecretString.length() < 32) {
            throw new IllegalArgumentException("JWT refresh secret must be at least 32 characters long");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretString.getBytes());
        this.refreshKey = Keys.hmacShaKeyFor(refreshSecretString.getBytes());
        this.jwtExpirationMs = jwtExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(User user, String tokenId) {
        Set<Role> roles = user.getRoles();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("roles", roles.stream().map(Role::getName).collect(Collectors.joining(".")))
                .claim("tv", user.getTokenVersion())
                .claim("tokenId", tokenId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(User user, String tokenId) {
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("tokenId", tokenId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpirationMs))
                .signWith(refreshKey, Jwts.SIG.HS256)
                .compact();
    }

    public String generateEmailVerificationToken(String email, Long userId) {
        return Jwts.builder()
                .subject(email)
                .claim("uid", userId)
                .claim("type", "EMAIL_VERIFICATION")
                .issuer("Aura")
                .audience().add("email-verification").and()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000)) // 24 hours
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims validateEmailVerificationToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer("Aura")
                .build()
                .parseSignedClaims(token)
                .getPayload();
                
        if (!"EMAIL_VERIFICATION".equals(claims.get("type", String.class))) {
            throw new JwtException("Invalid token type");
        }
        
        if (!claims.getAudience().contains("email-verification")) {
            throw new JwtException("Invalid audience");
        }
        
        return claims;
    }

    public String extractEmailFromVerificationToken(String token) {
        return validateEmailVerificationToken(token).getSubject();
    }

    public Claims parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Claims parseRefreshToken(String token) {
        return Jwts.parser()
                .verifyWith(refreshKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessTokenValid(String token) {
        try {
            parseAccessToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isRefreshTokenValid(String token) {
        try {
            parseRefreshToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = parseAccessToken(token);
        return claimsResolver.apply(claims);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractTokenId(String token) {
        return extractClaim(token, claims -> claims.get("tokenId", String.class));
    }

    public Integer extractTokenVersion(String token) {
        return extractClaim(token, claims -> claims.get("tv", Integer.class));
    }

    public long getExpirationMs() {
        return jwtExpirationMs;
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }
}