package com.example.taskflow.calendar.application;

import com.example.taskflow.calendar.infrastructure.CalendarEventRepository;
import com.example.taskflow.calendar.domain.CalendarEvent;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.taskflow.crew.infrastructure.persistence.CrewMemberRepository;
import com.example.taskflow.crew.infrastructure.persistence.CrewRepository;
import com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.calendar.api.CalendarEventDTOs.CalendarEventRequestDTO;
import com.example.taskflow.calendar.api.CalendarEventDTOs.CalendarEventResponseDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CalendarEventService {

    private final CalendarEventRepository calendarEventRepository;
    private final OrganizationRepository organizationRepository;
    private final CrewRepository crewRepository;
    private final OrganizationMembershipRepository organizationMembershipRepository;
    private final CrewMemberRepository crewMemberRepository;

    public List<CalendarEventResponseDTO> getEventsInRange(User user, LocalDateTime start, LocalDateTime end) {
        return calendarEventRepository
                .findByUserIdAndStartTimeBetweenOrderByStartTimeAsc(user.getId(), start, end)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<CalendarEventResponseDTO> getEventsInScope(User user, LocalDateTime start, LocalDateTime end,
                                                           Long orgId, Long crewId) {
        assertSingleScope(orgId, crewId);
        if (orgId != null) {
            requireOrgMember(user, orgId);
            return calendarEventRepository
                    .findByOrganizationIdAndStartTimeBetweenOrderByStartTimeAsc(orgId, start, end)
                    .stream().map(this::toDto).toList();
        }
        if (crewId != null) {
            requireCrewMember(user, crewId);
            return calendarEventRepository
                    .findByCrewIdAndStartTimeBetweenOrderByStartTimeAsc(crewId, start, end)
                    .stream().map(this::toDto).toList();
        }
        return getEventsInRange(user, start, end);
    }

    @Transactional
    public CalendarEventResponseDTO create(User user, CalendarEventRequestDTO req, Long orgId, Long crewId) {
        assertSingleScope(orgId, crewId);
        CalendarEvent event = new CalendarEvent();
        applyRequest(event, req);
        event.setUser(user);
        if (orgId != null) {
            requireOrgMember(user, orgId);
            event.setOrganization(organizationRepository.getReferenceById(orgId));
        } else if (crewId != null) {
            requireCrewMember(user, crewId);
            event.setCrew(crewRepository.getReferenceById(crewId));
        }
        return toDto(calendarEventRepository.save(event));
    }

    @Transactional
    public CalendarEventResponseDTO update(User user, Long id, CalendarEventRequestDTO req) {
        CalendarEvent event = calendarEventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Calendar event not found: " + id));
        assertAccess(user, event);
        applyRequest(event, req);
        return toDto(calendarEventRepository.save(event));
    }

    @Transactional
    public void delete(User user, Long id) {
        CalendarEvent event = calendarEventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Calendar event not found: " + id));
        assertAccess(user, event);
        calendarEventRepository.delete(event);
    }

    private void assertAccess(User user, CalendarEvent event) {
        if (event.getUser() != null && event.getUser().getId().equals(user.getId())) return;
        if (event.getOrganization() != null && organizationMembershipRepository
                .existsByUserIdAndOrganizationId(user.getId(), event.getOrganization().getId())) return;
        if (event.getCrew() != null && crewMemberRepository
                .existsByIdCrewIdAndIdUserId(event.getCrew().getId(), user.getId())) return;
        throw new SecurityException("Cannot modify another user's calendar event");
    }

    private void requireOrgMember(User user, Long orgId) {
        if (!organizationMembershipRepository.existsByUserIdAndOrganizationId(user.getId(), orgId)) {
            throw new SecurityException("You are not a member of this organization");
        }
    }

    private void requireCrewMember(User user, Long crewId) {
        if (!crewMemberRepository.existsByIdCrewIdAndIdUserId(crewId, user.getId())) {
            throw new SecurityException("You are not a member of this crew");
        }
    }

    private void assertSingleScope(Long orgId, Long crewId) {
        if (orgId != null && crewId != null) {
            throw new IllegalArgumentException("Cannot scope an event to both an organization and a crew");
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
                e.getStartTime(), e.getEndTime(), e.getIsAllDay(),
                e.getOrganization() == null ? null : e.getOrganization().getId(),
                e.getCrew() == null ? null : e.getCrew().getId());
    }
}
