# Zipkin Distributed Tracing Guide

## Overview

This guide explains how distributed tracing works in the Amigosservices microservices architecture using **Spring Cloud Sleuth** and **Zipkin**.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [How Tracing Works](#how-tracing-works)
3. [Trace Flow Example](#trace-flow-example)
4. [Configuration](#configuration)
5. [Reading Traces in Zipkin UI](#reading-traces-in-zipkin-ui)
6. [Common Issues & Solutions](#common-issues--solutions)
7. [Best Practices](#best-practices)
8. [Advanced Topics](#advanced-topics)

---

## Architecture Overview

### System Components

```
┌─────────────────────────────────────────────────────────────────┐
│                         API Gateway                             │
│                     (Spring Cloud Gateway)                      │
│                         Port: 8083                              │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  • Receives external requests                           │    │
│  │  • Generates traceId if not present                     │    │
│  │  • Creates first span (Gateway Ingress)                 │    │
│  └─────────────────────────────────────────────────────────┘    │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTP + Trace Headers
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Customer Service                           │
│                         Port: 8080                              │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  • Extracts trace context from headers                  │    │
│  │  • Creates child span (Customer Processing)             │    │
│  │  • Propagates trace to:                                 │    │
│  │    - Fraud Service (via Feign/HTTP)                     │    │
│  │    - RabbitMQ (async message)                           │    │
│  └─────────────────────────────────────────────────────────┘    │
└──────────┬──────────────────────┬───────────────────────────────┘
           │                      │
           ▼                      ▼
┌─────────────────┐    ┌─────────────────┐
│  Fraud Service  │    │    RabbitMQ     │
│   Port: 8081    │    │   Port: 5672    │
└─────────────────┘    └────────┬────────┘
                                │ (async)
                                ▼ 
                       ┌─────────────────┐
                       │Notification Svc │
                       │   Port: 8082    │
                       └─────────────────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │     Zipkin      │
                       │   Port: 9411    │
                       │  (Trace Store)  │
                       └─────────────────┘
```

### Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Trace Generation | Spring Cloud Sleuth | Automatic span creation and context propagation |
| Trace Storage | Zipkin | Collects and stores trace data |
| Transport | HTTP | Sends spans to Zipkin (default) |
| Propagation | B3 Propagation | W3C standard for trace context |

---

## How Tracing Works

### 1. Trace Context Propagation

#### HTTP Communication (Synchronous)

When services communicate via HTTP (Feign Client), trace information is passed through headers:

```
┌─────────────────────────────────────────────────────────────────┐
│  Client Request to API Gateway                                  │
├─────────────────────────────────────────────────────────────────┤
│  GET /api/v1/customers HTTP/1.1                                 │
│  Host: localhost:8083                                           │
│                                                                 │
│  ← No trace headers = Gateway creates new trace                 │
│                                                                 │
│  Generated:                                                     │
│    X-B3-TraceId: 4f6a9b2c8d3e1f5a  (64-bit hex)                 │
│    X-B3-SpanId:  4f6a9b2c8d3e1f5a  (root span)                  │
│    X-B3-Sampled: 1               (1=sampled, 0=not sampled)     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Gateway → Customer Service (via Feign/Ribbon)                  │
├─────────────────────────────────────────────────────────────────┤
│  GET /api/v1/customers HTTP/1.1                                 │
│  Host: customer-service                                         │
│  X-B3-TraceId: 4f6a9b2c8d3e1f5a  ← Same traceId                 │
│  X-B3-SpanId:  9c8d7e6f5a4b3c2d  ← New spanId (child)           │
│  X-B3-ParentSpanId: 4f6a9b2c8d3e1f5a  ← Parent reference        │
│  X-B3-Sampled: 1                                                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Customer → Fraud Service (via Feign Client)                    │
├─────────────────────────────────────────────────────────────────┤
│  GET /api/v1/fraud-check/123 HTTP/1.1                           │
│  Host: fraud-service                                            │
│  X-B3-TraceId: 4f6a9b2c8d3e1f5a  ← Same traceId                 │
│  X-B3-SpanId:  2b3c4d5e6f7a8b9c  ← New spanId                   │
│  X-B3-ParentSpanId: 9c8d7e6f5a4b3c2d  ← Parent from Customer    │
│  X-B3-Sampled: 1                                                │
└─────────────────────────────────────────────────────────────────┘
```

#### RabbitMQ Communication (Asynchronous)

For async messaging, trace context is embedded in message headers:

```java
// Customer Service publishes message
rabbitMQMessageProducer.publish(
    notificationRequest,
    "internal.exchange",
    "internal.notification.routing-key"
);

// Spring Sleuth automatically adds headers to AMQP message:
// ┌─────────────────────────────────────────┐
// │ Message Properties:                     │
// │   headers: {                            │
// │     "X-B3-TraceId": "4f6a9b2c8d3e1f5a", │
// │     "X-B3-SpanId": "3d4e5f6a7b8c9d0e",  │
// │     "X-B3-ParentSpanId": "9c8d7e6f5...",│
// │     "X-B3-Sampled": "1"                 │
// │   }                                     │
// └─────────────────────────────────────────┘
```

```java
// Notification Service consumes message
@RabbitListener(queues = "${rabbitmq.queues.notification}")
public void consumer(NotificationRequest request) {
    // Sleuth extracts headers and continues the trace
    // New span created: "rabbitmq:listener"
}
```

### 2. Automatic Span Creation

Spring Cloud Sleuth automatically creates spans for:

| Operation | Span Name | Tags Included |
|-----------|-----------|---------------|
| HTTP Incoming | `http:get:/api/v1/customers` | http.method, http.path, http.status_code |
| HTTP Outgoing (Feign) | `http:get` | http.method, http.url, peer.service |
| Database | `hibernate` | db.type, db.statement (if configured) |
| RabbitMQ Publish | `rabbitmq:template:convertAndSend` | exchange, routingKey |
| RabbitMQ Consume | `rabbitmq:listener` | queue, messageId |

### 3. Trace Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    Trace Data Lifecycle                         │
└─────────────────────────────────────────────────────────────────┘

1. REQUEST RECEIVED
   Service: API Gateway
   Action: Sleuth creates TraceContext
   ┌─────────────────────────────────────┐
   │ traceId:  abc123...                 │
   │ spanId:   abc123... (root)          │
   │ parentId: null                      │
   │ sampled:  true                      │
   └─────────────────────────────────────┘
            │
            ▼
2. SPAN STARTED
   Sleuth creates Span object
   ┌─────────────────────────────────────┐
   │ name: http:get:/api/v1/customers    │
   │ startTime: 1712645123456            │
   │ tags: {http.method: GET, ...}       │
   └─────────────────────────────────────┘
            │
            ▼
3. REQUEST PROCESSED
   Service processes request
   May create child spans for operations
            │
            ▼
4. SPAN FINISHED
   Sleuth calculates duration
   ┌─────────────────────────────────────┐
   │ duration: 150ms                     │
   │ tags: {http.status_code: 200}       │
   │ annotations: ["Server Received",    │
   │              "Server Sent"]         │
   └─────────────────────────────────────┘
            │
            ▼
5. SPAN REPORTED
   AsyncReporter sends to Zipkin
   POST http://zipkin:9411/api/v2/spans
            │
            ▼
6. TRACE STORED
   Zipkin indexes and stores trace
   Available for querying in UI
```

---

## Trace Flow Example

### Scenario: Customer Registration

```bash
curl -X POST http://localhost:8083/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{"firstName":"John","lastName":"Doe","email":"john@example.com"}'
```

### Step-by-Step Trace Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ STEP 1: API Gateway (Port 8083)                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│ Operation: Receive HTTP POST /api/v1/customers                              │
│                                                                             │
│ Span Details:                                                               │
│   Span ID:     4f6a9b2c8d3e1f5a                                             │
│   Trace ID:    4f6a9b2c8d3e1f5a                                             │
│   Parent ID:   null (root span)                                             │
│   Name:        http:post:/api/v1/customers                                  │
│   Service:     api-gateway                                                  │
│   Duration:    ~5ms                                                         │
│   Tags:                                                                     │
│     • http.method: POST                                                     │
│     • http.path: /api/v1/customers                                          │
│     • http.status_code: 200                                                 │
│     • http.route: /api/v1/customers                                         │
│                                                                             │
│ Action: Route to Customer Service                                           │
│         Propagate headers via Eureka service discovery                      │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ STEP 2: Customer Service (Port 8080)                                        │
├─────────────────────────────────────────────────────────────────────────────┤
│ Operation: Process customer registration                                    │
│                                                                             │
│ Span Details:                                                               │
│   Span ID:     9c8d7e6f5a4b3c2d                                             │
│   Trace ID:    4f6a9b2c8d3e1f5a (inherited)                                 │
│   Parent ID:   4f6a9b2c8d3e1f5a (Gateway span)                              │
│   Name:        http:post:/api/v1/customers                                  │
│   Service:     customer                                                     │
│   Duration:    ~120ms (total)                                               │
│                                                                             │
│ Child Spans Created:                                                        │
│                                                                             │
│ 2a. Database Operation (Hibernate)                                          │
│     Span ID:   a1b2c3d4e5f6a7b8                                             │
│     Parent ID: 9c8d7e6f5a4b3c2d                                             │
│     Name:      hibernate:query                                              │
│     Duration:  ~30ms                                                        │
│     Tags:      db.type: sql, db.statement: INSERT INTO customer...          │
│                                                                             │
│ 2b. Fraud Check (Feign Client)                                              │
│     Span ID:   2b3c4d5e6f7a8b9c                                             │
│     Parent ID: 9c8d7e6f5a4b3c2d                                             │
│     Name:      http:get                                                     │
│     Duration:  ~50ms                                                        │
│     Tags:      http.url: http://fraud:8081/api/v1/fraud-check/1             │
│                peer.service: fraud                                          │
│                http.status_code: 200                                        │
│                                                                             │
│ 2c. RabbitMQ Publish                                                        │
│     Span ID:   3d4e5f6a7b8c9d0e                                             │
│     Parent ID: 9c8d7e6f5a4b3c2d                                             │
│     Name:      rabbitmq:template:convertAndSend                             │
│     Duration:  ~10ms                                                        │
│     Tags:      exchange: internal.exchange                                  │
│                routingKey: internal.notification.routing-key                │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                    ┌─────────────────┴─────────────────┐
                    ▼                                   ▼
┌────────────────────────────────┐  ┌────────────────────────────────────────┐
│ STEP 3a: Fraud Service         │  │ STEP 3b: RabbitMQ (Async)              │
├────────────────────────────────┤  ├────────────────────────────────────────┤
│ Port: 8081                     │  │ Port: 5672                             │
│                                │  │                                        │
│ Span Details:                  │  │ • Message queued with trace headers    │
│   Span ID:   5c6d7e8f9a0b1c2d  │  │ • Notification Service will consume    │
│   Trace ID:  4f6a9b2c8d3e1f5a  │  │                                        │
│   Parent ID: 2b3c4d5e6f7a8b9c  │  └────────────────────────────────────────┘
│   Name:      http:get:/api/v1/fraud-check/{customerId}                     │
│   Service:   fraud                                                         │
│   Duration:  ~40ms                                                         │
│                                                                            │
│ Child Span:                                                                │
│   Database query to fraud_check_history                                    │
│   Duration: ~15ms                                                          │
└────────────────────────────────────────────────────────────────────────────┘
                                      │ (async, seconds later)
                                      ▼ 
┌─────────────────────────────────────────────────────────────────────────────┐
│ STEP 4: Notification Service (Port 8082)                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│ Operation: Consume message from RabbitMQ                                    │
│                                                                             │
│ Span Details:                                                               │
│   Span ID:     6e7f8a9b0c1d2e3f                                             │
│   Trace ID:    4f6a9b2c8d3e1f5a (extracted from message headers)            │
│   Parent ID:   3d4e5f6a7b8c9d0e (RabbitMQ publish span)                     │
│   Name:        rabbitmq:listener                                            │
│   Service:     notification                                                 │
│   Duration:    ~25ms                                                        │
│   Tags:        queue: notification.queue                                    │
│                                                                             │
│ Child Span:                                                                 │
│   Database INSERT into notification table                                   │
│   Duration: ~10ms                                                           │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ ZIPKIN TRACE COMPLETE                                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│ Trace ID: 4f6a9b2c8d3e1f5a                                                  │
│ Total Duration: ~200ms                                                      │
│ Services Involved: 4 (gateway, customer, fraud, notification)               │
│ Total Spans: 9                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Visual Representation in Zipkin UI

```
Trace: 4f6a9b2c8d3e1f5a (200ms) ▼

api-gateway                    [████████████████████] 5ms
└── customer                   [████████████████████████████████████████████████████████] 120ms
    ├── hibernate:query        [██████████] 30ms
    ├── http:get (fraud)       [████████████████] 50ms
    │   └── fraud              [████████████████████] 40ms
    │       └── hibernate      [██████] 15ms
    └── rabbitmq:publish       [████] 10ms
        └── notification       [██████████] 25ms (async, +2s)
            └── hibernate      [████] 10ms

Services: gateway (1) → customer (4) → fraud (2) → notification (2)
```

---

## Configuration

### Current Configuration

**Location:** `application.yml` (each service)

```yaml
spring:
  zipkin:
    base-url: http://localhost:9411  # Default profile
  sleuth:
    sampler:
      probability: 1.0  # Sample 100% of requests (dev only!)
```

**Docker Profile:** `application-docker.yml`

```yaml
spring:
  zipkin:
    base-url: http://zipkin:9411  # Service name in Docker network
```

### Configuration Properties Explained

| Property | Default | Description |
|----------|---------|-------------|
| `spring.zipkin.base-url` | - | URL of Zipkin server |
| `spring.zipkin.sender.type` | `web` | Transport: web (HTTP), kafka, rabbit |
| `spring.sleuth.sampler.probability` | `0.1` | Percentage of requests to trace (0.0-1.0) |
| `spring.sleuth.async.enabled` | `true` | Trace async operations |
| `spring.sleuth.messaging.enabled` | `true` | Trace messaging (RabbitMQ/Kafka) |
| `spring.sleuth.feign.enabled` | `true` | Trace Feign client calls |
| `spring.sleuth.web.enabled` | `true` | Trace web requests |

### Recommended Production Configuration

```yaml
spring:
  zipkin:
    base-url: http://zipkin:9411
    sender:
      type: kafka  # Use Kafka for high throughput
  sleuth:
    sampler:
      probability: 0.1  # Sample 10% of requests in production
    propagation:
      type: w3c,b3  # Support both W3C and B3 formats
logging:
  pattern:
    level: "%5p [%X{traceId:-},%X{spanId:-}]"  # Add traceId to logs
```

---

## Reading Traces in Zipkin UI

### Accessing Zipkin

```bash
# Local development
http://localhost:9411

# Docker
http://localhost:9411 (mapped to container port)
```

### Zipkin UI Components

#### 1. Trace Query Page

```
┌───────────────────────────────────────────────────────────────────┐
│  Zipkin                                                        [?]│
├───────────────────────────────────────────────────────────────────┤
│  Filter Traces                                                    │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌────────────┐   │
│  │ Service     │ │ Span Name   │ │ Lookback    │ │ Duration   │   │
│  │ [customer ▼]│ │ [All      ▼]│ │ [1 hour   ▼]│ │ [>10ms   ▼]│   │
│  └─────────────┘ └─────────────┘ └─────────────┘ └────────────┘   │
│                                                                   │
│  [Find Traces]                                                    │
│                                                                   │
│  Results: 15 traces                                               │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │ ▼ Trace: 4f6a9b2c... (200ms) - 9 spans                      │  │
│  │   gateway [█] customer [████████] fraud [██] notification[█]│  │
│  │   10:30:45  Duration: 200ms  Services: 4                    │  │
│  ├─────────────────────────────────────────────────────────────┤  │
│  │ ▶ Trace: 8e7d6c5b... (150ms) - 6 spans                      │  │
│  │   gateway [█] customer [██████] fraud [██]                  │  │
│  │   10:30:42  Duration: 150ms  Services: 3                    │  │
│  └─────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────┘
```

#### 2. Trace Detail View

```
┌─────────────────────────────────────────────────────────────────┐
│  ← Back to Traces              Trace: 4f6a9b2c8d3e1f5a          │
├─────────────────────────────────────────────────────────────────┤
│  Duration: 200ms  Started: 2026-04-09 10:30:45.123              │
│  Services: 4  Spans: 9  Depth: 4                                │
│                                                                 │
│  [Download JSON] [Share Trace]                                  │
│                                                                 │
│  Timeline View:                                                 │
│  Time →  0ms    50ms   100ms   150ms   200ms                    │
│  ─────────────────────────────────────────────────────────────  │
│  gateway       [██]                                             │
│  └─customer    [████████████████████████████████████████]       │
│    ├─hibernate   [██████████]                                   │
│    ├─fraud       [████████████████]       (fraud service)       │
│    │ └─fraud       [████████████████████]                       │
│    │   └─hibernate   [██████]                                   │
│    └─rabbitmq    [████]                                         │
│      └─notification  [██████████]       (+2s async gap)         │
│        └─hibernate     [████]                                   │
│                                                                 │
│  Service Dependencies:                                          │
│  gateway → customer → fraud                                     │
│                    ↘ notification                               │
└─────────────────────────────────────────────────────────────────┘
```

#### 3. Span Details Panel

Click any span to see:

```
┌─────────────────────────────────────────────────────────┐
│ Span: http:post:/api/v1/customers                       │
│ Service: customer                                       │
│ Duration: 120ms                                         │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ Tags:                                                   │
│   http.method        POST                               │
│   http.path          /api/v1/customers                  │
│   http.status_code   200                                │
│   http.route         /api/v1/customers                  │
│   mvc.controller.class    CustomerController            │
│   mvc.controller.method   registerCustomer              │
│                                                         │
│ Annotations:                                            │
│   Server Receive    10:30:45.123                        │
│   Server Send       10:30:45.243                        │
│                                                         │
│ Process IDs:                                            │
│   traceId:  4f6a9b2c8d3e1f5a                            │
│   spanId:   9c8d7e6f5a4b3c2d                            │
│   parentId: 4f6a9b2c8d3e1f5a                            │
└─────────────────────────────────────────────────────────┘
```

### Key Metrics to Watch

| Metric | Good | Warning | Bad |
|--------|------|---------|-----|
| Total Trace Duration | < 500ms | 500ms-2s | > 2s |
| Service Call Latency | < 50ms | 50-200ms | > 200ms |
| Database Query Time | < 20ms | 20-100ms | > 100ms |
| Async Delay | < 5s | 5-30s | > 30s |

---

## Common Issues & Solutions

### Issue 1: Missing Traces

**Symptom:** Request completes but no trace appears in Zipkin

**Diagnosis Steps:**

```bash
# 1. Check if Zipkin is running
curl http://localhost:9411/health

# 2. Check if services are reporting spans
# Look for errors in service logs:
docker compose logs customer | grep -i zipkin
docker compose logs fraud | grep -i zipkin
```

**Common Causes & Fixes:**

| Cause | Check | Fix |
|-------|-------|-----|
| Sampling too low | `spring.sleuth.sampler.probability: 0.001` | Set to `1.0` for dev |
| Wrong Zipkin URL | `spring.zipkin.base-url` | Verify host and port |
| Firewall blocking | Network connectivity | Open port 9411 |
| Async reporter full | High throughput | Use Kafka sender |

**Debug Logging:**

```yaml
# application.yml
logging:
  level:
    org.springframework.cloud.sleuth: DEBUG
    org.springframework.cloud.zipkin: DEBUG
```

### Issue 2: Broken Span Chain

**Symptom:** Spans appear in Zipkin but aren't connected (multiple root spans)

```
❌ Broken Chain:
Trace 1: gateway → customer → (stops)
Trace 2: fraud (separate trace)
Trace 3: notification (separate trace)

✅ Correct Chain:
Trace 1: gateway → customer → fraud
                     ↘ notification
```

**Common Causes:**

1. **Missing Feign Sleuth Integration**
   ```java
   // Ensure Feign clients are annotated properly
   @FeignClient(name = "fraud", url = "${clients.fraud.url}")
   public interface FraudClient { ... }
   ```

2. **Async Operations Not Traced**
   ```java
   // Problem: @Async methods lose trace context
   @Async
   public void processAsync() { ... }
   
   // Solution: Use Sleuth's tracing executor
   @Bean
   public Executor tracingExecutor(BeanFactory beanFactory) {
       ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
       // ... configure executor
       return new LazyTraceExecutor(beanFactory, executor);
   }
   ```

3. **Manual HTTP Calls Without Headers**
   ```java
   // Problem: RestTemplate without interceptor
   RestTemplate restTemplate = new RestTemplate();
   
   // Solution: Use traced RestTemplate
   @Bean
   @LoadBalanced
   public RestTemplate restTemplate() {
       return new RestTemplate();
   }
   // Sleuth auto-adds interceptor for @LoadBalanced RestTemplate
   ```

### Issue 3: Async Messaging Traces Not Connected

**Symptom:** RabbitMQ consumer shows as separate trace

**Diagnosis:**

```bash
# Check if trace headers are in message
docker compose exec rabbitmq rabbitmqctl list_queues
docker compose exec rabbitmq rabbitmqctl get_queue internal.notification 1
```

**Fix:**

```java
// Ensure Sleuth messaging is enabled
// application.yml
spring:
  sleuth:
    messaging:
      rabbit:
        enabled: true
```

### Issue 4: High Memory Usage

**Symptom:** Services consume too much memory with tracing enabled

**Causes:**
- Sampling rate too high in production (1.0)
- Large payload logging
- Long-running spans

**Solutions:**

```yaml
# 1. Reduce sampling in production
spring:
  sleuth:
    sampler:
      probability: 0.1  # Sample 10%
    
# 2. Exclude health checks
spring:
  sleuth:
    web:
      skip-pattern: "/actuator.*|/health.*|/ping"

# 3. Limit payload sizes
spring:
  sleuth:
    log:
      slf4j:
        enabled: true
```

---

## Best Practices

### 1. Always Include Trace IDs in Logs

```yaml
# application.yml
logging:
  pattern:
    level: "%5p [%X{traceId:-},%X{spanId:-}] [%t] %c{0} - %m%n"
```

**Example Output:**
```
INFO [4f6a9b2c8d3e1f5a,9c8d7e6f5a4b3c2d] [http-nio-8080-exec-1] CustomerService - Processing customer registration
```

### 2. Add Custom Tags for Business Context

```java
@Service
@AllArgsConstructor
public class CustomerService {
    private final Tracer tracer;
    
    public void registerCustomer(CustomerRegistrationRequest request) {
        // Add custom tags
        tracer.currentSpan()
            .tag("customer.email", request.email())
            .tag("customer.name", request.firstName() + " " + request.lastName())
            .tag("business.operation", "customer_registration");
        
        // Process...
    }
}
```

### 3. Use Baggage for Cross-Service Context

```java
// Propagate context across services without changing method signatures
// Customer Service:
BaggageField.create("customer.tier").updateValue("premium");

// Fraud Service (automatically receives):
String customerTier = BaggageField.create("customer.tier").getValue();
```

### 4. Handle Errors Properly

```java
@Service
public class CustomerService {
    private final Tracer tracer;
    
    public void registerCustomer(CustomerRegistrationRequest request) {
        try {
            // ... processing
        } catch (Exception e) {
            // Add error tag to span
            Span span = tracer.currentSpan();
            if (span != null) {
                span.tag("error", "true");
                span.tag("error.message", e.getMessage());
                span.tag("error.type", e.getClass().getSimpleName());
            }
            throw e;
        }
    }
}
```

### 5. Monitor Trace Quality

```bash
# Check trace completeness
curl "http://localhost:9411/api/v2/traces?limit=10" | jq '.[].traceId' | sort | uniq -c

# Look for orphaned spans (spans without root)
curl "http://localhost:9411/api/v2/traces?limit=100" | jq '.[] | select(length == 1)' 
```

---

## Advanced Topics

### 1. Custom Span Creation

```java
@Service
public class FraudCheckService {
    private final Tracer tracer;
    
    public FraudCheckResponse checkFraud(Integer customerId) {
        // Create a custom span
        Span fraudCheckSpan = tracer.nextSpan()
            .name("fraud.heuristic.check")
            .tag("customer.id", String.valueOf(customerId))
            .start();
        
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(fraudCheckSpan)) {
            // Perform fraud checks
            boolean isFraudulent = runHeuristics(customerId);
            
            fraudCheckSpan.tag("fraud.detected", String.valueOf(isFraudulent));
            
            return new FraudCheckResponse(isFraudulent);
        } catch (Exception e) {
            fraudCheckSpan.error(e);
            throw e;
        } finally {
            fraudCheckSpan.end();
        }
    }
}
```

### 2. Tracing Database Queries

```yaml
# Enable detailed SQL tracing (development only)
spring:
  jpa:
    properties:
      hibernate:
        format_sql: true
  sleuth:
    jdbc:
      enabled: true  # Add spring-cloud-sleuth-jdbc dependency
```

### 3. Kafka Transport for High Throughput

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

```yaml
spring:
  zipkin:
    sender:
      type: kafka
  kafka:
    bootstrap-servers: localhost:9092
```

### 4. Sampling Strategies

```java
@Bean
public Sampler customSampler() {
    // Sample based on HTTP method
    return new HttpRequestSampler() {
        @Override
        public Boolean trySample(HttpRequest request) {
            // Always trace POST/PUT/DELETE
            if (Arrays.asList("POST", "PUT", "DELETE")
                .contains(request.method())) {
                return true;
            }
            // Sample 10% of GET requests
            return ThreadLocalRandom.current().nextDouble() < 0.1;
        }
    };
}
```

---

## Quick Reference

### Debugging Commands

```bash
# Check Zipkin health
curl http://localhost:9411/health

# Get recent traces
curl "http://localhost:9411/api/v2/traces?limit=10" | jq

# Get specific trace
curl "http://localhost:9411/api/v2/trace/4f6a9b2c8d3e1f5a" | jq

# Check services reporting
curl "http://localhost:9411/api/v2/services" | jq

# Get dependencies
curl "http://localhost:9411/api/v2/dependencies?endTs=$(date +%s)000&lookback=86400000" | jq
```

### Trace Header Format

| Header | Format | Example |
|--------|--------|---------|
| `X-B3-TraceId` | 64-bit or 128-bit hex | `4f6a9b2c8d3e1f5a` |
| `X-B3-SpanId` | 64-bit hex | `9c8d7e6f5a4b3c2d` |
| `X-B3-ParentSpanId` | 64-bit hex | `4f6a9b2c8d3e1f5a` |
| `X-B3-Sampled` | 0 or 1 | `1` |
| `X-B3-Flags` | 64-bit hex (debug) | `1` |

---

## Migration to Micrometer Tracing (Spring Boot 3.x)

When you upgrade to Spring Boot 3.x, Sleuth will be replaced by Micrometer Tracing:

```xml
<!-- Old (Spring Boot 2.x) -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>

<!-- New (Spring Boot 3.x) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

Configuration changes:
```yaml
# Old
spring.sleuth.sampler.probability: 1.0

# New
management.tracing.sampling.probability: 1.0
```

---

**For questions or issues, check:**
- Zipkin UI: http://localhost:9411
- Service logs: `docker compose logs <service-name>`
- Spring Sleuth docs: https://spring.io/projects/spring-cloud-sleuth
