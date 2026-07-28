/**
 * Whiteboard collaboration module (Tier 3 — simple, flat structure).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>CRUD operations for collaborative whiteboards</li>
 *   <li>Real-time whiteboard state via WebSocket</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@code WhiteboardService} — whiteboard operations</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code user} (ownership), {@code organization.core} (org scope)</li>
 *   <li>Must NOT be depended on by other modules</li>
 * </ul>
 */
package com.example.taskflow.whiteboard;