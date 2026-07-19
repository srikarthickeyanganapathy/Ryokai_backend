package com.example.taskflow.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.taskflow.domain.CalendarEvent;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    List<CalendarEvent> findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(
            Long userId, LocalDateTime start, LocalDateTime end);
}
