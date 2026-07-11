package com.example.taskflow.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.taskflow.util.JwtUtil;
import com.example.taskflow.domain.RefreshToken;
import com.example.taskflow.domain.User;
import com.example.taskflow.exception.TokenRefreshException;
import com.example.taskflow.repository.RefreshTokenRepository;
import com.example.taskflow.repository.UserRepository;

import io.jsonwebtoken.Claims;
import jakarta.transaction.Transactional;

@Service
public class RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);
    
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final SecurityAuditService securityAuditService;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository, JwtUtil jwtUtil, SecurityAuditService securityAuditService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.securityAuditService = securityAuditService;
    }

    @Transactional
    public String createRefreshChain(Long userId, String deviceInfo, String tokenId) {
        User user = userRepository.findById(userId).orElseThrow();
        String rawToken = jwtUtil.generateRefreshToken(user, tokenId);
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(LocalDateTime.now().plusNanos(jwtUtil.getRefreshExpirationMs() * 1_000_000L));
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setTokenId(tokenId);
        refreshToken.setDeviceInfo(deviceInfo);

        refreshTokenRepository.save(refreshToken);
        
        return rawToken;
    }

    @Transactional
    public User verifyToken(String rawToken) {
        Claims claims;
        try {
            claims = jwtUtil.parseRefreshToken(rawToken);
        } catch (Exception e) {
            throw new TokenRefreshException(TokenRefreshException.ErrorCode.INVALID_TOKEN, "Invalid or expired refresh token");
        }

        String tokenHash = hashToken(rawToken);
        RefreshToken tokenEntity = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new TokenRefreshException(TokenRefreshException.ErrorCode.NOT_FOUND, "Refresh token not found in database"));

        if (Boolean.TRUE.equals(tokenEntity.getUsed())) {
            // REUSE DETECTED!
            logger.error("Security Event: Refresh token reuse detected for user {}", tokenEntity.getUser().getId());
            securityAuditService.record("TOKEN_REUSE_DETECTED", tokenEntity.getUser().getId(), tokenEntity.getUser().getUsername(), null, null, null, false);
            refreshTokenRepository.deleteByUser_Id(tokenEntity.getUser().getId()); // Revoke ALL tokens
            throw new TokenRefreshException(TokenRefreshException.ErrorCode.REUSE_DETECTED, "Token reuse detected. All sessions revoked.");
        }

        if (tokenEntity.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(tokenEntity);
            throw new TokenRefreshException(TokenRefreshException.ErrorCode.EXPIRED_TOKEN, "Refresh token was expired.");
        }

        // Mark as used atomically
        int affected = refreshTokenRepository.markAsUsed(tokenHash);
        if (affected == 0) {
            // Either the token was just marked used by another concurrent request (race),
            // or it didn't exist (handled above).
            logger.error("Security Event: Refresh token reuse detected (race) for user {}", tokenEntity.getUser().getId());
            securityAuditService.record("TOKEN_REUSE_DETECTED", tokenEntity.getUser().getId(), tokenEntity.getUser().getUsername(), null, null, "Race condition detected", false);
            refreshTokenRepository.deleteByUser_Id(tokenEntity.getUser().getId());
            throw new TokenRefreshException(TokenRefreshException.ErrorCode.REUSE_DETECTED, "Token reuse detected. All sessions revoked.");
        }

        User user = tokenEntity.getUser();
        user.getRoles().size();       // force initialization
        user.getTokenVersion();       // force initialization
        return user;
    }

    @Transactional
    public String findUsernameByRawToken(String rawToken) {
        return refreshTokenRepository.findByTokenHash(hashToken(rawToken))
            .map(t -> t.getUser().getUsername()).orElse(null);
    }

    @Transactional
    public RefreshToken findByRawToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        return refreshTokenRepository.findByTokenHash(tokenHash).orElse(null);
    }

    @Transactional
    public void deleteByToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        refreshTokenRepository.deleteByTokenHash(tokenHash);
    }
    
    @Transactional
    public void deleteByUserId(Long userId) {
        refreshTokenRepository.deleteByUser_Id(userId);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(token.getBytes());
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
