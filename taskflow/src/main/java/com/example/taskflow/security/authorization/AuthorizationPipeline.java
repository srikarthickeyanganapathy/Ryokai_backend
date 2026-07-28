package com.example.taskflow.security.authorization;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.organization.rbac.domain.PermissionAuditLog;
import com.example.taskflow.organization.rbac.domain.RolePermissionScope;
import com.example.taskflow.organization.rbac.domain.ResourceAssignment;
import com.example.taskflow.organization.rbac.domain.UserPermissionOverride;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository;
import com.example.taskflow.organization.rbac.infrastructure.persistence.PermissionAuditLogRepository;
import com.example.taskflow.organization.rbac.infrastructure.persistence.ResourceAssignmentRepository;
import com.example.taskflow.organization.rbac.infrastructure.persistence.RolePermissionScopeRepository;
import com.example.taskflow.organization.rbac.infrastructure.persistence.UserPermissionOverrideRepository;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.PermissionImplications;
import com.example.taskflow.security.ScopeType;
import com.example.taskflow.crew.domain.Crew;
import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.organization.rbac.domain.Permission;
import com.example.taskflow.organization.rbac.domain.Role;
import com.example.taskflow.organization.rbac.domain.Scope;
import com.example.taskflow.project.domain.Project;
import com.example.taskflow.security.platform.PlatformAuthorizationService;
import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.team.domain.Team;

/**
 * The authorization pipeline for <b>Organization Workspaces only</b>.
 *
 * <p>This pipeline evaluates workspace-level RBAC. It <b>never</b> checks
 * for platform identities (SUPER_ADMIN / PLATFORM_OWNER / etc.).
 * Platform authorization is handled by {@code PlatformAuthorizationService}
 * in a completely independent system.
 *
 * <p>Pipeline stages:
 * <ol>
 *   <li>Authenticate User (already done by Spring Security before this point)</li>
 *   <li>Resolve Workspace Type (handled by caller Ã¢â‚¬â€ this pipeline is ONLY for Org workspaces)</li>
 *   <li>Check Organization Status (active? suspended?)</li>
 *   <li>Load User Roles (via OrganizationMembership)</li>
 *   <li>Check User Overrides (GRANT/DENY per user)</li>
 *   <li>Resolve Permissions + Implications (transitive grant expansion)</li>
 *   <li>Resolve Scope + Resources (ORGANIZATION Ã¢â€ â€™ TEAM Ã¢â€ â€™ PROJECT Ã¢â€ â€™ OWN)</li>
 *   <li>Evaluate Policies (runtime predicates)</li>
 *   <li>Evaluate Field Restrictions (field-level access control)</li>
 * </ol>
 *
 * <p>If a request reaches this pipeline, it must already be:
 * <ul>
 *   <li>An <b>organization request</b> (not personal, not crew, not platform)</li>
 *   <li>Made by an <b>organization member</b> (resolved via OrganizationMembership)</li>
 * </ul>
 *
 * @see com.example.taskflow.security.platform.PlatformAuthorizationService
 */
