# Distributed Tracing with Zipkin

This guide explains how request tracing works across services using Zipkin.

---

## Overview

**Zipkin** helps you see how requests flow through your microservices. When a customer registers, you can trace the entire journey:

```
Request Flow:
Client → Gateway → Customer → Fraud (HTTP)
                     ↓
                RabbitMQ → Notification (async)
```

Each hop is recorded as a **span**, and all spans with the same **trace ID** form a complete **trace**.

---

## Quick Start

### 1. Access Zipkin UI

Open http://localhost:9411 in your browser.

### 2. Make a Request

```bash
curl -X POST http://localhost:8083/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Test","lastName":"User","email":"test@example.com"}'
```

### 3. View the Trace

In Zipkin UI:
1. Select service `customer` (or `gateway`)
2. Click **Find Traces**
3. Click on a trace to see the full flow

---

## How Tracing Works

### Automatic Span Creation

Spring Cloud Sleuth automatically creates spans for:

| Operation | Span Name Example |
|-----------|-------------------|
| HTTP Incoming | `http:post:/api/v1/customers` |
| HTTP Outgoing (Feign) | `http:get` |
| Database | `hibernate` |
| RabbitMQ Publish | `rabbitmq:template:convertAndSend` |
| RabbitMQ Consume | `rabbitmq:listener` |

### Trace Propagation

Trace information passes between services via HTTP headers:

```
Client → Gateway:
  (no headers - Gateway creates new trace)
  X-B3-TraceId: 4f6a9b2c8d3e1f5a
  X-B3-SpanId: 4f6a9b2c8d3e1f5a

Gateway → Customer:
  X-B3-TraceId: 4f6a9b2c8d3e1f5a (same trace)
  X-B3-SpanId: 9c8d7e6f5a4b3c2d (new span)
  X-B3-ParentSpanId: 4f6a9b2c8d3e1f5a

Customer → Fraud:
  X-B3-TraceId: 4f6a9b2c8d3e1f5a (same trace)
  X-B3-SpanId: 2b3c4d5e6f7a8b9c (new span)
  X-B3-ParentSpanId: 9c8d7e6f5a4b3c2d
```

---

## Example Trace

Here's what a customer registration trace looks like:

```
Trace: 4f6a9b2c8d3e1f5a (200ms)
├── api-gateway          [5ms]  ← Request enters
└── customer            [120ms] ← Main processing
    ├── hibernate        [30ms] ← Save to database
    ├── http:get         [50ms] ← Call Fraud Service
    │   └── fraud        [40ms]
    │       └── hibernate[15ms]
    └── rabbitmq:publish [10ms] ← Send to RabbitMQ
        └── notification [25ms] (2 seconds later)
            └── hibernate[10ms]
```

**Visual in Zipkin:**
```
Time →  0ms    50ms   100ms   150ms   200ms
────────────────────────────────────────────
api-gateway  [██]
└─customer   [████████████████████████████]
  ├─db         [██████████]
  ├─fraud      [████████████████]
  └─rabbitmq   [████]
    └─notif       [██████████] (+2s)
```

---

## Configuration

### Default (application.yml)

```yaml
spring:
  zipkin:
    base-url: http://localhost:9411
  sleuth:
    sampler:
      probability: 1.0  # Trace 100% of requests (dev only!)
```

### Docker (application-docker.yml)

```yaml
spring:
  zipkin:
    base-url: http://zipkin:9411  # Docker service name
```

### Key Settings

| Property | Default | Description |
|----------|---------|-------------|
| `spring.zipkin.base-url` | - | Zipkin server URL |
| `spring.sleuth.sampler.probability` | 0.1 | % of requests to trace (0.0-1.0) |

---

## Using the Zipkin UI

### Find Traces

1. Go to http://localhost:9411
2. Select a **Service** (e.g., `customer`)
3. Set **Lookback** time (e.g., "1 hour")
4. Click **Find Traces**

### Read a Trace

Click on any trace to see:

- **Timeline** - How long each step took
- **Dependencies** - Service call graph
- **Tags** - Details like HTTP method, status code
- **Span details** - Click any span for more info

### Key Metrics to Watch

| Metric | Good | Warning | Bad |
|--------|------|---------|-----|
| Total Duration | < 500ms | 500ms-2s | > 2s |
| Service Call | < 50ms | 50-200ms | > 200ms |
| Database Query | < 20ms | 20-100ms | > 100ms |

---

## Troubleshooting

### No Traces Appearing

**Check 1: Is Zipkin running?**
```bash
curl http://localhost:9411/health
```

**Check 2: Is sampling enabled?**
```yaml
spring:
  sleuth:
    sampler:
      probability: 1.0  # Set to 1.0 for development
```

**Check 3: Check service logs**
```bash
docker compose logs customer | grep -i zipkin
```

### Broken Trace Chain

If spans appear but aren't connected (separate traces):

**Problem:** Missing trace headers in HTTP calls

**Solution:** Use Spring's `@FeignClient` - it automatically propagates headers:

```java
@FeignClient(name = "fraud")
public interface FraudClient {
    @GetMapping("/api/v1/fraud-check/{customerId}")
    FraudCheckResponse isFraudster(@PathVariable("customerId") Integer customerId);
}
```

### RabbitMQ Traces Not Connected

**Problem:** Consumer shows as separate trace

**Solution:** Ensure Sleuth messaging is enabled (it is by default):
```yaml
spring:
  sleuth:
    messaging:
      rabbit:
        enabled: true
```

---

## Adding Trace IDs to Logs

Add trace IDs to your log output for easier debugging:

```yaml
# application.yml
logging:
  pattern:
    level: "%5p [%X{traceId:-},%X{spanId:-}]"
```

**Log output:**
```
INFO [4f6a9b2c8d3e1f5a,9c8d7e6f5a4b3c2d] CustomerService - Processing registration
```

---

## Production Configuration

For production, reduce sampling and add exclusions:

```yaml
spring:
  zipkin:
    base-url: http://zipkin:9411
  sleuth:
    sampler:
      probability: 0.1  # Sample only 10% of requests
    web:
      skip-pattern: "/actuator.*|/health.*"  # Skip health checks
```

---

## Quick Reference Commands

```bash
# Check Zipkin health
curl http://localhost:9411/health

# Get recent traces
curl "http://localhost:9411/api/v2/traces?limit=10"

# Get specific trace
curl "http://localhost:9411/api/v2/trace/TRACE_ID_HERE"

# List services reporting
curl "http://localhost:9411/api/v2/services"
```

---

## Additional Resources

- **Zipkin UI:** http://localhost:9411
- **Spring Sleuth Docs:** https://spring.io/projects/spring-cloud-sleuth
- **Trace Headers:**
  - `X-B3-TraceId` - Unique trace identifier
  - `X-B3-SpanId` - Current span identifier
  - `X-B3-ParentSpanId` - Parent span (null for root)
  - `X-B3-Sampled` - 1=sampled, 0=not sampled
