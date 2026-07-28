/**
 * Team management module (Tier 2 — medium complexity).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Team creation within organizations</li>
 *   <li>Team membership management</li>
 *   <li>Team messaging and observer pattern</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@code application.TeamService} — team CRUD and membership</li>
 *   <li>{@code application.TeamMessageService} — team messaging</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code user}, {@code organization.core}, {@code organization.membership}</li>
 *   <li>Referenced by: {@code project}, {@code task} (team-scoped work)</li>
 *   <li>Cross-module access via {@code TeamService}, not repositories</li>
 * </ul>
 */
package com.example.taskflow.team;