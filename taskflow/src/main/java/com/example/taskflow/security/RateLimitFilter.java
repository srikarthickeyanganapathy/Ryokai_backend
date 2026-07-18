package com.example.taskflow.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Cache<String, Bucket> cache = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    private final Cache<String, Bucket> forgotPasswordCache = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(10_000)
            .build();

    /**
     * SEC-M03 fix: comma-separated list of trusted proxy IPs/CIDRs.
     * Only when the immediate remote address is in this list do we honor
     * the X-Forwarded-For header. Previously any client could set
     * X-Forwarded-For: <random-unique-ip> on every request to bypass
     * rate limiting entirely  -  each request got a fresh bucket.
     *
     * Configure in application.yml:
     *   app.security.trusted-proxies: 10.0.0.0/8,172.16.0.0/12,127.0.0.1
     * Default: empty (X-Forwarded-For is NEVER trusted  -  use remoteAddr only).
     */
    @Value("${app.security.trusted-proxies:}")
    private String trustedProxiesRaw;

    private volatile Set<String> trustedProxies = null;

    private Set<String> getTrustedProxies() {
        if (trustedProxies == null) {
            synchronized (this) {
                if (trustedProxies == null) {
                    if (trustedProxiesRaw == null || trustedProxiesRaw.isBlank()) {
                        trustedProxies = Collections.emptySet();
                    } else {
                        trustedProxies = new HashSet<>(Arrays.asList(
                            trustedProxiesRaw.split("\\s*,\\s*")));
                    }
                }
            }
        }
        return trustedProxies;
    }

    /**
     * SEC-M03 fix: extract client IP safely.
     * If the immediate remoteAddr is in the trusted-proxies list, we trust
     * the X-Forwarded-For header and take the leftmost address. Otherwise
     * we use remoteAddr directly  -  ignoring any client-supplied X-Forwarded-For.
     */
    private String extractClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        Set<String> trusted = getTrustedProxies();

        if (trusted.contains(remoteAddr)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                return xff.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }

    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofMinutes(1)).build();
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createForgotPasswordBucket() {
        Bandwidth limit = Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofHours(1)).build();
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (uri.startsWith("/api/auth/")) {
            // SEC-M03 fix: use the safe extractClientIp method (was inline
            // X-Forwarded-For trust without proxy validation).
            String ip = extractClientIp(request);

            Bucket bucket;

            if (uri.equals("/api/auth/forgot-password")) {
                bucket = forgotPasswordCache.get(ip, k -> createForgotPasswordBucket());
            } else {
                bucket = cache.get(ip, k -> createNewBucket());
            }

            if (!bucket.tryConsume(1)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Too many requests. Please try again later.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
