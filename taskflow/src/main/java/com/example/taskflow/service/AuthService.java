package com.example.taskflow.service;

import com.example.taskflow.dto.JwtResponseDTO;
import com.example.taskflow.dto.RegisterRequestDTO;

public interface AuthService {
    JwtResponseDTO register(RegisterRequestDTO request, String deviceInfo, String ip);
    String verifyEmail(String token);
    void resendVerification(String email);
}
