/**
 * Goals and OKR (Objectives & Key Results) module (Tier 2).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Goal creation, tracking, and completion</li>
 *   <li>Key result management and progress tracking</li>
 *   <li>Organization-scoped goal alignment</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@code application.GoalService} — goal and key result operations</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code user}, {@code organization.core}</li>
 *   <li>Must NOT be depended on by other modules (leaf module)</li>
 * </ul>
 */
package com.example.taskflow.goal;