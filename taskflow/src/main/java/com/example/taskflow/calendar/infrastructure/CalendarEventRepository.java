package com.example.taskflow.calendar.infrastructure;

import com.example.taskflow.calendar.domain.CalendarEvent;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    List<CalendarEvent> findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(
            Long userId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT e FROM CalendarEvent e WHERE e.organization.id = :orgId " +
           "AND e.startTime BETWEEN :start AND :end ORDER BY e.startTime ASC")
    List<CalendarEvent> findByOrganizationIdAndStartTimeBetweenOrderByStartTimeAsc(
            @Param("orgId") Long orgId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT e FROM CalendarEvent e WHERE e.crew.id = :crewId " +
           "AND e.startTime BETWEEN :start AND :end ORDER BY e.startTime ASC")
    List<CalendarEvent> findByCrewIdAndStartTimeBetweenOrderByStartTimeAsc(
            @Param("crewId") Long crewId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
