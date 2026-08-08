package com.example.taskflow.note.api;

import java.time.LocalDateTime;
import java.util.List;

public class NoteDTOs {

    public static class NoteRequestDTO {
        private String title;
        private String content;
        private Boolean isPinned;
        private String color;
        private List<String> tags;
        private Long orgId;
        private Long crewId;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public Boolean getIsPinned() { return isPinned; }
        public void setIsPinned(Boolean isPinned) { this.isPinned = isPinned; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        public Long getOrgId() { return orgId; }
        public void setOrgId(Long orgId) { this.orgId = orgId; }
        public Long getCrewId() { return crewId; }
        public void setCrewId(Long crewId) { this.crewId = crewId; }
    }

    public static class NoteResponseDTO {
        private Long id;
        private String title;
        private String content;
        private Boolean isPinned;
        private String color;
        private List<String> tags;
        private Long orgId;
        private Long crewId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public NoteResponseDTO() {}

        public NoteResponseDTO(Long id, String title, String content, Boolean isPinned,
                                String color, List<String> tags, Long orgId, Long crewId,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {
            this.id = id; this.title = title; this.content = content;
            this.isPinned = isPinned; this.color = color;
            this.tags = tags; this.orgId = orgId; this.crewId = crewId;
            this.createdAt = createdAt; this.updatedAt = updatedAt;
        }

        public Long getId() { return id; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public Boolean getIsPinned() { return isPinned; }
        public String getColor() { return color; }
        public List<String> getTags() { return tags; }
        public Long getOrgId() { return orgId; }
        public Long getCrewId() { return crewId; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
    }
}
