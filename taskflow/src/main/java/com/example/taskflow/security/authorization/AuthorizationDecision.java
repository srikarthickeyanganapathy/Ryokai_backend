package com.example.taskflow.security.authorization;

/**
 * The result of an authorization evaluation through the pipeline.
 *
 * <p>Immutable value object that captures the decision (GRANT/DENY),
 * the pipeline stage that produced the decision, and a human-readable reason.
 */
public record AuthorizationDecision(
    Decision decision,
    String stage,
    String reason
) {

    public enum Decision {
        GRANT,
        DENY
    }

    public boolean isGranted() {
        return decision == Decision.GRANT;
    }

    public boolean isDenied() {
        return decision == Decision.DENY;
    }

    // â”€â”€ Factory methods â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static AuthorizationDecision grant(String stage) {
        return new AuthorizationDecision(Decision.GRANT, stage, null);
    }

    public static AuthorizationDecision deny(String stage, String reason) {
        return new AuthorizationDecision(Decision.DENY, stage, reason);
    }

    public static AuthorizationDecision denyPermission(String permissionCode) {
        return deny("PERMISSION", "Missing permission: " + permissionCode);
    }

    public static AuthorizationDecision denyScope(String permissionCode, String requiredScope) {
        return deny("SCOPE", "Permission " + permissionCode + " not granted at required scope: " + requiredScope);
    }

    public static AuthorizationDecision denyPolicy(String policyKey) {
        return deny("POLICY", "Policy predicate failed: " + policyKey);
    }

    public static AuthorizationDecision denyField(String fieldName) {
        return deny("FIELD", "Field restricted: " + fieldName);
    }

    public static AuthorizationDecision denyOverride() {
        return deny("OVERRIDE", "Explicit DENY override exists for this user");
    }

    public static AuthorizationDecision denyOrgInactive() {
        return deny("ORG_STATUS", "Organization is not active");
    }

    public static AuthorizationDecision denyNotMember() {
        return deny("MEMBERSHIP", "User is not a member of this organization");
    }


    public static AuthorizationDecision grantPersonalOwner() {
        return grant("PERSONAL_OWNER");
    }

    public static AuthorizationDecision grantCrewRole() {
        return grant("CREW_ROLE");
    }

    public static AuthorizationDecision grantOverride() {
        return grant("OVERRIDE");
    }
}