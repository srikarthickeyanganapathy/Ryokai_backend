/**
 * Crew management module (Tier 2 — medium complexity).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Crew creation, configuration, and lifecycle</li>
 *   <li>Crew membership: invitations, joins, leaves, role assignment</li>
 *   <li>Crew channels and messaging</li>
 *   <li>Crew-scoped project delegation</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@code application.CrewService} — crew lifecycle and queries</li>
 *   <li>{@code application.CrewMembershipService} — member management</li>
 *   <li>{@code application.CrewChannelService} — channel and messaging</li>
 *   <li>{@code application.CrewProjectService} — project delegation</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code user}, {@code organization.rbac}, {@code project}, {@code task}</li>
 *   <li>Other modules reference {@code crew.domain.Crew} as a relationship target</li>
 *   <li>Cross-module access must go through application services, not repositories</li>
 * </ul>
 */
package com.example.taskflow.crew;