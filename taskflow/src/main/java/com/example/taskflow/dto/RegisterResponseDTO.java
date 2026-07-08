package com.example.taskflow.dto;

public class RegisterResponseDTO {
    private String message;
    private Long userId;

    public RegisterResponseDTO() {}
    public RegisterResponseDTO(String message, Long userId) {
        this.message = message;
        this.userId = userId;
    }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
