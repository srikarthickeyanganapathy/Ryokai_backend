package com.example.taskflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry simpUserRegistry;

    @Async("realtimeExecutor")
    public void sendToUser(String username, String destination, Object payload) {
        try {
            messagingTemplate.convertAndSendToUser(username, destination, payload);
        } catch (Exception e) {
            log.warn("Failed to send WebSocket message to user {} at destination {}: {}", username, destination, e.getMessage());
        }
    }

    @Async("realtimeExecutor")
    public void broadcastTaskUpdate(Object taskDTO, Long taskId) {
        try {
            messagingTemplate.convertAndSend("/topic/tasks/" + taskId, taskDTO);
        } catch (Exception e) {
            log.warn("Failed to broadcast task update for task {}: {}", taskId, e.getMessage());
        }
    }

    @Async("realtimeExecutor")
    public void forceDisconnect(String username) {
        try {
            messagingTemplate.convertAndSendToUser(username, "/queue/force-disconnect", Map.of("reason", "logged_out"));
        } catch (Exception e) {
            log.warn("Failed to send force-disconnect message to user {}: {}", username, e.getMessage());
        }
    }
}
