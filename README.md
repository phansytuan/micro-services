# Amigosservices

[![Build](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Java](https://img.shields.io/badge/Java-17-orange)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.5.7-green)]()
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)]()

A simple, production-ready microservices reference architecture using Spring Cloud.

---

## What Is This?

Amigosservices demonstrates modern microservices patterns through a practical example: **customer registration with fraud detection**.

**The Flow:**
1. Customer registers via API
2. System checks for fraud (synchronous - must complete first)
3. Welcome notification sent (asynchronous - happens in background)

**Key Patterns Demonstrated:**
- ✅ Service Discovery (Eureka)
- ✅ API Gateway (Spring Cloud Gateway)
- ✅ Synchronous Communication (OpenFeign/REST)
- ✅ Asynchronous Messaging (RabbitMQ)
- ✅ Distributed Tracing (Zipkin)
- ✅ Database-per-Service (PostgreSQL)

---

## Architecture

```
                    ┌─────────┐
                    │ Client  │
                    └────┬────┘
                         │ HTTP
                         ▼
              ┌─────────────────────┐
              │   API Gateway       │
              │   Port: 8083        │
              └──────────┬──────────┘
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────────┐
│  Customer   │  │   Fraud     │  │  Notification   │
│   8080      │  │   8081      │  │    8082         │
└──────┬──────┘  └──────┬──────┘  └─────────────────┘
       │                │
       ▼                ▼
┌─────────────┐  ┌─────────────┐
│ PostgreSQL  │  │  RabbitMQ   │
│ (customer)  │  │  (async)    │
└─────────────┘  └─────────────┘
```

### Services

| Service | Port | Purpose |
|---------|------|---------|
| API Gateway | 8083 | Routes requests to services |
| Customer | 8080 | Customer registration & orchestration |
| Fraud | 8081 | Fraud risk assessment |
| Notification | 8082 | Email/SMS notifications (AMQP consumer) |
| Eureka | 8761 | Service discovery |
| Zipkin | 9411 | Distributed tracing |

---

## Quick Start

### Prerequisites

- Java 17
- Maven 3.8+
- Docker & Docker Compose

### Option 1: Docker (Easiest)

```bash
# Clone and build everything
git clone https://github.com/amigoscode/microservices.git
cd microservices
./build-and-run.sh
```

This builds all services and starts the complete stack.

**Access:**
- API: http://localhost:8083
- Eureka: http://localhost:8761
- Zipkin: http://localhost:9411
- RabbitMQ: http://localhost:15672 (guest/guest)

### Option 2: Local Development (with Hot Reload)

Best for active development:

```bash
# 1. Start infrastructure only
./start-infra.sh

# 2. Start services from IDE (in order):
#    EurekaServerApplication → ApiGWApplication → 
#    FraudApplication → CustomerApplication → NotificationApplication
```

Changes auto-reload in 2-3 seconds with Spring Boot DevTools.

---

## Try It Out

### Register a Customer

```bash
curl -X POST http://localhost:8083/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Jane",
    "lastName": "Doe",
    "email": "jane.doe@example.com"
  }'
```

**What happens:**
1. Customer saved to database
2. Fraud check performed (synchronous)
3. Notification queued (asynchronous)
4. Returns 200 OK immediately

### Check Fraud Status

```bash
curl http://localhost:8083/api/v1/fraud-check/1
```

Response: `{"isFraudster": false}`

---

## Project Structure

```
amigosservices/
├── pom.xml                    # Parent POM
├── docker-compose.yml         # Full stack
├── build-and-run.sh           # Build & deploy script
├── start-infra.sh             # Start infrastructure only
│
├── eureka-server/             # Service discovery
├── apigw/                     # API Gateway
├── customer/                  # Customer service
├── fraud/                     # Fraud service
├── notification/              # Notification service
├── clients/                   # Shared Feign clients
└── amqp/                      # Shared RabbitMQ config
```

---

## Testing

```bash
# Run all tests
mvn test

# Run specific service tests
mvn -pl customer test
mvn -pl fraud test
mvn -pl notification test
```

Tests use `@WebMvcTest` + MockMvc - no Docker or running services required.

---

## Monitoring

| Tool | URL | Purpose |
|------|-----|---------|
| Zipkin | http://localhost:9411 | Trace requests across services |
| Eureka | http://localhost:8761 | See registered services |
| RabbitMQ | http://localhost:15672 | View queues & messages |

### Health Checks

```bash
curl http://localhost:8080/actuator/health  # Customer
curl http://localhost:8081/actuator/health  # Fraud
curl http://localhost:8761/actuator/health  # Eureka
```

---

## Documentation

| Document | What's Inside |
|----------|---------------|
| [HLD.md](./HLD.md) | Architecture overview & data flow |
| [API.md](./API.md) | API endpoints & examples |
| [ZIPKIN_TRACING.md](./ZIPKIN_TRACING.md) | Tracing setup & troubleshooting |
| [AGENTS.md](./AGENTS.md) | Developer quick reference |

---

## Troubleshooting

### Services fail to start

**Problem:** `Connection refused` to Eureka

**Fix:** Start Eureka first and wait for it to be ready:
```bash
curl http://localhost:8761/actuator/health
```

### Registration returns "fraudster" error

This is expected! The fraud service flags certain emails. Try a different email.

### Database connection errors

```bash
# Check PostgreSQL is running
docker compose ps
docker compose logs postgres
```

---

## Tech Stack

| Category | Technology |
|----------|------------|
| Language | Java 17 |
| Framework | Spring Boot 2.5.7 |
| Cloud | Spring Cloud 2020.0.3 |
| Discovery | Netflix Eureka |
| Gateway | Spring Cloud Gateway |
| Communication | OpenFeign, RabbitMQ |
| Database | PostgreSQL |
| Tracing | Zipkin |
| Build | Maven 3.8+ |

---

## Contributing

Contributions welcome for:
- Bug fixes
- Documentation improvements
- Additional test coverage
- Resilience patterns (circuit breakers, retries)

---

## License

MIT License - For educational purposes.

---

<p align="center">
  Built with microservices patterns 🚀
</p>
