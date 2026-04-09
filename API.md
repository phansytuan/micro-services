# Amigosservices API Reference

## Overview

This document provides comprehensive API specifications for the Amigosservices microservices platform. All external traffic flows through the **API Gateway** (port 8083).

**Base URL:** `http://localhost:8083`  
**Content-Type:** `application/json`  
**Version:** 1.0

---

## Quick Reference

| Endpoint | Method | Service | Description |
|----------|--------|---------|-------------|
| `/api/v1/customers` | POST | Customer | Register new customer |
| `/api/v1/fraud-check/{customerId}` | GET | Fraud | Check fraud status |
| `/api/v1/notification` | POST | Notification | Send notification |

---

## 1. Customer API

### 1.1 Register Customer

Creates a new customer account with integrated fraud validation and async notification.

**Endpoint:** `POST /api/v1/customers`

#### Request

**Headers:**
```
Content-Type: application/json
```

**Request Body:**

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `firstName` | string | Yes | 1-100 chars | Customer first name |
| `lastName` | string | Yes | 1-100 chars | Customer last name |
| `email` | string | Yes | Valid email format | Unique email address |

**Example Request:**
```json
{
  "firstName": "Jane",
  "lastName": "Doe",
  "email": "jane.doe@example.com"
}
```

#### Response

**Success Response (200 OK):**

Returns empty body on success. The response is immediate after:
- Customer record persisted
- Fraud check passed (synchronous)
- Notification event published (asynchronous)

**Error Responses:**

| Status | Error Code | Description |
|--------|-----------|-------------|
| 400 | `VALIDATION_ERROR` | Invalid request body (malformed JSON or missing fields) |
| 409 | `CONFLICT` | Email already registered |
| 422 | `FRAUD_DETECTED` | Customer flagged as fraudster by Fraud Service |
| 500 | `INTERNAL_ERROR` | Unexpected server error |
| 503 | `SERVICE_UNAVAILABLE` | Fraud Service unavailable |

**Example Error Response (422):**
```json
{
  "timestamp": "2026-04-08T10:30:00.000+00:00",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Customer registration rejected: fraudster",
  "path": "/api/v1/customers"
}
```

#### Business Rules

1. **Email Uniqueness**: Email must be unique across the system
2. **Fraud Validation**: Synchronous blocking call to Fraud Service
3. **Notification**: Async message published to RabbitMQ on success
4. **Transaction**: Customer record created regardless of notification delivery

#### Example Usage

```bash
# Successful registration
curl -X POST http://localhost:8083/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Jane",
    "lastName": "Doe",
    "email": "jane.doe@example.com"
  }'

# Response: 200 OK (empty body)
```

```bash
# Fraudster detected
curl -X POST http://localhost:8083/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Suspicious",
    "lastName": "User",
    "email": "suspicious@example.com"
  }'

# Response: 422 Unprocessable Entity
# Body: {"timestamp":"...","status":422,"error":"Unprocessable Entity","message":"fraudster","path":"/api/v1/customers"}
```

---

## 2. Fraud API

### 2.1 Check Fraud Status

Evaluates whether a customer is flagged as fraudulent. Primarily consumed internally by Customer Service.

**Endpoint:** `GET /api/v1/fraud-check/{customerId}`

#### Request

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `customerId` | integer | Yes | Unique customer identifier |

#### Response

**Success Response (200 OK):**

| Field | Type | Description |
|-------|------|-------------|
| `isFraudster` | boolean | `true` if customer is flagged, `false` otherwise |

**Example Response:**
```json
{
  "isFraudster": false
}
```

**Error Responses:**

| Status | Error Code | Description |
|--------|-----------|-------------|
| 400 | `BAD_REQUEST` | Invalid customerId format (non-integer) |
| 404 | `NOT_FOUND` | Customer ID not found in system |

#### Business Rules

1. **Audit Trail**: Every check is logged to `fraud_check_history` table
2. **Deterministic**: Currently uses simple heuristics (e.g., email domain patterns)
3. **Read-Only**: This endpoint doesn't modify fraud status

#### Example Usage

```bash
# Check legitimate customer
curl http://localhost:8083/api/v1/fraud-check/1

# Response:
# {"isFraudster": false}
```

```bash
# Check fraudster
curl http://localhost:8083/api/v1/fraud-check/2

# Response:
# {"isFraudster": true}
```

---

## 3. Notification API

### 3.1 Send Notification

Dispatches a notification to a customer. Primarily invoked asynchronously via RabbitMQ; HTTP endpoint available for testing.

**Endpoint:** `POST /api/v1/notification`

#### Request

**Headers:**
```
Content-Type: application/json
```

**Request Body:**

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `toCustomerId` | integer | Yes | Positive integer | Target customer ID |
| `toCustomerName` | string | Yes | 1-200 chars | Customer name or identifier for display |
| `message` | string | Yes | 1-1000 chars | Notification content |

**Example Request:**
```json
{
  "toCustomerId": 1,
  "toCustomerName": "Jane Doe",
  "message": "Welcome to Amigosservices! Your registration is complete."
}
```

#### Response

**Success Response (200 OK):**

