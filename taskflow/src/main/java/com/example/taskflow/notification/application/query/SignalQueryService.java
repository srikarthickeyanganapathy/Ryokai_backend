package com.example.taskflow.notification.application.query;

import com.example.taskflow.notification.dto.NotificationDTO;
import com.example.taskflow.notification.infrastructure.persistence.NotificationRepository;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.security.authorization.engine.AuthorizationEngine;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.crew.application.CrewMembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SignalQueryService {

    private final NotificationRepository notificationRepository;
    private final AuthorizationEngine authorizationEngine;
    private final CrewMembershipService crewMembershipService;

    @Transactional(readOnly = true)
    public Page<NotificationDTO> getActiveSignals(User user, String workspaceMode, Long orgId, Long crewId, Pageable pageable) {
        
        // 1. Enforce RBAC at the query layer
        if ("ORG".equalsIgnoreCase(workspaceMode) || "ORGANIZATION".equalsIgnoreCase(workspaceMode)) {
            if (orgId == null) {
                throw new IllegalArgumentException("Organization ID is required for ORG signals");
            }
            if (!authorizationEngine.authorize(com.example.taskflow.security.authorization.AuthorizationRequest.builder(user, PermissionCode.DASHBOARD_VIEW).context(java.util.Map.of("organizationId", orgId)).requiredScope(com.example.taskflow.security.ScopeType.ORGANIZATION).build()).isGranted()) {
                return new PageImpl<>(List.of(), pageable, 0); // No access, return empty
            }
        } else if ("CREWS".equalsIgnoreCase(workspaceMode) || "CREW".equalsIgnoreCase(workspaceMode)) {
            if (crewId == null) {
                throw new IllegalArgumentException("Crew ID is required for CREW signals");
            }
            if (!crewMembershipService.isMember(crewId, user.getId())) {
                return new PageImpl<>(List.of(), pageable, 0); // No access, return empty
            }
        }

        // 2. Fetch signals.
        Page<com.example.taskflow.notification.domain.Notification> page = notificationRepository.findActiveSignalsByWorkspace(
                user.getId(), 
                com.example.taskflow.notification.domain.PriorityTier.ARCHIVE, 
                workspaceMode, 
                orgId, 
                crewId, 
                pageable);

        List<NotificationDTO> filteredList = page.getContent().stream()
                .filter(n -> isNotificationInWorkspace(n, workspaceMode, orgId, crewId))
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(filteredList, pageable, page.getTotalElements());
    }

    private boolean isNotificationInWorkspace(com.example.taskflow.notification.domain.Notification n, String workspaceMode, Long orgId, Long crewId) {
        if (n.getTaskId() != null) {
            return true; // Already filtered by SQL query based on Task relationship
        }

        // For non-task notifications (like Announcements), we check the deduplicationKey or metadata
        if (n.getDeduplicationKey() != null) {
            if (n.getDeduplicationKey().startsWith("announcement:")) {
                return "ORG".equalsIgnoreCase(workspaceMode) || "ORGANIZATION".equalsIgnoreCase(workspaceMode);
            }
            if (n.getDeduplicationKey().startsWith("crew:")) {
                return "CREWS".equalsIgnoreCase(workspaceMode) || "CREW".equalsIgnoreCase(workspaceMode);
            }
        }
        
        if (workspaceMode == null || "PERSONAL".equalsIgnoreCase(workspaceMode)) {
            return n.getDeduplicationKey() == null || (!n.getDeduplicationKey().startsWith("announcement:") && !n.getDeduplicationKey().startsWith("crew:"));
        }

        return false;
    }

    private NotificationDTO toDTO(com.example.taskflow.notification.domain.Notification n) {
        return new NotificationDTO(
                n.getId(),
                n.getType().name(),
                n.getTitle(),
                n.getMessage(),
                n.getTaskId(),
                n.getTaskTitleSnapshot(),
                n.isRead(),
                n.getCreatedAt(),
                NotificationDTO.getRelativeTime(n.getCreatedAt()),
                n.getDeduplicationKey(),
                n.getActor() != null ? n.getActor().getUsername() : null
        );
    }
}
