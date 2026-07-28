/**
 * Identity and authentication module (Tier 1 — complex).
 *
 * <p>This module owns the <em>business capability</em> of user authentication,
 * registration, password management, and session lifecycle. It is distinct from
 * {@code security}, which provides the <em>infrastructure</em> (JWT, filters, authorization).</p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>User registration and email verification</li>
 *   <li>Login, logout, and session management</li>
 *   <li>Password reset workflows</li>
 *   <li>Refresh token rotation and cleanup</li>
 *   <li>Token deny-listing for forced logout</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@code application.AuthService} — registration, login, logout</li>
 *   <li>{@code application.PasswordResetService} — password reset flow</li>
 *   <li>{@code application.RefreshTokenService} — token rotation</li>
 *   <li>{@code application.TokenDenylistService} — forced invalidation</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code user}, {@code security.jwt}, {@code integration.email}</li>
 *   <li>Must NOT depend on any feature module (task, project, crew, etc.)</li>
 *   <li>Other modules must NOT call identity services — authentication is handled by filters</li>
 * </ul>
 */
package com.example.taskflow.identity;