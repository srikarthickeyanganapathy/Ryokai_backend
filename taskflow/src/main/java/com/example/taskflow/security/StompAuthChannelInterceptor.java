package com.example.taskflow.security;

import com.example.taskflow.security.jwt.JwtUtil;
import com.example.taskflow.identity.application.CustomUserDetailsService;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.user.infrastructure.persistence.UserRepository;
import com.example.taskflow.task.infrastructure.persistence.TaskRepository;
import com.example.taskflow.whiteboard.infrastructure.WhiteboardRepository;
import com.example.taskflow.crew.infrastructure.persistence.CrewMemberRepository;
import com.example.taskflow.whiteboard.domain.Whiteboard;
import com.example.taskflow.security.authorization.CustomPermissionEvaluator;
import org.springframework.security.core.Authentication;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final WhiteboardRepository whiteboardRepository;
    private final CrewMemberRepository crewMemberRepository;
    private final CustomPermissionEvaluator permissionEvaluator;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            
            if (token == null || !token.startsWith("Bearer ")) {
                throw new AuthenticationCredentialsNotFoundException("No token in CONNECT");
            }
            
            token = token.substring(7);
            
            if (!jwtUtil.isAccessTokenValid(token)) {
                throw new AuthenticationCredentialsNotFoundException("Invalid token");
            }
            
            String username = jwtUtil.extractUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            
            // Set Principal using username as the principal name
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()));
                    
        } else if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/topic/tasks/")) {
                try {
                    String taskIdStr = destination.substring("/topic/tasks/".length());
                    
                    if (accessor.getUser() == null) {
                        throw new org.springframework.security.access.AccessDeniedException("Unauthenticated websocket connection");
                    }
                    
                    String username = accessor.getUser().getName();
                    User user = userRepository.findByUsername(username)
                            .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("User not found"));
                    
                    Long taskId = Long.parseLong(taskIdStr);
                    Task task = taskRepository.findById(taskId).orElse(null);
                    if (task == null) {
                        throw new org.springframework.security.access.AccessDeniedException("Task not found");
                    }
                    if (!permissionEvaluator.hasPermission((Authentication) accessor.getUser(), task, "TASK_VIEW")) {
                        throw new org.springframework.security.access.AccessDeniedException("Access denied for task " + taskId);
                    }
                } catch (NumberFormatException e) {
                    throw new org.springframework.security.access.AccessDeniedException("Invalid task ID format in destination");
                }
            } else if (destination != null && destination.startsWith("/topic/whiteboards/")) {
                try {
                    Long boardId = Long.parseLong(destination.substring("/topic/whiteboards/".length()));
                    
                    if (accessor.getUser() == null) {
                        throw new org.springframework.security.access.AccessDeniedException("Unauthenticated websocket connection");
                    }
                    
                    String username = accessor.getUser().getName();
                    User user = userRepository.findByUsername(username)
                            .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("User not found"));
                    
                    Whiteboard board = whiteboardRepository.findById(boardId)
                            .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Whiteboard not found"));
                            
                    boolean isMember = crewMemberRepository.existsByIdCrewIdAndIdUserId(board.getCrew().getId(), user.getId());
                    if (!isMember) {
                        throw new org.springframework.security.access.AccessDeniedException("Not a member of this crew");
                    }
                } catch (NumberFormatException e) {
                    throw new org.springframework.security.access.AccessDeniedException("Invalid board ID format");
                }
            }
        } else if (accessor != null && StompCommand.SEND.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && (destination.startsWith("/topic/") || destination.startsWith("/queue/"))) {
                throw new org.springframework.security.access.AccessDeniedException("Direct SEND to broker destinations is forbidden. Send via /app endpoints.");
            }
        }
        
        return message;
    }
}