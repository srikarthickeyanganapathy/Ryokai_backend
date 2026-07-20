package com.example.taskflow.controller;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.taskflow.domain.PasswordResetToken;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.ForgotPasswordRequestDTO;
import com.example.taskflow.dto.ResetPasswordRequestDTO;
import com.example.taskflow.service.UserService;
import com.example.taskflow.service.EmailService;
import com.example.taskflow.service.PasswordResetService;

import jakarta.validation.Valid;

@RestController
@RequestMapping(value = "/api/auth", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final UserService userService;
    private final EmailService emailService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public PasswordResetController(PasswordResetService passwordResetService, UserService userService, EmailService emailService) {
        this.passwordResetService = passwordResetService;
        this.userService = userService;
        this.emailService = emailService;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody @Valid ForgotPasswordRequestDTO req) {
        // ALWAYS return 200 with the same message, regardless of email existence (Anti-enumeration)
        Optional<User> userOpt = userService.findByEmail(req.email());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            PasswordResetToken token = passwordResetService.createToken(user);
            String resetLink = frontendUrl + "/reset-password?token=" + token.getRawToken();
            emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetLink);
        } else {
            // Simulate timing delay slightly if needed, though bcrypt is not used for email lookup so it's fast
            try {
                // simple simulated delay
                Thread.sleep((long)(Math.random() * 20));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        return ResponseEntity.ok(new com.example.taskflow.dto.MessageResponseDTO(
            "If that email exists, a reset link has been sent."
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody @Valid ResetPasswordRequestDTO req) {
        passwordResetService.resetPassword(req.token(), req.newPassword());
        return ResponseEntity.ok(new com.example.taskflow.dto.MessageResponseDTO(
            "Password has been reset successfully. Please login with your new password."
        ));
    }
}
