package com.example.taskflow.dto;

import java.time.LocalDateTime;
import java.util.List;

public class TaskCommentDTO {
    private Long id;
    private String username;
    private String comment;
    private Long parentId;
    private List<TaskCommentDTO> replies;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public TaskCommentDTO() {}

    public TaskCommentDTO(Long id, String username, String comment, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public TaskCommentDTO(Long id, String username, String comment, Long parentId, List<TaskCommentDTO> replies, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.username = username;
        this.comment = comment;
        this.parentId = parentId;
        this.replies = replies;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public List<TaskCommentDTO> getReplies() { return replies; }
    public void setReplies(List<TaskCommentDTO> replies) { this.replies = replies; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
