package com.example.taskflow.user.infrastructure;

import com.example.taskflow.user.domain.SessionMemory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionMemoryRepository extends JpaRepository<SessionMemory, Long> {
    Optional<SessionMemory> findByUserId(Long userId);
}
