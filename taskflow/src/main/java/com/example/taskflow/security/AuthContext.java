package com.example.taskflow.security;

import org.springframework.stereotype.Component;
import org.springframework.security.core.userdetails.UserDetails;
import com.example.taskflow.domain.User;
import com.example.taskflow.service.UserService;

@Component
public class AuthContext {
    private final UserService userService;

    public AuthContext(UserService userService) {
        this.userService = userService;
    }

    public User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
    }
}
