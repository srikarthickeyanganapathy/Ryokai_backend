package com.example.taskflow.security.authorization;

import com.example.taskflow.user.domain.User;
import com.example.taskflow.security.PermissionCode;
import com.example.taskflow.security.ScopeType;
import com.example.taskflow.security.WorkspaceType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class AuthorizationRequestBuilder {

    public AuthorizationRequest build(User user, 
                                      PermissionCode permission, 
                                      String resourceType, 
                                      Long resourceId,
                                      WorkspaceType workspaceType, 
                                      ScopeType requiredScope, 
                                      Map<String, Long> context, 
                                      Set<OwnershipRole> ownership) {
        
        return build(user, permission, resourceType, resourceId, workspaceType, requiredScope, context, ownership, java.util.Collections.emptyMap());
    }

    public AuthorizationRequest build(User user, 
                                      PermissionCode permission, 
                                      String resourceType, 
                                      Long resourceId,
                                      WorkspaceType workspaceType, 
                                      ScopeType requiredScope, 
                                      Map<String, Long> context, 
                                      Set<OwnershipRole> ownership,
                                      Map<String, Object> policyContext) {
        
        return AuthorizationRequest.builder(user, permission)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .workspaceType(workspaceType)
                .requiredScope(requiredScope)
                .context(context)
                .ownership(ownership)
                .policyContext(policyContext)
                .build();
    }

    public boolean supportsDto(Class<?> clazz, String resourceType) {
        String className = clazz.getSimpleName();
        if ("TASK".equalsIgnoreCase(resourceType)) {
            return className.equals("TaskRequestDTO") || className.equals("BulkAssignRequestDTO");
        } else if ("PROJECT".equalsIgnoreCase(resourceType)) {
            return className.equals("ProjectRequestDTO") || className.equals("CreateProjectRequestDTO");
        } else if ("TEAM".equalsIgnoreCase(resourceType)) {
            return className.equals("CreateTeamRequestDTO") || className.equals("TeamMemberRequestDTO");
        } else if ("ORGANIZATION".equalsIgnoreCase(resourceType)) {
            return className.equals("CreateOrganizationRequestDTO") || className.equals("UpdateOrganizationRequestDTO");
        }
        return false;
    }

    public AuthorizationRequest buildFromDto(User user, PermissionCode permission, Object dto, String resourceType) {
        Map<String, Long> context = new java.util.HashMap<>();
        
        try {
            // Best-effort context extraction without DB queries (keeps builder focused on request construction)
            java.lang.reflect.Method getProjectId = getMethodSafe(dto.getClass(), "getProjectId");
            if (getProjectId != null) context.put("projectId", (Long) getProjectId.invoke(dto));
            
            java.lang.reflect.Method getTeamId = getMethodSafe(dto.getClass(), "getTeamId");
            if (getTeamId != null) context.put("teamId", (Long) getTeamId.invoke(dto));
            
            java.lang.reflect.Method getOrgId = getMethodSafe(dto.getClass(), "getOrganizationId");
            if (getOrgId != null) {
                context.put("organizationId", (Long) getOrgId.invoke(dto));
            } else {
                java.lang.reflect.Method getOrgIdAlt = getMethodSafe(dto.getClass(), "getOrgId");
                if (getOrgIdAlt != null) context.put("organizationId", (Long) getOrgIdAlt.invoke(dto));
            }
            
            java.lang.reflect.Method getCrewId = getMethodSafe(dto.getClass(), "getCrewId");
            if (getCrewId != null) context.put("crewId", (Long) getCrewId.invoke(dto));

        } catch (Exception e) {
            // Ignore reflection errors, proceed with what we extracted
        }
        
        // Scope type defaults to the recommended scope for the permission, workspace type to ORGANIZATION 
        // (since we can't query DB here, this provides a fallback safe evaluation context)
        ScopeType scope = ScopeType.valueOf(com.example.taskflow.security.PermissionMetadataRegistry.getRecommendedScope(permission.name()));
        
        return build(user, permission, resourceType, null, WorkspaceType.ORGANIZATION, scope, context, java.util.Collections.emptySet());
    }
    
    private java.lang.reflect.Method getMethodSafe(Class<?> clazz, String methodName) {
        try {
            return clazz.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
