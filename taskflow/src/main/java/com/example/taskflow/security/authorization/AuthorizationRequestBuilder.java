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
}
