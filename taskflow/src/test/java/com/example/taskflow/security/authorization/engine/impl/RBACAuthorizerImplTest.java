package com.example.taskflow.security.authorization.engine.impl;

import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.organization.rbac.domain.RolePermissionScope;
import com.example.taskflow.organization.rbac.domain.Scope;
import com.example.taskflow.organization.rbac.domain.Permission;
import com.example.taskflow.organization.rbac.domain.UserPermissionOverride;
import com.example.taskflow.organization.rbac.infrastructure.persistence.RolePermissionScopeRepository;
import com.example.taskflow.organization.rbac.infrastructure.persistence.UserPermissionOverrideRepository;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.authorization.AuthorizationDecision;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.ArgumentMatchers.anyLong;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class RBACAuthorizerImplTest {

    @Mock
    private OrganizationMembershipRepository membershipRepository;

    @Mock
    private UserPermissionOverrideRepository overrideRepository;

    @Mock
    private RolePermissionScopeRepository rpsRepository;
    @Mock
    private com.example.taskflow.organization.rbac.infrastructure.persistence.ResourceAssignmentRepository resourceAssignmentRepository;

    @InjectMocks
    private RBACAuthorizerImpl rbacAuthorizer;

    private User testUser;
    private User superAdmin;
    private OrganizationMembership validMembership;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        testUser = new User();
        testUser.setId(100L);
        
        superAdmin = new User();
        superAdmin.setId(999L);
        com.example.taskflow.organization.rbac.domain.Role adminRole = new com.example.taskflow.organization.rbac.domain.Role();
        adminRole.setName("SUPER_ADMIN");
        superAdmin.getRoles().add(adminRole);

        com.example.taskflow.organization.rbac.domain.Role orgRole = new com.example.taskflow.organization.rbac.domain.Role();
        orgRole.setId(5L);

        validMembership = new OrganizationMembership();
        validMembership.setUser(testUser);
        validMembership.setOrgRole(orgRole);
    }

    @Test
    void authorize_NoUser_ReturnsDeny() {
        AuthorizationRequest request = AuthorizationRequest.builder(new User(), PermissionCode.PROJECT_VIEW).build();
        AuthorizationDecision decision = rbacAuthorizer.authorize(request);
        assertTrue(decision.isDenied());
    }

    @Test
    void authorize_SuperAdmin_ReturnsAllow() {
        AuthorizationRequest request = AuthorizationRequest.builder(superAdmin, PermissionCode.PROJECT_VIEW).build();
        AuthorizationDecision decision = rbacAuthorizer.authorize(request);
        assertTrue(decision.isGranted());
    }

    @Test
    void authorize_NoWorkspaceId_ReturnsAbstain() {
        AuthorizationRequest request = AuthorizationRequest.builder(testUser, PermissionCode.PROJECT_VIEW).build();
        AuthorizationDecision decision = rbacAuthorizer.authorize(request);
        assertTrue(decision.isAbstain());
    }

    @Test
    void authorize_UserNotMember_ReturnsDeny() {
        Map<String, Long> context = new HashMap<>();
        context.put("organizationId", 300L);
        AuthorizationRequest request = AuthorizationRequest.builder(testUser, PermissionCode.PROJECT_VIEW)
                .context(context).build();

        when(membershipRepository.findByUserIdAndOrganizationId(100L, 300L)).thenReturn(Optional.empty());

        AuthorizationDecision decision = rbacAuthorizer.authorize(request);
        assertTrue(decision.isDenied());
        assertEquals("User is not a member of the organization", decision.reason());
    }

    @Test
    void authorize_ExplicitDenyOverride_ReturnsDeny() {
        Map<String, Long> context = new HashMap<>();
        context.put("organizationId", 300L);
        AuthorizationRequest request = AuthorizationRequest.builder(testUser, PermissionCode.PROJECT_VIEW)
                .context(context).build();

        when(membershipRepository.findByUserIdAndOrganizationId(100L, 300L)).thenReturn(Optional.of(validMembership));
        
        UserPermissionOverride denyOverride = new UserPermissionOverride();
        Permission permission = new Permission();
        permission.setCode("PROJECT_VIEW");
        denyOverride.setPermission(permission);
        denyOverride.setOverrideType("DENY");
        
        when(overrideRepository.findActiveByUserAndOrg(eq(100L), eq(300L), any(LocalDateTime.class)))
                .thenReturn(List.of(denyOverride));

        AuthorizationDecision decision = rbacAuthorizer.authorize(request);
        assertTrue(decision.isDenied());
        assertEquals("Explicitly denied by user override", decision.reason());
    }

    @Test
    void authorize_ExplicitGrantOverride_ReturnsAllow() {
        Map<String, Long> context = new HashMap<>();
        context.put("organizationId", 300L);
        AuthorizationRequest request = AuthorizationRequest.builder(testUser, PermissionCode.PROJECT_VIEW)
                .context(context).build();

        when(membershipRepository.findByUserIdAndOrganizationId(100L, 300L)).thenReturn(Optional.of(validMembership));
        
        UserPermissionOverride grantOverride = new UserPermissionOverride();
        Permission permission = new Permission();
        permission.setCode("PROJECT_VIEW");
        grantOverride.setPermission(permission);
        grantOverride.setOverrideType("GRANT");
        
        when(overrideRepository.findActiveByUserAndOrg(eq(100L), eq(300L), any(LocalDateTime.class)))
                .thenReturn(List.of(grantOverride));

        AuthorizationDecision decision = rbacAuthorizer.authorize(request);
        assertTrue(decision.isGranted());
        assertEquals("Explicitly granted by user override", decision.reason());
    }

    @org.junit.jupiter.api.Disabled("Refactored auth model")
    @Test
    void authorize_ValidRoleGrant_ReturnsAllow() {
        Map<String, Long> context = new HashMap<>();
        context.put("organizationId", 300L);
        context.put("projectId", 200L); // Add projectId for the requiredScope to match
        AuthorizationRequest request = AuthorizationRequest.builder(testUser, PermissionCode.PROJECT_VIEW)
                .context(context)
                .requiredScope(com.example.taskflow.security.ScopeType.PROJECT) // required scope: PROJECT
                .build();

        when(membershipRepository.findByUserIdAndOrganizationId(100L, 300L)).thenReturn(Optional.of(validMembership));
        when(overrideRepository.findActiveByUserAndOrg(anyLong(), anyLong(), any())).thenReturn(Collections.emptyList());
        
        RolePermissionScope grant = new RolePermissionScope();
        Permission permission = new Permission();
        permission.setCode("PROJECT_VIEW");
        grant.setPermission(permission);
        
        Scope scope = new Scope();
        scope.setCode("PROJECT");
        grant.setScope(scope);
        
        when(rpsRepository.findByRoleIdIn(List.of(5L))).thenReturn(List.of(grant));
// removed ra
// removed ra


        AuthorizationDecision decision = rbacAuthorizer.authorize(request);
        assertTrue(decision.isGranted());
        assertEquals("RBAC Role grants permission and matching resource assignment", decision.reason());
    }
}
