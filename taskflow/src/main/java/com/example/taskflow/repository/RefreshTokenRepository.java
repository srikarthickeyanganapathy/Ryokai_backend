package com.example.taskflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.taskflow.domain.RefreshToken;
import com.example.taskflow.domain.User;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUser(User user);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.used = true, r.usedAt = CURRENT_TIMESTAMP WHERE r.tokenHash = :tokenHash")
    void markAsUsed(@Param("tokenHash") String tokenHash);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    void deleteByTokenHash(String tokenHash);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    void deleteByUser_Id(Long userId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken r WHERE r.expiryDate <= :now")
    int deleteAllExpiredSince(@Param("now") java.time.LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken r WHERE r.user = :user AND r.tokenId = :tokenId")
    int deleteByUserAndTokenId(@Param("user") User user, @Param("tokenId") String tokenId);
}
