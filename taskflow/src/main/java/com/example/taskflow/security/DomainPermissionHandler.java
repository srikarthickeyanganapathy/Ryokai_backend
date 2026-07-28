package com.example.taskflow.security;

import org.springframework.security.core.Authentication;
import com.example.taskflow.user.domain.User;
import java.io.Serializable;
import com.example.taskflow.organization.rbac.domain.Permission;

public interface DomainPermissionHandler {
    String getTargetType();
    
    boolean hasPermission(Authentication auth, User user, Object targetDomainObject, String permission);
    
    boolean hasPermission(Authentication auth, User user, Serializable targetId, String permission);
}