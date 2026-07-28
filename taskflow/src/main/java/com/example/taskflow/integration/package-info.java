/**
 * External integration adapters (infrastructure layer).
 *
 * <p>Contains adapters for communicating with external systems and protocols.
 * This is NOT for domain events — those live in {@code shared.events}.</p>
 *
 * <h2>Sub-packages</h2>
 * <ul>
 *   <li>{@code email} — SMTP email delivery</li>
 *   <li>{@code websocket} — Real-time WebSocket broadcasting and configuration</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>Depends on: {@code security.jwt} (WebSocket auth), {@code user} (email targets)</li>
 *   <li>Feature modules depend on integration services (e.g., {@code EmailService})</li>
 *   <li>Integration must NOT contain business logic</li>
 * </ul>
 */
package com.example.taskflow.integration;