package com.example.taskflow.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.taskflow.repository.RefreshTokenRepository;
import com.example.taskflow.repository.PasswordResetTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import java.time.ZoneId;
import jakarta.annotation.PostConstruct;

import jakarta.transaction.Transactional;

@Service
public class TokenCleanupService {
    
    private static final Logger logger = LoggerFactory.getLogger(TokenCleanupService.class);
    
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Value("${app.reminders.timezone:Asia/Kolkata}")
    private String timezoneProperty;
    
    private ZoneId zoneId;

    public TokenCleanupService(RefreshTokenRepository refreshTokenRepository, PasswordResetTokenRepository passwordResetTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @PostConstruct
    public void init() {
        this.zoneId = ZoneId.of(timezoneProperty);
    }

    // Run once a day at midnight to clean up expired and stale refresh tokens
    @Scheduled(cron = "0 0 0 * * ?", zone = "${app.reminders.timezone:Asia/Kolkata}")
    @Transactional
    public void cleanUpExpiredTokens() {
        logger.info("Running scheduled cleanup for expired tokens...");
        LocalDateTime now = LocalDateTime.now(zoneId);
        
        int refreshDeleted = refreshTokenRepository.deleteAllExpiredSince(now);
        // Purge used (rotated) refresh tokens after a 7-day grace period.
        // During the grace period, reuse-detection can still trigger "revoke all sessions"
        // if an attacker replays a rotated token.
        int usedRefreshDeleted = refreshTokenRepository.deleteUsedTokensOlderThan(now.minusDays(7));
        int expiredDeleted = passwordResetTokenRepository.deleteExpiredUnusedTokens(now);
        int oldUsedDeleted = passwordResetTokenRepository.deleteUsedTokensOlderThan(now.minusDays(7));
        
        logger.info("Token cleanup complete: {} expired refresh tokens, {} stale used refresh tokens, {} expired reset tokens, {} old used reset tokens",
                refreshDeleted, usedRefreshDeleted, expiredDeleted, oldUsedDeleted);
    }
}
