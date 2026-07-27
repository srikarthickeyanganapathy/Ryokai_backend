package com.example.taskflow.security.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for securing platform-level controller methods.
 *
 * <p>Applying this annotation ensures that the method can only be executed
 * if the authenticated user has a platform identity with the required permission.
 * This completely bypasses the workspace RBAC pipeline.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PlatformAuthorize {
    PlatformPermission value();
}
