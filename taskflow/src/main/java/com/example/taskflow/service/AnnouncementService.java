package com.example.taskflow.service;

import com.example.taskflow.domain.Announcement;
import com.example.taskflow.domain.Organization;
import com.example.taskflow.domain.User;
import com.example.taskflow.dto.AnnouncementRequestDTO;
import com.example.taskflow.dto.AnnouncementResponseDTO;
import com.example.taskflow.dto.MembershipResponseDTO;
import com.example.taskflow.exception.ResourceNotFoundException;
import com.example.taskflow.exception.UnauthorizedActionException;
import com.example.taskflow.exception.OrganizationSuspendedException;
import com.example.taskflow.notification.NotificationEvent;
import com.example.taskflow.repository.AnnouncementRepository;
import com.example.taskflow.repository.OrganizationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationService organizationService;
    private final PermissionService permissionService;
    private final NotificationService notificationService;

    public AnnouncementService(AnnouncementRepository announcementRepository,
                               OrganizationRepository organizationRepository,
                               OrganizationService organizationService,
                               PermissionService permissionService,
                               NotificationService notificationService) {
        this.announcementRepository = announcementRepository;
        this.organizationRepository = organizationRepository;
        this.organizationService = organizationService;
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
        if (!permissionService.hasPermission(user, "ANNOUNCEMENT_MANAGE")) {
            throw new UnauthorizedActionException("You do not have permission to manage announcements.");
        }

        Announcement announcement = new Announcement(request.getTitle(), request.getContent(), user, org);
        Announcement saved = announcementRepository.save(announcement);

        // Real-time push notification to all org members
        List<MembershipResponseDTO> members = organizationService.listOrganizationMembers(orgId, user);
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
        boolean hasPermission = permissionService.hasPermission(user, "ANNOUNCEMENT_MANAGE");

        if (!isAuthor && !hasPermission) {
            throw new UnauthorizedActionException("You do not have permission to delete this announcement.");
        }

        announcementRepository.delete(announcement);
    }
}
