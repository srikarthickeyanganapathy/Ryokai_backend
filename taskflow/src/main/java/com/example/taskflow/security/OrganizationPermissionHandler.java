package com.example.taskflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.io.Serializable;

import com.example.taskflow.domain.Organization;
import com.example.taskflow.domain.User;
import com.example.taskflow.repository.OrganizationRepository;
import com.example.taskflow.service.PermissionService;
import com.example.taskflow.security.authorization.LegacyPermissionMapper;
import com.example.taskflow.security.PermissionCode;

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
        if (targetDomainObject instanceof Organization org) {
            if ("MEMBER".equals(permission)) {
                return user.isMemberOf(org);
            }
            PermissionCode code = LegacyPermissionMapper.resolveForDomain("ORG", permission);
            if (code != null) {
                return permissionService.isAuthorized(user, code, org.getId(), "ORGANIZATION", org.getId());
            }
        }
        return false;
    }

    @Override
    public boolean hasPermission(Authentication auth, User user, Serializable targetId, String permission) {
        if (targetId instanceof Long) {
            Organization org = organizationRepository.findById((Long) targetId).orElse(null);
            if (org == null) return false;
            if ("MEMBER".equals(permission)) {
                return user.isMemberOf(org);
            }
            PermissionCode code = LegacyPermissionMapper.resolveForDomain("ORG", permission);
            if (code != null) {
                return permissionService.isAuthorized(user, code, org.getId(), "ORGANIZATION", org.getId());
            }
        }
        return false;
    }
}
