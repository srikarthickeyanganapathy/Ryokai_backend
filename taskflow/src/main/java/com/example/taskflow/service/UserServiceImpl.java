package com.example.taskflow.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.taskflow.domain.User;
import com.example.taskflow.exception.UserNotFoundException;
import com.example.taskflow.repository.UserRepository;
import com.example.taskflow.repository.OrganizationMembershipRepository;
import com.example.taskflow.domain.OrganizationMembership;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;

    public UserServiceImpl(UserRepository userRepository, OrganizationMembershipRepository membershipRepository) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
    }

    @Override
    public User getCurrentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    @Override
    public java.util.Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public java.util.Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    @Override
    @Deprecated
    public List<User> getTeamMembers(Long managerId) {
        // In the new org/team model, team membership replaces the old manager hierarchy.
        // This method is retained for backward compatibility but now returns empty.
        return java.util.Collections.emptyList();
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<User> getVisibleUsers(User caller) {
        if (caller.isSuperAdmin()) {
            return userRepository.findAll();
        }
        List<OrganizationMembership> memberships = membershipRepository.findByUserId(caller.getId());
        if (memberships.isEmpty()) {
            return List.of(caller);
        }
        Long orgId = memberships.get(0).getOrganization().getId();
        return membershipRepository.findByOrganizationId(orgId).stream()
                .map(OrganizationMembership::getUser)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
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
