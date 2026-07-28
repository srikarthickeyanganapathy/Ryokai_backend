/**
 * Dashboard orchestration module (Tier 2 — read-only aggregation).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Aggregating statistics from multiple modules (tasks, projects, goals)</li>
 *   <li>Strategy-based dashboard rendering (personal, org, crew)</li>
 *   <li>Activity event streams</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@code application.DashboardQueryService} — orchestrates dashboard data retrieval</li>
 * </ul>
 *
 * <h2>Design Decisions</h2>
 * <ul>
 *   <li>This module does NOT own business logic — it queries other modules</li>
 *   <li>Uses Strategy pattern ({@code DashboardStatsStrategy}) for scope-specific rendering</li>
 *   <li>Named "QueryService" to emphasize its read-only orchestration role</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code task}, {@code project}, {@code crew}, {@code organization}, {@code user}</li>
 *   <li>No module should depend on dashboard — it is a leaf consumer</li>
 * </ul>
 */
package com.example.taskflow.dashboard;