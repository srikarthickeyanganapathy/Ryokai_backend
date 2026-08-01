package com.example.taskflow.task.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyBriefDTO {
    private String greeting;
    private int focusTasksCount;
    private int remindersCount;
    private int meetingsCount;
    private int completionStreak;
    private String streakMessage;
}
