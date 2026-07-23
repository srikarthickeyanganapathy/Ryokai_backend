package com.example.taskflow.controller;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.taskflow.domain.User;
import com.example.taskflow.dto.ActivityEventDTO;
import com.example.taskflow.dto.DashboardStatsDTO;
import com.example.taskflow.service.DashboardService;
import com.example.taskflow.service.UserService;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping(value = "/api/v1/dashboard", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserService userService;

    public DashboardController(DashboardService dashboardService, UserService userService) {
        this.dashboardService = dashboardService;
        this.userService = userService;
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDTO> getStats(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false, defaultValue = "PERSONAL") String scope,
            @RequestParam(required = false) Long orgId) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(dashboardService.getStats(user, scope, orgId));
    }

    @GetMapping("/activity")
    public ResponseEntity<Page<ActivityEventDTO>> getActivityFeed(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean includeComments) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        
        int pageSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, pageSize);
        return ResponseEntity.ok(dashboardService.getActivityFeed(user, pageable, includeComments));
    }

    @PreAuthorize("hasPermission(#taskId, 'Task', 'VIEW')")
    @GetMapping("/activity/task/{taskId}")
    public ResponseEntity<Page<ActivityEventDTO>> getActivityFeedForTask(
            @PathVariable Long taskId,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User user = userService.getCurrentUser(userDetails.getUsername());
        
        int pageSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "changedAt"));
        // I need to update DashboardService to have getActivityFeedForTask(taskId, user, pageable) 
        // that delegates to auditService, wait I didn't add it in DashboardService yet. 
        // I will add it to DashboardService next, or just call TaskAuditService directly here?
        // Feedback said: "Fix: Signature must be getActivityFeedForTask(Long taskId, User user, Pageable pageable). Inside the method, call roleStrategyFactory.getStrategy(user).canViewTask(user, task)  -  throw UnauthorizedActionException if false."
        // Let's call dashboardService.
        return ResponseEntity.ok(dashboardService.getActivityFeedForTask(taskId, user, pageable));
    }

    @GetMapping("/activity/export")
    public void export(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletResponse response) throws IOException {
        User user = userService.getCurrentUser(userDetails.getUsername());

        if (!"csv".equals(format) && !"json".equals(format)) {
            response.setStatus(400);
            response.getWriter().write("{\"error\":\"Unsupported format. Use csv or json.\"}");
            return;
        }

        response.setContentType("csv".equals(format) ? "text/csv" : "application/json");
        response.setHeader("Content-Disposition", 
            "attachment; filename=\"activity-" + LocalDate.now() + "." + format + "\"");

        try (Writer writer = new OutputStreamWriter(response.getOutputStream())) {
            if ("csv".equals(format)) {
                writer.write("Timestamp,EventType,TaskID,TaskTitle,Actor,FromStatus,ToStatus,Reason\n");
            } else {
                writer.write("[\n");
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            int pageNumber = 0;
            boolean isFirst = true;

            while (true) {
                Pageable pageable = PageRequest.of(pageNumber, 500, Sort.by(Sort.Direction.DESC, "changedAt"));
                Page<ActivityEventDTO> page = dashboardService.getActivityFeed(user, pageable, false);

                for (ActivityEventDTO e : page.getContent()) {
                    if ("csv".equals(format)) {
                        writer.write(String.format("%s,%s,%d,%s,%s,%s,%s,%s\n",
                                escapeCsv(e.occurredAt() != null ? e.occurredAt().toString() : ""),
                                escapeCsv(e.eventType()),
                                e.taskId(),
                                escapeCsv(e.taskTitle()),
                                escapeCsv(e.actor() != null ? e.actor().username() : ""),
                                escapeCsv(e.fromStatus()),
                                escapeCsv(e.toStatus()),
                                escapeCsv(e.reason())
                        ));
                    } else {
                        if (!isFirst) {
                            writer.write(",\n");
                        }
                        writer.write(mapper.writeValueAsString(e));
                        isFirst = false;
                    }
                }
                writer.flush();

                if (!page.hasNext() || pageNumber >= 19) { // up to 10k items
                    break;
                }
                pageNumber++;
            }

            if ("json".equals(format)) {
                writer.write("\n]");
            }
        }
    }

    private String escapeCsv(String data) {
        if (data == null) {
            return "\"\"";
        }
        if (data.startsWith("=") || data.startsWith("+") || data.startsWith("-") || data.startsWith("@")) {
            data = "'" + data;
        }
        String escaped = data.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
