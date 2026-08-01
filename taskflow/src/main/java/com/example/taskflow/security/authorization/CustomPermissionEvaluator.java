package com.example.taskflow.security.authorization;

import java.io.Serializable;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.taskflow.user.domain.User;
import com.example.taskflow.user.infrastructure.persistence.UserRepository;
import com.example.taskflow.security.authorization.engine.AuthorizationEngine;
import com.example.taskflow.security.AuthorizationResourceResolver;
import com.example.taskflow.security.PermissionCode;

@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(CustomPermissionEvaluator.class);

    private final UserRepository userRepository;
    private final List<AuthorizationResourceResolver> handlers;
    private final AuthorizationEngine authorizationEngine;
    private final WorkspaceContextResolver contextResolver;

    public CustomPermissionEvaluator(UserRepository userRepository,
                                     List<AuthorizationResourceResolver> handlers,
                                     AuthorizationEngine authorizationEngine,
                                     WorkspaceContextResolver contextResolver) {
        this.userRepository = userRepository;
        this.handlers = handlers;
        this.authorizationEngine = authorizationEngine;
        this.contextResolver = contextResolver;
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
            return evaluateNullTarget(user, perm);
        }

        PermissionCode pCode;
        try {
            pCode = PermissionCode.valueOf(perm);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown permission code or legacy string used: '{}'", perm);
            return false;
        }

        for (AuthorizationResourceResolver handler : handlers) {
            if (handler.supportsClass(targetDomainObject.getClass())) {
                AuthorizationRequest request = handler.buildRequest(auth, user, targetDomainObject, pCode);
                if (request == null) return false;
                
                return authorizationEngine.authorize(request).isGranted();
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
        
        if (targetId == null && !"Project".equalsIgnoreCase(targetType) && !"Task".equalsIgnoreCase(targetType) && !"Organization".equalsIgnoreCase(targetType)) {
             return evaluateNullTarget(user, perm);
        }

        PermissionCode pCode;
        try {
            pCode = PermissionCode.valueOf(perm);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown permission code or legacy string used: '{}'", perm);
            return false;
        }

        for (AuthorizationResourceResolver handler : handlers) {
            if (handler.supportsResourceType(targetType)) {
                AuthorizationRequest request = handler.buildRequest(auth, user, targetId, pCode);
                if (request == null) return false;
                
                return authorizationEngine.authorize(request).isGranted();
            }
        }

        return false;
    }

    private boolean evaluateNullTarget(User user, String perm) {
        PermissionCode code;
        try {
            code = PermissionCode.valueOf(perm);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown permission code used for null target: '{}'", perm);
            return false;
        }

        // Attempt pipeline evaluation — requires org context
        Long orgId = contextResolver.resolveOrgIdForUser(user);
        if (orgId != null) {
            AuthorizationRequest request = AuthorizationRequest.builder(user, code)
                    .context(java.util.Map.of("organizationId", orgId))
                    .build();
            AuthorizationDecision decision = authorizationEngine.authorize(request);
            return decision.isGranted();
        }

        return false;
    }
}