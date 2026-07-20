package com.example.taskflow.security;

import java.io.Serializable;
import java.util.List;

import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.taskflow.domain.User;
import com.example.taskflow.repository.UserRepository;
import com.example.taskflow.service.PermissionService;

@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final List<DomainPermissionHandler> handlers;

    public CustomPermissionEvaluator(UserRepository userRepository, 
                                     PermissionService permissionService,
                                     List<DomainPermissionHandler> handlers) {
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.handlers = handlers;
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
            return permissionService.hasPermission(user, perm);
        }

        for (DomainPermissionHandler handler : handlers) {
            String typeName = targetDomainObject.getClass().getSimpleName();
            String handlerType = handler.getTargetType();
            
            if (typeName.equals(handlerType) || 
               (handlerType.equals("Task") && (typeName.equals("TaskRequestDTO") || typeName.equals("BulkAssignRequestDTO")))) {
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
             return permissionService.hasPermission(user, perm);
        }

        for (DomainPermissionHandler handler : handlers) {
            if (targetType.equals(handler.getTargetType())) {
                return handler.hasPermission(auth, user, targetId, perm);
            }
        }

        return false;
    }
}
