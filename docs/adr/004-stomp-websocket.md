# ADR-004: STOMP over WebSocket for Whiteboards

Back to **[ADR Index](README.md)**

---

## Status
**Accepted** (2026-07-21)

## Decision Drivers
- **Latency** — Collaborative whiteboard requires sub-50ms stroke synchronization.
- **Simplicity** — Minimize infrastructure complexity for real-time features.
- **Spring Ecosystem** — Leverage existing Spring WebSocket support without external brokers.
- **Cost** — Avoid dedicated real-time infrastructure (e.g., Pusher, Ably) for initial launch.

## Context
Collaborative whiteboard rendering requires sub-50ms stroke synchronization across active crew members without persistent DB writes for every mouse/pen movement.

## Alternatives Considered

| Alternative | Why Rejected |
| :--- | :--- |
| **Raw WebSocket (no sub-protocol)** | No built-in pub/sub semantics. Requires custom message routing, topic management, and connection lifecycle handling. |
| **Server-Sent Events (SSE)** | Unidirectional (server → client only). Whiteboards need bidirectional stroke data flow. |
| **Third-party service (Pusher / Ably / Firebase)** | Adds external dependency, vendor lock-in, and per-message cost. Over-engineered for initial feature set. |
| **gRPC bidirectional streaming** | Not natively supported in browsers without grpc-web proxy. Adds protocol translation complexity. |
| **MQTT** | Designed for IoT. Spring ecosystem support is limited compared to STOMP. Requires a separate broker (Mosquitto). |

## Decision
Use STOMP sub-protocol over native WebSocket (no SockJS):
- Real-time stroke events pass through `@MessageMapping("/whiteboards/{boardId}/draw")` and broadcast to `/topic/whiteboards/{boardId}`.
- Board state persistence occurs via REST `PUT /api/crews/{crewId}/whiteboards/{boardId}/snapshot` storing Base64 canvas URLs.
- Authentication enforced via `StompAuthChannelInterceptor` on CONNECT frame — validates JWT token.
- Heartbeat: 10s send / 10s receive for connection health monitoring.
- Transport limits: 64KB per message to prevent abuse.

## Consequences
### Positive
- Sub-50ms drawing performance for connected clients on the same node.
- Low database overhead during active drawing sessions — only snapshots persist.
- Spring's `@MessageMapping` provides clean annotation-based routing.
- STOMP's built-in pub/sub (`/topic/*`) eliminates custom routing code.

### Negative
- Multi-node application scaling requires a Redis pub/sub broker relay for cross-node WebSocket forwarding (see [future-architecture.md §5](../future-architecture.md#5-distributed-cache-redis)).
- In-memory `SimpleBrokerMessageHandler` loses all subscriptions on restart.
- 64KB message limit may need adjustment for very complex whiteboard strokes.

## Implemented In

| File | Role |
| :--- | :--- |
| `config/WebSocketConfig.java` | STOMP endpoint `/ws`, broker config, heartbeat, transport limits |
| `config/WebSocketHandshakeInterceptor.java` | Origin validation on upgrade handshake |
| `security/StompAuthChannelInterceptor.java` | JWT validation on CONNECT, crew membership on SUBSCRIBE |
| `controller/WhiteboardSocketController.java` | `@MessageMapping("/whiteboards/{boardId}/draw")` |
| `controller/WhiteboardController.java` | REST: create whiteboard, save snapshot |
| `service/WhiteboardService.java` | Whiteboard CRUD and snapshot persistence |
| `service/RealtimeBroadcaster.java` | `SimpMessagingTemplate` wrapper for topic broadcasting |
| `domain/Whiteboard.java` | Entity: `dataUrl` (LONGTEXT Base64) |
