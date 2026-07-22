# System Architecture Overview

Back to **[Master Index](README.md)** | View **[Architecture Decision Records](adr/README.md)**

---

## 1. Deployment Topology

```mermaid
graph TD
    Client["Frontend SPA (React / Vite)<br/>Port: 5173 / 3000"] -->|REST / HTTP 1.1| ReverseProxy["Reverse Proxy / NGINX / Gateway<br/>Port: 443"]
    Client -->|WebSocket / STOMP| ReverseProxy
    
    subgraph AppServer["Spring Boot 3.2 Backend Container"]
        ReverseProxy -->|Port 8080| SecurityChain["Spring Security FilterChain"]
        SecurityChain --> RateLimiter["RateLimitFilter (Bucket4j)"]
        RateLimiter --> MDCFilter["CorrelationIdFilter (MDC)"]
        MDCFilter --> JwtAuth["JwtAuthenticationFilter"]
        JwtAuth --> Dispatcher["Spring DispatcherServlet"]
        
        Dispatcher --> RESTControllers["REST Controllers"]
        Dispatcher --> WSController["WebSocket Controllers (STOMP)"]
        
        RESTControllers --> Services["Domain Services & Strategies"]
        WSController --> Broadcaster["RealtimeBroadcaster"]
    end
    
    subgraph DataStorage["Persistence & External Services"]
        Services -->|JDBC / Connection Pool| RDBMS[(Relational DB: MySQL / Postgres / H2)]
        Services -->|JavaMailSender| SMTP["SMTP Server (Email Service)"]
        Services -->|Local Disk Write| FileStore["Task Evidence Storage"]
    end
    
    subgraph FutureState["Future Infrastructure Architecture"]
        RedisCache["Redis (Distributed Session / Rate Limit)"]
        KafkaBroker["Kafka / RabbitMQ (Async Event Bus)"]
        S3Storage["AWS S3 / MinIO (Object Storage)"]
        OpenTelemetry["OpenTelemetry Collector + Grafana"]
    end

    Services -.->|Planned| RedisCache
    Services -.->|Planned| KafkaBroker
    Services -.->|Planned| S3Storage
    AppServer -.->|Planned| OpenTelemetry
```

---

## 2. Package Layer Dependencies & Boundaries

```
src/main/java/com/example/taskflow/
├── config/              # Global Configuration & Security Chain
├── controller/          # REST Controllers
├── domain/              # JPA Entities & Enums
├── dto/                 # Request & Response DTO Data Contracts
├── exception/           # Custom Domain Runtime Exceptions
├── repository/          # Spring Data JPA Repositories
├── security/            # SpEL Evaluators & Permission Handlers
├── service/             # Domain Services & Business Logic
└── strategy/task/       # Task Scope Lifecycle Strategies
```

### Layer Isolation Rules
1. **Repositories** must never inject Services or Controllers.
2. **Domain Entities** must remain pure JPA POJOs without `@Autowired` dependencies.
3. **Strategies** must never reference Controller classes directly.
