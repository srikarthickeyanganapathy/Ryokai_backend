package com.example.taskflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.io.Serializable;

import com.example.taskflow.domain.Organization;
import com.example.taskflow.domain.User;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.service.PermissionService;

@Component
public class OrganizationPermissionHandler implements DomainPermissionHandler {

    private final OrganizationRepository organizationRepository;
    private final PermissionService permissionService;

    public OrganizationPermissionHandler(OrganizationRepository organizationRepository, PermissionService permissionService) {
        this.organizationRepository = organizationRepository;
        this.permissionService = permissionService;
    }

    @Override
    public String getTargetType() {
        return "Organization";
    }

    @Override
    public boolean hasPermission(Authentication auth, User user, Object targetDomainObject, String permission) {
        if (user != null && user.isSuperAdmin()) return true;
        if (targetDomainObject instanceof Organization) {
            Organization org = (Organization) targetDomainObject;
            if ("MEMBER".equals(permission)) {
                return user.isMemberOf(org);
            }
            try {
                permissionService.requirePermission(user, org, permission);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean hasPermission(Authentication auth, User user, Serializable targetId, String permission) {
        if (user != null && user.isSuperAdmin()) return true;
        if (targetId instanceof Long) {
            Organization org = organizationRepository.findById((Long) targetId).orElse(null);
            if (org == null) return false;
            if ("MEMBER".equals(permission)) {
                return user.isMemberOf(org);
            }
            try {
                permissionService.requirePermission(user, org, permission);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
}
