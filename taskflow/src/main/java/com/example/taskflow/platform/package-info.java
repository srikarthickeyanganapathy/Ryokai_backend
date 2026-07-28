/**
 * Platform super-administration module.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Platform-wide user management (ban, elevate, impersonate)</li>
 *   <li>Platform-wide organization oversight (suspend, audit)</li>
 *   <li>Platform role and permission management</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@code application.PlatformUserService} — platform-level user operations</li>
 *   <li>{@code application.PlatformOrganizationService} — platform-level org operations</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code user}, {@code organization}, {@code security.platform}</li>
 *   <li>Must NOT be depended on by any other module — this is a leaf admin module</li>
 *   <li>Guarded by {@code @PlatformAuthorize} aspect — requires SUPER_ADMIN role</li>
 * </ul>
 */
package com.example.taskflow.platform;