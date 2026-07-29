package com.example.taskflow.security.authorization;

import com.example.taskflow.security.PermissionCode;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps legacy permission strings used in existing {@code @PreAuthorize} annotations
 * and {@code DomainPermissionHandler} implementations to the new {@link PermissionCode} enum.
 *
 * <p>This bridge enables incremental migration: existing controllers continue to use
 * their current permission strings while the pipeline resolves them to the new codes.
 *
 * <p>Once all controllers and handlers are migrated to use {@code PermissionCode} directly,
 * this class can be removed (Phase 4).
 *
 * @deprecated Will be removed once all callers use {@code PermissionCode} directly.
 */
@Deprecated(forRemoval = true)
public final class LegacyPermissionMapper {

    private static final Map<String, PermissionCode> LEGACY_MAP;

    static {
        Map<String, PermissionCode> map = new HashMap<>();

        // â”€â”€ Task domain â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // Controllers use both short ("VIEW") and prefixed ("TASK_VIEW") forms
        map.put("VIEW", PermissionCode.TASK_VIEW);
        map.put("TASK_VIEW", PermissionCode.TASK_VIEW);
        map.put("READ", PermissionCode.TASK_VIEW);
        map.put("TASK_READ", PermissionCode.TASK_VIEW);
        map.put("COMMENT", PermissionCode.TASK_COMMENT_CREATE);
        map.put("TASK_CREATE", PermissionCode.TASK_CREATE);
        map.put("CREATE", PermissionCode.TASK_CREATE);
        map.put("EDIT", PermissionCode.TASK_UPDATE);
        map.put("TASK_EDIT", PermissionCode.TASK_UPDATE);
        map.put("CHECKLIST_EDIT", PermissionCode.TASK_UPDATE);
        map.put("DELETE", PermissionCode.TASK_DELETE);
        map.put("TASK_DELETE", PermissionCode.TASK_DELETE);
        map.put("REVIEW", PermissionCode.TASK_APPROVE);
        map.put("TASK_REVIEW", PermissionCode.TASK_APPROVE);
        map.put("REASSIGN", PermissionCode.TASK_REASSIGN);
        map.put("TASK_REASSIGN", PermissionCode.TASK_REASSIGN);
        map.put("ASSIGN", PermissionCode.TASK_ASSIGN);
        map.put("TASK_ASSIGN", PermissionCode.TASK_ASSIGN);
        map.put("DEPENDENCY_EDIT", PermissionCode.TASK_DEPENDENCY_UPDATE);
        map.put("TASK_DEPENDENCY_EDIT", PermissionCode.TASK_DEPENDENCY_UPDATE);
        map.put("EVIDENCE_EDIT", PermissionCode.TASK_UPDATE);
        map.put("TASK_EVIDENCE_EDIT", PermissionCode.TASK_UPDATE);
        map.put("ARCHIVE", PermissionCode.TASK_ARCHIVE);
        map.put("TASK_ARCHIVE", PermissionCode.TASK_ARCHIVE);
        map.put("TASK_OVERRIDE", PermissionCode.TASK_OVERRIDE);

        // â”€â”€ Project domain â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        map.put("PROJECT_CREATE", PermissionCode.PROJECT_CREATE);
        map.put("PROJECT_MANAGE", PermissionCode.PROJECT_UPDATE);
        map.put("PROJECT_READ", PermissionCode.PROJECT_VIEW);
        map.put("PROJECT_VIEW", PermissionCode.PROJECT_VIEW);
        map.put("PROJECT_EDIT", PermissionCode.PROJECT_UPDATE);
        map.put("PROJECT_DELETE", PermissionCode.PROJECT_DELETE);

        // â”€â”€ Organization domain â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        map.put("ORG_MEMBER_INVITE", PermissionCode.MEMBER_INVITE);
        map.put("ORG_MEMBER_REMOVE", PermissionCode.MEMBER_REMOVE);
        map.put("ORG_READ", PermissionCode.ORG_VIEW);
        map.put("ORG_VIEW", PermissionCode.ORG_VIEW);
        map.put("ORG_EDIT", PermissionCode.ORG_PROFILE_UPDATE);
        map.put("ORG_UPDATE", PermissionCode.ORG_PROFILE_UPDATE);
        map.put("ORG_DELETE", PermissionCode.ORG_ARCHIVE);
        map.put("ORG_MEMBER", PermissionCode.ORG_VIEW); // A generic fallback often used

        // â”€â”€ Team domain â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        map.put("TEAM_CREATE", PermissionCode.TEAM_CREATE);
        map.put("TEAM_MANAGE", PermissionCode.TEAM_UPDATE);

        // â”€â”€ Role domain â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        map.put("ROLE_MANAGE", PermissionCode.ROLE_UPDATE);

        // â”€â”€ Leave domain â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        map.put("LEAVE_REQUEST_MANAGE", PermissionCode.LEAVE_APPROVE);

        // â”€â”€ Announcement domain â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        map.put("ANNOUNCEMENT_MANAGE", PermissionCode.ANNOUNCEMENT_CREATE);

        // â”€â”€ Goal domain â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        map.put("GOAL_MANAGE", PermissionCode.GOAL_CREATE);

        // â”€â”€ Dashboard domain â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        map.put("DASHBOARD_ORG_WIDE_VIEW", PermissionCode.DASHBOARD_VIEW);

        LEGACY_MAP = Collections.unmodifiableMap(map);
    }

    private LegacyPermissionMapper() {
        // utility class
    }

    /**
     * Resolves a legacy permission string to a {@link PermissionCode}.
     *
     * @param legacyPermission the string used in existing {@code @PreAuthorize} or handler code
     * @return the resolved {@code PermissionCode}, or {@code null} if unmapped
     */
    public static PermissionCode resolve(String legacyPermission) {
        if (legacyPermission == null) return null;
        return LEGACY_MAP.get(legacyPermission);
    }

    /**
     * Resolves a legacy permission string, throwing if no mapping exists.
     */
    public static PermissionCode resolveOrThrow(String legacyPermission) {
        PermissionCode code = resolve(legacyPermission);
        if (code == null) {
            throw new IllegalArgumentException("No PermissionCode mapping for legacy permission: " + legacyPermission);
        }
        return code;
    }

    /**
     * Returns the context-appropriate target type based on the handler type.
     * Used to determine which domain the permission string applies to when the same
     * short form (e.g., "VIEW") could belong to different modules.
     */
    public static PermissionCode resolveForDomain(String domain, String permission) {
        // Try domain-prefixed form first
        String prefixed = domain.toUpperCase() + "_" + permission;
        PermissionCode code = resolve(prefixed);
        if (code != null) return code;

        // Fall back to raw permission string
        return resolve(permission);
    }
}