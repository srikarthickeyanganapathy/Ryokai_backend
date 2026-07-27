package com.example.taskflow.util;

import java.util.HashMap;
import java.util.Map;

public class PermissionMetadataRegistry {

    public static class Metadata {
        public final String group;
        public final String riskLevel;
        public final int order;

        public Metadata(String group, String riskLevel, int order) {
            this.group = group;
            this.riskLevel = riskLevel;
            this.order = order;
        }
    }

    private static final Map<String, Metadata> metadataMap = new HashMap<>();
    private static final Map<String, Integer> moduleOrderMap = new HashMap<>();

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
        register("ORG_VIEW", "READ", "LOW", 1);
        register("ORG_EDIT", "WRITE", "MEDIUM", 2);
        register("ORG_SETTINGS_MANAGE", "ADMINISTRATION", "HIGH", 3);
        register("ORG_BILLING_MANAGE", "ADMINISTRATION", "CRITICAL", 4);
        register("ORG_DELETE", "ADMINISTRATION", "CRITICAL", 5);

        // MEMBER
        register("MEMBER_VIEW", "READ", "LOW", 1);
        register("MEMBER_INVITE", "WRITE", "MEDIUM", 2);
        register("MEMBER_EDIT", "WRITE", "MEDIUM", 3);
        register("MEMBER_REMOVE", "ADMINISTRATION", "HIGH", 4);
        register("MEMBER_ROLE_ASSIGN", "ADMINISTRATION", "HIGH", 5);

        // ROLE
        register("ROLE_VIEW", "READ", "LOW", 1);
        register("ROLE_CREATE", "WRITE", "MEDIUM", 2);
        register("ROLE_EDIT", "WRITE", "HIGH", 3);
        register("ROLE_DELETE", "ADMINISTRATION", "CRITICAL", 4);
        register("ROLE_ASSIGN", "ADMINISTRATION", "HIGH", 5);

        // TEAM
        register("TEAM_VIEW", "READ", "LOW", 1);
        register("TEAM_CREATE", "WRITE", "MEDIUM", 2);
        register("TEAM_EDIT", "WRITE", "MEDIUM", 3);
        register("TEAM_DELETE", "ADMINISTRATION", "HIGH", 4);
        register("TEAM_MEMBER_MANAGE", "WORKFLOW", "MEDIUM", 5);

        // PROJECT
        register("PROJECT_VIEW", "READ", "LOW", 1);
        register("PROJECT_CREATE", "WRITE", "MEDIUM", 2);
        register("PROJECT_EDIT", "WRITE", "MEDIUM", 3);
        register("PROJECT_DELETE", "ADMINISTRATION", "CRITICAL", 4);
        register("PROJECT_MEMBER_MANAGE", "WORKFLOW", "MEDIUM", 5);

        // TASK
        register("TASK_VIEW", "READ", "LOW", 1);
        register("TASK_CREATE", "WRITE", "MEDIUM", 2);
        register("TASK_EDIT", "WRITE", "MEDIUM", 3);
        register("TASK_DELETE", "ADMINISTRATION", "HIGH", 4);
        register("TASK_ASSIGN", "WORKFLOW", "LOW", 5);
        register("TASK_START", "WORKFLOW", "LOW", 6);
        register("TASK_SUBMIT", "WORKFLOW", "LOW", 7);
        register("TASK_APPROVE", "WORKFLOW", "MEDIUM", 8);
        register("TASK_REJECT", "WORKFLOW", "MEDIUM", 9);
        register("TASK_OVERRIDE", "ADMINISTRATION", "CRITICAL", 10);

        // GOAL
        register("GOAL_VIEW", "READ", "LOW", 1);
        register("GOAL_CREATE", "WRITE", "MEDIUM", 2);
        register("GOAL_EDIT", "WRITE", "MEDIUM", 3);
        register("GOAL_DELETE", "ADMINISTRATION", "HIGH", 4);
        register("GOAL_ASSIGN", "WORKFLOW", "LOW", 5);
        register("GOAL_APPROVE", "WORKFLOW", "MEDIUM", 6);

        // CALENDAR
        register("CALENDAR_VIEW", "READ", "LOW", 1);
        register("CALENDAR_CREATE", "WRITE", "MEDIUM", 2);
        register("CALENDAR_EDIT", "WRITE", "MEDIUM", 3);
        register("CALENDAR_DELETE", "ADMINISTRATION", "HIGH", 4);

        // ACTIVITY
        register("ACTIVITY_VIEW", "READ", "LOW", 1);
        register("ACTIVITY_EXPORT", "READ", "MEDIUM", 2);

        // DASHBOARD
        register("DASHBOARD_VIEW", "READ", "LOW", 1);
        register("DASHBOARD_CREATE", "WRITE", "MEDIUM", 2);
        register("DASHBOARD_EDIT", "WRITE", "MEDIUM", 3);
        register("DASHBOARD_DELETE", "ADMINISTRATION", "HIGH", 4);

        // ANNOUNCEMENT
        register("ANNOUNCEMENT_VIEW", "READ", "LOW", 1);
        register("ANNOUNCEMENT_CREATE", "WRITE", "MEDIUM", 2);
        register("ANNOUNCEMENT_EDIT", "WRITE", "MEDIUM", 3);
        register("ANNOUNCEMENT_DELETE", "ADMINISTRATION", "HIGH", 4);

        // LEAVE
        register("LEAVE_VIEW", "READ", "LOW", 1);
        register("LEAVE_REQUEST", "WRITE", "LOW", 2);
        register("LEAVE_APPROVE", "WORKFLOW", "MEDIUM", 3);
        register("LEAVE_REJECT", "WORKFLOW", "MEDIUM", 4);
        register("LEAVE_DELETE", "ADMINISTRATION", "HIGH", 5);
    }

    private static void register(String code, String group, String riskLevel, int order) {
        metadataMap.put(code, new Metadata(group, riskLevel, order));
    }

    public static String getGroup(String code) {
        Metadata meta = metadataMap.get(code);
        if (meta != null) return meta.group;
        // Fallback logic
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
}
