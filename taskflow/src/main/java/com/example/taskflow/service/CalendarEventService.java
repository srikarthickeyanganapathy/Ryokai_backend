package com.example.taskflow.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.domain.CalendarEvent;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.CalendarEventDTOs.CalendarEventRequestDTO;
import com.example.taskflow.dto.CalendarEventDTOs.CalendarEventResponseDTO;
import com.example.taskflow.repository.CalendarEventRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private final CalendarEventRepository calendarEventRepository;

    public List<CalendarEventResponseDTO> getEventsInRange(User user, LocalDateTime start, LocalDateTime end) {
        return calendarEventRepository
                .findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(user.getId(), start, end)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CalendarEventResponseDTO create(User user, CalendarEventRequestDTO req) {
        CalendarEvent event = new CalendarEvent();
        applyRequest(event, req);
        event.setUser(user);
        return toDto(calendarEventRepository.save(event));
    }

    @Transactional
    public CalendarEventResponseDTO update(User user, Long id, CalendarEventRequestDTO req) {
        CalendarEvent event = calendarEventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Calendar event not found: " + id));
        assertOwner(user, event);
        applyRequest(event, req);
        return toDto(calendarEventRepository.save(event));
    }

    @Transactional
    public void delete(User user, Long id) {
        CalendarEvent event = calendarEventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Calendar event not found: " + id));
        assertOwner(user, event);
        calendarEventRepository.delete(event);
    }

    private void assertOwner(User user, CalendarEvent event) {
        if (!event.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Cannot modify another user's calendar event");
        }
    }

    private void applyRequest(CalendarEvent event, CalendarEventRequestDTO req) {
        event.setTitle(req.getTitle());
        event.setDescription(req.getDescription());
        event.setStartTime(req.getStartTime());
        event.setEndTime(req.getEndTime() != null ? req.getEndTime() : req.getStartTime());
        event.setIsAllDay(Boolean.TRUE.equals(req.getIsAllDay()));
    }

    private CalendarEventResponseDTO toDto(CalendarEvent e) {
        return new CalendarEventResponseDTO(
                e.getId(), e.getTitle(), e.getDescription(),
                e.getStartTime(), e.getEndTime(), e.getIsAllDay());
    }
}
