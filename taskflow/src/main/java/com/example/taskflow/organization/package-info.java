/**
 * Organization module (Tier 1 — complex, with sub-domains).
 *
 * <p>The largest module by sub-domain count. Split into four bounded sub-contexts
 * that share the Organization aggregate root but have distinct responsibilities.</p>
 *
 * <h2>Sub-domains</h2>
 * <ul>
 *   <li>{@code core} — Organization entity, lifecycle (create, suspend, delete), org-level queries</li>
 *   <li>{@code membership} — Invitations, join/leave workflows, member management, leave requests</li>
 *   <li>{@code rbac} — Roles, permissions, scopes, policy evaluation, permission auditing</li>
 *   <li>{@code announcement} — Organization-wide announcements</li>
 * </ul>
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@code core.application.OrganizationService} — org queries</li>
 *   <li>{@code core.application.OrganizationLifecycleService} — org creation and deletion</li>
 *   <li>{@code membership.application.OrganizationInviteService} — invite workflows</li>
 *   <li>{@code membership.application.OrganizationMemberService} — member management</li>
 *   <li>{@code membership.application.OrganizationLeaveService} — leave request workflows</li>
 *   <li>{@code rbac.application.RoleService} — role CRUD</li>
 *   <li>{@code rbac.application.AuthorizationEngine} — permission evaluation and assignment</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code user}, {@code security}, {@code shared}, {@code notification}</li>
 *   <li>Widely depended on by: {@code task}, {@code project}, {@code crew}, {@code team}, {@code dashboard}</li>
 *   <li>Sub-domains may reference each other's domain models but should prefer application services</li>
 * </ul>
 */
package com.example.taskflow.organization;