/**
 * Audit and compliance tracking module.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Recording audit events for user and system actions</li>
 *   <li>Security audit trail for authentication and authorization events</li>
 *   <li>Querying and reporting on audit history</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@code application.AuditService} — primary entry point for recording audit events</li>
 *   <li>{@code application.SecurityAuditService} — security-specific audit operations</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code user} (for actor identity)</li>
 *   <li>Must NOT depend on: any other feature module</li>
 *   <li>Other modules call {@code AuditService} to record events — never access repositories directly</li>
 * </ul>
 */
package com.example.taskflow.audit;