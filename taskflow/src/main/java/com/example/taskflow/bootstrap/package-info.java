/**
 * Application data initialization and seeding.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Seeding default roles, permissions, and admin users on first startup</li>
 *   <li>Ensuring required reference data exists</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>May reference any module's repositories for seeding (startup-only code)</li>
 *   <li>No module should depend on this package at compile time</li>
 *   <li>Runs once at application startup via Spring lifecycle hooks</li>
 * </ul>
 */
package com.example.taskflow.bootstrap;