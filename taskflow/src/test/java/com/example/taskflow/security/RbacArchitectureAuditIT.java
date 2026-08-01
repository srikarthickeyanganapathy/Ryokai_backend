package com.example.taskflow.security;

import java.io.FileWriter;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.taskflow.organization.rbac.application.OrganizationRoleService;
import com.example.taskflow.organization.rbac.domain.Role;
import com.example.taskflow.organization.rbac.dto.RoleCreateRequestDTO;
import com.example.taskflow.organization.rbac.dto.RoleResponseDTO;
import com.example.taskflow.organization.rbac.infrastructure.persistence.RoleRepository;
import com.example.taskflow.organization.rbac.infrastructure.persistence.ScopeRepository;
import com.example.taskflow.organization.rbac.domain.Permission;
import com.example.taskflow.organization.rbac.domain.Scope;
import com.example.taskflow.security.authorization.AuthorizationDecision;
import com.example.taskflow.security.authorization.engine.AuthorizationEngine;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.user.infrastructure.persistence.UserRepository;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.core.infrastructure.persistence.OrganizationRepository;
import com.example.taskflow.organization.membership.domain.OrganizationMembership;
import com.example.taskflow.organization.membership.infrastructure.persistence.OrganizationMembershipRepository;

@SpringBootTest
public class RbacArchitectureAuditIT {

    @Autowired
    private OrganizationRoleService roleService;

    @Autowired
    private AuthorizationEngine engine;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ScopeRepository scopeRepository;

