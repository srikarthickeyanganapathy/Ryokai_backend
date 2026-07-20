package com.example.taskflow.dto;

import java.time.LocalDateTime;

public class NoteDTOs {

    public static class NoteRequestDTO {
        private String title;
        private String content;
        private Boolean isPinned;
        private String color;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Boolean getIsPinned() { return isPinned; }
        public void setIsPinned(Boolean isPinned) { this.isPinned = isPinned; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
    }

    public static class NoteResponseDTO {
        private Long id;
        private String title;
        private String content;
        private Boolean isPinned;
        private String color;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public NoteResponseDTO() {}

        public NoteResponseDTO(Long id, String title, String content, Boolean isPinned,
                                String color, LocalDateTime createdAt, LocalDateTime updatedAt) {
            this.id = id; this.title = title; this.content = content;
            this.isPinned = isPinned; this.color = color;
            this.createdAt = createdAt; this.updatedAt = updatedAt;
        }

        public Long getId() { return id; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public Boolean getIsPinned() { return isPinned; }
        public String getColor() { return color; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
    }
}
