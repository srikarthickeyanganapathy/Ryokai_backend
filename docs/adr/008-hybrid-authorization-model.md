# ADR 008: Hybrid Authorization Model (Defense-in-Depth)

## Status
Accepted

## Context
During the Backend Engineering Audit (Phase 3), we discovered a divergence between our documented architecture constraints (AC-5) and our implementation. The documentation required controller-level `@PreAuthorize` annotations, while the implementation largely relied on Service-Layer Authorization, leaving controllers completely open prior to business logic execution.

While Service-Layer Authorization is correct for enforcing domain invariants, tenant isolation, ownership rules, and complex permission strategies, omitting coarse-grained authorization at the controller layer poses a risk. If a service method is inadvertently modified or bypassed, the controller would allow anonymous or unauthorized users to invoke the endpoint, violating Defense-in-Depth principles.

On the other hand, indiscriminately applying `@PreAuthorize("isAuthenticated()")` everywhere is insufficient for endpoints acting on specific business resources (like Projects, Crews, or Tasks) because it fails to evaluate context-aware authorization prior to entering the service tier.

## Decision
We are adopting a **Hybrid Authorization Model** that strictly defines the responsibilities across different architectural layers. The flow of a request must enforce security at multiple checkpoints before reaching the database:

**Request Flow:**
`Controller` (Authentication & Coarse Auth) → `Service` (Business Auth) → `Strategy` (Permission Evaluation) → `Repository` (Data Access)

### Layer Responsibilities

1. **Controller Layer (Authentication & Coarse Authorization):**
   - **Baseline:** Every protected endpoint MUST reject anonymous users immediately, at minimum using `@PreAuthorize("isAuthenticated()")`.
   - **Coarse Authorization:** Where the controller has the necessary context (e.g., path variables for `crewId`, `projectId`, or `taskId`), it MUST use coarse-grained permission checks like `@PreAuthorize("hasPermission(#crewId, 'Crew', 'VIEW')")` or role checks like `hasRole('ADMIN')`.
   - **Exceptions:** True public endpoints (e.g., Login, Registration, Token Refresh, Email Verification) remain intentionally unprotected.

2. **Service Layer (Business Authorization):**
   - Retains full responsibility for domain invariants, tenant isolation, and complex business authorization rules.
   - Must never assume the controller has performed exhaustive security checks.
   - The service layer remains the **source of truth** for authorization.

3. **Strategy Layer (Permission Evaluation):**
   - Encapsulates complex RBAC and ABAC evaluations for use by the service (and coarsely by the controller).

4. **Repository Layer:**
   - Strictly responsible for data access. No authorization logic should reside here.

## Consequences
- **Positive:** True Defense-in-Depth. Controller-level checks prevent obviously unauthorized requests from ever executing business logic.
- **Positive:** Better consistency. We no longer have a mix of protected and unprotected controllers serving authenticated features.
- **Positive:** Clear boundaries. Future developers are explicitly instructed not to duplicate business logic in controllers, nor to weaken the service layer.
- **Negative:** Minor overhead in ensuring `@PreAuthorize` rules in controllers stay aligned with service requirements, but this is mitigated by keeping controller checks "coarse".

## Implementation Guidelines
- Add `@PreAuthorize("isAuthenticated()")` to general authenticated self-service endpoints (e.g., `/api/v1/users/me`, `/api/v1/session/logout`).
- Add `@PreAuthorize("hasPermission(...)")` to resource-specific endpoints (e.g., `/api/v1/crews/{crewId}`, `/api/v1/teams/{teamId}/messages`).
- Keep public endpoints untouched.
