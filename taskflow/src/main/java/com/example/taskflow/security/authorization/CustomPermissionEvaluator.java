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
import com.example.taskflow.organization.rbac.application.PermissionService;
import com.example.taskflow.security.authorization.AuthorizationDecision;
import com.example.taskflow.security.authorization.AuthorizationPipeline;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.authorization.LegacyPermissionMapper;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.organization.rbac.domain.Permission;
import com.example.taskflow.project.domain.Project;
import com.example.taskflow.project.dto.ProjectRequestDTO;
import com.example.taskflow.security.DomainPermissionHandler;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.task.api.request.BulkAssignRequestDTO;
import com.example.taskflow.task.api.request.TaskRequestDTO;
import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.team.domain.Team;
import com.example.taskflow.team.dto.CreateTeamRequestDTO;
import com.example.taskflow.team.dto.TeamMemberRequestDTO;

@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(CustomPermissionEvaluator.class);

    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final List<DomainPermissionHandler> handlers;
    private final AuthorizationPipeline authorizationPipeline;
    private final OrganizationMembershipRepository membershipRepository;

    public CustomPermissionEvaluator(UserRepository userRepository,
                                     PermissionService permissionService,
                                     List<DomainPermissionHandler> handlers,
                                     AuthorizationPipeline authorizationPipeline,
                                     OrganizationMembershipRepository membershipRepository) {
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.handlers = handlers;
        this.authorizationPipeline = authorizationPipeline;
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
        for (DomainPermissionHandler handler : handlers) {
            String typeName = targetDomainObject.getClass().getSimpleName();
            String handlerType = handler.getTargetType();
            
            if (typeName.equals(handlerType) || 
               (handlerType.equals("Task") && (typeName.equals("TaskRequestDTO") || typeName.equals("BulkAssignRequestDTO"))) ||
               (handlerType.equals("Project") && typeName.equals("ProjectRequestDTO")) ||
               (handlerType.equals("Team") && (typeName.equals("CreateTeamRequestDTO") || typeName.equals("TeamMemberRequestDTO")))) {
                return handler.hasPermission(auth, user, targetDomainObject, perm);
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

        // Domain-specific handlers (legacy Ã¢â‚¬â€ handles by-ID lookups with task/project caching)
        for (DomainPermissionHandler handler : handlers) {
            if (targetType.equals(handler.getTargetType())) {
                return handler.hasPermission(auth, user, targetId, perm);
            }
        }

        return false;
    }

    /**
     * Evaluates permission checks where no target domain object is provided.
     *
     * <p>Tries the new pipeline first (via LegacyPermissionMapper), then falls
     * back to the legacy PermissionService.hasPermission if the permission
     * string is not mapped to a PermissionCode.
     */
    @SuppressWarnings("deprecation")
    private boolean evaluateNullTarget(User user, String perm) {
        // Try to resolve through the new pipeline
        PermissionCode code = LegacyPermissionMapper.resolve(perm);
        if (code != null) {
            // Attempt pipeline evaluation Ã¢â‚¬â€ requires org context
            Long orgId = resolveOrgIdForUser(user);
            if (orgId != null) {
                AuthorizationRequest request = AuthorizationRequest.builder(user, code)
                        .organizationId(orgId)
                        .build();
                AuthorizationDecision decision = authorizationPipeline.evaluate(request);
                return decision.isGranted();
            }
        }

        // Fall back to legacy flat permission check
        return permissionService.hasPermission(user, perm);
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