package com.example.taskflow.task.api.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalStripDTO {
    private List<ActionSummaryDTO> actions;
    private int totalRequiredActions;
}
