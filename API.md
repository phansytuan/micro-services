# Amigosservices: API Documentation

## Overview

The Amigosservices project exposes RESTful endpoints across three core microservices: **Customer Service**, **Fraud Service**, and **Notification Service**. A centralized **API Gateway** acts as the primary reverse proxy bridging external clients to internal clusters.

- **Base URL**: `http://<apigw-host>:8083` for all external interactions.
- **Content Type**: `application/json`
- **Error Format**: Standard Spring Boot error response schema unless bypassed.

---

## 1. Customer Service

The Customer service acts as the primary access point for onboarding users to the application. 

### Register a New Customer
Registers a prospective customer. As part of its synchronous lifecycle, it contacts the Fraud Service to assess the prospect before finalizing registration.

- **URL**: `/api/v1/customers`
- **Method**: `POST`
- **Description**: Registers a new customer into the system, runs fraud validation, and asynchronously dispatches a welcome notification.

#### Request 
- **Headers**: `Content-Type: application/json`
- **Body Parameters**:
  - `firstName` *(string, required)* - The first name of the customer.
  - `lastName` *(string, required)* - The last name of the customer.
  - `email` *(string, required)* - A valid email address.

**Request Example**:
```json
{
  "firstName": "Alex",
  "lastName": "Doe",
  "email": "alex.doe@example.com"
}
```

#### Responses
- **200 OK**: Registration was successful. (No body returned)
- **400 Bad Request**: Malformed JSON or missing required arguments.
- **500 Internal Server Error**: Thrown when fraud checks flag the user (`IllegalStateException: fraudster`) or downstream connectivity fails.

---

## 2. Fraud Service

The Fraud Service evaluates customers against internal heuristics/records to determine if they are legitimate or fraudulent. Internally consumed primarily by the Customer Service.

### Fraud Detection Check
Determines if a customer ID belongs to a known or suspected fraudster.

- **URL**: `/api/v1/fraud-check/{customerId}`
- **Method**: `GET`
- **Description**: Evaluates fraud status uniquely tied to a specific `customerId`.

#### Request
- **Path Parameters**:
  - `customerId` *(integer, required)* - The unique identifier of the customer being evaluated.

**Request Example**:
```http
GET /api/v1/fraud-check/1
```

#### Responses
- **200 OK**: Evaluated successfully.
  
**Response Example**:
```json
{
  "isFraudster": false
}
```
- **400 Bad Request**: Integer validation failed for the ID format.

---

## 3. Notification Service

Provides asynchronous messaging endpoints (and RabbitMQ integrations) to dispatch communications to users. Note: While available over HTTP, primary system usage is asynchronous via AMQP.

### Send a Notification
Submits a plain text notification block to be stored and processed by the engine.

- **URL**: `/api/v1/notification`
- **Method**: `POST`
- **Description**: Instructs the system to dispatch and securely log a notification to the customer.

#### Request 
- **Headers**: `Content-Type: application/json`
- **Body Parameters**:
  - `toCustomerId` *(integer, required)* - The targeted customer's ID.
  - `toCustomerName` *(string, required)* - Name/alias for styling the payload.
  - `message` *(string, required)* - The notification text/content.

**Request Example**:
```json
{
  "toCustomerId": 1,
  "toCustomerName": "alex.doe@example.com",
  "message": "Hi Alex, welcome to Amigoscode..."
}
```

#### Responses
- **200 OK**: Notification stored and dispatch scheduled. (No body returned)
- **400 Bad Request**: Invalid body payload mapping.

---

## Standard Error Response Format

A traditional un-handled fault fallback managed by `ResponseEntityExceptionHandler`.

**Example (500 Internal Error)**:
```json
{
  "timestamp": "2026-04-08T08:00:00.000+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "message": "fraudster",
  "path": "/api/v1/customers"
}
```
