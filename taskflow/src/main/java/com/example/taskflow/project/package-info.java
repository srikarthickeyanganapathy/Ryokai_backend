/**
 * Project management module (Tier 2 — medium complexity).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Project creation, configuration, and lifecycle</li>
 *   <li>Project scope management (personal, org, crew, team)</li>
 *   <li>Project activity logging</li>
 *   <li>Collaborator role assignment</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@code application.ProjectService} — project CRUD and queries</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code user}, {@code organization.core}, {@code crew}, {@code team}</li>
 *   <li>Referenced by: {@code task} (tasks belong to projects)</li>
 *   <li>Cross-module access via {@code ProjectService}, not repositories</li>
 * </ul>
 */
package com.example.taskflow.project;