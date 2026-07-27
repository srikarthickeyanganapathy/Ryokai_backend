package com.example.taskflow.security.authorization;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.taskflow.domain.FieldRestriction;
import com.example.taskflow.repository.FieldRestrictionRepository;

/**
 * Evaluates field-level access control for _UPDATE operations.
 *
 * <p>Logic:
 * <ol>
 *   <li>Load field restrictions for the user's role(s) and the target resource type</li>
 *   <li>For each field being modified:
 *       <ul>
 *         <li>If the field has an explicit DENY → deny the entire request</li>
 *         <li>If the field has READ_ONLY → deny the entire request</li>
 *         <li>If the field has ALLOW → permit</li>
 *         <li>If no restriction exists → permit (default open)</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>SYSTEM-tier fields (id, created_at, updated_at, created_by) are always denied.
 */
@Service
public class FieldRestrictionEvaluator {

    private static final Set<String> SYSTEM_FIELDS = Set.of(
            "id", "created_at", "createdAt", "updated_at", "updatedAt",
            "created_by", "createdBy", "version"
    );

    private final FieldRestrictionRepository fieldRestrictionRepository;

    public FieldRestrictionEvaluator(FieldRestrictionRepository fieldRestrictionRepository) {
        this.fieldRestrictionRepository = fieldRestrictionRepository;
    }

    /**
     * Evaluates field restrictions for the given roles, resource type, and modified fields.
     *
     * @param roleIds      the user's role IDs (all org + team + project roles)
     * @param resourceType the resource type being updated (e.g., "TASK", "PROJECT")
     * @param modifiedFields the set of field names being modified in the request
     * @return GRANT if all fields are allowed, DENY with the first restricted field name
     */
    public AuthorizationDecision evaluate(List<Long> roleIds, String resourceType, Set<String> modifiedFields) {
        if (modifiedFields == null || modifiedFields.isEmpty()) {
            return AuthorizationDecision.grant("FIELD");
        }

        // System fields are always denied
        for (String field : modifiedFields) {
            if (SYSTEM_FIELDS.contains(field)) {
                return AuthorizationDecision.denyField(field + " (system field)");
            }
        }

        if (resourceType == null) {
            // No resource type = no field restrictions to apply
            return AuthorizationDecision.grant("FIELD");
        }

        // Load restrictions for all the user's roles
        List<FieldRestriction> restrictions = fieldRestrictionRepository
                .findByRoleIdInAndResourceType(roleIds, resourceType);

        if (restrictions.isEmpty()) {
            // No restrictions configured → default open
            return AuthorizationDecision.grant("FIELD");
        }

        // Check each modified field against restrictions
        // Strategy: the MOST PERMISSIVE restriction across all roles wins
        // (union of allowed fields across roles)
        for (String field : modifiedFields) {
            boolean fieldAllowed = false;
            boolean fieldRestricted = false;

            for (FieldRestriction restriction : restrictions) {
                if (restriction.getFieldName().equals(field)) {
                    String level = restriction.getAccessLevel();
                    if ("ALLOW".equals(level)) {
                        fieldAllowed = true;
                        break; // Any ALLOW from any role permits
                    } else if ("DENY".equals(level) || "READ_ONLY".equals(level)) {
                        fieldRestricted = true;
                    }
                }
            }

            // If we found an explicit restriction and no ALLOW from any role → deny
            if (fieldRestricted && !fieldAllowed) {
                return AuthorizationDecision.denyField(field);
            }

            // If no restriction found for this field at all → default open (allow)
        }

        return AuthorizationDecision.grant("FIELD");
    }
}
