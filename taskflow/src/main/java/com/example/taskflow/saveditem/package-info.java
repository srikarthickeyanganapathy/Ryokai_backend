/**
 * Saved/bookmarked items module (Tier 3 — simple, flat structure).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Bookmarking tasks, projects, notes, and other entities</li>
 *   <li>Querying saved items by user and entity type</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@code SavedItemService} — save, unsave, and query bookmarks</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code user}, and multiple modules for entity resolution</li>
 *   <li>Must NOT be depended on by other modules</li>
 * </ul>
 */
package com.example.taskflow.saveditem;