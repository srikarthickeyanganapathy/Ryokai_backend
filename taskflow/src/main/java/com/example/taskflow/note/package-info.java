/**
 * Notes module (Tier 3 — simple, flat structure).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>CRUD operations for user notes</li>
 *   <li>Note search and filtering</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@code NoteService} — all note operations</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code user} (note ownership)</li>
 *   <li>Referenced by: {@code task} (evidence can link to notes)</li>
 * </ul>
 */
package com.example.taskflow.note;