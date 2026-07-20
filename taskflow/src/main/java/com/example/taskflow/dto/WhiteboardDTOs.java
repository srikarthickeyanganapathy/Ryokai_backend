package com.example.taskflow.dto;

import java.time.LocalDateTime;

public class WhiteboardDTOs {

    public static class WhiteboardRequestDTO {
        private String title;
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }

    public static class WhiteboardResponseDTO {
        private Long id;
        private String title;
        private String snapshotDataUrl;
        private String createdByUsername;
        private LocalDateTime updatedAt;

        public WhiteboardResponseDTO() {}

        public WhiteboardResponseDTO(Long id, String title, String snapshotDataUrl,
                                      String createdByUsername, LocalDateTime updatedAt) {
            this.id = id; this.title = title; this.snapshotDataUrl = snapshotDataUrl;
            this.createdByUsername = createdByUsername; this.updatedAt = updatedAt;
        }

        public Long getId() { return id; }
        public String getTitle() { return title; }
        public String getSnapshotDataUrl() { return snapshotDataUrl; }
        public String getCreatedByUsername() { return createdByUsername; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
    }

    // Ephemeral over-the-wire shape only — never persisted as its own row.
    public static class DrawEventDTO {
        private String type; // "stroke" | "clear"
        private String username;
        private java.util.List<double[]> points; // [[x,y], [x,y], ...]
        private String color;
        private Double strokeWidth;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public java.util.List<double[]> getPoints() { return points; }
        public void setPoints(java.util.List<double[]> points) { this.points = points; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public Double getStrokeWidth() { return strokeWidth; }
        public void setStrokeWidth(Double strokeWidth) { this.strokeWidth = strokeWidth; }
    }
}
