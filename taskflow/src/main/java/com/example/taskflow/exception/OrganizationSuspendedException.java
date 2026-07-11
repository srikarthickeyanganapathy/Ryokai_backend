package com.example.taskflow.exception;

/**
 * Thrown when an operation is attempted on a resource belonging to
 * a suspended or deleted organization.
 */
public class OrganizationSuspendedException extends RuntimeException {
    public OrganizationSuspendedException(String message) {
        super(message);
    }
}
