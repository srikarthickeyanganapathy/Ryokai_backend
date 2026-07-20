package com.example.taskflow.controller;

import com.example.taskflow.dto.WhiteboardDTOs.DrawEventDTO;
import com.example.taskflow.service.WhiteboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class WhiteboardSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final WhiteboardService whiteboardService;

    @MessageMapping("/whiteboards/{boardId}/draw")
    public void handleDraw(@DestinationVariable Long boardId, DrawEventDTO event, Authentication authentication) {
        String username = authentication.getName();
        if (!whiteboardService.canDraw(boardId, username)) {
            return;
        }

        event.setUsername(username);
        messagingTemplate.convertAndSend("/topic/whiteboards/" + boardId, event);
    }
}