Returns empty body. The notification is persisted and queued for delivery.

**Error Responses:**

| Status | Error Code | Description |
|--------|-----------|-------------|
| 400 | `VALIDATION_ERROR` | Invalid request body |
| 404 | `CUSTOMER_NOT_FOUND` | Customer ID doesn't exist |
| 500 | `INTERNAL_ERROR` | Failed to persist notification |

#### Async Behavior

When triggered via RabbitMQ (normal flow):

1. Customer Service publishes event to `internal.exchange`
2. Notification Service consumes from `internal.notification` queue
3. Notification is persisted and dispatched
4. Message acknowledged after successful processing

**Queue Configuration:**
- **Exchange**: `internal.exchange` (topic)
- **Routing Key**: `internal.notification.routing-key`
- **Queue**: `internal.notification`
- **Durability**: Durable

#### Example Usage

```bash
# Direct HTTP call (testing only)
curl -X POST http://localhost:8083/api/v1/notification \
  -H "Content-Type: application/json" \
  -d '{
    "toCustomerId": 1,
    "toCustomerName": "Jane Doe",
    "message": "Your account has been verified."
  }'

# Response: 200 OK (empty body)
```

---

## 4. Common Patterns

### 4.1 Error Response Format

All errors follow RFC 7807-inspired structure:

```json
{
  "timestamp": "2026-04-08T10:30:00.000+00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for object='customerRegistrationRequest'",
  "path": "/api/v1/customers",
  "errors": [
    {
      "field": "email",
      "message": "must be a well-formed email address"
    }
  ]
}
```

### 4.2 HTTP Status Codes

| Code | Usage |
|------|-------|
| 200 | Successful GET or POST with response body |
| 201 | Resource created (future implementations) |
| 204 | Successful POST without response body |
| 400 | Malformed request or validation error |
| 401 | Authentication required (future) |
| 403 | Permission denied (future) |
| 404 | Resource not found |
| 409 | Resource conflict (e.g., duplicate email) |
| 422 | Business rule violation (e.g., fraudster) |
| 500 | Unexpected server error |
| 503 | Service unavailable or timeout |

### 4.3 Timeout Guidelines

| Call Type | Timeout | Notes |
|-----------|---------|-------|
| Customer → Fraud | 5 seconds | Synchronous, blocks registration |
| Gateway → Services | 30 seconds | Default Spring Cloud Gateway |
| RabbitMQ Publish | 10 seconds | Async, non-blocking |

---

## 5. Architecture Context

### Service Communication

```
┌─────────────────────────────────────────────────────────────────┐
│                         API Gateway                              │
│                         (Port 8083)                              │
└─────────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│    Customer     │  │     Fraud       │  │  Notification   │
│    Service      │  │    Service      │  │    Service      │
│   (Port 8080)   │  │   (Port 8081)   │  │   (Port 8082)   │
└─────────────────┘  └─────────────────┘  └─────────────────┘
          │                   │                   │
          │                   ▼                   ▼
          │           ┌─────────────────┐  ┌─────────────────┐
          └──────────>│   PostgreSQL    │  │    RabbitMQ     │
             (Feign)  │   (fraud DB)    │  │  (async queue)  │
                      └─────────────────┘  └─────────────────┘
                              ▲
          ┌───────────────────┴───────────────────┐
          ▼                                       ▼
┌─────────────────┐                      ┌─────────────────┐
│   PostgreSQL    │                      │   PostgreSQL    │
│  (customer DB)  │                      │ (notification DB)│
└─────────────────┘                      └─────────────────┘
```

### Request Flow: Customer Registration

```
1. Client ──POST /api/v1/customers──> API Gateway
                                       │
                                       ▼
2.                            Customer Service
                                       │
                       ┌───────────────┴───────────────┐
                       ▼                               ▼
3.              PostgreSQL (save)            Fraud Service
                       │                               │
                       │                       PostgreSQL (log)
                       │                               │
                       └───────────┬───────────────────┘
                                   ▼
4.                         RabbitMQ (publish)
                                   │
                                   ▼
5.                        Notification Service
                                   │
                                   ▼
6.                        PostgreSQL (save)
```

---

## 6. Testing & Development

### Local Testing

```bash
# 1. Start infrastructure
docker compose up -d postgres rabbitmq zipkin

# 2. Start services in order:
#    EurekaServerApplication → ApiGWApplication → FraudApplication → CustomerApplication → NotificationApplication

# 3. Test customer registration
curl -X POST http://localhost:8083/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Test",
    "lastName": "User",
    "email": "test@example.com"
  }'

# 4. Verify fraud check
curl http://localhost:8083/api/v1/fraud-check/1

# 5. Check RabbitMQ management console
open http://localhost:15672  # guest/guest
```

### Health Checks

All services expose Actuator endpoints:

```bash
# Check service health
curl http://localhost:8080/actuator/health    # Customer
curl http://localhost:8081/actuator/health    # Fraud
curl http://localhost:8082/actuator/health    # Notification
curl http://localhost:8761/actuator/health    # Eureka
```

---

## 7. Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-04-08 | Initial API release |
