package com.example.taskflow.organization.core.exception;
import com.example.taskflow.organization.core.domain.Organization;

/**
 * Thrown when an operation is attempted on a resource belonging to
 * a suspended or deleted organization.
 */
public class OrganizationSuspendedException extends RuntimeException {
    public OrganizationSuspendedException(String message) {
        super(message);
    }
}