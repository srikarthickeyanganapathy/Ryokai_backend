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
public class ExecutionContextDTO {
    private TaskResponseDTO focusRecommendation;
    private List<TaskResponseDTO> queueOrdering;
    private List<TaskResponseDTO> suggestedActions;
    private String resumeContext;
}
