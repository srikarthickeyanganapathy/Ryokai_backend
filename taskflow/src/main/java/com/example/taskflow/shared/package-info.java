/**
 * Shared kernel — framework-agnostic types used across all modules.
 *
 * <p>This package is deliberately kept minimal. Only types that are genuinely
 * needed by multiple bounded contexts belong here. Resist the temptation to
 * use this as a dumping ground.</p>
 *
 * <h2>Sub-packages</h2>
 * <ul>
 *   <li>{@code events} — Domain event publishing infrastructure
 *       ({@code DomainEventPublisher}, {@code SpringDomainEventPublisher},
 *       {@code OutboxDomainEventPublisher}, {@code OutboxPoller})</li>
 *   <li>{@code exception} — Shared exception types
 *       ({@code ResourceNotFoundException}, {@code UnauthorizedActionException})</li>
 *   <li>{@code util} — Cross-cutting utilities ({@code RelativeTimeFormatter})</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Must NOT depend on any feature module</li>
 *   <li>All modules may depend on shared</li>
 *   <li>Keep this package as small as possible — if a type is only used by 1-2 modules, it belongs there</li>
 * </ul>
 */
package com.example.taskflow.shared;