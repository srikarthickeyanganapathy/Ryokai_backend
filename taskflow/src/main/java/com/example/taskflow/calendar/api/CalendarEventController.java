package com.example.taskflow.calendar.api;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.taskflow.calendar.api.CalendarEventDTOs.CalendarEventRequestDTO;
import com.example.taskflow.calendar.api.CalendarEventDTOs.CalendarEventResponseDTO;
import com.example.taskflow.calendar.application.CalendarEventService;
import com.example.taskflow.user.application.UserService;
import com.example.taskflow.user.domain.User;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(value = "/api/v1/calendar-events", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
public class CalendarEventController {

    private final CalendarEventService calendarEventService;
    private final UserService userService;

    private User getCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized: No authenticated user found");
        }
        return userService.getCurrentUser(userDetails.getUsername());
    }

    /**
     * Events for the current workspace scope.
     * No scope params = personal events; ?orgId=1 = org events; ?crewId=1 = crew events.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CalendarEventResponseDTO>> getEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) Long crewId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(calendarEventService.getEventsInScope(user, start, end, orgId, crewId));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CalendarEventResponseDTO> createEvent(
            @Valid @RequestBody CalendarEventRequestDTO request,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) Long crewId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        Long effectiveOrg = orgId != null ? orgId : request.getOrgId();
        Long effectiveCrew = crewId != null ? crewId : request.getCrewId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(calendarEventService.create(user, request, effectiveOrg, effectiveCrew));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CalendarEventResponseDTO> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody CalendarEventRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(calendarEventService.update(user, id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        calendarEventService.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
