package com.example.taskflow.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.taskflow.domain.User;
import com.example.taskflow.exception.UserNotFoundException;
import com.example.taskflow.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    @Deprecated
    public List<User> getTeamMembers(Long managerId) {
        // In the new org/team model, team membership replaces the old manager hierarchy.
        // This method is retained for backward compatibility but now returns empty.
        return java.util.Collections.emptyList();
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}
