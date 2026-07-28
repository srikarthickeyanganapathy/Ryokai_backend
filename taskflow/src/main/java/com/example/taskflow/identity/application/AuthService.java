package com.example.taskflow.identity.application;

import com.example.taskflow.identity.dto.JwtResponseDTO;
import com.example.taskflow.identity.dto.RegisterRequestDTO;

public interface AuthService {
    JwtResponseDTO register(RegisterRequestDTO request, String deviceInfo, String ip);
    String verifyEmail(String token);
    void resendVerification(String email);
}
