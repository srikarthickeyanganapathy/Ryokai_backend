/**
 * Task management module (Tier 1 — richest domain, 82 files).
 *
 * <p>The most complex module in the application. Uses full hexagonal architecture
 * with clear separation between API, application, domain, and infrastructure layers.</p>
 *
 * <h2>Layer Structure</h2>
 * <ul>
 *   <li>{@code api} — REST controllers, request/response DTOs</li>
 *   <li>{@code application.command} — Write operations (lifecycle, assignment, comments, dependencies)</li>
 *   <li>{@code application.query} — Read operations (task queries, workload)</li>
 *   <li>{@code application.orchestration} — Cross-concern orchestration (audit, notifications)</li>
 *   <li>{@code application.strategy} — Strategy factory (orchestration, not infrastructure)</li>
 *   <li>{@code domain.model} — Task aggregate, value objects, enums</li>
 *   <li>{@code domain.strategy} — Pure Java strategy interfaces and implementations</li>
 *   <li>{@code domain.validation} — Domain validation rules</li>
 *   <li>{@code event} — Domain event definitions and listeners</li>
 *   <li>{@code infrastructure.persistence} — JPA repositories</li>
 *   <li>{@code infrastructure.monitoring} — Metrics collection</li>
 *   <li>{@code mapper} — Response mapping</li>
 *   <li>{@code security} — Task-specific permission handler</li>
 *   <li>{@code exception} — Task-specific exceptions</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@code application.command.TaskLifecycleService} — create, update, delete tasks</li>
 *   <li>{@code application.command.TaskAssignmentService} — assign, reassign, claim</li>
 *   <li>{@code application.query.TaskQueryService} — task queries and search</li>
 *   <li>{@code application.query.WorkloadService} — workload analysis</li>
 * </ul>
 *
 * <h2>Design Decisions</h2>
 * <ul>
 *   <li>{@code TaskStrategyFactory} is in {@code application.strategy} — it's orchestration, not infrastructure</li>
 *   <li>Strategy implementations are pure Java (no Spring annotations)</li>
 *   <li>Domain validation ({@code TaskHierarchyValidator}) is in domain layer, not application</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code user}, {@code organization}, {@code project}, {@code crew}, {@code team}, {@code notification}, {@code shared}</li>
 *   <li>Referenced by: {@code dashboard} (for statistics), {@code saveditem} (for bookmarks)</li>
 *   <li>{@code domain} package must NOT import Spring — pure Java only</li>
 *   <li>{@code infrastructure} must NOT be imported by other modules</li>
 * </ul>
 */
package com.example.taskflow.task;