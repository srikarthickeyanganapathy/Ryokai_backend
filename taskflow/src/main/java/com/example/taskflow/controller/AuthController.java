package com.example.taskflow.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.taskflow.util.JwtUtil;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.LoginRequestDTO;
import com.example.taskflow.dto.RegisterRequestDTO;
import com.example.taskflow.dto.JwtResponseDTO;
import com.example.taskflow.dto.UserResponseDTO;
import com.example.taskflow.dto.TokenRefreshRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.taskflow.service.RefreshTokenService;
import com.example.taskflow.repository.RoleRepository;
import com.example.taskflow.repository.UserRepository;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;

// existing imports
import com.example.taskflow.service.UserProfileService;
import com.example.taskflow.service.EmailService;

@RestController
@RequestMapping(value = "/api/auth", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
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
    private final com.example.taskflow.service.RealtimeBroadcaster realtimeBroadcaster;
    private final com.example.taskflow.service.AuthService authService;

    @Value("${app.email.send-welcome:true}")
    private boolean sendWelcomeEmail;

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil,
                          UserRepository userRepository, RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService,
                          UserProfileService userProfileService, EmailService emailService,
                          com.example.taskflow.service.RealtimeBroadcaster realtimeBroadcaster,
                          com.example.taskflow.service.AuthService authService) {
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
    }

    private final com.github.benmanes.caffeine.cache.Cache<String, io.github.bucket4j.Bucket> loginBuckets = com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .expireAfterAccess(15, java.util.concurrent.TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    private io.github.bucket4j.Bucket createLoginBucket() {
        io.github.bucket4j.Bandwidth limit = io.github.bucket4j.Bandwidth.builder()
                .capacity(10)
                .refillGreedy(10, java.time.Duration.ofMinutes(15))
                .build();
        return io.github.bucket4j.Bucket.builder().addLimit(limit).build();
    }

    private final com.github.benmanes.caffeine.cache.Cache<String, io.github.bucket4j.Bucket> resendBuckets = com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .expireAfterAccess(60, java.util.concurrent.TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    private io.github.bucket4j.Bucket createResendBucket() {
        // max 5 per hour, refill 1 every 12 minutes
        io.github.bucket4j.Bandwidth limit = io.github.bucket4j.Bandwidth.builder()
                .capacity(5)
                .refillGreedy(5, java.time.Duration.ofHours(1))
                .build();
        return io.github.bucket4j.Bucket.builder().addLimit(limit).build();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDTO request, HttpServletRequest httpRequest) {
        io.github.bucket4j.Bucket bucket = loginBuckets.get(request.getUsername(), k -> createLoginBucket());
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                .body(new com.example.taskflow.dto.MessageResponseDTO("Too many login attempts for this user. Please try again later."));
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (Exception e) {
            throw new com.example.taskflow.exception.InvalidCredentialsException("Invalid username or password");
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found"));
        String tokenId = java.util.UUID.randomUUID().toString();
        String accessToken = jwtUtil.generateAccessToken(user, tokenId);
        
        String deviceInfo = httpRequest.getHeader("User-Agent");
        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = httpRequest.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }

        String refreshToken = refreshTokenService.createRefreshChain(user.getId(), deviceInfo, tokenId);
        
        try {
            userProfileService.recordLoginTime(user.getUsername(), ip, deviceInfo);
        } catch (Exception e) {
            // Log and ignore to prevent blocking login
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
        String deviceInfo = httpRequest.getHeader("User-Agent");
        JwtResponseDTO response = authService.register(request, deviceInfo);

        // Extracting userId from response is not directly available, but we can return OK.
        // Assuming location header is nice, we might just return 201 with the tokens.
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
            .body(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@org.springframework.web.bind.annotation.RequestParam String token) {
        String status = authService.verifyEmail(token);
        return ResponseEntity.ok(java.util.Map.of("status", status));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody java.util.Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "Email is required"));
        }

        io.github.bucket4j.Bucket bucket = resendBuckets.get(email, k -> createResendBucket());
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                .body(new com.example.taskflow.dto.MessageResponseDTO("Too many resend attempts. Please try again later."));
        }

        authService.resendVerification(email);
        return ResponseEntity.ok(java.util.Map.of(
            "message", "If the email exists and isn't verified, a verification email has been sent."
        ));
    }

    /**
     * Refresh access token.
     * Idempotency Note: Clients should persist the new refresh token pair before discarding the old one.
     * Retrying a request with an already-used refresh token will result in all sessions being revoked.
     */
    @io.swagger.v3.oas.annotations.Operation(summary = "Refresh access token", description = "Idempotency Note: Clients must persist the new token before discarding the old one to avoid session revocation on retry.")
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody TokenRefreshRequestDTO request, HttpServletRequest httpRequest) {
        String requestRefreshToken = request.getRefreshToken();

        User user = refreshTokenService.verifyToken(requestRefreshToken);
        
        String tokenId = java.util.UUID.randomUUID().toString();
        String newAccessToken = jwtUtil.generateAccessToken(user, tokenId);
        String deviceInfo = httpRequest.getHeader("User-Agent");
        String newRefreshToken = refreshTokenService.createRefreshChain(user.getId(), deviceInfo, tokenId);
        
        return ResponseEntity.ok(new JwtResponseDTO(
            newAccessToken, 
            newRefreshToken, 
            jwtUtil.getExpirationMs() / 1000, 
            jwtUtil.getRefreshExpirationMs() / 1000,
            UserResponseDTO.from(user)
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser(@Valid @RequestBody TokenRefreshRequestDTO request) {
        String username = refreshTokenService.findUsernameByRawToken(request.getRefreshToken());
        if (username != null) {
            realtimeBroadcaster.forceDisconnect(username);
        }
        
        // We delete the specific token using its raw value
        refreshTokenService.deleteByToken(request.getRefreshToken());
        return ResponseEntity.ok(new com.example.taskflow.dto.MessageResponseDTO("Log out successful!"));
    }
}