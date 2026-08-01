package com.example.taskflow.user.application;

import com.example.taskflow.user.domain.SessionMemory;
import com.example.taskflow.user.infrastructure.SessionMemoryRepository;
import com.example.taskflow.user.infrastructure.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class SessionMemoryService {

    private final SessionMemoryRepository sessionMemoryRepository;
    private final UserRepository userRepository;

    public SessionMemoryService(SessionMemoryRepository sessionMemoryRepository, UserRepository userRepository) {
        this.sessionMemoryRepository = sessionMemoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public SessionMemory saveMemoryState(Long userId, String lens, String filters, String drawer, String searches, String pinned, com.example.taskflow.workspace.domain.Mode mode) {
        SessionMemory memory = sessionMemoryRepository.findByUserId(userId).orElseGet(() -> {
            SessionMemory newMemory = new SessionMemory();
            newMemory.setUser(userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found")));
            return newMemory;
        });

        if (lens != null) memory.setLastWorkspaceLens(lens);
        if (filters != null) memory.setActiveFilters(filters);
        if (drawer != null) memory.setLastActiveDrawer(drawer);
        if (searches != null) memory.setRecentSearches(searches);
        if (pinned != null) memory.setPinnedItems(pinned);
        if (mode != null) memory.setMode(mode);
        memory.setUpdatedAt(LocalDateTime.now());

        return sessionMemoryRepository.save(memory);
    }

    @Transactional(readOnly = true)
    public SessionMemory getMemoryState(Long userId) {
        return sessionMemoryRepository.findByUserId(userId).orElse(null);
    }
}
