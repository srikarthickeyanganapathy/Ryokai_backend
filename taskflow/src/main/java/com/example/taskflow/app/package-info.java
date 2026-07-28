/**
 * Application bootstrap and cross-cutting configuration.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Global exception handling ({@code GlobalExceptionHandler})</li>
 *   <li>OpenAPI/Swagger configuration</li>
 *   <li>Async thread pool configuration</li>
 *   <li>CORS and application-level settings</li>
 * </ul>
 *
 * <h2>Dependency Rules</h2>
 * <ul>
 *   <li>May reference {@code shared} for common exception types</li>
 *   <li>Must NOT contain business logic — delegates to feature modules</li>
 *   <li>No module should depend on this package</li>
 * </ul>
 */
package com.example.taskflow.app;