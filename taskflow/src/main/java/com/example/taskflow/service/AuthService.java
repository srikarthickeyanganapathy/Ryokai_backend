package com.example.taskflow.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.JwtResponseDTO;
import com.example.taskflow.dto.RegisterRequestDTO;
import com.example.taskflow.dto.UserResponseDTO;
import com.example.taskflow.exception.UsernameConflictException;
import com.example.taskflow.repository.UserRepository;
import com.example.taskflow.util.JwtUtil;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final SecurityAuditService securityAuditService;

    // sendWelcomeEmail was removed  -  it was injected but never referenced in this service.

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       EmailService emailService, JwtUtil jwtUtil, RefreshTokenService refreshTokenService,
                       SecurityAuditService securityAuditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jwtUtil = jwtUtil;
        this.refreshTokenService = refreshTokenService;
        this.securityAuditService = securityAuditService;
    }

    @Transactional
    public JwtResponseDTO register(RegisterRequestDTO request, String deviceInfo, String ip) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new UsernameConflictException("Username already exists");
        }

        User newUser = new User();
        newUser.setUsername(request.getUsername());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setEmail(request.getEmail());
        newUser.setEmailVerified(false);

        User savedUser = userRepository.save(newUser);

        // Generate and send email verification
        try {
            String verificationToken = jwtUtil.generateEmailVerificationToken(savedUser.getEmail(), savedUser.getId());
            emailService.sendEmailVerification(savedUser.getEmail(), savedUser.getUsername(), verificationToken);
        } catch (Exception e) {
            log.error("Failed to send verification email during registration for {}", savedUser.getEmail(), e);
        }

        String tokenId = UUID.randomUUID().toString();
        String accessToken = jwtUtil.generateAccessToken(savedUser, tokenId);
        String refreshToken = refreshTokenService.createRefreshChain(savedUser.getId(), deviceInfo, tokenId);
        
        securityAuditService.record("REGISTER", savedUser.getId(), savedUser.getUsername(), ip, deviceInfo, null, true);

        return new JwtResponseDTO(
            accessToken,
            refreshToken,
            jwtUtil.getExpirationMs() / 1000,
            jwtUtil.getRefreshExpirationMs() / 1000,
            UserResponseDTO.from(savedUser)
        );
    }

    @Transactional
    public String verifyEmail(String token) {
        try {
            Claims claims = jwtUtil.validateEmailVerificationToken(token);
            String email = claims.getSubject();
            Long uid = claims.get("uid", Long.class);

            User user = userRepository.findById(uid).orElse(null);
            if (user == null || !user.getEmail().equals(email)) {
                return "INVALID";
            }

            if (user.isEmailVerified()) {
                return "ALREADY_VERIFIED";
            }

            user.setEmailVerified(true);
            userRepository.save(user);
            return "VERIFIED";

        } catch (ExpiredJwtException e) {
            return "EXPIRED";
        } catch (JwtException e) {
            return "INVALID";
        }
    }

    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        
        // Generic return to prevent email enumeration (if null or already verified, just ignore silently)
        if (user == null || user.isEmailVerified()) {
            return;
        }

        try {
            String verificationToken = jwtUtil.generateEmailVerificationToken(user.getEmail(), user.getId());
            emailService.sendEmailVerification(user.getEmail(), user.getUsername(), verificationToken);
        } catch (Exception e) {
            log.error("Failed to resend verification email for {}", email, e);
        }
    }
}
