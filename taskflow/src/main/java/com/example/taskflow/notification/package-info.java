/**
 * Notification module (Tier 2 — medium complexity).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Creating and dispatching in-app notifications</li>
 *   <li>Email notification rendering via strategy-based renderers</li>
 *   <li>Notification read/unread state management</li>
 *   <li>Domain event listeners for notification triggers</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@code application.NotificationService} — notification CRUD and dispatch</li>
 *   <li>{@code event.NotificationEvent} — event type for cross-module notification triggers</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code user}, {@code integration.email}</li>
 *   <li>Other modules publish {@code NotificationEvent} — never call notification services directly</li>
 * </ul>
 */
package com.example.taskflow.notification;