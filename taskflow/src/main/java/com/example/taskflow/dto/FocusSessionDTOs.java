package com.example.taskflow.dto;

import java.time.LocalDateTime;

public class FocusSessionDTOs {

    public static class FocusSessionStartRequestDTO {
        private Long taskId; // nullable — untethered focus session

        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }
    }

    public static class FocusSessionResponseDTO {
        private Long id;
        private Long taskId;
        private String taskTitle;
        private LocalDateTime startedAt;
        private LocalDateTime endedAt;
        private Long durationSeconds;

        public FocusSessionResponseDTO() {}

        public FocusSessionResponseDTO(Long id, Long taskId, String taskTitle,
                                        LocalDateTime startedAt, LocalDateTime endedAt,
                                        Long durationSeconds) {
            this.id = id;
            this.taskId = taskId;
            this.taskTitle = taskTitle;
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.durationSeconds = durationSeconds;
        }

        public Long getId() { return id; }
        public Long getTaskId() { return taskId; }
        public String getTaskTitle() { return taskTitle; }
        public LocalDateTime getStartedAt() { return startedAt; }
        public LocalDateTime getEndedAt() { return endedAt; }
        public Long getDurationSeconds() { return durationSeconds; }
    }
}
