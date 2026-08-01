package com.example.taskflow.platform.api.dto;

import com.example.taskflow.task.api.response.HeaderDTO;
import com.example.taskflow.task.api.response.DailyBriefDTO;
import com.example.taskflow.task.api.response.FocusPanelDTO;
import com.example.taskflow.task.api.response.SignalStripDTO;
import com.example.taskflow.task.api.response.PersonalContextDTO;
import com.example.taskflow.task.api.response.CrewContextDTO;
import com.example.taskflow.task.api.response.OrganizationContextDTO;
import com.example.taskflow.task.api.response.ExecutionQueueDTO;
import com.example.taskflow.task.api.response.WidgetVisibilityDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionControlDTO {
    private String workspaceMode;
    private HeaderDTO header;
    private DailyBriefDTO dailyBrief;
    private FocusPanelDTO focusPanel;
    private SignalStripDTO signalStrip;
    private PersonalContextDTO personalContext;
    private CrewContextDTO crewContext;
    private OrganizationContextDTO organizationContext;
    private ExecutionQueueDTO executionQueue;
    private WidgetVisibilityDTO widgetVisibility;
    private String resumeContext;
}
