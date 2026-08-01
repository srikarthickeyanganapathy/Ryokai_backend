package com.example.taskflow.security;

import org.springframework.security.core.Authentication;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.security.authorization.AuthorizationRequest;
import com.example.taskflow.security.PermissionCode;
import java.io.Serializable;

public interface AuthorizationResourceResolver {
    String getTargetType();
    
    AuthorizationRequest buildRequest(Authentication auth, User user, Object targetDomainObject, PermissionCode permissionCode);
    
    AuthorizationRequest buildRequest(Authentication auth, User user, Serializable targetId, PermissionCode permissionCode);
}
