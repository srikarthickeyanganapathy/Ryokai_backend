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
import com.example.taskflow.repository.RoleRepository;
import com.example.taskflow.repository.UserRepository;
import com.example.taskflow.service.AuthService;
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
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
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

    @Value("${ratelimit.resend.capacity:5}")
    private int resendCapacity;

    @Value("${ratelimit.resend.refill-minutes:60}")
    private int resendRefillMinutes;

    @Value("${ratelimit.register.capacity:5}")
    private int registerCapacity;

    @Value("${ratelimit.register.refill-minutes:60}")
    private int registerRefillMinutes;

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

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil,
                          UserRepository userRepository, RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService,
                          UserProfileService userProfileService, EmailService emailService,
                          RealtimeBroadcaster realtimeBroadcaster,
                          AuthService authService,
                          SecurityAuditService securityAuditService,
                          TokenDenylistService tokenDenylistService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
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

    // Per (IP + email) bucket for resend verification
    private final Cache<String, Bucket> resendBuckets = Caffeine.newBuilder()
            .expireAfterAccess(60, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    // Per-IP bucket for /register (prevents mass account creation)
    private final Cache<String, Bucket> registerBuckets = Caffeine.newBuilder()
            .expireAfterAccess(60, TimeUnit.MINUTES)
            .maximumSize(50_000)
            .build();

    // Per-IP bucket for /refresh (prevents brute-force / DB load)
    private final Cache<String, Bucket> refreshBuckets = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(50_000)
            .build();

    // Per-IP bucket for /verify-email (prevents brute-force of verification tokens)
    private final Cache<String, Bucket> verifyEmailBuckets = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(50_000)
            .build();

    // Per-IP bucket for /logout (prevents DB load from random token spam)
    private final Cache<String, Bucket> logoutBuckets = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
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

    private Bucket createResendBucket() {
        // refillGreedy(N, D) adds tokens continuously at a rate of N/D
        // (e.g. 5 per 60 min  -  1 token every 12 min, but added fractionally, not discretely)
        Bandwidth limit = Bandwidth.builder()
                .capacity(resendCapacity)
                .refillGreedy(resendCapacity, Duration.ofMinutes(resendRefillMinutes))
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
        User user = userRepository.findByUsername(request.getUsername())
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

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token, HttpServletRequest httpRequest) {
        String ip = extractClientIp(httpRequest);

        // Rate-limit verification attempts per IP to prevent brute-force of verification tokens
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

        // Rate-limit by IP+email: prevents an attacker from blocking a legitimate user's
        // resend capability, and avoids leaking email existence via per-email bucket exhaustion
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

    /**
     * Refresh access token.
     * Idempotency Note: Clients should persist the new refresh token pair before discarding the old one.
     * Retrying a request with an already-used refresh token will result in all sessions being revoked.
     */
    @Operation(summary = "Refresh access token", description = "Idempotency Note: Clients must persist the new token before discarding the old one to avoid session revocation on retry.")
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody TokenRefreshRequestDTO request, HttpServletRequest httpRequest) {
        String ip = extractClientIp(httpRequest);
        String deviceInfo = httpRequest.getHeader("User-Agent");

        // Rate-limit refresh attempts per IP
        Bucket bucket = refreshBuckets.get(ip, k -> createRefreshBucket());
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new MessageResponseDTO("Too many refresh attempts. Please try again later."));
        }

        String requestRefreshToken = request.getRefreshToken();

        // Retrieve original device info BEFORE verifyToken marks the token as used
        RefreshToken originalToken = refreshTokenService.findByRawToken(requestRefreshToken);
        String originalDeviceInfo = (originalToken != null) ? originalToken.getDeviceInfo() : null;

        User user;
        try {
            user = refreshTokenService.verifyToken(requestRefreshToken);
        } catch (TokenRefreshException e) {
            // Audit failed refresh attempts for security observability
            securityAuditService.record("TOKEN_REFRESH_FAILED", null, null, ip, deviceInfo, e.getMessage(), false);
            throw e;
        }
        
        // NOTE: We do NOT delete the old token immediately. verifyToken() has already
        // marked it as used=true atomically. Keeping the used row allows reuse-detection
        // to trigger "revoke all sessions" if an attacker replays this token.
        // Stale used tokens are cleaned up by the scheduled purge job.

        // Detect device change  -  flag as suspicious if User-Agent differs from the
        // device that originally obtained this refresh token (possible token theft)
        if (originalDeviceInfo != null && deviceInfo != null && !originalDeviceInfo.equals(deviceInfo)) {
            log.warn("Device change detected during token refresh for user {}: original='{}', current='{}'",
                    user.getUsername(), originalDeviceInfo, deviceInfo);
            securityAuditService.record("TOKEN_REFRESH_DEVICE_CHANGE", user.getId(), user.getUsername(), ip, deviceInfo,
                    Map.of("originalDevice", originalDeviceInfo, "newDevice", deviceInfo), true);
        }
        
        String tokenId = UUID.randomUUID().toString();
        String newAccessToken = jwtUtil.generateAccessToken(user, tokenId);
        
        // Preserve original device info for the new token chain so audit trail
        // reflects the session's originating device, not the refreshing device
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

        // Rate-limit logout attempts per IP to prevent DB load from random token spam
        Bucket bucket = logoutBuckets.get(ip, k -> createLogoutBucket());
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new MessageResponseDTO("Too many logout attempts. Please try again later."));
        }

        // Deny the current access token (from Authorization header) so it cannot be
        // used for the remainder of its TTL. This closes the stateless-JWT gap where
        // a stolen access token would remain valid after logout.
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            try {
                String accessTokenId = jwtUtil.extractTokenId(accessToken);
                tokenDenylistService.denyToken(accessTokenId);
            } catch (Exception e) {
                // Best-effort: if token is already expired or malformed, skip
                log.debug("Could not extract tokenId for denylist on logout: {}", e.getMessage());
            }
        }

        String username = refreshTokenService.findUsernameByRawToken(request.getRefreshToken());
        
        if (username == null) {
            // Token not found / invalid / already revoked  -  audit the failed attempt
            securityAuditService.record("LOGOUT_FAILED", null, null, ip, deviceInfo, "Invalid or already-revoked refresh token", false);
            return ResponseEntity.badRequest()
                .body(new MessageResponseDTO("Invalid or expired refresh token."));
        }
        
        realtimeBroadcaster.forceDisconnect(username);
        User user = userRepository.findByUsername(username).orElse(null);
        Long userId = user != null ? user.getId() : null;
        
        // Delete the specific token using its raw value
        refreshTokenService.deleteByToken(request.getRefreshToken());
        
        securityAuditService.record("LOGOUT", userId, username, ip, deviceInfo, null, true);
        return ResponseEntity.ok(new MessageResponseDTO("Log out successful!"));
    }

    /**
     * SEC-Min01 fix: logout-all endpoint.
     * Spec implies token_version should be incrementable on "logout-all / password change".
     * Previously only changePassword and resetPassword incremented token_version  - 
     * a user who suspected compromise could not invalidate all other sessions'
     * access tokens without changing their password.
     *
     * This endpoint:
     *   1. Increments user.tokenVersion (invalidates ALL access tokens immediately)
     *   2. Deletes ALL refresh tokens for the user (forces re-login on every device)
     *   3. Denylists the current access token (so the caller's own session ends now)
     *
     * One-click "sign out everywhere" without forcing a password change.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(@RequestBody(required = false) TokenRefreshRequestDTO request,
                                        HttpServletRequest httpRequest) {
        String ip = extractClientIp(httpRequest);
        String deviceInfo = httpRequest.getHeader("User-Agent");

        // Rate-limit logout-all per IP to prevent abuse
        Bucket bucket = logoutBuckets.get(ip, k -> createLogoutBucket());
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new MessageResponseDTO("Too many logout attempts. Please try again later."));
        }

        // Resolve the user from the current access token (in Authorization header)
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
            user = userRepository.findByUsername(username).orElse(null);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new MessageResponseDTO("Invalid or expired access token."));
        }
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new MessageResponseDTO("User not found."));
        }

        // 1. Increment token_version  -  invalidates ALL access tokens immediately
        // 2. Delete ALL refresh tokens for the user
        userProfileService.logoutAll(user);

        // 3. Denylist the current access token so the caller's own session ends now
        try {
            String accessTokenId = jwtUtil.extractTokenId(accessToken);
            tokenDenylistService.denyToken(accessTokenId);
        } catch (Exception e) {
            log.debug("Could not extract tokenId for denylist on logout-all: {}", e.getMessage());
        }

        // Force-disconnect any WebSocket sessions for this user
        realtimeBroadcaster.forceDisconnect(username);

        securityAuditService.record("LOGOUT_ALL", user.getId(), username, ip, deviceInfo,
            java.util.Map.of("reason", "user-initiated logout-all"), true);

        return ResponseEntity.ok(new MessageResponseDTO("Signed out of all sessions successfully."));
    }
}