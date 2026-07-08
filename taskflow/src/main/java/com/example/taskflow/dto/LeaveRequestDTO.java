package com.example.taskflow.dto;

import java.time.LocalDateTime;

public class LeaveRequestDTO {
    private Long id;
    private Long userId;
    private String username;
    private Long organizationId;
    private String organizationName;
    private String reason;
    private String status;       // PENDING, APPROVED, REJECTED
    private String adminComment;
    private String reviewedBy;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    public LeaveRequestDTO() {}

    public LeaveRequestDTO(Long id, Long userId, String username, Long organizationId,
                           String organizationName, String reason, String status,
                           String adminComment, String reviewedBy,
                           LocalDateTime createdAt, LocalDateTime reviewedAt) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.organizationId = organizationId;
        this.organizationName = organizationName;
        this.reason = reason;
        this.status = status;
        this.adminComment = adminComment;
        this.reviewedBy = reviewedBy;
        this.createdAt = createdAt;
        this.reviewedAt = reviewedAt;
    }

    // Getters/Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAdminComment() { return adminComment; }
    public void setAdminComment(String adminComment) { this.adminComment = adminComment; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
