package com.example.taskflow.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.example.taskflow.goal.domain.Goal;
import com.example.taskflow.organization.announcement.domain.Announcement;
import com.example.taskflow.organization.core.domain.Organization;
import com.example.taskflow.organization.rbac.domain.Permission;
import com.example.taskflow.organization.rbac.domain.Role;
import com.example.taskflow.organization.rbac.domain.Scope;
import com.example.taskflow.project.domain.Project;
import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.team.domain.Team;

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

        public Metadata(String group, String riskLevel, int order, List<String> supportedScopes, boolean scopeRequired, List<String> requires, String recommendedScope, String actionRules) {
            this.group = group;
            this.riskLevel = riskLevel;
            this.order = order;
            this.supportedScopes = supportedScopes != null ? supportedScopes : List.of("ORGANIZATION");
            this.scopeRequired = scopeRequired;
            this.requires = requires != null ? requires : List.of();
            this.recommendedScope = recommendedScope != null ? recommendedScope : "ORGANIZATION";
            this.actionRules = actionRules;
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
        register("ORG_VIEW", "READ", "LOW", 1, ORG_ONLY, false, null, "ORGANIZATION", null);
        register("ORG_EDIT", "WRITE", "MEDIUM", 2, ORG_ONLY, false, null, "ORGANIZATION", null);
        register("ORG_SETTINGS_MANAGE", "ADMINISTRATION", "HIGH", 3, ORG_ONLY, false, null, "ORGANIZATION", null);
        register("ORG_BILLING_MANAGE", "ADMINISTRATION", "CRITICAL", 4, ORG_ONLY, false, null, "ORGANIZATION", null);
        register("ORG_DELETE", "ADMINISTRATION", "CRITICAL", 5, ORG_ONLY, false, null, "ORGANIZATION", null);

        // MEMBER
        register("MEMBER_VIEW", "READ", "LOW", 1, TEAM_ORG_SCOPES, true, null, "ORGANIZATION", null);
        register("MEMBER_INVITE", "WRITE", "MEDIUM", 2, TEAM_ORG_SCOPES, true, null, "ORGANIZATION", null);
        register("MEMBER_EDIT", "WRITE", "MEDIUM", 3, TEAM_ORG_SCOPES, true, null, "ORGANIZATION", null);
        register("MEMBER_REMOVE", "ADMINISTRATION", "HIGH", 4, TEAM_ORG_SCOPES, true, null, "ORGANIZATION", null);
        register("MEMBER_ROLE_ASSIGN", "ADMINISTRATION", "HIGH", 5, ORG_ONLY, false, null, "ORGANIZATION", null);

        // ROLE
        register("ROLE_VIEW", "READ", "LOW", 1, ORG_ONLY, false, null, "ORGANIZATION", null);
        register("ROLE_CREATE", "WRITE", "MEDIUM", 2, ORG_ONLY, false, null, "ORGANIZATION", null);
        register("ROLE_EDIT", "WRITE", "HIGH", 3, ORG_ONLY, false, null, "ORGANIZATION", null);
        register("ROLE_DELETE", "ADMINISTRATION", "CRITICAL", 4, ORG_ONLY, false, null, "ORGANIZATION", null);
        register("ROLE_ASSIGN", "ADMINISTRATION", "HIGH", 5, ORG_ONLY, false, null, "ORGANIZATION", null);

        // TEAM
        register("TEAM_VIEW", "READ", "LOW", 1, TEAM_ORG_SCOPES, true, null, "TEAM", null);
        register("TEAM_CREATE", "WRITE", "MEDIUM", 2, ORG_ONLY, false, null, "ORGANIZATION", null);
        register("TEAM_EDIT", "WRITE", "MEDIUM", 3, TEAM_ORG_SCOPES, true, null, "TEAM", null);
        register("TEAM_DELETE", "ADMINISTRATION", "HIGH", 4, TEAM_ORG_SCOPES, true, null, "ORGANIZATION", null);
        register("TEAM_MEMBER_MANAGE", "WORKFLOW", "MEDIUM", 5, TEAM_ORG_SCOPES, true, null, "TEAM", null);

        // PROJECT
        register("PROJECT_VIEW", "READ", "LOW", 1, NON_OWN_SCOPES, true, null, "PROJECT", null);
        register("PROJECT_CREATE", "WRITE", "MEDIUM", 2, TEAM_ORG_SCOPES, true, null, "ORGANIZATION", null);
        register("PROJECT_EDIT", "WRITE", "MEDIUM", 3, NON_OWN_SCOPES, true, null, "PROJECT", null);
        register("PROJECT_DELETE", "ADMINISTRATION", "CRITICAL", 4, NON_OWN_SCOPES, true, null, "PROJECT", null);
        register("PROJECT_MEMBER_MANAGE", "WORKFLOW", "MEDIUM", 5, NON_OWN_SCOPES, true, null, "PROJECT", null);

        // TASK
        register("TASK_VIEW", "READ", "LOW", 1, ALL_SCOPES, true, null, "OWN", null);
        register("TASK_CREATE", "WRITE", "MEDIUM", 2, NON_OWN_SCOPES, true, List.of("TASK_VIEW"), "PROJECT", null);
        register("TASK_EDIT", "WRITE", "MEDIUM", 3, ALL_SCOPES, true, List.of("TASK_VIEW"), "OWN", null);
        register("TASK_DELETE", "ADMINISTRATION", "HIGH", 4, NON_OWN_SCOPES, true, List.of("TASK_VIEW"), "PROJECT", null);
        register("TASK_ASSIGN", "WORKFLOW", "LOW", 5, NON_OWN_SCOPES, true, List.of("TASK_VIEW"), "PROJECT", "Assignors may reassign tasks within their permitted scope.");
        register("TASK_START", "WORKFLOW", "LOW", 6, ALL_SCOPES, true, List.of("TASK_VIEW"), "OWN", null);
        register("TASK_SUBMIT", "WORKFLOW", "LOW", 7, ALL_SCOPES, true, List.of("TASK_VIEW"), "OWN", "Only assigned users can submit tasks for review.");
        register("TASK_APPROVE", "WORKFLOW", "MEDIUM", 8, NON_OWN_SCOPES, true, List.of("TASK_VIEW"), "PROJECT", "Assignees can never approve their own tasks.");
        register("TASK_REJECT", "WORKFLOW", "MEDIUM", 9, NON_OWN_SCOPES, true, List.of("TASK_VIEW"), "PROJECT", "Assignees can never reject their own tasks.");
        register("TASK_OVERRIDE", "ADMINISTRATION", "CRITICAL", 10, ORG_ONLY, false, List.of("TASK_VIEW"), "ORGANIZATION", "Allows overriding frozen or locked tasks across organization.");

        // GOAL
        register("GOAL_VIEW", "READ", "LOW", 1, ALL_SCOPES, true, null, "OWN", null);
        register("GOAL_CREATE", "WRITE", "MEDIUM", 2, NON_OWN_SCOPES, true, List.of("GOAL_VIEW"), "PROJECT", null);
        register("GOAL_EDIT", "WRITE", "MEDIUM", 3, ALL_SCOPES, true, List.of("GOAL_VIEW"), "OWN", null);
        register("GOAL_DELETE", "ADMINISTRATION", "HIGH", 4, NON_OWN_SCOPES, true, List.of("GOAL_VIEW"), "PROJECT", null);
        register("GOAL_ASSIGN", "WORKFLOW", "LOW", 5, NON_OWN_SCOPES, true, List.of("GOAL_VIEW"), "PROJECT", null);
        register("GOAL_APPROVE", "WORKFLOW", "MEDIUM", 6, NON_OWN_SCOPES, true, List.of("GOAL_VIEW"), "PROJECT", "Creators cannot self-approve milestone goals.");

        // CALENDAR
        register("CALENDAR_VIEW", "READ", "LOW", 1, ALL_SCOPES, true, null, "OWN", null);
        register("CALENDAR_CREATE", "WRITE", "MEDIUM", 2, NON_OWN_SCOPES, true, List.of("CALENDAR_VIEW"), "PROJECT", null);
        register("CALENDAR_EDIT", "WRITE", "MEDIUM", 3, ALL_SCOPES, true, List.of("CALENDAR_VIEW"), "OWN", null);
        register("CALENDAR_DELETE", "ADMINISTRATION", "HIGH", 4, NON_OWN_SCOPES, true, List.of("CALENDAR_VIEW"), "PROJECT", null);

        // ACTIVITY
        register("ACTIVITY_VIEW", "READ", "LOW", 1, ALL_SCOPES, true, null, "OWN", null);
        register("ACTIVITY_EXPORT", "READ", "MEDIUM", 2, TEAM_ORG_SCOPES, true, List.of("ACTIVITY_VIEW"), "ORGANIZATION", null);

        // DASHBOARD
        register("DASHBOARD_VIEW", "READ", "LOW", 1, ALL_SCOPES, true, null, "OWN", null);
        register("DASHBOARD_CREATE", "WRITE", "MEDIUM", 2, ALL_SCOPES, true, List.of("DASHBOARD_VIEW"), "OWN", null);
        register("DASHBOARD_EDIT", "WRITE", "MEDIUM", 3, ALL_SCOPES, true, List.of("DASHBOARD_VIEW"), "OWN", null);
        register("DASHBOARD_DELETE", "ADMINISTRATION", "HIGH", 4, ALL_SCOPES, true, List.of("DASHBOARD_VIEW"), "OWN", null);

        // ANNOUNCEMENT
        register("ANNOUNCEMENT_VIEW", "READ", "LOW", 1, ORG_ONLY, false, null, "ORGANIZATION", null);
        register("ANNOUNCEMENT_CREATE", "WRITE", "MEDIUM", 2, ORG_ONLY, false, List.of("ANNOUNCEMENT_VIEW"), "ORGANIZATION", null);
        register("ANNOUNCEMENT_EDIT", "WRITE", "MEDIUM", 3, ORG_ONLY, false, List.of("ANNOUNCEMENT_VIEW"), "ORGANIZATION", null);
        register("ANNOUNCEMENT_DELETE", "ADMINISTRATION", "HIGH", 4, ORG_ONLY, false, List.of("ANNOUNCEMENT_VIEW"), "ORGANIZATION", null);

        // LEAVE
        register("LEAVE_VIEW", "READ", "LOW", 1, ALL_SCOPES, true, null, "OWN", null);
        register("LEAVE_REQUEST", "WRITE", "LOW", 2, ALL_SCOPES, true, List.of("LEAVE_VIEW"), "OWN", null);
        register("LEAVE_APPROVE", "WORKFLOW", "MEDIUM", 3, TEAM_ORG_SCOPES, true, List.of("LEAVE_VIEW"), "TEAM", "Managers cannot approve their own leave requests.");
        register("LEAVE_REJECT", "WORKFLOW", "MEDIUM", 4, TEAM_ORG_SCOPES, true, List.of("LEAVE_VIEW"), "TEAM", null);
        register("LEAVE_DELETE", "ADMINISTRATION", "HIGH", 5, TEAM_ORG_SCOPES, true, List.of("LEAVE_VIEW"), "ORGANIZATION", null);
    }

    private static void register(String code, String group, String riskLevel, int order, List<String> supportedScopes, boolean scopeRequired, List<String> requires, String recommendedScope, String actionRules) {
        metadataMap.put(code, new Metadata(group, riskLevel, order, supportedScopes, scopeRequired, requires, recommendedScope, actionRules));
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

    public static String getActionRules(String code) {
        Metadata meta = metadataMap.get(code);
        return meta != null ? meta.actionRules : null;
    }
}
