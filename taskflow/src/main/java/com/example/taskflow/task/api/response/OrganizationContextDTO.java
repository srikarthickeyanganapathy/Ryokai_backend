package com.example.taskflow.task.api.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.example.taskflow.project.dto.ProjectResponseDTO;
import com.example.taskflow.team.dto.TeamResponseDTO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationContextDTO {
    private OrgInsightsDTO insights;
    private List<ProjectResponseDTO> projects;
    private List<TeamResponseDTO> teams;
}
