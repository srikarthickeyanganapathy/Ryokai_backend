package com.example.taskflow.security.authorization.engine.impl;

import com.example.taskflow.security.authorization.AuthorizationDecision;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.WorkspaceType;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;
import com.example.taskflow.crew.infrastructure.persistence.CrewMemberRepository;
import com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository;
import com.example.taskflow.organization.core.domain.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

class MembershipResolverImplTest {

    @Mock
    private OrganizationMembershipRepository orgRepo;

    @Mock
    private CrewMemberRepository crewRepo;

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private MembershipResolverImpl membershipResolver;

    private User testUser;
    private User superAdmin;

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
    }

    @Test
    void resolveMembership_SuperAdmin_ReturnsAllow() {
        AuthorizationRequest request = AuthorizationRequest.builder(superAdmin, PermissionCode.TASK_VIEW)
                
                .build();

        AuthorizationDecision decision = membershipResolver.resolveMembership(request);

        assertTrue(decision.isGranted());
        assertEquals("SuperAdmin bypass", decision.reason());
    }

    @Test
    void resolveMembership_PersonalWorkspace_ReturnsAbstain() {
        AuthorizationRequest request = AuthorizationRequest.builder(testUser, PermissionCode.TASK_VIEW)
                .workspaceType(WorkspaceType.PERSONAL)
                .build();

        AuthorizationDecision decision = membershipResolver.resolveMembership(request);

        assertTrue(decision.isAbstain());
        assertEquals("Personal workspace requires explicit ownership check", decision.reason());
    }

    @Test
    void resolveMembership_OrganizationWorkspace_MissingOrgId_ReturnsDeny() {
        Map<String, Long> context = new HashMap<>();
        AuthorizationRequest request = AuthorizationRequest.builder(testUser, PermissionCode.TASK_VIEW)
                
                .context(context)
                .build();

        AuthorizationDecision decision = membershipResolver.resolveMembership(request);

        assertFalse(decision.isGranted());
        assertEquals("Organization ID required for org-scoped check", decision.reason());
    }

    @Test
    void resolveMembership_OrganizationWorkspace_OrgNotFound_ReturnsDeny() {
        Map<String, Long> context = new HashMap<>();
        context.put("organizationId", 300L);

        AuthorizationRequest request = AuthorizationRequest.builder(testUser, PermissionCode.TASK_VIEW)
                
                .context(context)
                .build();

        when(organizationRepository.findById(300L)).thenReturn(Optional.empty());

        AuthorizationDecision decision = membershipResolver.resolveMembership(request);

        assertFalse(decision.isGranted());
        assertEquals("Organization not found", decision.reason());
    }

    @Test
    void resolveMembership_OrganizationWorkspace_NotAMember_ReturnsDeny() {
        Map<String, Long> context = new HashMap<>();
        context.put("organizationId", 300L);

        AuthorizationRequest request = AuthorizationRequest.builder(testUser, PermissionCode.TASK_VIEW)
                
                .context(context)
                .build();

        Organization org = new Organization();
        org.setId(300L);
        org.setStatus(Organization.OrgStatus.ACTIVE);

        when(organizationRepository.findById(300L)).thenReturn(Optional.of(org));
        when(orgRepo.existsByUserIdAndOrganizationId(100L, 300L)).thenReturn(false);

        AuthorizationDecision decision = membershipResolver.resolveMembership(request);

        assertFalse(decision.isGranted());
        assertEquals("User is not an Organization member", decision.reason());
    }

    @Test
    void resolveMembership_OrganizationWorkspace_IntrinsicAction_ReturnsAllow() {
        Map<String, Long> context = new HashMap<>();
        context.put("organizationId", 300L);

        AuthorizationRequest request = AuthorizationRequest.builder(testUser, PermissionCode.TASK_VIEW)
                
                .context(context)
                .build();

        Organization org = new Organization();
        org.setId(300L);
        org.setStatus(Organization.OrgStatus.ACTIVE);

        when(organizationRepository.findById(300L)).thenReturn(Optional.of(org));
        when(orgRepo.existsByUserIdAndOrganizationId(100L, 300L)).thenReturn(true);

        AuthorizationDecision decision = membershipResolver.resolveMembership(request);

        assertTrue(decision.isGranted());
        assertEquals("User is an Organization member (Intrinsic)", decision.reason());
    }

    @Test
    void resolveMembership_OrganizationWorkspace_MutatingAction_ReturnsAbstain() {
        Map<String, Long> context = new HashMap<>();
        context.put("organizationId", 300L);

        AuthorizationRequest request = AuthorizationRequest.builder(testUser, PermissionCode.TASK_DELETE)
                
                .context(context)
                .build();

        Organization org = new Organization();
        org.setId(300L);
        org.setStatus(Organization.OrgStatus.ACTIVE);

        when(organizationRepository.findById(300L)).thenReturn(Optional.of(org));
        when(orgRepo.existsByUserIdAndOrganizationId(100L, 300L)).thenReturn(true);

        AuthorizationDecision decision = membershipResolver.resolveMembership(request);

        assertTrue(decision.isAbstain());
        assertEquals("User is an Organization member evaluating non-intrinsic action", decision.reason());
    }
}
