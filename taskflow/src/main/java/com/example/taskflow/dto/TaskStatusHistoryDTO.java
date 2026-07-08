package com.example.taskflow.dto;

import java.time.LocalDateTime;

public class TaskStatusHistoryDTO {
    private Long id;
    private String status;
    private String changedByUsername;
    private LocalDateTime changedAt;

    public TaskStatusHistoryDTO() {}

    public TaskStatusHistoryDTO(Long id, String status, String changedByUsername, LocalDateTime changedAt) {
        this.id = id;
        this.status = status;
        this.changedByUsername = changedByUsername;
        this.changedAt = changedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getChangedByUsername() { return changedByUsername; }
    public void setChangedByUsername(String changedByUsername) { this.changedByUsername = changedByUsername; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
}
