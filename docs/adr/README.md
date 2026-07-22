# Architecture Decision Records (ADRs)

Back to **[Master Index](../README.md)** | Back to **[Architecture Overview](../architecture.md)**

---

Architecture Decision Records (ADRs) document key architectural choices, their contextual motivations, and their operational consequences.

## Index of ADRs

- **[ADR-001: Strategy Pattern for Task Lifecycles](001-strategy-pattern.md)** — Encapsulating mode-specific task rules inside strategy classes.
- **[ADR-002: Stateless JWT Authentication with Refresh Token Rotation](002-jwt-auth.md)** — Managing stateless REST sessions and secure token rotation.
- **[ADR-003: Tri-Modal Workspace Isolation](003-tri-modal-workspaces.md)** — Enforcing strict isolation between Personal, Crew, and Organization spaces.
- **[ADR-004: STOMP over WebSocket for Whiteboards](004-stomp-websocket.md)** — Low-latency drawing broadcasts using STOMP sub-protocol.
- **[ADR-005: Custom RBAC & Role Priority Hierarchy](005-rbac-role-priority.md)** — Preventing vertical privilege escalation using integer role priority ranks.
