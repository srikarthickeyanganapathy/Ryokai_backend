package com.example.taskflow.workspace.application;

import com.example.taskflow.workspace.domain.Lens;
import com.example.taskflow.user.application.SessionMemoryService;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceRuntimeService {

    private final SessionMemoryService sessionMemoryService;

    public WorkspaceRuntimeService(SessionMemoryService sessionMemoryService) {
        this.sessionMemoryService = sessionMemoryService;
    }

    public void switchLens(Long userId, Lens lens) {
        sessionMemoryService.saveMemoryState(userId, lens.name(), null, null, null, null, null);
    }
}
