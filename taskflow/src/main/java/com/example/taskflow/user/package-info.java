/**
 * User management module (Tier 2 — medium complexity).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>User profile management (display name, avatar, preferences)</li>
 *   <li>User queries and lookups</li>
 *   <li>User settings (notification preferences, email settings)</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@code application.UserService} — user queries and lookups</li>
 *   <li>{@code application.UserProfileService} — profile updates</li>
 *   <li>{@code domain.User} — shared entity referenced by all modules</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code organization.membership} (membership relationship), {@code organization.rbac} (roles)</li>
 *   <li>Widely depended on: almost every module references {@code User}</li>
 *   <li>{@code User} entity is the most cross-referenced type in the system</li>
 * </ul>
 */
package com.example.taskflow.user;