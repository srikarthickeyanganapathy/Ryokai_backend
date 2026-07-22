# Ryokai Taskflow Backend

![Java](https://img.shields.io/badge/Java-17%20LTS-blue.svg)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-green.svg)
![Spring Security](https://img.shields.io/badge/Spring_Security-6.2-brightgreen.svg)
![Database](https://img.shields.io/badge/Relational_DB-MySQL%20%7C%20PostgreSQL%20%7C%20H2-blue.svg)
![WebSockets](https://img.shields.io/badge/STOMP-SockJS-orange.svg)

**Ryokai** is a production-ready tri-modal task management and enterprise collaboration backend built on Spring Boot 3.2. It provides three strictly isolated workspace modes—**Personal**, **Crew**, and **Organization**—governed by dynamic strategy patterns, fine-grained RBAC role priorities, STOMP WebSockets, and evidence-based review chains.

---

## 📚 Complete Engineering Documentation Suite

The complete engineering documentation suite is available in the repository at `../docs/`:

- 🚀 **[Engineering Documentation Index](../docs/README.md)** — Master portal, technology matrix, and changelog.
- 🏗️ **[System Architecture](../docs/architecture.md)** — Deployment topology, package boundaries, and ADRs 001–005.
- 🧬 **[Domain Model & ER Diagram](../docs/domain.md)** — Tri-modal workspace rules, entity relationship diagrams, and entity specs.
- 🛡️ **[Security Architecture](../docs/security.md)** — Spring Security 6 filter chain, custom SpEL evaluators, RBAC matrix, and audit.
- 🔌 **[API Reference Catalogue](../docs/api.md)** — Comprehensive REST API reference across all workspace modules.
- 🔄 **[Workflows & Sequence Diagrams](../docs/workflows.md)** — Sequence diagrams (1–9) and state machine specifications.
- 🛠️ **[Operations & Runbooks](../docs/operations.md)** — Configuration reference, trace ID MDC logging, operational runbooks, and known limits.
- 🧪 **[Developer Guide & Onboarding](../docs/developer-guide.md)** — Onboarding guide, testing infrastructure specs, exception catalogue, and glossary.

---

## 🌟 Core Features & Workspace Modes

### 1. Tri-Modal Workspace Isolation
- **Personal Workspace**: Private tasks, checklists, pomodoro focus timers, private notes, bookmarks, and calendar events.
- **Crew Workspace**: Flat peer collaboration, real-time STOMP whiteboard drawing, text channels, claim-based task pools, and direct peer completions.
- **Organization Vault**: Multi-tenant corporate boundary, custom RBAC with integer role-priority governance, evidence uploads, department team observers, OKRs/Goals, and HR leave requests.

### 2. Project Connection Bridge
- Allows personal projects to be shared with Crews for peer collaboration.
- Permanently blocks enterprise organization projects (`project.organization != null`) from external sharing to guarantee zero data leakage.

### 3. Task State Machines & Strategy Pattern
- `PersonalTaskStrategy`: `TODO -> IN_PROGRESS -> COMPLETED`. Self-assigned.
- `CrewTaskStrategy`: `TODO (Unclaimed) -> IN_PROGRESS (Claimed) -> COMPLETED`. Flat peer completion.
- `OrgTaskStrategy`: `TODO -> IN_PROGRESS -> SUBMITTED -> APPROVED / REJECTED`. Requires `TaskEvidence` upload and assignor priority check (`TaskHierarchyValidator`). Prevents assignee self-approval. Allows assignor recall.

---

## 🛠️ Technology Stack

- **Core**: Java 17 LTS, Spring Boot 3.2.x, Spring Framework 6.x
- **Security**: Spring Security 6, JJWT 0.12.x, Bucket4j 8.x (Rate Limiting)
- **Data & Persistence**: Spring Data JPA, Hibernate 6.x, MySQL 8.0 / PostgreSQL 16 / H2
- **Real-Time Communication**: Spring STOMP over SockJS WebSockets
- **Observability**: SLF4J, Logback JSON Encoder, MDC Trace IDs (`X-Correlation-ID`)
- **Documentation**: Springdoc OpenAPI v3, Swagger UI (`/swagger-ui/index.html`)
- **Testing**: JUnit 5, Mockito, Spring Security Test, MockMvc

---

## 🚀 Getting Started

### Prerequisites
- JDK 17+
- Maven 3.8+
- MySQL 8.0+ or PostgreSQL 15+ (or default in-memory H2 profile)

### Environment Variables
Configure the following environment variables or override default values in `src/main/resources/application.yml`:

```bash
export DB_URL="jdbc:mysql://localhost:3306/taskflow?useSSL=false&allowPublicKeyRetrieval=true"
export DB_USERNAME="root"
export DB_PASSWORD="your_secure_db_password"
export JWT_SECRET="your_secure_256bit_base64_secret_key"
```

### Build & Run
```bash
# Clean and compile
mvn clean install -DskipTests

# Run Spring Boot application
mvn spring-boot:run
```

The application will start at `http://localhost:8080`.

---

## 🔌 API & Interactive Documentation

When the application is running:
- **Swagger UI Interactive Portal**: `http://localhost:8080/swagger-ui/index.html`
- **OpenAPI v3 Specification**: `http://localhost:8080/v3/api-docs`

---

## 🧪 Testing

Execute the unit and integration test suite:
```bash
mvn clean test
```

Tests cover strategy validation, security evaluators, repository JPA queries, and controller state machine transitions.
