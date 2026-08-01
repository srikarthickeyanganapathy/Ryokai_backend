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
        DENY,
        ABSTAIN
    }

    public boolean isGranted() {
        return decision == Decision.GRANT;
    }

    public boolean isDenied() {
        return decision == Decision.DENY;
    }

    public boolean isAbstain() {
        return decision == Decision.ABSTAIN;
    }

    // â”€â”€ Factory methods â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static AuthorizationDecision allow(String stage, String reason) {
        return new AuthorizationDecision(Decision.GRANT, stage, reason);
    }

    public static AuthorizationDecision abstain(String reason) {
        return new AuthorizationDecision(Decision.ABSTAIN, "ABSTAIN", reason);
    }

    public static AuthorizationDecision grant(String stage) {
        return new AuthorizationDecision(Decision.GRANT, stage, null);
    }


    public static AuthorizationDecision deny(String stage, String reason) {
        return new AuthorizationDecision(Decision.DENY, stage, reason);
    }

    public static AuthorizationDecision denyPolicy(String policyKey) {
        return deny("POLICY", "Policy predicate failed: " + policyKey);
    }

    public static AuthorizationDecision denyField(String fieldName) {
        return deny("FIELD", "Field restricted: " + fieldName);
    }
}
