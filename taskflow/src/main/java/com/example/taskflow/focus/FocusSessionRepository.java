package com.example.taskflow.focus;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskflow.focus.FocusSession;

public interface FocusSessionRepository extends JpaRepository<FocusSession, Long> {

    Page<FocusSession> findByUserIdOrderByStartedAtDesc(Long userId, Pageable pageable);

    Optional<FocusSession> findByUserIdAndEndedAtIsNull(Long userId);
}
