package com.example.taskflow.task.api.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.example.taskflow.project.dto.ProjectResponseDTO;
import com.example.taskflow.crew.dto.CrewChannelDTO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrewContextDTO {
    private String activeEmptyStateMessage;
    private List<TaskResponseDTO> activeTasks;
    private List<ProjectResponseDTO> projects;
    private List<CrewChannelDTO> channels;
    private List<String> recentActivity; 
}
