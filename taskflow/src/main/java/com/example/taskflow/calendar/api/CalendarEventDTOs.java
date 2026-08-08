package com.example.taskflow.calendar.api;

import java.time.LocalDateTime;

public class CalendarEventDTOs {

    public static class CalendarEventRequestDTO {
        private String title;
        private String description;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Boolean isAllDay;
        private Long orgId;
        private Long crewId;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public Boolean getIsAllDay() { return isAllDay; }
        public void setIsAllDay(Boolean isAllDay) { this.isAllDay = isAllDay; }
        public Long getOrgId() { return orgId; }
        public void setOrgId(Long orgId) { this.orgId = orgId; }
        public Long getCrewId() { return crewId; }
        public void setCrewId(Long crewId) { this.crewId = crewId; }
    }

    public static class CalendarEventResponseDTO {
        private Long id;
        private String title;
        private String description;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Boolean isAllDay;
        private Long orgId;
        private Long crewId;

        public CalendarEventResponseDTO() {}

        public CalendarEventResponseDTO(Long id, String title, String description,
                                         LocalDateTime startTime, LocalDateTime endTime,
                                         Boolean isAllDay, Long orgId, Long crewId) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.startTime = startTime;
            this.endTime = endTime;
            this.isAllDay = isAllDay;
            this.orgId = orgId;
            this.crewId = crewId;
        }

        public Long getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public LocalDateTime getStartTime() { return startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public Boolean getIsAllDay() { return isAllDay; }
        public Long getOrgId() { return orgId; }
        public Long getCrewId() { return crewId; }
    }
}
