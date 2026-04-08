# Amigosservices: Microservices Project

![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![JavaVersion](https://img.shields.io/badge/java-17-orange)
![Spring Boot](https://img.shields.io/badge/spring_boot-2.5.7-green)
![Spring Cloud](https://img.shields.io/badge/spring_cloud-2020.0.3-blue)

## 📌 Project Overview
Amigosservices is a modern, distributed backend application demonstrating a complete microservices architecture. It simulates an onboarding ecosystem capable of registering new users, proactively scanning identities for fraud, and asynchronously dispatching notification events—all handled via loosely coupled, highly scalable standalone services.

## 🏛 Architecture Overview
The system relies heavily on a hybrid architecture implementing both synchronous (REST) and asynchronous (AMQP) patterns.
- External traffic routes through a unified **API Gateway**.
- Internal service discovery operates via **Eureka** allowing dynamic service scaling.
- Heavy validation pipelines (like checking fraud status) run via **OpenFeign HTTP** requests.
- Downstream tasks (like database notification logging) publish to via **RabbitMQ Exchanges** to decouple workloads from the primary transactional lane.
- Every major business boundary operates on an infrastructure-isolated **PostgreSQL Database** instance schema.

![Microservices Architecture Diagram](https://user-images.githubusercontent.com/40702606/144061535-7a42e85b-59d6-4f7f-9c35-18a48b49e6de.png)

*(See [HLD.md](./HLD.md) for a detailed architecture diagram and breakdown).*

## 🛠 Tech Stack
- **Languages**: Java 17
- **Frameworks**: Spring Boot (2.5.7), Spring Cloud (2020.0.3)
- **Database**: PostgreSQL
- **Message Broker**: RabbitMQ
- **Service Discovery**: Spring Cloud Netflix Eureka
- **API Gateway**: Spring Cloud Gateway
- **Tracing / Observability**: Zipkin & Spring Cloud Sleuth
- **Tooling**: Docker, Docker Compose, Maven, pgAdmin

## 📦 Project Structure
This environment holds multiple submodules orchestrated under a parent `pom.xml`:

| Module           | Description                                                                                          |
|------------------|------------------------------------------------------------------------------------------------------|
| `eureka-server`  | The Registry where all service instances register themselves.                                        |
| `apigw`          | API Gateway acting as a single entry point directing HTTP requests to respective service portfolios. |
| `customer`       | Core domain service managing customer entity registrations.                                          |
| `fraud`          | Standalone scanner preventing malicious user activations based on historical checking.               |
| `notification`   | Background AMQP consumer interpreting event streams to fire-off alerts and system messages.          |
| `clients`        | A shared, declarative `OpenFeign` library enabling strongly-typed inter-service synchronous calls.   |
| `amqp`           | A shared configuration utility library governing Queue/Exchange topological setups.                  |

## 🚀 Setup Instructions

### Prerequisites
- [Java 17 JDK](https://adoptium.net/) installed
- [Apache Maven](https://maven.apache.org/) (minimum v3.8+)
- [Docker](https://www.docker.com/) & [Docker Compose](https://docs.docker.com/compose/)

### Full Docker Environment (Recommended)
You can optionally spin up the entirety of the architecture including databases via the compose file.

1. Ensure the parent POM compiles everything cleanly:
   ```bash
   mvn clean install
   ```
2. Build the Docker Images:
   ```bash
   mvn spring-boot:build-image
   ```
   *(Alternatively, run the bash executable if configured: `./build-and-run.sh`)*
3. Spin up the cluster:
   ```bash
   docker-compose up -d
   ```

### Local Development (Manual Services)
If modifying code directly, run the infrastructure dependencies detached, then start apps via IDE:
1. Start required backing persistence maps:
   ```bash
   docker-compose up -d postgres pgadmin zipkin rabbitmq
   ```
2. Run microservices manually from your IDE or terminal prioritizing this boot order: 
   - `EurekaServerApplication` (Wait for startup)
   - `ApiGWApplication` 
   - `FraudApplication`, `CustomerApplication`, `NotificationApplication`

## 📖 API Documentation
For standard integrations, all endpoints route dynamically through the API Gateway defaulting on `http://localhost:8083`. 
Extensive details covering payloads and schemas are provided in the [API.md](./API.md) artifact.

## 💻 Example Usage
To create a customer and watch the full Sync-to-Async propagation cycle:

```bash
curl -X POST http://localhost:8083/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Elon",
    "lastName": "Musk",
    "email": "elon.musk@mars.com"
  }'
```

**Expected Result**:
1. The **Customer Data** commits to the User Postgres.
2. The **Fraud Tracker** validates the schema without failing via Feign.
3. The **Notification Server** writes an async log picking up the RabbitMQ topic from `internal.exchange`.

## ⚙ Environment Variables
*The Docker container environments dictate override profiles. To override manually, ensure these standard spring props match:*

| Variable Key                 | Purpose                                  | Fallback Value                                    |
|------------------------------|------------------------------------------|---------------------------------------------------|
| `SPRING_PROFILES_ACTIVE`     | Sets deployment variables mapping.       | Typically omitted locally or set to `docker`.      |
| `POSTGRES_USER`              | DB root user auth.                       | `amigoscode`                                      |
| `POSTGRES_PASSWORD`          | DB secure root password.                 | `password`                                        |

## 🤝 Contribution Guidelines
This project acts primarily as an architecture showcase. Please open a discussion issue before raising any pull requests focused purely on framework upgrades.

## 📄 License
This project is typically released by Amigoscode under standard MIT permissive usages for demonstrational setups. *(Verify upstream rights)*
