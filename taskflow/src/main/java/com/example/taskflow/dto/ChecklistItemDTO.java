package com.example.taskflow.dto;

import java.time.LocalDateTime;

public class ChecklistItemDTO {
    private Long id;
    private String text;
    private Boolean isCompleted;
    private Integer displayOrder;
    private String createdBy;
    private LocalDateTime completedAt;

    public ChecklistItemDTO() {}

    public ChecklistItemDTO(Long id, String text, Boolean isCompleted, Integer displayOrder, String createdBy) {
        this.id = id;
        this.text = text;
        this.isCompleted = isCompleted;
        this.displayOrder = displayOrder;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Boolean getIsCompleted() { return isCompleted; }
    public void setIsCompleted(Boolean isCompleted) { this.isCompleted = isCompleted; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
