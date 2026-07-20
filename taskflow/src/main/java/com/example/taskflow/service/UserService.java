package com.example.taskflow.service;

import java.util.List;
import java.util.Optional;
import com.example.taskflow.domain.User;

public interface UserService {
    User getCurrentUser(String username);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    User getUserById(Long id);
    List<User> getTeamMembers(Long managerId);
    List<User> getAllUsers();
    List<User> getVisibleUsers(User caller);
    void setManager(Long userId, Long managerId);
}
