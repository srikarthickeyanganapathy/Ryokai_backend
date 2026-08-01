package com.example.taskflow.task.api.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.example.taskflow.project.dto.ProjectResponseDTO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalContextDTO {
    private String todaySummary;
    private String waitingSummary;
    private String comingNextSummary;
    private List<TaskResponseDTO> activeTasks;
    private List<ProjectResponseDTO> projects;
}
