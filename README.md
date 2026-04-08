# Amigosservices

[![Build](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/amigoscode/microservices)
[![Java](https://img.shields.io/badge/Java-17-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.5.7-green)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2020.0.3-blue)](https://spring.io/projects/spring-cloud)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> A production-ready microservices reference architecture demonstrating distributed system patterns with Spring Cloud.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Quick Start (Docker)](#quick-start-docker)
  - [Local Development](#local-development)
- [API Usage](#api-usage)
- [Testing](#testing)
- [Monitoring](#monitoring)
- [Documentation](#documentation)
- [Contributing](#contributing)

---

## Overview

Amigosservices is a complete microservices ecosystem showcasing modern patterns for building scalable, resilient distributed systems on the JVM. It implements a customer onboarding flow with fraud detection and notification dispatching.

### Key Features

- ✅ **Service Discovery** — Netflix Eureka for dynamic service registration
- ✅ **API Gateway** — Spring Cloud Gateway for unified ingress
- ✅ **Synchronous Communication** — OpenFeign for inter-service REST calls
- ✅ **Asynchronous Messaging** — RabbitMQ for event-driven architecture
- ✅ **Distributed Tracing** — Zipkin for request correlation
- ✅ **Database-per-Service** — PostgreSQL with isolated schemas
- ✅ **Containerized Deployment** — Docker Compose for local development

### Demo Flow

```
┌─────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Client │────>│ API Gateway  │────>│   Customer   │────>│    Fraud     │
│         │     │   (8083)     │     │   Service    │     │   Service    │
└─────────┘     └──────────────┘     └──────────────┘     └──────────────┘
                                              │                   │
                                              ▼                   ▼
                                       ┌──────────────┐     ┌──────────────┐
                                       │   PostgreSQL │     │   PostgreSQL │
                                       │  (customer)  │     │   (fraud)    │
                                       └──────────────┘     └──────────────┘
                                              │
                                              ▼
                                       ┌──────────────┐
                                       │   RabbitMQ   │
                                       │(internal.ex) │
                                       └──────────────┘
                                              │
                                              ▼
                                       ┌──────────────┐
                                       │ Notification │
                                       │   Service    │
                                       └──────────────┘
```

---

## Architecture

The system follows a **hybrid communication model**:

- **Synchronous (REST)**: Critical path validation (Fraud check during registration)
- **Asynchronous (AMQP)**: Non-blocking operations (Notification dispatch)

Read the full architecture documentation: [HLD.md](./HLD.md)

---

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| **Language** | Java | 17 |
| **Framework** | Spring Boot | 2.5.7 |
| **Cloud** | Spring Cloud | 2020.0.3 |
| **Service Discovery** | Netflix Eureka | — |
| **API Gateway** | Spring Cloud Gateway | — |
| **Service Client** | OpenFeign | — |
| **Message Broker** | RabbitMQ | 3.9 |
| **Database** | PostgreSQL | 14+ |
| **Tracing** | Zipkin | — |
| **Build Tool** | Maven | 3.8+ |
| **Containerization** | Docker & Docker Compose | — |

---

## Project Structure

```
amigosservices/
├── pom.xml                    # Parent POM (dependency management)
├── docker-compose.yml         # Full stack orchestration
├── build-and-run.sh          # One-shot build & deploy script
│
├── eureka-server/            # Service discovery registry
│   └── src/
│
├── apigw/                    # API Gateway (Spring Cloud Gateway)
│   └── src/
│
├── customer/                 # Customer registration service
│   └── src/
│
├── fraud/                    # Fraud detection service
│   └── src/
│
├── notification/             # Notification dispatch service
│   └── src/
│
├── clients/                  # Shared OpenFeign clients (library)
│   └── src/
│
└── amqp/                     # Shared RabbitMQ config (library)
    └── src/
```

### Module Overview

| Module | Type | Port | Description |
|--------|------|------|-------------|
| `eureka-server` | Infrastructure | 8761 | Service registry and discovery |
| `apigw` | Infrastructure | 8083 | API Gateway and routing |
| `customer` | Domain Service | 8080 | Customer onboarding and orchestration |
| `fraud` | Domain Service | 8081 | Fraud risk assessment |
| `notification` | Domain Service | 8082 | Notification processing (AMQP consumer) |
| `clients` | Shared Library | — | Feign client interfaces |
| `amqp` | Shared Library | — | RabbitMQ configuration |

---

## Getting Started

### Prerequisites

- **Java 17 JDK** — [Download](https://adoptium.net/)
- **Maven 3.8+** — [Download](https://maven.apache.org/download.cgi)
- **Docker & Docker Compose** — [Download](https://docs.docker.com/get-docker/)

Verify installation:

```bash
java -version        # Should show Java 17
mvn -version         # Should show Maven 3.8+
docker --version     # Should show Docker 20.10+
docker-compose --version
```

### Quick Start (Docker)

The fastest way to run the entire stack:

```bash
# Clone the repository
git clone https://github.com/amigoscode/microservices.git
cd microservices

# Build and run everything
./build-and-run.sh
```

This script will:
1. Compile all Maven modules
2. Build Docker images using Jib
3. Start infrastructure (PostgreSQL, RabbitMQ, Zipkin)
4. Create database schemas
5. Start all services in correct order
6. Verify all endpoints are healthy

**Access the services:**

| Service | URL |
|---------|-----|
| API Gateway | http://localhost:8083 |
| Eureka Dashboard | http://localhost:8761 |
| Zipkin UI | http://localhost:9411 |
| RabbitMQ Management | http://localhost:15672 (guest/guest) |
| pgAdmin | http://localhost:5050 (pgadmin4@pgadmin.org/admin) |

### Local Development

For active development with hot-reload:

**Step 1: Start Infrastructure**

```bash
docker-compose up -d postgres rabbitmq zipkin
```

**Step 2: Start Services (in order)**

From your IDE or terminal, start the Spring Boot applications in this sequence:

1. **EurekaServerApplication** (wait for "Started EurekaServerApplication")
2. **ApiGWApplication**
3. **FraudApplication**
4. **CustomerApplication**
5. **NotificationApplication**

> ⚠️ **Important**: Service startup order matters. Services will fail to start if Eureka is not available.

**Step 3: Verify**

```bash
# Check Eureka for registered services
curl http://localhost:8761/eureka/apps

# Check Customer Service health
curl http://localhost:8080/actuator/health
```

---

## API Usage

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
1. ✅ Customer record created in PostgreSQL
2. ✅ Fraud check performed via synchronous REST call
3. ✅ Notification event published to RabbitMQ
4. ✅ Notification Service processes event asynchronously

### Check Fraud Status

```bash
curl http://localhost:8083/api/v1/fraud-check/1
```

Response:
```json
{
  "isFraudster": false
}
```

### Send Notification (Direct)

```bash
curl -X POST http://localhost:8083/api/v1/notification \
  -H "Content-Type: application/json" \
  -d '{
    "toCustomerId": 1,
    "toCustomerName": "Jane Doe",
    "message": "Welcome to our platform!"
  }'
```

For complete API documentation, see [API.md](./API.md).

---

## Testing

### Run All Tests

```bash
# From project root
mvn test
```

### Run Tests for Specific Service

```bash
# Customer Service tests
mvn -pl customer test -Dtest=CustomerControllerTest

# Fraud Service tests
mvn -pl fraud test -Dtest=FraudControllerTest

# Notification Service tests
mvn -pl notification test -Dtest=NotificationControllerTest
```

### Test Reports

After running tests, view detailed reports:

```
customer/target/surefire-reports/
fraud/target/surefire-reports/
notification/target/surefire-reports/
```

### Test Architecture

Tests use **`@WebMvcTest`** with MockMvc — they:
- ✅ Do NOT require Docker or running services
- ✅ Do NOT require PostgreSQL or RabbitMQ
- ✅ Mock all external dependencies
- ✅ Run fast as true unit tests

> See [running_tests_guide.md](./running_tests_guide.md) for detailed testing documentation.

---

## Monitoring

### Zipkin Distributed Tracing

View request traces across all services:

```bash
open http://localhost:9411
```

Traces include:
- API Gateway routing
- Customer Service processing
- Fraud Service client call
- Database operations
- RabbitMQ message publish

### Eureka Service Registry

View registered service instances:

```bash
open http://localhost:8761
```

### RabbitMQ Management

Monitor queues, exchanges, and message flow:

```bash
open http://localhost:15672
# Username: guest
# Password: guest
```

### Health Endpoints

All services expose health checks:

```bash
curl http://localhost:8080/actuator/health  # Customer
curl http://localhost:8081/actuator/health  # Fraud
curl http://localhost:8082/actuator/health  # Notification
```

---

## Documentation

| Document | Purpose |
|----------|---------|
| [HLD.md](./HLD.md) | High-level architecture, data flow, and design decisions |
| [API.md](./API.md) | Complete API reference with examples |
| [running_tests_guide.md](./running_tests_guide.md) | Testing documentation |
| [AGENTS.md](./AGENTS.md) | Quick reference for AI coding assistants |

---

## Environment Configuration

### Spring Profiles

| Profile | Use Case |
|---------|----------|
| `default` | Local IDE development (localhost URLs) |
| `docker` | Container deployment (service names as hosts) |
| `kube` | Kubernetes deployment |

Set profile:

```bash
# Docker deployment
export SPRING_PROFILES_ACTIVE=docker

# Or via docker-compose
environment:
  - SPRING_PROFILES_ACTIVE=docker
```

### Key Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | — | Spring profile selection |
| `POSTGRES_USER` | `amigoscode` | PostgreSQL username |
| `POSTGRES_PASSWORD` | `password` | PostgreSQL password |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | `http://localhost:8761/eureka` | Eureka server URL |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ hostname |

---

## Troubleshooting

### Services fail to start

**Problem**: `Connection refused` to Eureka

**Solution**: Ensure Eureka Server is fully started before other services. Check:
```bash
curl http://localhost:8761/actuator/health
```

### Fraud check fails

**Problem**: Registration returns `500 Internal Server Error` with message "fraudster"

**Solution**: This is expected behavior. The fraud service flags certain emails as fraudulent. Try a different email domain.

### Database connection errors

**Problem**: `PSQLException: Connection refused`

**Solution**: Ensure PostgreSQL container is healthy:
```bash
docker-compose ps
docker-compose logs postgres
```

### RabbitMQ connection errors

**Problem**: `AmqpConnectException: connect timeout`

**Solution**: Verify RabbitMQ is running:
```bash
docker-compose ps rabbitmq
curl http://localhost:15672
```

---

## Contributing

This project serves as an educational reference architecture. Contributions are welcome for:

- Bug fixes
- Documentation improvements
- Additional test coverage
- Resilience patterns (circuit breakers, retries)
- Observability enhancements (metrics, alerting)

Please open an issue before submitting significant changes or framework upgrades.

---

## License

This project is released under the MIT License for educational purposes.

---

## Acknowledgments

This project was created by [Amigoscode](https://amigoscode.com) as a comprehensive microservices learning resource.

**Key Learning Outcomes:**
- Microservices architecture patterns
- Service discovery and load balancing
- Synchronous vs asynchronous communication
- Distributed tracing and observability
- Containerization and orchestration

---

<p align="center">
  <b>Built with ❤️ by the Amigoscode Community</b>
</p>
