# ADR-004: STOMP over WebSocket for Whiteboards

Back to **[ADR Index](README.md)**

---

## Status
**Accepted** (2026-07-21)

## Context
Collaborative whiteboard rendering requires sub-50ms stroke synchronization across active crew members without persistent DB writes for every mouse/pen movement.

## Decision
Use STOMP sub-protocol over SockJS WebSockets:
- Real-time stroke events pass through `@MessageMapping("/whiteboards/{boardId}/draw")` and broadcast to `/topic/whiteboards/{boardId}`.
- Board state persistence occurs via REST `PUT /api/crews/{crewId}/whiteboards/{boardId}/snapshot` storing Base64 canvas URLs.

## Consequences
### Positive
- Sub-50ms drawing performance for connected clients.
- Low database overhead during active drawing sessions.

### Negative
- Multi-node application scaling requires a Redis pub/sub broker relay for cross-node WebSocket forwarding.
