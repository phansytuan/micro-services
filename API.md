# Amigosservices API Reference

Complete API documentation for the microservices platform.

**Base URL:** `http://localhost:8083`  
**Content-Type:** `application/json`

---

## Quick Reference

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/customers` | POST | Register new customer |
| `/api/v1/fraud-check/{customerId}` | GET | Check fraud status |
| `/api/v1/notification` | POST | Send notification (testing) |

---

## 1. Register Customer

Creates a new customer with fraud validation and async notification.

```http
POST /api/v1/customers
```

### Request Body

```json
{
  "firstName": "Jane",
  "lastName": "Doe",
  "email": "jane.doe@example.com"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `firstName` | string | Yes | Customer first name |
| `lastName` | string | Yes | Customer last name |
| `email` | string | Yes | Valid email address |

### Responses

**Success (200 OK)**
- Empty response body
- Customer created in database
- Fraud check passed
- Notification queued (async)

**Error (500 Internal Server Error)**
```json
{
  "timestamp": "2026-04-08T10:30:00.000+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "message": "fraudster",
  "path": "/api/v1/customers"
}
```
This means the customer was flagged as fraudulent.

### Example Usage

```bash
# Register a customer
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

# Response: 500 Internal Server Error
# Body: {"message":"fraudster", ...}
```

### What Happens

1. **Saves customer** to PostgreSQL
2. **Calls Fraud Service** via HTTP (blocking)
3. **Publishes event** to RabbitMQ (non-blocking, async)
4. **Returns 200** immediately on success

---

## 2. Check Fraud Status

Checks if a customer is flagged as fraudulent.

```http
GET /api/v1/fraud-check/{customerId}
```

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `customerId` | integer | Yes | Customer ID to check |

### Response

**Success (200 OK)**
```json
{
  "isFraudster": false
}
```

| Field | Type | Description |
|-------|------|-------------|
| `isFraudster` | boolean | `true` if fraudulent, `false` otherwise |

### Example Usage

```bash
# Check customer 1
curl http://localhost:8083/api/v1/fraud-check/1

# Response: {"isFraudster": false}
```

---

## 3. Send Notification

Sends a notification to a customer. Mainly used for testing; normally triggered via RabbitMQ.

```http
POST /api/v1/notification
```

### Request Body

```json
{
  "toCustomerId": 1,
  "toCustomerName": "Jane Doe",
  "message": "Welcome to Amigosservices!"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `toCustomerId` | integer | Yes | Target customer ID |
| `toCustomerName` | string | Yes | Customer name for display |
| `message` | string | Yes | Notification content |

### Response

**Success (200 OK)**
- Empty response body
- Notification saved to database

### Example Usage

```bash
# Send notification
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

## Asynchronous Flow

When a customer registers successfully, this happens:

```
Customer Service          RabbitMQ           Notification Service
     │                       │                       │
     │ ─────── Publish ─────>│                       │
     │   exchange: internal    │                       │
     │   routing: internal     │                       │
     │                       │ ─────── Consume ─────>│
     │                       │                       │ ─── Save to DB
     │                       │                       │ ─── Send email
```

### Message Queue Configuration

- **Exchange:** `internal.exchange` (topic)
- **Routing Key:** `internal.notification.routing-key`
- **Queue:** `internal.notification`

---

## Service Ports

For direct access (bypassing API Gateway):

| Service | Direct Port | Through Gateway |
|---------|-------------|-----------------|
| Customer | 8080 | `localhost:8083/api/v1/customers` |
| Fraud | 8081 | `localhost:8083/api/v1/fraud-check/{id}` |
| Notification | 8082 | `localhost:8083/api/v1/notification` |

---

## Testing

### Register and Verify

```bash
# 1. Register a customer
curl -X POST http://localhost:8083/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Test",
    "lastName": "User",
    "email": "test@example.com"
  }'

# 2. Check fraud status (customer ID is typically 1)
curl http://localhost:8083/api/v1/fraud-check/1

# 3. Check RabbitMQ for messages
open http://localhost:15672  # guest/guest
```

### Health Checks

```bash
# Check all services
curl http://localhost:8080/actuator/health  # Customer
curl http://localhost:8081/actuator/health  # Fraud
curl http://localhost:8082/actuator/health  # Notification
curl http://localhost:8761/actuator/health  # Eureka
```

---

## HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | Success |
| 400 | Bad request (validation error) |
| 404 | Resource not found |
| 500 | Internal server error (including "fraudster") |
| 503 | Service unavailable |

---

## Architecture

```
┌─────────┐
│ Client  │
└────┬────┘
     │ POST /api/v1/customers
     ▼
┌──────────────┐
│ API Gateway  │
└──────┬───────┘
       │ Route to Customer Service
       ▼
┌──────────────────────────────────────┐
│         Customer Service             │
│  ├─ Save to PostgreSQL               │
│  ├─ Call Fraud Service (HTTP/Feign)  │
│  └─ Publish to RabbitMQ              │
└──────────────────────────────────────┘
```
