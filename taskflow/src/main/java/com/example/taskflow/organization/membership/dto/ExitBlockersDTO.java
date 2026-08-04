package com.example.taskflow.organization.membership.dto;

import java.util.List;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ExitBlockersDTO {
    private int openTasksCount;
    private int ownedProjectsCount;
    private int teamLeadCount;
    private int pendingApprovalsCount;
    private List<String> details;
    private boolean canSubmit;
}
