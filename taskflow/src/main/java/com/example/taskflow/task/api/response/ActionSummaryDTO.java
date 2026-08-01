package com.example.taskflow.task.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionSummaryDTO {
    private String id;
    private String type;
    private String title;
    private String message;
    private int count; // for grouped signals
}
