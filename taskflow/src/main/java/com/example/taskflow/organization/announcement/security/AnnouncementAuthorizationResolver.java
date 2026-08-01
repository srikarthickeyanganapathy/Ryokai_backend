package com.example.taskflow.organization.announcement.security;

import com.example.taskflow.organization.announcement.domain.Announcement;
import com.example.taskflow.organization.announcement.infrastructure.persistence.AnnouncementRepository;
import com.example.taskflow.security.AuthorizationResourceResolver;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.authorization.AuthorizationRequestBuilder;
import com.example.taskflow.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.io.Serializable;

@Component
@RequiredArgsConstructor
public class AnnouncementAuthorizationResolver implements AuthorizationResourceResolver {

    private final AnnouncementRepository announcementRepository;
    private final AuthorizationRequestBuilder requestBuilder;

    @Override
    public boolean supportsClass(Class<?> targetClass) {
        return Announcement.class.isAssignableFrom(targetClass) || requestBuilder.supportsDto(targetClass, "ANNOUNCEMENT");
    }

    @Override
    public boolean supportsResourceType(String resourceType) {
        return "Announcement".equalsIgnoreCase(resourceType);
    }

    @Override
    public AuthorizationRequest buildRequest(Authentication auth, User user, Object targetDomainObject, PermissionCode permission) {
        if (targetDomainObject instanceof Announcement announcement) {
            java.util.Map<String, Long> context = new java.util.HashMap<>();
            if (announcement.getOrganization() != null) {
                context.put("organizationId", announcement.getOrganization().getId());
            }
            java.util.Set<com.example.taskflow.security.authorization.OwnershipRole> ownership = java.util.EnumSet.noneOf(com.example.taskflow.security.authorization.OwnershipRole.class);
            if (announcement.getAuthor() != null && announcement.getAuthor().getId().equals(user.getId())) {
                ownership.add(com.example.taskflow.security.authorization.OwnershipRole.CREATOR);
            }
            com.example.taskflow.security.ScopeType scope = com.example.taskflow.security.ScopeType.valueOf(com.example.taskflow.security.PermissionMetadataRegistry.getRecommendedScope(permission.name()));
            
            return requestBuilder.build(user, permission, "Announcement", announcement.getId(), com.example.taskflow.security.WorkspaceType.ORGANIZATION, scope, context, ownership);
        }
        
        return requestBuilder.buildFromDto(user, permission, targetDomainObject, "ANNOUNCEMENT");
    }

    @Override
    public AuthorizationRequest buildRequest(Authentication auth, User user, Serializable targetId, PermissionCode permission) {
        if (targetId instanceof Long id) {
            Announcement announcement = announcementRepository.findById(id).orElse(null);
            if (announcement != null) {
                return buildRequest(auth, user, announcement, permission);
            }
        }
        return null;
    }
}
