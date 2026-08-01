package com.example.taskflow.focus.infrastructure;

import com.example.taskflow.focus.domain.FocusSession;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FocusSessionRepository extends JpaRepository<FocusSession, Long> {

    Page<FocusSession> findByUserIdOrderByStartedAtDesc(Long userId, Pageable pageable);

    Optional<FocusSession> findByUserIdAndEndedAtIsNull(Long userId);
}
