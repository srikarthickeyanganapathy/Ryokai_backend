package com.example.taskflow.organization.announcement.application;

import com.example.taskflow.organization.announcement.domain.Announcement;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.organization.announcement.dto.AnnouncementRequestDTO;
import com.example.taskflow.organization.announcement.dto.AnnouncementResponseDTO;
import com.example.taskflow.organization.membership.dto.MembershipResponseDTO;
import com.example.taskflow.shared.exception.ResourceNotFoundException;
import com.example.taskflow.shared.exception.UnauthorizedActionException;
import com.example.taskflow.organization.core.exception.OrganizationSuspendedException;
import com.example.taskflow.notification.event.NotificationEvent;
import com.example.taskflow.organization.announcement.infrastructure.persistence.AnnouncementRepository;
import com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.example.taskflow.notification.application.NotificationService;
import com.example.taskflow.notification.domain.Notification;
import com.example.taskflow.organization.membership.application.OrganizationMemberService;
import com.example.taskflow.organization.rbac.application.PermissionService;
import com.example.taskflow.organization.rbac.domain.Permission;
import com.example.taskflow.security.PermissionCode;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberService memberService;
    private final PermissionService permissionService;
    private final NotificationService notificationService;

    public AnnouncementService(AnnouncementRepository announcementRepository,
                               OrganizationRepository organizationRepository,
                               OrganizationMemberService memberService,
                               PermissionService permissionService,
                               NotificationService notificationService) {
        this.announcementRepository = announcementRepository;
        this.organizationRepository = organizationRepository;
        this.memberService = memberService;
        this.permissionService = permissionService;
        this.notificationService = notificationService;
    }

    private Organization getActiveOrganization(Long orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        if (org.getStatus() != Organization.OrgStatus.ACTIVE) {
            throw new OrganizationSuspendedException("Organization is not active.");
        }
        return org;
    }

    @Transactional(readOnly = true)
    public Page<AnnouncementResponseDTO> listAnnouncements(Long orgId, User user, Pageable pageable) {
        Organization org = getActiveOrganization(orgId);
        return announcementRepository.findByOrganizationIdOrderByCreatedAtDesc(org.getId(), pageable)
                .map(AnnouncementResponseDTO::new);
    }

    @Transactional
    public AnnouncementResponseDTO createAnnouncement(Long orgId, AnnouncementRequestDTO request, User user) {
        Organization org = getActiveOrganization(orgId);

        // Check permission
        permissionService.requireAuthorization(user, com.example.taskflow.security.PermissionCode.ANNOUNCEMENT_CREATE, orgId);

        Announcement announcement = new Announcement(request.getTitle(), request.getContent(), user, org);
        Announcement saved = announcementRepository.save(announcement);

        // Real-time push notification to all org members
        List<MembershipResponseDTO> members = memberService.listOrganizationMembers(orgId, user);
        for (MembershipResponseDTO member : members) {
            User recipient = new User();
            recipient.setId(member.getUserId());
            recipient.setUsername(member.getUsername());

            String dedupKey = "announcement:" + saved.getId();
            notificationService.createAndSend(
                    recipient,
                    null, // Don't exclude the author, they might want to see it was sent
                    NotificationEvent.ANNOUNCEMENT_CREATED,
                    "New Announcement: " + saved.getTitle(),
                    "From " + user.getUsername(),
                    null,
                    dedupKey
            );
        }

        return new AnnouncementResponseDTO(saved);
    }

    @Transactional
    public void deleteAnnouncement(Long orgId, Long announcementId, User user) {
        Organization org = getActiveOrganization(orgId);

        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found"));

        if (!announcement.getOrganization().getId().equals(org.getId())) {
            throw new ResourceNotFoundException("Announcement not found in this organization");
        }

        boolean isAuthor = announcement.getAuthor().getId().equals(user.getId());
        boolean hasPermission = permissionService.isAuthorized(user, com.example.taskflow.security.PermissionCode.ANNOUNCEMENT_CREATE, orgId);

        if (!isAuthor && !hasPermission) {
            throw new UnauthorizedActionException("You do not have permission to delete this announcement.");
        }

        announcementRepository.delete(announcement);
    }
}