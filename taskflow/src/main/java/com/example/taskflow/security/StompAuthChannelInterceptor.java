package com.example.taskflow.security;

import com.example.taskflow.util.JwtUtil;
import com.example.taskflow.service.CustomUserDetailsService;
import com.example.taskflow.domain.User;
import com.example.taskflow.domain.Task;
import com.example.taskflow.repository.UserRepository;
import com.example.taskflow.repository.TaskRepository;
import com.example.taskflow.repository.WhiteboardRepository;
import com.example.taskflow.repository.CrewMemberRepository;
import com.example.taskflow.domain.Whiteboard;
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
    private final RoleStrategyFactory roleStrategyFactory;
    private final WhiteboardRepository whiteboardRepository;
    private final CrewMemberRepository crewMemberRepository;

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
                    Long taskId = Long.parseLong(destination.substring("/topic/tasks/".length()));
                    
                    if (accessor.getUser() == null) {
                        throw new org.springframework.security.access.AccessDeniedException("Unauthenticated websocket connection");
                    }
                    
                    String username = accessor.getUser().getName();
                    User user = userRepository.findByUsername(username)
                            .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("User not found"));
                    Task task = taskRepository.findById(taskId)
                            .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Task not found"));
                    
                    if (!roleStrategyFactory.getStrategy(user).canViewTask(user, task)) {
                        throw new org.springframework.security.access.AccessDeniedException("Not authorized to subscribe to this task updates");
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
        }
        
        return message;
    }
}
