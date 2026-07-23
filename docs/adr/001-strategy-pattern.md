# ADR-001: Strategy Pattern for Task Lifecycles

Back to **[ADR Index](README.md)**

---

## Status
**Accepted** (2026-07-20)

## Decision Drivers
- **Maintainability** — Mode-specific logic was spreading across multiple service classes.
- **Open/Closed Principle** — Adding new task modes should not require modifying existing mode logic.
- **Testability** — Each mode's rules must be independently unit-testable.
- **Developer Experience** — New engineers should be able to understand a single mode without reading all three.

## Context
Tasks operate across three distinct operational modes in Ryokai: `PERSONAL`, `CREW`, and `ORG`. Each mode imposes fundamentally different rules:
- Personal tasks must only be assigned to their creator and terminate at `COMPLETED`.
- Crew tasks are claimable by any member and terminate at `COMPLETED` without approval.
- Organization tasks require evidence proof, manager approval, assignor recall rights, role priority validation, and terminate at `APPROVED`.

Writing monolithic `if/else` checks across service classes (`TaskStateTransitionServiceImpl`, `TaskAssignmentServiceImpl`) violates the Open/Closed Principle (OCP) and leads to fragile code.

## Alternatives Considered

| Alternative | Why Rejected |
| :--- | :--- |
| **Monolithic if/else in services** | Violates OCP; adding a new mode requires modifying every conditional branch in every service. High regression risk. |
| **Separate TaskService per mode** | Duplicates shared logic (CRUD, pagination, filtering). Services would diverge over time. |
| **State Machine library (Spring Statemachine)** | Heavyweight dependency for three relatively simple lifecycle graphs. Adds configuration complexity without proportional benefit at current scale. |
| **Enum-based behavior (methods on TaskMode)** | Enums cannot inject Spring beans, making DB lookups and permission checks impossible inside lifecycle rules. |

## Decision
Implement a central `TaskStrategyFactory` and encapsulate lifecycle rules into dedicated strategy classes implementing the `TaskLifecycleStrategy` interface:
- `PersonalTaskStrategy`: Enforces self-assignment and direct completion.
- `CrewTaskStrategy`: Enforces claim-based access and peer completion.
- `OrgTaskStrategy`: Enforces evidence presence (`countByTaskIdAndDeletedFalse > 0`), prohibits self-approval, and handles recall/rejection state changes.

Additional mixin interfaces provide opt-in capabilities:
- `Approvable`: `canSubmit()`, `canApprove()`, `canReject()` — implemented by `OrgTaskStrategy`.
- `Claimable`: `canClaim()` — implemented by `CrewTaskStrategy`.
- `TaskScopeBehavior`: `initialStatus()`, `canBeReviewed()`, `onComplete()` — implemented by all three.

## Consequences
### Positive
- Adding new task modes (e.g., `CLIENT`, `EDUCATION`) requires only a new strategy class + factory registration.
- Completely removes nested conditional branches from core business services.
- Unit testing for mode transitions becomes isolated and deterministic.
- Mixin interfaces allow fine-grained capability composition.

### Negative
- Slightly increases class count in the strategy package (8 files).
- Developers must understand the factory pattern to trace execution flow.

## Implemented In

| File | Role |
| :--- | :--- |
| `strategy/task/TaskLifecycleStrategy.java` | Base interface (8 methods) |
| `strategy/task/TaskStrategyFactory.java` | `TaskMode` → Strategy resolver |
| `strategy/task/PersonalTaskStrategy.java` | PERSONAL mode rules |
| `strategy/task/CrewTaskStrategy.java` | CREW mode rules |
| `strategy/task/OrgTaskStrategy.java` | ORG mode rules |
| `strategy/task/Approvable.java` | Mixin: submit/approve/reject |
| `strategy/task/Claimable.java` | Mixin: crew claim |
| `strategy/task/TaskScopeBehavior.java` | Mixin: initial status, completion |
| `service/TaskStateTransitionServiceImpl.java` | Consumes strategies for state transitions |
| `service/TaskAssignmentServiceImpl.java` | Consumes strategies for assignment validation |
| `service/TaskLifecycleService.java` | Consumes strategies for lifecycle operations |
