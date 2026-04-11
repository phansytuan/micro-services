# Amigosservices - Microservices Architecture

A simple, production-ready microservices reference architecture built with Spring Boot.

---

## Overview

Amigosservices demonstrates modern microservices patterns through a customer registration flow:
1. **Customer Service** - Registers customers and orchestrates the flow
2. **Fraud Service** - Checks if a customer is fraudulent (synchronous)
3. **Notification Service** - Sends welcome notifications (asynchronous)

### Communication Patterns

| Pattern | Use Case | Technology |
|---------|----------|------------|
| **Synchronous** | Critical operations that need immediate response | REST + OpenFeign |
| **Asynchronous** | Non-blocking operations that can happen later | RabbitMQ |

---

## System Architecture

```
                    ┌──────────────┐
                    │   Client     │
                    └──────┬───────┘
                           │ HTTP
                           ▼
┌─────────────────────────────────────────────┐
│           API Gateway (Port 8083)           │
│         Routes requests to services         │
└──────────────────┬──────────────────────────┘
                   │
       ┌───────────┼───────────┐
       ▼           ▼           ▼
┌──────────┐  ┌──────────┐  ┌──────────────┐
│ Customer │  │  Fraud   │  │ Notification │
│  8080    │  │  8081    │  │    8082      │
└────┬─────┘  └────┬─────┘  └──────┬───────┘
     │             │               │
     ▼             ▼               ▼
┌──────────┐  ┌──────────┐  ┌──────────────┐
│PostgreSQL│  │PostgreSQL│  │   RabbitMQ   │
│ customer │  │  fraud   │  │   (async)    │
└──────────┘  └──────────┘  └──────────────┘

Infrastructure:
• Eureka (8761) - Service Discovery
• Zipkin (9411) - Distributed Tracing
```

### Service Responsibilities

| Service | Port | Responsibility | Communication |
|---------|------|----------------|---------------|
| **API Gateway** | 8083 | Single entry point, routes requests | - |
| **Customer** | 8080 | Customer registration, orchestration | Feign → Fraud, AMQP → Notification |
| **Fraud** | 8081 | Fraud risk assessment | Internal only |
| **Notification** | 8082 | Message dispatching | AMQP consumer |
| **Eureka** | 8761 | Service registry | All services register here |

---

## Data Flow: Customer Registration

```
Step 1: Client sends registration request
    POST /api/v1/customers
    
Step 2: API Gateway routes to Customer Service

Step 3: Customer Service processes (SYNCHRONOUS)
    ├─ Saves customer to database
    ├─ Calls Fraud Service via HTTP
    │   └─ Fraud Service saves check history
    └─ Returns success/failure

Step 4: If successful (ASYNCHRONOUS)
    ├─ Publishes event to RabbitMQ
    └─ Returns 200 OK to client immediately

Step 5: Notification Service (ASYNC - happens later)
    ├─ Consumes message from RabbitMQ
    ├─ Saves notification to database
    └─ Sends actual notification (email/SMS)
```

### Why Two Communication Patterns?

| Pattern | When to Use | In This System |
|---------|-------------|----------------|
| **Synchronous (REST)** | Need immediate answer | Fraud check - must know result before continuing |
| **Asynchronous (AMQP)** | Can wait, better scalability | Notification - doesn't block registration response |

---

## Database Design

Each service has its own database schema (Database-per-Service pattern):

| Service | Schema | Tables |
|---------|--------|--------|
| Customer | `customer` | `customer` |
| Fraud | `fraud` | `fraud_check_history` |
| Notification | `notification` | `notification` |

### Benefits

- **Independent scaling** - Each service manages its own data
- **Technology flexibility** - Could use different databases per service
- **Fault isolation** - One database down doesn't affect others

---

## Service Discovery

Services register themselves with **Eureka** on startup:

```
┌──────────────┐     Register     ┌──────────────┐
│   Customer   │ ───────────────> │    Eureka    │
│   Service    │                  │   (8761)     │
└──────────────┘                  └──────┬───────┘
                                         │
       ┌─────────────────────────────────┼──────────────────────┐
       ▼                                 ▼                      ▼
┌──────────────┐              ┌──────────────┐        ┌──────────────┐
│    Fraud     │              │ Notification │        │ API Gateway  │
│   Service    │              │   Service    │        │              │
└──────────────┘              └──────────────┘        └──────────────┘
```

### How It Works

1. Services register: `POST http://eureka:8761/eureka/apps/{service-name}`
2. Services send heartbeats every 30 seconds
3. Eureka tracks which instances are healthy
4. Client-side load balancing via OpenFeign + Ribbon

---

## Deployment

### Startup Order (Important!)

```
1. Infrastructure First:
   postgres, rabbitmq, zipkin

2. Then Services (in order):
   eureka-server  (must be healthy)
   ↓
   apigw          (registers with Eureka)
   ↓
   fraud          (registers with Eureka)
   ↓
   customer       (needs postgres, rabbitmq, eureka)
   ↓
   notification   (needs postgres, rabbitmq)
```

### Spring Profiles

| Profile | Use Case | Configuration |
|---------|----------|---------------|
| `default` | Local IDE development | `localhost` URLs |
| `docker` | Docker deployment | Service names as hosts |
| `kube` | Kubernetes deployment | ConfigMaps/Secrets |

---

## Observability

### Distributed Tracing with Zipkin

All requests are traced across services:

```
Trace: abc123 (200ms)
├── api-gateway     [5ms]
└── customer       [120ms]
    ├── database    [30ms]
    ├── fraud       [50ms]  ← HTTP call to Fraud Service
    │   └── database[15ms]
    └── rabbitmq    [10ms]  ← Async message publish
        └── notification [25ms] (2s later)
```

**Access Zipkin UI:** http://localhost:9411

### Health Endpoints

```bash
# All services expose:
GET /actuator/health          # Overall health
GET /actuator/health/liveness  # Kubernetes liveness probe
GET /actuator/health/readiness # Kubernetes readiness probe
```

---

## Scalability

| Component | Stateless? | How to Scale |
|-----------|-----------|--------------|
| API Gateway | ✅ Yes | Multiple instances behind load balancer |
| Customer Service | ✅ Yes | Multiple instances with Eureka |
| Fraud Service | ✅ Yes | Multiple instances with Eureka |
| Notification Service | ✅ Yes | Competing consumer pattern (RabbitMQ) |
| PostgreSQL | ❌ No | Primary-replica (not configured) |
| RabbitMQ | ⚠️ Clustered | Mirrored queues |
| Eureka | ⚠️ Clustered | Peer-to-peer replication |

---

## Future Enhancements

1. **Resilience**: Circuit breakers (Resilience4j) for fraud service calls
2. **Authentication**: OAuth2/JWT at API Gateway
3. **Rate Limiting**: Request throttling per client
4. **Caching**: Redis for frequently accessed data
5. **Monitoring**: Prometheus/Grafana for metrics
6. **Event Sourcing**: Track customer lifecycle changes

---

## Documentation

| Document | Purpose |
|----------|---------|
| [API.md](./API.md) | API endpoints and usage |
| [ZIPKIN_TRACING.md](./ZIPKIN_TRACING.md) | Tracing setup and troubleshooting |
| [README.md](./README.md) | Quick start guide |
| [AGENTS.md](./AGENTS.md) | Developer quick reference |
