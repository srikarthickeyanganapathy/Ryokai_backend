package com.example.taskflow.controller;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.example.taskflow.domain.RefreshToken;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.JwtResponseDTO;
import com.example.taskflow.dto.LoginRequestDTO;
import com.example.taskflow.dto.MessageResponseDTO;
import com.example.taskflow.dto.RegisterRequestDTO;
import com.example.taskflow.dto.ResendVerificationRequestDTO;
import com.example.taskflow.dto.TokenRefreshRequestDTO;
import com.example.taskflow.dto.UserResponseDTO;
import com.example.taskflow.exception.InvalidCredentialsException;
import com.example.taskflow.exception.TokenRefreshException;
import com.example.taskflow.service.AuthService;
import com.example.taskflow.service.UserService;
import com.example.taskflow.service.EmailService;
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
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserProfileService userProfileService;
    private final EmailService emailService;
    private final RealtimeBroadcaster realtimeBroadcaster;
    private final AuthService authService;
    private final SecurityAuditService securityAuditService;
    private final TokenDenylistService tokenDenylistService;

    // --- Externalized rate-limit configuration (tunable via application.properties) ---

    @Value("${ratelimit.login.capacity:10}")
    private int loginCapacity;

    @Value("${ratelimit.login.refill-minutes:15}")
    private int loginRefillMinutes;

    @Value("${ratelimit.login-ip.capacity:50}")
    private int loginIpCapacity;

    @Value("${ratelimit.login-ip.refill-minutes:15}")
    private int loginIpRefillMinutes;

    @Value("${ratelimit.register.capacity:5}")
    private int registerCapacity;

    @Value("${ratelimit.register.refill-minutes:60}")
    private int registerRefillMinutes;

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil,
                          UserService userService,
                          PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService,
                          UserProfileService userProfileService, EmailService emailService,
                          RealtimeBroadcaster realtimeBroadcaster,
                          AuthService authService,
                          SecurityAuditService securityAuditService,
                          TokenDenylistService tokenDenylistService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.userProfileService = userProfileService;
        this.emailService = emailService;
        this.realtimeBroadcaster = realtimeBroadcaster;
        this.authService = authService;
        this.securityAuditService = securityAuditService;
        this.tokenDenylistService = tokenDenylistService;
    }

    // --- Rate limiting bucket caches ---
    // Caches hold one Bucket per key; buckets are created lazily via the create* methods.
    // The @Value-backed fields supply capacity/refill at creation time, so changes
    // take effect for newly-created buckets on the next deploy (no restart needed for
    // existing buckets since they expire from the cache after their access TTL).

    // Per (IP + username) bucket: prevents brute-force against a specific account
    private final Cache<String, Bucket> loginBuckets = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    // Per-IP global bucket: prevents distributed attacks from a single IP across many usernames
    private final Cache<String, Bucket> loginIpBuckets = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(50_000)
            .build();

    // Per-IP bucket for /register (prevents mass account creation)
    private final Cache<String, Bucket> registerBuckets = Caffeine.newBuilder()
            .expireAfterAccess(60, TimeUnit.MINUTES)
            .maximumSize(50_000)
            .build();

    // --- Bucket factory methods (use @Value-backed fields for capacity/refill) ---

    private Bucket createLoginBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(loginCapacity)
                .refillGreedy(loginCapacity, Duration.ofMinutes(loginRefillMinutes))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createLoginIpBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(loginIpCapacity)
                .refillGreedy(loginIpCapacity, Duration.ofMinutes(loginIpRefillMinutes))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createRegisterBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(registerCapacity)
                .refillGreedy(registerCapacity, Duration.ofMinutes(registerRefillMinutes))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Extract the client IP from the request, respecting X-Forwarded-For for proxied environments.
     */
    private String extractClientIp(HttpServletRequest httpRequest) {
        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            return httpRequest.getRemoteAddr();
        }
        return ip.split(",")[0].trim();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDTO request, HttpServletRequest httpRequest) {
        String deviceInfo = httpRequest.getHeader("User-Agent");
        String ip = extractClientIp(httpRequest);

        // Rate-limit by IP+username (prevents brute-force against a specific account
        // without letting an attacker lock out a user from a different IP)
        String ipUserKey = ip + ":" + request.getUsername();
        Bucket bucket = loginBuckets.get(ipUserKey, k -> createLoginBucket());
        Bucket ipBucket = loginIpBuckets.get(ip, k -> createLoginIpBucket());
        if (!bucket.tryConsume(1) || !ipBucket.tryConsume(1)) {
            securityAuditService.record("LOGIN_FAILED", null, request.getUsername(), ip, deviceInfo, "Too many login attempts", false);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new MessageResponseDTO("Too many login attempts. Please try again later."));
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (LockedException e) {
            securityAuditService.record("LOGIN_FAILED", null, request.getUsername(), ip, deviceInfo, "Account locked", false);
            throw e;
        } catch (DisabledException e) {
            securityAuditService.record("LOGIN_FAILED", null, request.getUsername(), ip, deviceInfo, "Account disabled", false);
            throw e;
        } catch (AccountExpiredException e) {
            securityAuditService.record("LOGIN_FAILED", null, request.getUsername(), ip, deviceInfo, "Account expired", false);
            throw e;
        } catch (CredentialsExpiredException e) {
            securityAuditService.record("LOGIN_FAILED", null, request.getUsername(), ip, deviceInfo, "Credentials expired", false);
            throw e;
        } catch (BadCredentialsException e) {
            securityAuditService.record("LOGIN_FAILED", null, request.getUsername(), ip, deviceInfo, "Invalid credentials", false);
            throw new InvalidCredentialsException("Invalid username or password");
        } catch (InternalAuthenticationServiceException e) {
            log.error("Internal authentication error for user {}: {}", request.getUsername(), e.getMessage(), e);
            securityAuditService.record("LOGIN_FAILED", null, request.getUsername(), ip, deviceInfo, "Internal authentication error", false);
            throw e;
        } catch (AuthenticationException e) {
            // Catch-all for any other AuthenticationException subtypes
            securityAuditService.record("LOGIN_FAILED", null, request.getUsername(), ip, deviceInfo, e.getClass().getSimpleName(), false);
            throw e;
        }

        // Extract the User from the Authentication result to avoid a redundant DB round-trip.
        // The AuthenticationManager already loaded the user via UserDetailsService during authenticate().
        User user = userService.findByUsername(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        String tokenId = UUID.randomUUID().toString();
        String accessToken = jwtUtil.generateAccessToken(user, tokenId);
        
        String refreshToken = refreshTokenService.createRefreshChain(user.getId(), deviceInfo, tokenId);
        
        securityAuditService.record("LOGIN_SUCCESS", user.getId(), user.getUsername(), ip, deviceInfo, null, true);
        
        try {
            userProfileService.recordLoginTime(user.getUsername(), ip, deviceInfo);
        } catch (DataAccessException e) {
            // Narrow catch: only suppress DB/persistence errors to prevent blocking login.
            // Other exceptions (NPE, IllegalState, etc.) should propagate as real bugs.
            log.warn("Failed to record login time for {}: {}", user.getUsername(), e.getMessage());
        }

        return ResponseEntity.ok(new JwtResponseDTO(
            accessToken, 
            refreshToken, 
            jwtUtil.getExpirationMs() / 1000, 
            jwtUtil.getRefreshExpirationMs() / 1000,
            UserResponseDTO.from(user)
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequestDTO request, HttpServletRequest httpRequest) {
        String ip = extractClientIp(httpRequest);
        String deviceInfo = httpRequest.getHeader("User-Agent");

        // Rate-limit registrations per IP to prevent mass account creation
        Bucket bucket = registerBuckets.get(ip, k -> createRegisterBucket());
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new MessageResponseDTO("Too many registration attempts. Please try again later."));
        }

        JwtResponseDTO response = authService.register(request, deviceInfo, ip);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(response);
    }

    // Session management endpoints moved to SessionController
}