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

    @org.springframework.transaction.annotation.Transactional
    public void setManager(Long userId, Long managerId) {
        if (userId.equals(managerId)) {
            throw new IllegalArgumentException("A user cannot be their own manager");
        }
        
        User user = getUserById(userId);
        
        if (managerId == null) {
            user.setManager(null);
            userRepository.save(user);
            return;
        }

        User manager = getUserById(managerId);

        // Detect cycle: traverse up the manager chain (max 10 hops to prevent infinite loops)
        User currentManager = manager.getManager();
        int hops = 0;
        while (currentManager != null) {
            if (currentManager.getId().equals(userId)) {
                throw new IllegalArgumentException("Cannot set manager: creates a reporting cycle");
            }
            if (hops++ > 10) break; // Defensive bound
            currentManager = currentManager.getManager();
        }

        user.setManager(manager);
        userRepository.save(user);
    }
}
