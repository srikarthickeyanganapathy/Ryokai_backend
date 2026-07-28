/**
 * Calendar events module (Tier 3 — simple, flat structure).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>CRUD operations for calendar events</li>
 *   <li>User-scoped and organization-scoped calendar management</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@code CalendarEventService} — all calendar operations</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code user} (event ownership)</li>
 *   <li>Must NOT be depended on by other modules</li>
 * </ul>
 */
package com.example.taskflow.calendar;