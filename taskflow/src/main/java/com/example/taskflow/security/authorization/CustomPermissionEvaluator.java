package com.example.taskflow.security.authorization;

import java.io.Serializable;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.taskflow.user.domain.User;
import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.user.infrastructure.persistence.UserRepository;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.security.authorization.engine.AuthorizationEngine;
import com.example.taskflow.security.AuthorizationResourceResolver;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.authorization.engine.AuthorizationEngine;

@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(CustomPermissionEvaluator.class);

    private final UserRepository userRepository;
    private final List<AuthorizationResourceResolver> handlers;
    private final AuthorizationEngine authorizationEngine;
    private final OrganizationMembershipRepository membershipRepository;

    public CustomPermissionEvaluator(UserRepository userRepository,
                                     List<AuthorizationResourceResolver> handlers,
                                     AuthorizationEngine authorizationEngine,
                                     OrganizationMembershipRepository membershipRepository) {
        this.userRepository = userRepository;
        this.handlers = handlers;
        this.authorizationEngine = authorizationEngine;
        this.membershipRepository = membershipRepository;
    }

    private User getUser(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) return null;
        String username = null;
        if (auth.getPrincipal() instanceof UserDetails) {
            username = ((UserDetails) auth.getPrincipal()).getUsername();
        } else if (auth.getPrincipal() instanceof String) {
            username = (String) auth.getPrincipal();
        }
        if (username == null) return null;

        org.springframework.web.context.request.RequestAttributes attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            User cachedUser = (User) attrs.getAttribute("CACHED_USER_" + username, org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
            if (cachedUser != null) {
                return cachedUser;
            }
        }

        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null && attrs != null) {
            attrs.setAttribute("CACHED_USER_" + username, user, org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        }
        return user;
    }

    @Override
    public boolean hasPermission(Authentication auth, Object targetDomainObject, Object permission) {
        if ((auth == null) || !(permission instanceof String)){
            return false;
        }
        User user = getUser(auth);
        if (user == null) return false;
        
        String perm = (String) permission;

        if (targetDomainObject == null) {
            return evaluateNullTarget(user, perm);
        }

        // Domain object checks delegate to existing handlers
        // These handlers will be migrated individually in Phase 4
        for (AuthorizationResourceResolver handler : handlers) {
            String typeName = targetDomainObject.getClass().getSimpleName();
            String handlerType = handler.getTargetType();
            
            if (typeName.equals(handlerType) || 
               (handlerType.equals("Task") && (typeName.equals("TaskRequestDTO") || typeName.equals("BulkAssignRequestDTO"))) ||
               (handlerType.equals("Project") && typeName.equals("ProjectRequestDTO")) ||
               (handlerType.equals("Team") && (typeName.equals("CreateTeamRequestDTO") || typeName.equals("TeamMemberRequestDTO")))) {
                
                PermissionCode pCode = resolveGlobalPermission(perm, handlerType);
                if (pCode == null) return false;
                
                AuthorizationRequest request = handler.buildRequest(auth, user, targetDomainObject, pCode);
                if (request == null) return false;
                
                return authorizationEngine.authorize(request).isGranted();
            }
        }
        
        return false;
    }

    @Override
    public boolean hasPermission(Authentication auth, Serializable targetId, String targetType, Object permission) {
        if ((auth == null) || (targetType == null) || !(permission instanceof String)) {
            return false;
        }

        User user = getUser(auth);
        if (user == null) return false;

        String perm = (String) permission;
        
        if (targetId == null && !"Project".equals(targetType) && !"Task".equals(targetType)) {
             return evaluateNullTarget(user, perm);
        }

        // Domain-specific handlers (legacy — handles by-ID lookups with task/project caching)
        for (AuthorizationResourceResolver handler : handlers) {
            if (targetType.equals(handler.getTargetType())) {
                PermissionCode pCode = resolveGlobalPermission(perm, targetType);
                if (pCode == null) return false;
                
                AuthorizationRequest request = handler.buildRequest(auth, user, targetId, pCode);
                if (request == null) return false;
                
                return authorizationEngine.authorize(request).isGranted();
            }
        }

        return false;
    }

    /**
     * Evaluates permission checks where no target domain object is provided.
     *
     * <p>Tries the new pipeline first (via LegacyPermissionMapper), then falls
     * back to the legacy AuthorizationEngine.hasPermission if the permission
     * string is not mapped to a PermissionCode.
     */
    
    private PermissionCode resolveGlobalPermission(String perm, String targetType) {
        if (perm == null) return null;
        try {
            return PermissionCode.valueOf(perm);
        } catch (IllegalArgumentException e) {
            if ("CREATE".equals(perm)) {
                if ("Project".equals(targetType)) return PermissionCode.PROJECT_CREATE;
                if ("Team".equals(targetType)) return PermissionCode.TEAM_CREATE;
                return PermissionCode.TASK_CREATE;
            }
            return switch (perm) {
                case "PROJECT_CREATE" -> PermissionCode.PROJECT_CREATE;
                case "TEAM_CREATE" -> PermissionCode.TEAM_CREATE;
                case "ORG_MEMBER_INVITE" -> PermissionCode.MEMBER_INVITE;
                default -> null;
            };
        }
    }

    private boolean evaluateNullTarget(User user, String perm) {
        // Try to resolve through the new pipeline
        PermissionCode code = resolveGlobalPermission(perm, null);
        if (code != null) {
            // Attempt pipeline evaluation Ã¢â‚¬â€ requires org context
            Long orgId = resolveOrgIdForUser(user);
            if (orgId != null) {
                AuthorizationRequest request = AuthorizationRequest.builder(user, code)
                        .context(java.util.Map.of("organizationId", orgId))
                        .build();
                AuthorizationDecision decision = authorizationEngine.authorize(request);
                return decision.isGranted();
            }
        }

        // Fall back to legacy flat permission check
        return java.util.Collections.emptySet().contains(perm);
    }

    /**
     * Resolves the organization ID for a user.
     * Since Ryokai enforces one-user-one-org, this returns the single org membership's org ID.
     */
    private Long resolveOrgIdForUser(User user) {
        if (user == null || user.getId() == null) return null;
        try {
            List<OrganizationMembership> memberships = membershipRepository.findByUserId(user.getId());
            if (!memberships.isEmpty()) {
                // One-user-one-org constraint: return the first (and only) org ID
                return memberships.get(0).getOrganization().getId();
            }
        } catch (Exception e) {
            log.debug("Could not resolve org ID for user {}: {}", user.getId(), e.getMessage());
        }
        return null;
    }
}