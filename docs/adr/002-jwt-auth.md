# ADR-002: Stateless JWT Authentication with Refresh Token Rotation

Back to **[ADR Index](README.md)**

---

## Status
**Accepted** (2026-07-20)

## Context
The backend serves both web frontend single-page applications (SPAs) and future mobile clients. Storing HTTP session state on application servers prevents horizontal scaling and introduces session affinity requirements.

## Decision
Adopt stateless JWT authentication:
- Short-lived Access Tokens: 15-minute expiration, signed with HMAC-SHA512.
- Refresh Tokens: Cryptographically random UUID strings stored in `refresh_tokens` database table with 7-day expiration.
- Single-Use Rotation: Whenever a refresh token is presented to `/api/session/refresh`, the old token is invalidated and a fresh token pair is issued.

## Consequences
### Positive
- Allows backend services to scale horizontally behind a load balancer without sticky sessions.
- Token revocation is supported via single-use refresh token invalidation and `TokenDenylistService`.

### Negative
- Compromised access tokens remain valid until their 15-minute expiration unless explicitly checked against a denylist.
