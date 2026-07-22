# ADR-001: Strategy Pattern for Task Lifecycles

Back to **[ADR Index](README.md)**

---

## Status
**Accepted** (2026-07-20)

## Context
Tasks operate across three distinct operational modes in Ryokai: `PERSONAL`, `CREW`, and `ORG`. Each mode imposes fundamentally different rules:
- Personal tasks must only be assigned to their creator and terminate at `COMPLETED`.
- Crew tasks are claimable by any member and terminate at `COMPLETED` without approval.
- Organization tasks require evidence proof, manager approval, assignor recall rights, role priority validation, and terminate at `APPROVED`.

Writing monolithic `if/else` checks across service classes (`TaskStateTransitionServiceImpl`, `TaskAssignmentServiceImpl`) violates the Open/Closed Principle (OCP) and leads to fragile code.

## Decision
Implement a central `TaskStrategyFactory` and encapsulate lifecycle rules into dedicated strategy classes implementing the `TaskLifecycleStrategy` interface:
- `PersonalTaskStrategy`: Enforces self-assignment and direct completion.
- `CrewTaskStrategy`: Enforces claim-based access and peer completion.
- `OrgTaskStrategy`: Enforces evidence presence (`countByTaskIdAndDeletedFalse > 0`), prohibits self-approval, and handles recall/rejection state changes.

## Consequences
### Positive
- Adding new task modes or modifying existing mode logic requires modifying only the corresponding strategy class.
- Completely removes nested conditional branches from core business services.
- Unit testing for mode transitions becomes isolated and deterministic.

### Negative
- Slightly increases class count in the strategy package.
