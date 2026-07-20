package com.example.taskflow.dto;

import com.example.taskflow.domain.GoalStatus;
import java.time.LocalDate;
import java.util.List;

public class GoalDTOs {

    public static class KeyResultDTO {
        private Long id;
        private String title;
        private Double currentValue;
        private Double targetValue;
        private String unit;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Double getCurrentValue() { return currentValue; }
        public void setCurrentValue(Double currentValue) { this.currentValue = currentValue; }
        public Double getTargetValue() { return targetValue; }
        public void setTargetValue(Double targetValue) { this.targetValue = targetValue; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
    }

    public static class GoalRequestDTO {
        private String title;
        private String description;
        private String period;
        private GoalStatus status;
        private LocalDate startDate;
        private LocalDate endDate;
        private List<KeyResultDTO> keyResults;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
        public GoalStatus getStatus() { return status; }
        public void setStatus(GoalStatus status) { this.status = status; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
        public List<KeyResultDTO> getKeyResults() { return keyResults; }
        public void setKeyResults(List<KeyResultDTO> keyResults) { this.keyResults = keyResults; }
    }

    public static class GoalResponseDTO {
        private Long id;
        private String title;
        private String description;
        private String period;
        private String ownerUsername;
        private GoalStatus status;
        private Integer progress;
        private LocalDate startDate;
        private LocalDate endDate;
        private List<KeyResultDTO> keyResults;

        public GoalResponseDTO() {}

        public GoalResponseDTO(Long id, String title, String description, String period, String ownerUsername,
                                GoalStatus status, Integer progress, LocalDate startDate,
                                LocalDate endDate, List<KeyResultDTO> keyResults) {
            this.id = id; this.title = title; this.description = description; this.period = period;
            this.ownerUsername = ownerUsername; this.status = status; this.progress = progress;
            this.startDate = startDate; this.endDate = endDate; this.keyResults = keyResults;
        }

        public Long getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getPeriod() { return period; }
        public String getOwnerUsername() { return ownerUsername; }
        public GoalStatus getStatus() { return status; }
        public Integer getProgress() { return progress; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        public List<KeyResultDTO> getKeyResults() { return keyResults; }
    }
}
