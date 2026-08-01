/**
 * Security infrastructure module (Tier 1 — complex).
 *
 * <p>Provides the <em>infrastructure</em> for authentication and authorization.
 * This is distinct from {@code identity}, which owns the <em>business capability</em>
 * of user registration, login, and session management.</p>
 *
 * <h2>Sub-packages</h2>
 * <ul>
 *   <li>{@code authorization} — Permission evaluation pipeline, policy engine, field restrictions</li>
 *   <li>{@code config} — Spring Security configuration, method security</li>
 *   <li>{@code filters} — JWT authentication filter, rate limiting</li>
 *   <li>{@code jwt} — JWT token generation and validation utility</li>
 *   <li>{@code platform} — Platform-level authorization (super-admin aspect and roles)</li>
 *   <li>{@code exception} — Security-specific exceptions</li>
 *   <li>Root package — Permission codes, permission metadata, scope types, domain handlers</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@code authorization.CustomPermissionEvaluator} — Spring Security integration point</li>
 *   <li>{@code authorization.engine.AuthorizationEngine} — programmatic permission checks</li>
 *   <li>{@code jwt.JwtUtil} — token operations</li>
 *   <li>{@code PermissionCode} — canonical permission identifiers</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code user}, {@code organization.rbac}, {@code organization.membership}</li>
 *   <li>Widely depended on by all modules needing permission checks</li>
 *   <li>Domain handlers ({@code TaskPermissionHandler}, etc.) live in their respective modules</li>
 * </ul>
 */
package com.example.taskflow.security;