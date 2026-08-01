package com.example.taskflow.task.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WidgetVisibilityDTO {
    private boolean showFocusPanel;
    private boolean showExecutionQueue;
    private boolean showContextRail;
    private boolean showDailyBrief;
}
