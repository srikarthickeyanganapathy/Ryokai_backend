package com.example.taskflow.security;

import org.springframework.security.core.Authentication;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import java.io.Serializable;

public interface AuthorizationResourceResolver {
    boolean supportsResourceType(String resourceType);
    
    boolean supportsClass(Class<?> targetClass);
    
    AuthorizationRequest buildRequest(Authentication auth, User user, Object targetDomainObject, PermissionCode permissionCode);
    
    AuthorizationRequest buildRequest(Authentication auth, User user, Serializable targetId, PermissionCode permissionCode);
}
