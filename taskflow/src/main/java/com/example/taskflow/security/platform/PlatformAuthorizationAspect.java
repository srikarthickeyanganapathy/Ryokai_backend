package com.example.taskflow.security.platform;

import com.example.taskflow.domain.User;
import com.example.taskflow.repository.UserRepository;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Aspect that enforces {@link PlatformAuthorize} on platform endpoints.
 *
 * <p>This aspect extracts the currently authenticated user and delegates
 * the permission check to {@link PlatformAuthorizationService}.
 * If the user lacks the required platform permission, it throws an exception.
 */
@Aspect
@Component
public class PlatformAuthorizationAspect {

    private static final Logger log = LoggerFactory.getLogger(PlatformAuthorizationAspect.class);

    private final PlatformAuthorizationService platformAuthService;
    private final UserRepository userRepository;

    public PlatformAuthorizationAspect(PlatformAuthorizationService platformAuthService, UserRepository userRepository) {
        this.platformAuthService = platformAuthService;
        this.userRepository = userRepository;
    }

    @Before("@annotation(platformAuthorize)")
    public void authorizeMethod(JoinPoint joinPoint, PlatformAuthorize platformAuthorize) {
        doAuthorize(platformAuthorize);
    }

    @Before("@within(platformAuthorize)")
    public void authorizeClass(JoinPoint joinPoint, PlatformAuthorize platformAuthorize) {
        doAuthorize(platformAuthorize);
    }

    private void doAuthorize(PlatformAuthorize platformAuthorize) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException("User not authenticated");
        }

        User user = getUser(auth);
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("User not found");
        }

        PlatformPermission requiredPermission = platformAuthorize.value();
        platformAuthService.requirePermission(user, requiredPermission);
        log.debug("Platform authorization granted to user {} for permission {}", user.getId(), requiredPermission);
    }

    private User getUser(Authentication auth) {
        String username = null;
        if (auth.getPrincipal() instanceof UserDetails details) {
            username = details.getUsername();
        } else if (auth.getPrincipal() instanceof String str) {
            username = str;
        }

        if (username == null) return null;
        return userRepository.findByUsername(username).orElse(null);
    }
}
