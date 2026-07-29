package com.example.taskflow.security;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Framework-level permission implication rules.
 *
 * <p>These are universal invariants of the permission model, not tenant-configurable.
 * If a user has permission A and A implies B, the user implicitly has permission B.
 *
 * <p>Implications are resolved <b>transitively</b>:
 * {@code TASK_OVERRIDE â†’ TASK_REASSIGN â†’ TASK_ASSIGN â†’ TASK_VIEW}
 *
 * <p>Example: A user with {@code TASK_UPDATE} implicitly has {@code TASK_VIEW}
 * without needing a separate row in {@code role_permission_scopes}.
 */
public final class PermissionImplications {

    private static final Map<PermissionCode, Set<PermissionCode>> DIRECT_IMPLICATIONS;
    private static final Map<PermissionCode, Set<PermissionCode>> TRANSITIVE_CACHE;

    static {
        EnumMap<PermissionCode, Set<PermissionCode>> map = new EnumMap<>(PermissionCode.class);

        // â”€â”€ Task â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        map.put(PermissionCode.TASK_UPDATE,           EnumSet.of(PermissionCode.TASK_VIEW));
        map.put(PermissionCode.TASK_DELETE,           EnumSet.of(PermissionCode.TASK_VIEW));
        map.put(PermissionCode.TASK_APPROVE,          EnumSet.of(PermissionCode.TASK_VIEW));
        map.put(PermissionCode.TASK_REJECT,           EnumSet.of(PermissionCode.TASK_VIEW));
        map.put(PermissionCode.TASK_ASSIGN,           EnumSet.of(PermissionCode.TASK_VIEW));
        map.put(PermissionCode.TASK_REASSIGN,         EnumSet.of(PermissionCode.TASK_ASSIGN));
        map.put(PermissionCode.TASK_OVERRIDE,         EnumSet.of(
                PermissionCode.TASK_APPROVE,
                PermissionCode.TASK_REJECT,
                PermissionCode.TASK_REASSIGN,
                PermissionCode.TASK_REOPEN,
                PermissionCode.TASK_CANCEL,
                PermissionCode.TASK_DEPENDENCY_UPDATE));

        // â”€â”€ Project â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        map.put(PermissionCode.PROJECT_UPDATE,         EnumSet.of(PermissionCode.PROJECT_VIEW));
        map.put(PermissionCode.PROJECT_DELETE,         EnumSet.of(PermissionCode.PROJECT_VIEW));
        map.put(PermissionCode.PROJECT_SETTINGS_UPDATE, EnumSet.of(PermissionCode.PROJECT_VIEW));
        map.put(PermissionCode.PROJECT_MEMBER_ADD,     EnumSet.of(PermissionCode.PROJECT_VIEW));
        map.put(PermissionCode.PROJECT_ARCHIVE,        EnumSet.of(PermissionCode.PROJECT_VIEW));

        // â”€â”€ Organization â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        map.put(PermissionCode.ORG_SETTINGS_UPDATE,    EnumSet.of(PermissionCode.ORG_SETTINGS_VIEW));
        map.put(PermissionCode.ORG_PROFILE_UPDATE,     EnumSet.of(PermissionCode.ORG_VIEW));

        DIRECT_IMPLICATIONS = Collections.unmodifiableMap(map);

        // Pre-compute transitive closure for every permission that has implications
        EnumMap<PermissionCode, Set<PermissionCode>> cache = new EnumMap<>(PermissionCode.class);
        for (PermissionCode code : PermissionCode.values()) {
            Set<PermissionCode> expanded = EnumSet.noneOf(PermissionCode.class);
            expandTransitive(code, expanded);
            if (!expanded.isEmpty()) {
                cache.put(code, Collections.unmodifiableSet(expanded));
            }
        }
        TRANSITIVE_CACHE = Collections.unmodifiableMap(cache);
    }

    private PermissionImplications() {
        // utility class
    }

    /**
     * Returns all permissions that are <b>directly</b> implied by the given permission.
     * Returns an empty set if the permission has no direct implications.
     */
    public static Set<PermissionCode> getDirectImplications(PermissionCode permission) {
        return DIRECT_IMPLICATIONS.getOrDefault(permission, EnumSet.noneOf(PermissionCode.class));
    }

    /**
     * Returns all permissions that are <b>transitively</b> implied by the given permission.
     * The result is pre-computed and cached for O(1) lookups.
     *
     * <p>Example: {@code getTransitiveImplications(TASK_OVERRIDE)} returns
     * {@code {TASK_APPROVE, TASK_REJECT, TASK_REASSIGN, TASK_REOPEN, TASK_CANCEL,
     *          TASK_DEPENDENCY_UPDATE, TASK_ASSIGN, TASK_VIEW}}
     */
    public static Set<PermissionCode> getTransitiveImplications(PermissionCode permission) {
        return TRANSITIVE_CACHE.getOrDefault(permission, EnumSet.noneOf(PermissionCode.class));
    }

    /**
     * Returns true if {@code granted} permission implicitly includes {@code required} permission,
     * either directly or transitively.
     */
    public static boolean implies(PermissionCode granted, PermissionCode required) {
        if (granted == required) {
            return true;
        }
        Set<PermissionCode> implied = TRANSITIVE_CACHE.get(granted);
        return implied != null && implied.contains(required);
    }

    /**
     * Given a set of explicitly granted permissions, returns the full effective set
     * including all transitively implied permissions.
     */
    public static Set<PermissionCode> expandAll(Set<PermissionCode> granted) {
        EnumSet<PermissionCode> expanded = EnumSet.copyOf(granted);
        for (PermissionCode code : granted) {
            Set<PermissionCode> implied = TRANSITIVE_CACHE.get(code);
            if (implied != null) {
                expanded.addAll(implied);
            }
        }
        return Collections.unmodifiableSet(expanded);
    }

    private static void expandTransitive(PermissionCode code, Set<PermissionCode> accumulator) {
        Set<PermissionCode> direct = DIRECT_IMPLICATIONS.get(code);
        if (direct == null) {
            return;
        }
        for (PermissionCode implied : direct) {
            if (accumulator.add(implied)) {
                expandTransitive(implied, accumulator);
            }
        }
    }
}