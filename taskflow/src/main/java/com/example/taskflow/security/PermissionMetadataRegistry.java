package com.example.taskflow.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PermissionMetadataRegistry {

    public static class Metadata {
        public final String group;
        public final String riskLevel;
        public final int order;
        public final List<String> supportedScopes;
        public final boolean scopeRequired;
        public final List<String> requires;
        public final String recommendedScope;
        public final String actionRules;
        public final boolean requiresResourceAssignment;

        public Metadata(String group, String riskLevel, int order, List<String> supportedScopes, boolean scopeRequired, List<String> requires, String recommendedScope, String actionRules, boolean requiresResourceAssignment) {
            this.group = group;
            this.riskLevel = riskLevel;
            this.order = order;
            this.supportedScopes = supportedScopes != null ? supportedScopes : List.of("ORGANIZATION");
            this.scopeRequired = scopeRequired;
            this.requires = requires != null ? requires : List.of();
            this.recommendedScope = recommendedScope != null ? recommendedScope : "ORGANIZATION";
            this.actionRules = actionRules;
            this.requiresResourceAssignment = requiresResourceAssignment;
        }
    }

    private static final Map<String, Metadata> metadataMap = new HashMap<>();
    private static final Map<String, Integer> moduleOrderMap = new HashMap<>();

    private static final List<String> ALL_SCOPES = List.of("OWN", "PROJECT", "TEAM", "ORGANIZATION");
    private static final List<String> NON_OWN_SCOPES = List.of("PROJECT", "TEAM", "ORGANIZATION");
    private static final List<String> TEAM_ORG_SCOPES = List.of("TEAM", "ORGANIZATION");
    private static final List<String> ORG_ONLY = List.of("ORGANIZATION");

    static {
        // --- MODULE ORDERING ---
        moduleOrderMap.put("ORGANIZATION", 1);
        moduleOrderMap.put("MEMBER", 2);
        moduleOrderMap.put("ROLE", 3);
        moduleOrderMap.put("TEAM", 4);
        moduleOrderMap.put("PROJECT", 5);
        moduleOrderMap.put("TASK", 6);
        moduleOrderMap.put("GOAL", 7);
        moduleOrderMap.put("CALENDAR", 8);
        moduleOrderMap.put("ACTIVITY", 9);
        moduleOrderMap.put("DASHBOARD", 10);
        moduleOrderMap.put("ANNOUNCEMENT", 11);
        moduleOrderMap.put("LEAVE", 12);
        
        // --- PERMISSION METADATA ---
        
        // ORGANIZATION
        register("ORG_VIEW", "READ", "LOW", 1, ORG_ONLY, false, null, "ORGANIZATION", null, false);
        register("ORG_PROFILE_UPDATE", "WRITE", "MEDIUM", 2, ORG_ONLY, false, null, "ORGANIZATION", null, false);
        register("ORG_SETTINGS_UPDATE", "ADMINISTRATION", "HIGH", 3, ORG_ONLY, false, null, "ORGANIZATION", null, false);
        register("ORG_TRANSFER_OWNERSHIP", "ADMINISTRATION", "CRITICAL", 4, ORG_ONLY, false, null, "ORGANIZATION", null, false);
        register("ORG_ARCHIVE", "ADMINISTRATION", "CRITICAL", 5, ORG_ONLY, false, null, "ORGANIZATION", null, false);
        register("ORG_RESTORE", "ADMINISTRATION", "CRITICAL", 6, ORG_ONLY, false, null, "ORGANIZATION", null, false);

        // MEMBER
        register("MEMBER_VIEW", "READ", "LOW", 1, TEAM_ORG_SCOPES, true, null, "ORGANIZATION", null, false);
        register("MEMBER_INVITE", "WRITE", "MEDIUM", 2, TEAM_ORG_SCOPES, true, null, "ORGANIZATION", null, false);
        register("MEMBER_SUSPEND", "WRITE", "MEDIUM", 3, TEAM_ORG_SCOPES, true, null, "ORGANIZATION", null, false);
        register("MEMBER_REACTIVATE", "WRITE", "MEDIUM", 4, TEAM_ORG_SCOPES, true, null, "ORGANIZATION", null, false);
        register("MEMBER_REMOVE", "ADMINISTRATION", "HIGH", 5, TEAM_ORG_SCOPES, true, null, "ORGANIZATION", null, false);
        register("MEMBER_ROLE_UPDATE", "ADMINISTRATION", "HIGH", 6, ORG_ONLY, false, null, "ORGANIZATION", null, false);
        register("MEMBER_EXPORT", "READ", "LOW", 7, ORG_ONLY, false, null, "ORGANIZATION", null, false);

        // ROLE
        register("ROLE_VIEW", "READ", "LOW", 1, ORG_ONLY, false, null, "ORGANIZATION", null, false);
        register("ROLE_CREATE", "WRITE", "MEDIUM", 2, ORG_ONLY, false, null, "ORGANIZATION", null, false);
        register("ROLE_UPDATE", "WRITE", "HIGH", 3, ORG_ONLY, false, null, "ORGANIZATION", null, false);
        register("ROLE_DELETE", "ADMINISTRATION", "CRITICAL", 4, ORG_ONLY, false, null, "ORGANIZATION", null, false);
        

        // TEAM
        register("TEAM_VIEW", "READ", "LOW", 1, TEAM_ORG_SCOPES, true, null, "TEAM", null, true);
        register("TEAM_CREATE", "WRITE", "MEDIUM", 2, ORG_ONLY, false, null, "ORGANIZATION", null, false);
        register("TEAM_UPDATE", "WRITE", "MEDIUM", 3, TEAM_ORG_SCOPES, true, null, "TEAM", null, true);
        register("TEAM_DELETE", "ADMINISTRATION", "HIGH", 4, TEAM_ORG_SCOPES, true, null, "TEAM", null, true);
        register("TEAM_ARCHIVE", "ADMINISTRATION", "HIGH", 5, TEAM_ORG_SCOPES, true, null, "TEAM", null, true);
        register("TEAM_MEMBER_ADD", "WORKFLOW", "MEDIUM", 6, TEAM_ORG_SCOPES, true, null, "TEAM", null, true);
        register("TEAM_MEMBER_REMOVE", "WORKFLOW", "MEDIUM", 7, TEAM_ORG_SCOPES, true, null, "TEAM", null, true);
        register("TEAM_MEMBER_ROLE_UPDATE", "WORKFLOW", "MEDIUM", 8, TEAM_ORG_SCOPES, true, null, "TEAM", null, true);

        // PROJECT
        register("PROJECT_VIEW", "READ", "LOW", 1, NON_OWN_SCOPES, true, null, "PROJECT", null, true);
        register("PROJECT_CREATE", "WRITE", "MEDIUM", 2, TEAM_ORG_SCOPES, true, null, "TEAM", null, true);
        register("PROJECT_UPDATE", "WRITE", "MEDIUM", 3, NON_OWN_SCOPES, true, null, "PROJECT", null, true);
        register("PROJECT_DELETE", "ADMINISTRATION", "CRITICAL", 4, NON_OWN_SCOPES, true, null, "PROJECT", null, true);
        register("PROJECT_ARCHIVE", "ADMINISTRATION", "HIGH", 5, NON_OWN_SCOPES, true, null, "PROJECT", null, true);
        register("PROJECT_RESTORE", "ADMINISTRATION", "HIGH", 6, NON_OWN_SCOPES, true, null, "PROJECT", null, true);
        register("PROJECT_SETTINGS_UPDATE", "SETTINGS", "MEDIUM", 7, NON_OWN_SCOPES, true, null, "PROJECT", null, true);
        register("PROJECT_MEMBER_ADD", "WORKFLOW", "MEDIUM", 8, NON_OWN_SCOPES, true, null, "PROJECT", null, true);
        register("PROJECT_MEMBER_REMOVE", "WORKFLOW", "MEDIUM", 9, NON_OWN_SCOPES, true, null, "PROJECT", null, true);
        register("PROJECT_MEMBER_ROLE_UPDATE", "WORKFLOW", "MEDIUM", 10, NON_OWN_SCOPES, true, null, "PROJECT", null, true);
        register("PROJECT_EXPORT", "READ", "LOW", 11, NON_OWN_SCOPES, true, null, "PROJECT", null, true);

        // TASK
        register("TASK_VIEW", "READ", "LOW", 1, ALL_SCOPES, true, null, "OWN", null, true);
        register("TASK_CREATE", "WRITE", "MEDIUM", 2, NON_OWN_SCOPES, true, List.of("TASK_VIEW"), "PROJECT", null, true);
        register("TASK_UPDATE", "WRITE", "MEDIUM", 3, ALL_SCOPES, true, List.of("TASK_VIEW"), "OWN", null, true);
        register("TASK_DELETE", "ADMINISTRATION", "HIGH", 4, NON_OWN_SCOPES, true, List.of("TASK_VIEW"), "PROJECT", null, true);
        register("TASK_ARCHIVE", "ADMINISTRATION", "HIGH", 41, NON_OWN_SCOPES, true, List.of("TASK_VIEW"), "PROJECT", null, true);
        register("TASK_RESTORE", "ADMINISTRATION", "HIGH", 42, NON_OWN_SCOPES, true, List.of("TASK_VIEW"), "PROJECT", null, true);
        register("TASK_COMPLETE", "WORKFLOW", "LOW", 43, ALL_SCOPES, true, List.of("TASK_VIEW"), "OWN", null, true);
        register("TASK_RECALL", "WORKFLOW", "LOW", 44, ALL_SCOPES, true, List.of("TASK_VIEW"), "OWN", null, true);
        register("TASK_REOPEN", "WORKFLOW", "LOW", 45, ALL_SCOPES, true, List.of("TASK_VIEW"), "OWN", null, true);
        register("TASK_CANCEL", "WORKFLOW", "LOW", 46, ALL_SCOPES, true, List.of("TASK_VIEW"), "OWN", null, true);
        register("TASK_DEPENDENCY_UPDATE", "SETTINGS", "LOW", 47, ALL_SCOPES, true, List.of("TASK_VIEW"), "OWN", null, true);
        register("TASK_COMMENT_CREATE", "WRITE", "LOW", 48, ALL_SCOPES, true, List.of("TASK_VIEW"), "OWN", null, true);
        register("TASK_REASSIGN", "WORKFLOW", "LOW", 49, NON_OWN_SCOPES, true, List.of("TASK_VIEW"), "PROJECT", null, true);
        register("TASK_ASSIGN", "WORKFLOW", "LOW", 5, NON_OWN_SCOPES, true, List.of("TASK_VIEW"), "PROJECT", "Assignors may reassign tasks within their permitted scope.", true);
        register("TASK_START", "WORKFLOW", "LOW", 6, ALL_SCOPES, true, List.of("TASK_VIEW"), "OWN", null, true);
        register("TASK_SUBMIT", "WORKFLOW", "LOW", 7, ALL_SCOPES, true, List.of("TASK_VIEW"), "OWN", "Only assigned users can submit tasks for review.", true);
        register("TASK_APPROVE", "WORKFLOW", "MEDIUM", 8, NON_OWN_SCOPES, true, List.of("TASK_VIEW"), "PROJECT", "Assignees can never approve their own tasks.", true);
        register("TASK_REJECT", "WORKFLOW", "MEDIUM", 9, NON_OWN_SCOPES, true, List.of("TASK_VIEW"), "PROJECT", "Assignees can never reject their own tasks.", true);
        register("TASK_OVERRIDE", "ADMINISTRATION", "CRITICAL", 10, ORG_ONLY, false, List.of("TASK_VIEW"), "ORGANIZATION", "Allows overriding frozen or locked tasks across organization.", false);

        // GOAL
        register("GOAL_VIEW", "READ", "LOW", 1, ALL_SCOPES, true, null, "OWN", null, true);
        register("GOAL_CREATE", "WRITE", "MEDIUM", 2, NON_OWN_SCOPES, true, List.of("GOAL_VIEW"), "PROJECT", null, true);
        register("GOAL_UPDATE", "WRITE", "MEDIUM", 3, ALL_SCOPES, true, List.of("GOAL_VIEW"), "OWN", null, true);
        register("GOAL_PROGRESS_UPDATE", "WRITE", "MEDIUM", 31, ALL_SCOPES, true, List.of("GOAL_VIEW"), "OWN", null, true);
        register("GOAL_DELETE", "ADMINISTRATION", "HIGH", 4, NON_OWN_SCOPES, true, List.of("GOAL_VIEW"), "PROJECT", null, true);
        register("GOAL_ARCHIVE", "ADMINISTRATION", "HIGH", 41, NON_OWN_SCOPES, true, List.of("GOAL_VIEW"), "PROJECT", null, true);
        register("GOAL_ASSIGN", "WORKFLOW", "LOW", 5, NON_OWN_SCOPES, true, List.of("GOAL_VIEW"), "PROJECT", null, true);
        

        // CALENDAR
        register("CALENDAR_VIEW", "READ", "LOW", 1, ALL_SCOPES, true, null, "OWN", null, true);
        register("CALENDAR_CREATE", "WRITE", "MEDIUM", 2, NON_OWN_SCOPES, true, List.of("CALENDAR_VIEW"), "PROJECT", null, true);
        register("CALENDAR_UPDATE", "WRITE", "MEDIUM", 3, ALL_SCOPES, true, List.of("CALENDAR_VIEW"), "OWN", null, true);
        register("CALENDAR_DELETE", "ADMINISTRATION", "HIGH", 4, NON_OWN_SCOPES, true, List.of("CALENDAR_VIEW"), "PROJECT", null, true);
        register("CALENDAR_EXPORT", "READ", "LOW", 5, ALL_SCOPES, true, List.of("CALENDAR_VIEW"), "OWN", null, true);

        // ACTIVITY
        register("ACTIVITY_VIEW", "READ", "LOW", 1, ALL_SCOPES, true, null, "OWN", null, true);
        register("ACTIVITY_EXPORT", "READ", "MEDIUM", 2, TEAM_ORG_SCOPES, true, List.of("ACTIVITY_VIEW"), "ORGANIZATION", null, false);

        // DASHBOARD
        register("DASHBOARD_VIEW", "READ", "LOW", 1, ALL_SCOPES, true, null, "OWN", null, true);
        register("DASHBOARD_EXPORT", "READ", "MEDIUM", 2, ALL_SCOPES, true, List.of("DASHBOARD_VIEW"), "OWN", null, true);
        register("DASHBOARD_WIDGET_UPDATE", "WRITE", "MEDIUM", 3, ALL_SCOPES, true, List.of("DASHBOARD_VIEW"), "OWN", null, true);
        

        // ANNOUNCEMENT
        register("ANNOUNCEMENT_VIEW", "READ", "LOW", 1, ORG_ONLY, false, null, "ORGANIZATION", null, false);
        register("ANNOUNCEMENT_CREATE", "WRITE", "MEDIUM", 2, ORG_ONLY, false, List.of("ANNOUNCEMENT_VIEW"), "ORGANIZATION", null, false);
        register("ANNOUNCEMENT_UPDATE", "WRITE", "MEDIUM", 3, ORG_ONLY, false, List.of("ANNOUNCEMENT_VIEW"), "ORGANIZATION", null, false);
        register("ANNOUNCEMENT_DELETE", "ADMINISTRATION", "HIGH", 4, ORG_ONLY, false, List.of("ANNOUNCEMENT_VIEW"), "ORGANIZATION", null, false);
        register("ANNOUNCEMENT_PIN", "WORKFLOW", "MEDIUM", 5, ORG_ONLY, false, List.of("ANNOUNCEMENT_VIEW"), "ORGANIZATION", null, false);

        // LEAVE
        register("LEAVE_VIEW", "READ", "LOW", 1, ALL_SCOPES, true, null, "OWN", null, true);
        register("LEAVE_CREATE", "WRITE", "LOW", 2, ALL_SCOPES, true, List.of("LEAVE_VIEW"), "OWN", null, true);
        register("LEAVE_UPDATE", "WRITE", "LOW", 21, ALL_SCOPES, true, List.of("LEAVE_VIEW"), "OWN", null, true);
        register("LEAVE_APPROVE", "WORKFLOW", "MEDIUM", 3, TEAM_ORG_SCOPES, true, List.of("LEAVE_VIEW"), "TEAM", "Managers cannot approve their own leave requests.", true);
        register("LEAVE_REJECT", "WORKFLOW", "MEDIUM", 4, TEAM_ORG_SCOPES, true, List.of("LEAVE_VIEW"), "TEAM", null, true);
        register("LEAVE_DELETE", "ADMINISTRATION", "HIGH", 5, TEAM_ORG_SCOPES, true, List.of("LEAVE_VIEW"), "ORGANIZATION", null, true);
        register("LEAVE_SETTINGS_UPDATE", "SETTINGS", "HIGH", 6, ORG_ONLY, false, null, "ORGANIZATION", null, false);
    }

    private static void register(String code, String group, String riskLevel, int order, List<String> supportedScopes, boolean scopeRequired, List<String> requires, String recommendedScope, String actionRules, boolean requiresResourceAssignment) {
        metadataMap.put(code, new Metadata(group, riskLevel, order, supportedScopes, scopeRequired, requires, recommendedScope, actionRules, requiresResourceAssignment));
    }

    public static String getGroup(String code) {
        Metadata meta = metadataMap.get(code);
        if (meta != null) return meta.group;
        if (code.contains("VIEW") || code.contains("READ") || code.contains("EXPORT")) return "READ";
        if (code.contains("CREATE") || code.contains("EDIT") || code.contains("UPDATE") || code.contains("DELETE")) return "WRITE";
        if (code.contains("ASSIGN") || code.contains("APPROVE") || code.contains("REJECT") || code.contains("START") || code.contains("SUBMIT") || code.contains("REQUEST")) return "WORKFLOW";
        return "GENERAL";
    }

    public static String getRiskLevel(String code) {
        Metadata meta = metadataMap.get(code);
        if (meta != null) return meta.riskLevel;
        if (code.contains("DELETE") || code.contains("OVERRIDE")) return "CRITICAL";
        return "LOW";
    }

    public static int getPermissionOrder(String code) {
        Metadata meta = metadataMap.get(code);
        return meta != null ? meta.order : 999;
    }

    public static int getModuleOrder(String module) {
        return moduleOrderMap.getOrDefault(module, 999);
    }

    public static List<String> getSupportedScopes(String code) {
        Metadata meta = metadataMap.get(code);
        return meta != null ? meta.supportedScopes : ORG_ONLY;
    }

    public static boolean isScopeRequired(String code) {
        Metadata meta = metadataMap.get(code);
        return meta != null ? meta.scopeRequired : false;
    }

    public static List<String> getRequires(String code) {
        Metadata meta = metadataMap.get(code);
        return meta != null ? meta.requires : Collections.emptyList();
    }

    public static String getRecommendedScope(String code) {
        Metadata meta = metadataMap.get(code);
        return meta != null ? meta.recommendedScope : "ORGANIZATION";
    }

    public static boolean requiresResourceAssignment(String code) {
        Metadata meta = metadataMap.get(code);
        return meta != null ? meta.requiresResourceAssignment : false;
    }

    public static String getActionRules(String code) {
        Metadata meta = metadataMap.get(code);
        return meta != null ? meta.actionRules : null;
    }
}
