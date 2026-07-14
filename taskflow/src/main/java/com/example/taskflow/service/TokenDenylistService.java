package com.example.taskflow.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import com.example.taskflow.util.JwtUtil;

import java.util.concurrent.TimeUnit;

/**
 * In-memory denylist for revoked access tokens (by tokenId / jti).
 * <p>
 * Entries auto-expire after the access token TTL, since an expired JWT
 * would be rejected by signature validation anyway — no need to keep
 * stale denylist entries.
 * <p>
 * Trade-off: In a multi-instance deployment, this cache is per-instance.
 * For full consistency, replace with Redis or a shared store.
 */
@Service
public class TokenDenylistService {

    private final Cache<String, Boolean> denylist;

    public TokenDenylistService(JwtUtil jwtUtil) {
        this.denylist = Caffeine.newBuilder()
                .expireAfterWrite(jwtUtil.getExpirationMs(), TimeUnit.MILLISECONDS)
                .maximumSize(100_000)
                .build();
    }

    /**
     * Add a tokenId to the denylist, preventing further use of that access token.
     */
    public void denyToken(String tokenId) {
        if (tokenId != null) {
            denylist.put(tokenId, Boolean.TRUE);
        }
    }

    /**
     * Check if a tokenId has been denied (revoked).
     */
    public boolean isDenied(String tokenId) {
        return tokenId != null && denylist.getIfPresent(tokenId) != null;
    }
}
