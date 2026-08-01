package com.example.taskflow.task.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgInsightsDTO {
    private List<String> narrativeInsights;
    private int membersCount;
    private int teamsCount;
    private int projectsCount;
}
