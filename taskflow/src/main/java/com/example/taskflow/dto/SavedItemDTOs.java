package com.example.taskflow.dto;

import com.example.taskflow.domain.SavedEntityType;
import java.time.LocalDateTime;

public class SavedItemDTOs {

    public static class SavedItemRequestDTO {
        private SavedEntityType entityType;
        private Long entityId;

        public SavedEntityType getEntityType() { return entityType; }
        public void setEntityType(SavedEntityType entityType) { this.entityType = entityType; }
        public Long getEntityId() { return entityId; }
        public void setEntityId(Long entityId) { this.entityId = entityId; }
    }

    public static class SavedItemResponseDTO {
        private Long id;
        private SavedEntityType entityType;
        private Long entityId;
        private String title;
        private String subtitle;
        private LocalDateTime savedAt;

        public SavedItemResponseDTO() {}

        public SavedItemResponseDTO(Long id, SavedEntityType entityType, Long entityId,
                                     String title, String subtitle, LocalDateTime savedAt) {
            this.id = id; this.entityType = entityType; this.entityId = entityId;
            this.title = title; this.subtitle = subtitle; this.savedAt = savedAt;
        }

        public Long getId() { return id; }
        public SavedEntityType getEntityType() { return entityType; }
        public Long getEntityId() { return entityId; }
        public String getTitle() { return title; }
        public String getSubtitle() { return subtitle; }
        public LocalDateTime getSavedAt() { return savedAt; }
    }
}