    @Autowired
    private com.example.taskflow.organization.rbac.infrastructure.persistence.PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Test
    public void executeComprehensiveAudit() throws Exception {
        System.out.println("Starting Dynamic RBAC Architecture Audit...");

        User adminUser = new User();
        adminUser.setUsername("audit_admin_" + System.currentTimeMillis());
        adminUser.setPassword("password");
        adminUser = userRepository.saveAndFlush(adminUser);

        Organization org = new Organization();
        org.setName("Audit Corp");
        org.setStatus(Organization.OrgStatus.ACTIVE);
        org.setCreatedBy(adminUser);
        org = organizationRepository.saveAndFlush(org);

        Role adminRole = new Role();
        adminRole.setName("System Admin");
        adminRole.setOrganization(org);
        adminRole.setPriority(1);
        
        // Assign all permissions to the admin user's role so matrix passes stage 2
        for (PermissionCode pCode : PermissionCode.values()) {
            com.example.taskflow.organization.rbac.domain.RolePermissionScope rps = new com.example.taskflow.organization.rbac.domain.RolePermissionScope();
            rps.setRole(adminRole);
            
            Optional<Permission> permOpt = permissionRepository.findByCode(pCode.name());
            Optional<Scope> scopeOpt = scopeRepository.findByCode(ScopeType.ORGANIZATION.name());
            
            if (permOpt.isPresent() && scopeOpt.isPresent()) {
                rps.setPermission(permOpt.get());
                rps.setScope(scopeOpt.get());
                adminRole.getRolePermissionScopes().add(rps);
            }
        }
        
        adminRole = roleRepository.saveAndFlush(adminRole);

        OrganizationMembership mem = new OrganizationMembership();
        mem.setUser(adminUser);
        mem.setOrganization(org);
        mem.setOrgRole(adminRole);
        mem = membershipRepository.saveAndFlush(mem);

        StringBuilder report = new StringBuilder();
        report.append("# Enterprise RBAC Architecture Audit Report\n\n");
        
        report.append("## 1. Default Role Generation Audit\n\n");
        report.append("| Permission | Expected Assignment | Current Result | Reason |\n");
        report.append("|---|---|---|---|\n");

        int missingMetadata = 0;
        int defaultGenerationFailures = 0;

        for (PermissionCode code : PermissionCode.values()) {
            String expectedAssignment = PermissionMetadataRegistry.getRecommendedScope(code.name());
            boolean shouldAssign = expectedAssignment != null;
            if (!shouldAssign) missingMetadata++;

            boolean assigned = false;
            String reason = "N/A";
            
            try {
                RoleCreateRequestDTO req = new RoleCreateRequestDTO("TestRole_" + code.name(), "Desc", org.getId(), 10);
                RoleResponseDTO resp = roleService.createRole(req, adminUser);
                
                Role role = roleRepository.findById(resp.getId()).orElseThrow();
                
                Optional<com.example.taskflow.organization.rbac.domain.RolePermissionScope> rpsOpt = role.getRolePermissionScopes().stream()
                        .filter(rps -> rps.getPermission().getCode().equals(code.name()))
                        .findFirst();
                
                if (rpsOpt.isPresent()) {
                    assigned = true;
                    reason = "Persisted Scope: " + rpsOpt.get().getScope().getCode();
                } else {
                    if (shouldAssign) {
                        reason = "Service Filtered / Unsupported Scope";
                        defaultGenerationFailures++;
                    } else {
                        reason = "Metadata Missing";
                    }
                }
            } catch (Exception e) {
                reason = "Pipeline Rejected / Validation Failed: " + e.getMessage();
                defaultGenerationFailures++;
            }

            report.append(String.format("| %s | %s | %s | %s |\n",
                code.name(),
                expectedAssignment != null ? expectedAssignment : "None",
                assigned ? "Assigned" : "Skipped",
                reason
            ));
        }

        report.append("\n## 2. Cross-Scope Authorization Matrix & Pipeline Stage Verification\n\n");
        report.append("| Permission | OWN | PROJECT | TEAM | ORGANIZATION | Decision Source & Failed Stage |\n");
        report.append("|---|---|---|---|---|---|\n");

        for (PermissionCode code : PermissionCode.values()) {
            StringBuilder matrix = new StringBuilder(String.format("| %s | ", code.name()));
            String failureReason = "";

            ScopeType[] testScopes = {ScopeType.OWN, ScopeType.PROJECT, ScopeType.TEAM, ScopeType.ORGANIZATION};
            for (ScopeType scope : testScopes) {
                AuthorizationRequest.Builder reqBuilder = AuthorizationRequest.builder(adminUser, code);
                
                if (scope == ScopeType.PROJECT) {
                    reqBuilder.context(java.util.Map.of("organizationId", org.getId(), "projectId", 1L));
                } else if (scope == ScopeType.TEAM) {
                    reqBuilder.context(java.util.Map.of("organizationId", org.getId(), "teamId", 1L));
                } else if (scope == ScopeType.OWN) {
                    reqBuilder.context(java.util.Map.of("organizationId", org.getId()));

                    // Attempting to simulate OWN. The current builder fallback might force ORGANIZATION if we only set orgId.
                    // This is part of the architecture verification.
                    reqBuilder.resourceType("TASK").resourceId(1L);
                }

                AuthorizationRequest req = reqBuilder.build();

                AuthorizationDecision decision = engine.authorize(req);
                if (decision.isGranted()) {
                    matrix.append("PASS | ");
                } else {
                    matrix.append("FAIL | ");
                    failureReason = decision.reason();
                }
            }

            matrix.append(failureReason.isEmpty() ? "All Passed" : failureReason).append(" |\n");
            report.append(matrix.toString());
        }

        report.append("\n## 3. Architecture Health Summary\n\n");
        report.append(String.format("Permission Coverage (Metadata vs Enum): %d / %d\n", (PermissionCode.values().length - missingMetadata), PermissionCode.values().length));
        report.append(String.format("Orphaned/Dead Permissions Found: %d\n", missingMetadata));
        report.append(String.format("Default Role Generation Failures: %d\n", defaultGenerationFailures));
        
        try (FileWriter fw = new FileWriter("rbac_dynamic_audit_report.md")) {
            fw.write(report.toString());
        }
        System.out.println("Audit complete. Report written to rbac_dynamic_audit_report.md.");
    }
}
