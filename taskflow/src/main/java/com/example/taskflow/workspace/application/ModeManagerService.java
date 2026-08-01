package com.example.taskflow.workspace.application;

import com.example.taskflow.workspace.domain.Mode;
import com.example.taskflow.user.application.SessionMemoryService;
import com.example.taskflow.user.domain.SessionMemory;
import com.example.taskflow.integration.websocket.RealtimeBroadcaster;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ModeManagerService {

    private final SessionMemoryService sessionMemoryService;
    private final RealtimeBroadcaster broadcaster;

    public void setMode(Long userId, String username, Mode mode) {
        sessionMemoryService.saveMemoryState(userId, null, null, null, null, null, mode);
        broadcaster.sendToUser(username, "/queue/workspace-mode", mode.name());
    }

    public Mode getMode(Long userId) {
        SessionMemory memory = sessionMemoryService.getMemoryState(userId);
        return memory != null ? memory.getMode() : Mode.NORMAL;
    }
}