@Service
public class AuthorizationPipeline {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationPipeline.class);

    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserPermissionOverrideRepository overrideRepository;
    private final RolePermissionScopeRepository rpsRepository;
    private final ResourceAssignmentRepository resourceAssignmentRepository;
    private final PolicyEvaluator policyEvaluator;
    private final FieldRestrictionEvaluator fieldRestrictionEvaluator;
    private final PermissionAuditLogRepository auditLogRepository;
    private final TransactionTemplate transactionTemplate;

    public AuthorizationPipeline(
            OrganizationRepository organizationRepository,
            OrganizationMembershipRepository membershipRepository,
            UserPermissionOverrideRepository overrideRepository,
            RolePermissionScopeRepository rpsRepository,
            ResourceAssignmentRepository resourceAssignmentRepository,
            PolicyEvaluator policyEvaluator,
            FieldRestrictionEvaluator fieldRestrictionEvaluator,
            PermissionAuditLogRepository auditLogRepository,
            PlatformTransactionManager transactionManager) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.overrideRepository = overrideRepository;
        this.rpsRepository = rpsRepository;
        this.resourceAssignmentRepository = resourceAssignmentRepository;
        this.policyEvaluator = policyEvaluator;
        this.fieldRestrictionEvaluator = fieldRestrictionEvaluator;
        this.auditLogRepository = auditLogRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Evaluates the full authorization pipeline for an Organization Workspace request.
     *
     * <p><b>Important:</b> This pipeline must not be called for platform-level requests.
     * Platform authorization is handled by {@code PlatformAuthorizationService}.
     *
     * @param request the authorization request containing user, permission, and context
     * @return the authorization decision (GRANT or DENY with reason)
     */
    public AuthorizationDecision evaluate(AuthorizationRequest request) {
        User user = request.getUser();
        PermissionCode permission = request.getPermission();

        // Ã¢â€˜Â  Check Organization Status
        if (request.getOrganizationId() != null) {
            Organization org = organizationRepository.findById(request.getOrganizationId()).orElse(null);
            if (org == null) {
                return audit(request, AuthorizationDecision.deny("ORG_STATUS", "Organization not found"));
            }
            if (org.getStatus() != Organization.OrgStatus.ACTIVE) {
                return audit(request, AuthorizationDecision.deny("ORG_STATUS",
                        "Organization is " + org.getStatus() + " Ã¢â‚¬â€ workspace access is disabled"));
            }
        }

        // Ã¢â€˜Â£ Load User Roles
        Long orgId = request.getOrganizationId();
        if (orgId == null) {
            return audit(request, AuthorizationDecision.deny("CONTEXT", "Organization ID is required for org-scoped permission checks"));
        }

        OrganizationMembership membership = membershipRepository.findByUserIdAndOrganizationId(user.getId(), orgId)
                .orElse(null);
        if (membership == null) {
            return audit(request, AuthorizationDecision.denyNotMember());
        }

        if (membership.getOrgRole() == null) {
            return audit(request, AuthorizationDecision.deny("ROLE", "User has no role assigned in this organization"));
        }

        List<Long> roleIds = new ArrayList<>();
        roleIds.add(membership.getOrgRole().getId());
        // Future: add team-level and project-level roles here

        // Ã¢â€˜Â¤ Check User Overrides
        List<UserPermissionOverride> overrides = overrideRepository.findActiveByUserAndOrg(
                user.getId(), orgId, LocalDateTime.now());

        for (UserPermissionOverride override : overrides) {
            if (override.getPermission().getCode().equals(permission.code())) {
                if ("DENY".equals(override.getOverrideType())) {
                    return audit(request, AuthorizationDecision.denyOverride());
                }
                if ("GRANT".equals(override.getOverrideType())) {
                    // Skip permission/scope checks, but still evaluate policies and fields
                    AuthorizationDecision policyResult = policyEvaluator.evaluate(request);
                    if (policyResult.isDenied()) {
                        return audit(request, policyResult);
                    }
                    return evaluateFields(request, roleIds);
                }
            }
        }

        // Ã¢â€˜Â¥ Resolve Permissions + Implications
        // Expand the requested permission to include all permissions that imply it
        Set<PermissionCode> satisfyingPermissions = findSatisfyingPermissions(permission);

        // Query role_permission_scopes for any of these permissions
        Set<String> satisfyingCodes = satisfyingPermissions.stream()
                .map(PermissionCode::code)
                .collect(Collectors.toSet());

        List<RolePermissionScope> grants = rpsRepository.findByRoleIdIn(roleIds).stream()
                .filter(rps -> rps.getPermission() != null && satisfyingCodes.contains(rps.getPermission().getCode()))
                .collect(Collectors.toList());

        if (grants.isEmpty()) {
            return audit(request, AuthorizationDecision.denyPermission(permission.code()));
        }

        // Ã¢â€˜Â¦ Resolve Scope + Resources
        AuthorizationDecision scopeResult = resolveScope(request, grants);
        if (scopeResult.isDenied()) {
            return audit(request, scopeResult);
        }

        // Ã¢â€˜Â§ Evaluate Policies
        AuthorizationDecision policyResult = policyEvaluator.evaluate(request);
        if (policyResult.isDenied()) {
            return audit(request, policyResult);
        }

        // Ã¢â€˜Â¨ Evaluate Field Restrictions
        return evaluateFields(request, roleIds);
    }

    /**
     * Finds all permissions that would satisfy the requested permission.
     * This is the REVERSE of implication: if TASK_OVERRIDE implies TASK_APPROVE,
     * then having TASK_OVERRIDE satisfies a check for TASK_APPROVE.
     */
    private Set<PermissionCode> findSatisfyingPermissions(PermissionCode required) {
        EnumSet<PermissionCode> satisfying = EnumSet.of(required);
        // Check every permission to see if it implies the required one
        for (PermissionCode candidate : PermissionCode.values()) {
            if (candidate != required && PermissionImplications.implies(candidate, required)) {
                satisfying.add(candidate);
            }
        }
        return satisfying;
    }

    /**
     * Stage Ã¢â€˜Â¦: Checks if any grant's scope is sufficient for the request's context.
     */
    private AuthorizationDecision resolveScope(AuthorizationRequest request, List<RolePermissionScope> grants) {
        ScopeType requiredScope = request.getRequiredScope();

        for (RolePermissionScope grant : grants) {
            ScopeType grantedScope = ScopeType.valueOf(grant.getScope().getCode());

            // Check scope hierarchy: granted scope must include required scope
            if (grantedScope.includes(requiredScope)) {
                // Check resource assignments (optional narrowing)
                List<ResourceAssignment> assignments = resourceAssignmentRepository
                        .findByRolePermissionScopeId(grant.getId());

                if (assignments.isEmpty()) {
                    // No narrowing Ã¢â‚¬â€ permission applies to all resources within scope
                    return AuthorizationDecision.grant("SCOPE");
                }

                // Check if the target resource is in the assignment list
                if (request.getResourceType() != null && request.getResourceId() != null) {
                    boolean resourceMatch = assignments.stream().anyMatch(ra ->
                            ra.getResourceType().equals(request.getResourceType())
                                    && ra.getResourceId().equals(request.getResourceId()));
                    if (resourceMatch) {
                        return AuthorizationDecision.grant("SCOPE");
                    }
                }

                // Also check parent resources (e.g., if the grant narrows to a team,
                // and the request targets a task within that team)
                if (request.getTeamId() != null) {
                    boolean teamMatch = assignments.stream().anyMatch(ra ->
                            "TEAM".equals(ra.getResourceType())
                                    && ra.getResourceId().equals(request.getTeamId()));
                    if (teamMatch) {
                        return AuthorizationDecision.grant("SCOPE");
                    }
                }
                if (request.getProjectId() != null) {
                    boolean projectMatch = assignments.stream().anyMatch(ra ->
                            "PROJECT".equals(ra.getResourceType())
                                    && ra.getResourceId().equals(request.getProjectId()));
                    if (projectMatch) {
                        return AuthorizationDecision.grant("SCOPE");
                    }
                }
            }
        }

        return AuthorizationDecision.denyScope(
                request.getPermission().code(),
                requiredScope.name());
    }

    /**
     * Stage Ã¢â€˜Â¨: Evaluate field restrictions (only for _UPDATE operations with modified fields).
     */
    private AuthorizationDecision evaluateFields(AuthorizationRequest request, List<Long> roleIds) {
        if (request.getModifiedFields().isEmpty()) {
            return audit(request, AuthorizationDecision.grant("PIPELINE"));
        }

        AuthorizationDecision fieldResult = fieldRestrictionEvaluator.evaluate(
                roleIds, request.getResourceType(), request.getModifiedFields());
        return audit(request, fieldResult);
    }

    /**
     * Records the authorization decision in the audit log.
     */
    private AuthorizationDecision audit(AuthorizationRequest request, AuthorizationDecision decision) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                PermissionAuditLog entry = new PermissionAuditLog();
                entry.setUserId(request.getUser().getId());
                entry.setPermissionCode(request.getPermission().code());
                entry.setResourceType(request.getResourceType());
                entry.setResourceId(request.getResourceId());
                entry.setDecision(decision.decision().name());
                entry.setDenyReason(decision.reason());
                auditLogRepository.save(entry);
            });
        } catch (Exception e) {
            // Audit logging must never block authorization decisions
            log.warn("Failed to write permission audit log: {}", e.getMessage());
        }
        return decision;
    }
}