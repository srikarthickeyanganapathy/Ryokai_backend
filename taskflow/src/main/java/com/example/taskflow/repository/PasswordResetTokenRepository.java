package com.example.taskflow.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.taskflow.domain.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true, t.usedAt = CURRENT_TIMESTAMP WHERE t.id = :id AND t.used = false")
    int markAsUsed(@Param("id") Long id);

    void deleteByUser_Id(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PasswordResetToken t WHERE t.used = true AND t.usedAt < :date")
    int deleteUsedTokensOlderThan(@Param("date") LocalDateTime date);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM PasswordResetToken t WHERE t.used = false AND t.expiryDate < :date")
    int deleteExpiredUnusedTokens(@Param("date") LocalDateTime date);
}
