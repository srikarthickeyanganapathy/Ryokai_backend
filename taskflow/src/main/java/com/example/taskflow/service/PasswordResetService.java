package com.example.taskflow.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.example.taskflow.domain.PasswordResetToken;
import com.example.taskflow.domain.User;
import com.example.taskflow.exception.UnauthorizedActionException;
import com.example.taskflow.repository.PasswordResetTokenRepository;
import com.example.taskflow.repository.UserRepository;
import com.example.taskflow.util.JwtUtil;

@Service
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final JwtUtil jwtUtil; // to hash token
    private final SecurityAuditService securityAuditService;

    @Value("${app.password-reset.expiry-minutes:60}")
    private int expiryMinutes;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository, UserRepository userRepository,
                                PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService,
                                EmailService emailService, JwtUtil jwtUtil, SecurityAuditService securityAuditService) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.emailService = emailService;
        this.jwtUtil = jwtUtil;
        this.securityAuditService = securityAuditService;
    }

    @Transactional
    public PasswordResetToken createToken(User user) {
        tokenRepository.deleteByUser_Id(user.getId());
        
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setExpiryDate(LocalDateTime.now().plusMinutes(expiryMinutes));
        token.setRawToken(rawToken); // transient

        PasswordResetToken saved = tokenRepository.save(token);
        securityAuditService.record("PASSWORD_RESET_REQUESTED", user.getId(), user.getUsername(), null, null, null, true);
        return saved;
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = hashToken(rawToken);
        
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid reset token"));

        if (token.isUsed()) {
            refreshTokenService.deleteByUserId(token.getUser().getId());
            securityAuditService.record("PASSWORD_RESET_FAILED", token.getUser().getId(), token.getUser().getUsername(), null, null, "Token reuse detected", false);
            throw new UnauthorizedActionException("Reset token reuse detected for user " + token.getUser().getUsername());
        }

        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Reset token has expired");
        }

        int affected = tokenRepository.markAsUsed(token.getId());
        if (affected == 0) {
            refreshTokenService.deleteByUserId(token.getUser().getId());
            throw new UnauthorizedActionException("Reset token reuse detected for user " + token.getUser().getUsername());
        }

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1); // Invalidate active access tokens
        userRepository.save(user);
        
        // Invalidate ALL refresh tokens
        refreshTokenService.deleteByUserId(user.getId());

        // Send email
        emailService.sendPasswordChangedNotification(user.getEmail(), user.getUsername(), user.getLastLoginIp());
        
        securityAuditService.record("PASSWORD_RESET", user.getId(), user.getUsername(), null, null, null, true);
    }

    @Scheduled(cron = "0 0 0 * * ?", zone = "${app.reminders.timezone:Asia/Kolkata}")
    public void cleanupExpiredPasswordResetTokens() {
        tokenRepository.deleteExpiredUnusedTokens(LocalDateTime.now());
        tokenRepository.deleteUsedTokensOlderThan(LocalDateTime.now().minusDays(30));
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
