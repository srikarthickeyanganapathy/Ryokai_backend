/**
 * Focus sessions module (Tier 3 — simple, flat structure).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Creating and managing timed focus/deep-work sessions</li>
 *   <li>Tracking focus session history and statistics</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@code FocusSessionService} — all focus session operations</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code user}, {@code task} (optional task association)</li>
 *   <li>Must NOT be depended on by other modules</li>
 * </ul>
 */
package com.example.taskflow.focus;