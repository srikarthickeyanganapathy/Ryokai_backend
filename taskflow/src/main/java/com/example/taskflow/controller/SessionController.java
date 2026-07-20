package com.example.taskflow.controller;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.example.taskflow.domain.RefreshToken;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.JwtResponseDTO;
import com.example.taskflow.dto.MessageResponseDTO;
import com.example.taskflow.dto.ResendVerificationRequestDTO;
import com.example.taskflow.dto.TokenRefreshRequestDTO;
import com.example.taskflow.dto.UserResponseDTO;
import com.example.taskflow.exception.TokenRefreshException;
import com.example.taskflow.service.AuthService;
import com.example.taskflow.service.UserService;
import com.example.taskflow.service.RealtimeBroadcaster;
import com.example.taskflow.service.RefreshTokenService;
import com.example.taskflow.service.SecurityAuditService;
import com.example.taskflow.service.TokenDenylistService;
import com.example.taskflow.service.UserProfileService;
import com.example.taskflow.util.JwtUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.swagger.v3.oas.annotations.Operation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/session", produces = MediaType.APPLICATION_JSON_VALUE)
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final UserProfileService userProfileService;
    private final RealtimeBroadcaster realtimeBroadcaster;
    private final AuthService authService;
    private final SecurityAuditService securityAuditService;
    private final TokenDenylistService tokenDenylistService;

    @Value("${ratelimit.resend.capacity:5}")
    private int resendCapacity;
    @Value("${ratelimit.resend.refill-minutes:60}")
    private int resendRefillMinutes;

    @Value("${ratelimit.refresh.capacity:30}")
    private int refreshCapacity;
    @Value("${ratelimit.refresh.refill-minutes:15}")
    private int refreshRefillMinutes;

    @Value("${ratelimit.verify-email.capacity:10}")
    private int verifyEmailCapacity;
    @Value("${ratelimit.verify-email.refill-minutes:15}")
    private int verifyEmailRefillMinutes;

    @Value("${ratelimit.logout.capacity:20}")
    private int logoutCapacity;
    @Value("${ratelimit.logout.refill-minutes:15}")
    private int logoutRefillMinutes;

    public SessionController(JwtUtil jwtUtil, UserService userService, RefreshTokenService refreshTokenService,
                             UserProfileService userProfileService, RealtimeBroadcaster realtimeBroadcaster,
                             AuthService authService, SecurityAuditService securityAuditService,
                             TokenDenylistService tokenDenylistService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.userProfileService = userProfileService;
        this.realtimeBroadcaster = realtimeBroadcaster;
        this.authService = authService;
        this.securityAuditService = securityAuditService;
        this.tokenDenylistService = tokenDenylistService;
    }

    private final Cache<String, Bucket> resendBuckets = Caffeine.newBuilder()
            .expireAfterAccess(60, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    private final Cache<String, Bucket> refreshBuckets = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(50_000)
            .build();

    private final Cache<String, Bucket> verifyEmailBuckets = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(50_000)
            .build();

    private final Cache<String, Bucket> logoutBuckets = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(50_000)
            .build();

    private Bucket createResendBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(resendCapacity)
                .refillGreedy(resendCapacity, Duration.ofMinutes(resendRefillMinutes))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createRefreshBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(refreshCapacity)
                .refillGreedy(refreshCapacity, Duration.ofMinutes(refreshRefillMinutes))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createVerifyEmailBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(verifyEmailCapacity)
                .refillGreedy(verifyEmailCapacity, Duration.ofMinutes(verifyEmailRefillMinutes))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createLogoutBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(logoutCapacity)
                .refillGreedy(logoutCapacity, Duration.ofMinutes(logoutRefillMinutes))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String extractClientIp(HttpServletRequest httpRequest) {
        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            return httpRequest.getRemoteAddr();
        }
        return ip.split(",")[0].trim();
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token, HttpServletRequest httpRequest) {
        String ip = extractClientIp(httpRequest);
        Bucket bucket = verifyEmailBuckets.get(ip, k -> createVerifyEmailBucket());
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new MessageResponseDTO("Too many verification attempts. Please try again later."));
        }
        String status = authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("status", status));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ResendVerificationRequestDTO request, HttpServletRequest httpRequest) {
        String email = request.email();
        String ip = extractClientIp(httpRequest);
        String ipEmailKey = ip + ":" + email;
        Bucket bucket = resendBuckets.get(ipEmailKey, k -> createResendBucket());
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new MessageResponseDTO("Too many resend attempts. Please try again later."));
        }
        authService.resendVerification(email);
        return ResponseEntity.ok(Map.of(
            "message", "If the email exists and isn't verified, a verification email has been sent."
        ));
    }

    @Operation(summary = "Refresh access token", description = "Idempotency Note: Clients must persist the new token before discarding the old one to avoid session revocation on retry.")
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody TokenRefreshRequestDTO request, HttpServletRequest httpRequest) {
        String ip = extractClientIp(httpRequest);
        String deviceInfo = httpRequest.getHeader("User-Agent");
        Bucket bucket = refreshBuckets.get(ip, k -> createRefreshBucket());
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new MessageResponseDTO("Too many refresh attempts. Please try again later."));
        }

        String requestRefreshToken = request.getRefreshToken();
        RefreshToken originalToken = refreshTokenService.findByRawToken(requestRefreshToken);
        String originalDeviceInfo = (originalToken != null) ? originalToken.getDeviceInfo() : null;

        User user;
        try {
            user = refreshTokenService.verifyToken(requestRefreshToken);
        } catch (TokenRefreshException e) {
            securityAuditService.record("TOKEN_REFRESH_FAILED", null, null, ip, deviceInfo, e.getMessage(), false);
            throw e;
        }
        
        if (originalDeviceInfo != null && deviceInfo != null && !originalDeviceInfo.equals(deviceInfo)) {
            log.warn("Device change detected during token refresh for user {}: original='{}', current='{}'",
                    user.getUsername(), originalDeviceInfo, deviceInfo);
            securityAuditService.record("TOKEN_REFRESH_DEVICE_CHANGE", user.getId(), user.getUsername(), ip, deviceInfo,
                    Map.of("originalDevice", originalDeviceInfo, "newDevice", deviceInfo), true);
        }
        
        String tokenId = UUID.randomUUID().toString();
        String newAccessToken = jwtUtil.generateAccessToken(user, tokenId);
        String chainDeviceInfo = (originalDeviceInfo != null) ? originalDeviceInfo : deviceInfo;
        String newRefreshToken = refreshTokenService.createRefreshChain(user.getId(), chainDeviceInfo, tokenId);
        
        securityAuditService.record("TOKEN_REFRESH", user.getId(), user.getUsername(), ip, deviceInfo, null, true);
        
        return ResponseEntity.ok(new JwtResponseDTO(
            newAccessToken, 
            newRefreshToken, 
            jwtUtil.getExpirationMs() / 1000, 
            jwtUtil.getRefreshExpirationMs() / 1000,
            UserResponseDTO.from(user)
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(@Valid @RequestBody TokenRefreshRequestDTO request, HttpServletRequest httpRequest) {
        String ip = extractClientIp(httpRequest);
        String deviceInfo = httpRequest.getHeader("User-Agent");
        Bucket bucket = logoutBuckets.get(ip, k -> createLogoutBucket());
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new MessageResponseDTO("Too many logout attempts. Please try again later."));
        }

        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            try {
                String accessTokenId = jwtUtil.extractTokenId(accessToken);
                tokenDenylistService.denyToken(accessTokenId);
            } catch (Exception e) {
                log.debug("Could not extract tokenId for denylist on logout: {}", e.getMessage());
            }
        }

        String username = refreshTokenService.findUsernameByRawToken(request.getRefreshToken());
        if (username == null) {
            securityAuditService.record("LOGOUT_FAILED", null, null, ip, deviceInfo, "Invalid or already-revoked refresh token", false);
            return ResponseEntity.badRequest()
                .body(new MessageResponseDTO("Invalid or expired refresh token."));
        }
        
        realtimeBroadcaster.forceDisconnect(username);
        User user = userService.findByUsername(username).orElse(null);
        Long userId = user != null ? user.getId() : null;
        
        refreshTokenService.deleteByToken(request.getRefreshToken());
        securityAuditService.record("LOGOUT", userId, username, ip, deviceInfo, null, true);
        return ResponseEntity.ok(new MessageResponseDTO("Log out successful!"));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(@RequestBody(required = false) TokenRefreshRequestDTO request,
                                        HttpServletRequest httpRequest) {
        String ip = extractClientIp(httpRequest);
        String deviceInfo = httpRequest.getHeader("User-Agent");
        Bucket bucket = logoutBuckets.get(ip, k -> createLogoutBucket());
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new MessageResponseDTO("Too many logout attempts. Please try again later."));
        }

        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new MessageResponseDTO("Authentication required."));
        }
        String accessToken = authHeader.substring(7);

        String username;
        User user;
        try {
            username = jwtUtil.extractUsername(accessToken);
            user = userService.findByUsername(username).orElse(null);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new MessageResponseDTO("Invalid or expired access token."));
        }
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new MessageResponseDTO("User not found."));
        }

        userProfileService.logoutAll(user);

        try {
            String accessTokenId = jwtUtil.extractTokenId(accessToken);
            tokenDenylistService.denyToken(accessTokenId);
        } catch (Exception e) {
            log.debug("Could not extract tokenId for denylist on logout-all: {}", e.getMessage());
        }

        realtimeBroadcaster.forceDisconnect(username);
        securityAuditService.record("LOGOUT_ALL", user.getId(), username, ip, deviceInfo,
            java.util.Map.of("reason", "user-initiated logout-all"), true);

        return ResponseEntity.ok(new MessageResponseDTO("Signed out of all sessions successfully."));
    }
}
