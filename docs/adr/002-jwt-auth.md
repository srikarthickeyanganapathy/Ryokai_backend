# ADR-002: Stateless JWT Authentication with Refresh Token Rotation

Back to **[ADR Index](README.md)**

---

## Status
**Accepted** (2026-07-20)

## Decision Drivers
- **Scalability** — Backend must scale horizontally without session affinity.
- **Security** — Compromised tokens must be revocable without global session stores.
- **Multi-client support** — Web SPAs and future mobile clients share the same auth mechanism.
- **Developer Experience** — Simple `Authorization: Bearer <token>` header for all API calls.

## Context
The backend serves both web frontend single-page applications (SPAs) and future mobile clients. Storing HTTP session state on application servers prevents horizontal scaling and introduces session affinity requirements.

## Alternatives Considered

| Alternative | Why Rejected |
| :--- | :--- |
| **HTTP Session (server-side)** | Requires sticky sessions or centralized session store. Prevents stateless horizontal scaling. |
| **Redis-backed sessions** | Adds infrastructure dependency (Redis). Still requires session ID management in cookies. Not RESTful. |
| **OAuth2 / OpenID Connect** | Correct for third-party authentication, but adds unnecessary complexity when the backend is both the identity provider and the resource server. Over-engineered for a single-tenant identity model. |
| **Opaque tokens + DB lookup** | Every API call requires a DB query. Defeats the purpose of stateless architecture. High latency at scale. |

## Decision
Adopt stateless JWT authentication with dual-key signing:
- **Access Tokens**: 15-minute expiration, signed with HMAC-SHA256 (`jwt.secret`). Contains `sub` (username), `roles`, `tv` (token version), `tokenId` (UUID).
- **Refresh Tokens**: Cryptographically random UUID strings stored in `refresh_tokens` database table with 7-day expiration. Signed with a separate key (`jwt.refreshSecret`).
- **Single-Use Rotation**: Whenever a refresh token is presented to `/api/session/refresh`, the old token is invalidated and a fresh token pair is issued.
- **Replay Detection**: If a previously-replaced refresh token is presented, the system throws `TokenRefreshException(REUSE_DETECTED)` → HTTP 401.
- **Mass Invalidation**: `User.tokenVersion` integer is incremented on "logout all" — all outstanding JWTs with older `tv` claim are rejected.

## Consequences
### Positive
- Allows backend services to scale horizontally behind a load balancer without sticky sessions.
- Token revocation is supported via single-use refresh token invalidation and `TokenDenylistService`.
- Dual-key architecture limits blast radius — compromised access key doesn't expose refresh tokens.
- Token version provides instant mass invalidation without touching refresh token table.

### Negative
- Compromised access tokens remain valid until their 15-minute expiration unless explicitly checked against a denylist.
- Token denylist is currently Caffeine in-memory — not shared across nodes (see [operations.md known issue KI-2](../operations.md#5-known-issues-limitations--workarounds)).
- Dual secrets increase operational complexity for secret rotation.

## Implemented In

| File | Role |
| :--- | :--- |
| `util/JwtUtil.java` | Token generation, validation, claim extraction (HS256) |
| `util/JwtAuthenticationFilter.java` | Servlet filter: Bearer header → SecurityContext |
| `service/AuthServiceImpl.java` | Login flow: credentials → token pair issuance |
| `service/RefreshTokenService.java` | Refresh rotation, replay detection, revocation |
| `service/TokenDenylistService.java` | Caffeine-backed access token denylist |
| `service/TokenCleanupService.java` | Expired token cleanup |
| `config/SecurityConfig.java` | Filter chain registration, BCrypt encoder |
| `domain/RefreshToken.java` | Persistent refresh token entity |
| `domain/User.java` | `tokenVersion` field for mass invalidation |
| `controller/AuthController.java` | Login endpoint with per-user rate limiting |
| `controller/SessionController.java` | Refresh, logout, logout-all, verify-email endpoints |
