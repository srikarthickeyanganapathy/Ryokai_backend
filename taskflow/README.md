# Ryokai

![CI Status](https://github.com/USERNAME/task-system-backend/actions/workflows/ci.yml/badge.svg)
![Coverage](https://img.shields.io/badge/Coverage-80%25%2B-brightgreen)
![Java](https://img.shields.io/badge/Java-21-blue.svg)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-green.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)

A production-ready task management backend designed with a focus on security, observability, and robust architecture. Built with Spring Boot 3 and PostgreSQL.

## Core Features

- **Role-Based Access Control (RBAC):** Hierarchical roles (Employee, Manager, Director, Super Admin) with granular domain-level permissions implemented via `CustomPermissionEvaluator`.
- **State Machine Workflow:** Strict transitions for tasks (ASSIGNED -> SUBMITTED -> APPROVED/REJECTED) enforced through domain validation.
- **Security Hardening:** Rate-limiting (Bucket4j), JWT with rotating refresh tokens (hashed in DB), XSS protection, and comprehensive audit logging.
- **Observability:** Prometheus metrics, JSON-formatted structured logging for Logstash, MDC trace IDs, and Actuator endpoints.
- **Real-Time Updates:** WebSocket STOMP messaging for instant notifications on task assignment and status changes.
- **Performance:** Addressed N+1 query problems utilizing Spring Data JPA `@EntityGraph`.

## Tech Stack

- **Framework:** Java 21, Spring Boot 3, Spring Security 6
- **Database & Migrations:** PostgreSQL, Spring Data JPA, Hibernate, Flyway
- **Authentication:** JJWT 0.12.x
- **Testing:** JUnit 5, MockMvc, Testcontainers, JaCoCo
- **Observability:** Micrometer, Prometheus, Logback JSON Encoder
- **Deployment:** Docker, Docker Compose, GitHub Actions, Render

## Architecture

Ryokai relies on a robust layered architecture, emphasizing separation of concerns:
- `Controller`: REST endpoints and WebSocket handlers.
- `Service`: Business logic, transaction boundaries, and state validation.
- `Repository`: Data access and optimized query execution (N+1 prevention).
- `Security`: Custom permission evaluators, JWT filters, rate limiters.

## Getting Started

### Prerequisites
- Java 21
- Maven
- Docker & Docker Compose

### Run Locally (Docker)
```bash
docker-compose up -d --build
```
The API will be available at `http://localhost:8080`.

### Run Locally (Manual)
1. Start the database:
```bash
docker run -d -p 5432:5432 -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=taskflow postgres:16-alpine
```
2. Build and run:
```bash
mvn clean install -DskipTests
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

## API Overview
API documentation is automatically generated.
- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

## Testing
The test suite utilizes Testcontainers for true integration testing against a real PostgreSQL instance.
```bash
mvn clean verify
```
This ensures >80% code coverage across core domains, strictly enforced by JaCoCo in the CI pipeline.
