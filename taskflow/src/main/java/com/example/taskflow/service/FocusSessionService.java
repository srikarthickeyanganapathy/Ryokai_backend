package com.example.taskflow.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.domain.FocusSession;
import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.FocusSessionDTOs.FocusSessionResponseDTO;
import com.example.taskflow.repository.FocusSessionRepository;
import com.example.taskflow.repository.TaskRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FocusSessionService {

    private static final long MAX_SESSION_HOURS = 8;

    private final FocusSessionRepository focusSessionRepository;
    private final TaskRepository taskRepository;

    @Transactional
    public FocusSessionResponseDTO start(User user, Long taskId) {
        // Auto-close any stale/active session first — enforces "one active
        // session at a time" without a separate error path.
        autoCloseIfActive(user);

        FocusSession session = new FocusSession();
        session.setUser(user);
        session.setStartedAt(LocalDateTime.now());

        if (taskId != null) {
            Task task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
            session.setTask(task);
        }

        FocusSession saved = focusSessionRepository.save(session);
        return toDto(saved);
    }

    @Transactional
    public FocusSessionResponseDTO stop(User user, Long sessionId) {
        FocusSession session = focusSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Focus session not found: " + sessionId));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Cannot stop another user's focus session");
        }
        if (session.getEndedAt() != null) {
            return toDto(session); // already stopped — idempotent
        }

        closeSession(session, LocalDateTime.now());
        return toDto(focusSessionRepository.save(session));
    }

    @Transactional
    public Optional<FocusSessionResponseDTO> getActive(User user) {
        return autoCloseIfActive(user).map(this::toDto);
    }

    public Page<FocusSessionResponseDTO> getHistory(User user, Pageable pageable) {
        return focusSessionRepository
                .findByUserIdOrderByStartedAtDesc(user.getId(), pageable)
                .map(this::toDto);
    }

    /**
     * Returns the user's active session if one exists and is still fresh.
     * If the active session has been running longer than MAX_SESSION_HOURS
     * (e.g. the user closed the browser without stopping it), it is
     * auto-closed at the max-duration mark and treated as no longer active.
     */
    private Optional<FocusSession> autoCloseIfActive(User user) {
        Optional<FocusSession> activeOpt = focusSessionRepository.findByUserIdAndEndedAtIsNull(user.getId());
        if (activeOpt.isEmpty()) return Optional.empty();

        FocusSession active = activeOpt.get();
        LocalDateTime cutoff = active.getStartedAt().plusHours(MAX_SESSION_HOURS);

        if (LocalDateTime.now().isAfter(cutoff)) {
            closeSession(active, cutoff);
            focusSessionRepository.save(active);
            return Optional.empty();
        }
        return Optional.of(active);
    }

    private void closeSession(FocusSession session, LocalDateTime endedAt) {
        session.setEndedAt(endedAt);
        session.setDurationSeconds(Duration.between(session.getStartedAt(), endedAt).getSeconds());
    }

    private FocusSessionResponseDTO toDto(FocusSession s) {
        return new FocusSessionResponseDTO(
                s.getId(),
                s.getTask() != null ? s.getTask().getId() : null,
                s.getTask() != null ? s.getTask().getTitle() : null,
                s.getStartedAt(),
                s.getEndedAt(),
                s.getDurationSeconds()
        );
    }
}
