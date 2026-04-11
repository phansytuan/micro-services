## OpenFeign Client Guide

Simple guide to HTTP clients in this project.

---

### What is OpenFeign?

**OpenFeign** lets you call other services using simple Java interfaces. No need to write HTTP code manually.

### Without Feign (Complex)
```java
// Lots of code to make an HTTP call
RestTemplate restTemplate = new RestTemplate();
String url = "http://localhost:8081/api/v1/fraud-check/1";
ResponseEntity<FraudCheckResponse> response = 
    restTemplate.getForEntity(url, FraudCheckResponse.class);
return response.getBody();
```

### With Feign (Simple)
```java
// Just call a method
FraudCheckResponse response = fraudClient.isFraudster(1);
```

---

## Our Feign Clients

We have **2 clients** defined, but only **1 is used**:

| Client | Calls Service | Used? | Purpose |
|--------|--------------|-------|---------|
| **FraudClient** | Fraud Service | ✅ Yes | Check if customer is fraudulent |
| **NotificationClient** | Notification Service | ❌ No | Available but unused (we use RabbitMQ instead) |

---

## FraudClient

### What It Does

When a customer registers, we need to check if they're a fraudster. This is done **synchronously** - we must wait for the answer before continuing.

### The Code

**Location:** `clients/src/main/java/com/amigoscode/clients/fraud/FraudClient.java`

```java
@FeignClient(
    name = "fraud",
    url = "${clients.fraud.url}"
)
public interface FraudClient {

    @GetMapping("api/v1/fraud-check/{customerId}")
    FraudCheckResponse isFraudster(
        @PathVariable("customerId") Integer customerId
    );
}
```

### Response Object

```java
public record FraudCheckResponse(Boolean isFraudster) {}
```

**Example response:**
```json
{
  "isFraudster": false
}
```

### How It's Used

**Location:** `customer/src/main/java/com/amigoscode/customer/CustomerService.java`

```java
@Service
@AllArgsConstructor
public class CustomerService {
    
    private final FraudClient fraudClient;
    
    public void registerCustomer(CustomerRegistrationRequest request) {
        // 1. Save customer to database
        Customer customer = Customer.builder()
            .firstName(request.firstName())
            .lastName(request.lastName())
            .email(request.email())
            .build();
        customerRepository.saveAndFlush(customer);
        
        // 2. Check if fraudster (THIS IS THE FEIGN CALL)
        FraudCheckResponse fraudCheckResponse = 
            fraudClient.isFraudster(customer.getId());
        
        // 3. If fraudster, throw error
        if (fraudCheckResponse.isFraudster()) {
            throw new IllegalStateException("fraudster");
        }
        
        // 4. Send notification via RabbitMQ
        // ...
    }
}
```

### Step-by-Step Flow

```
1. Customer Service calls: fraudClient.isFraudster(1)
                           ↓
2. Feign creates HTTP request: GET http://fraud:8081/api/v1/fraud-check/1
                           ↓
3. Fraud Service receives request
   └── Saves to fraud_check_history table
                           ↓
4. Fraud Service responds: {"isFraudster": false}
                           ↓
5. Feign converts JSON to Java object: FraudCheckResponse
                           ↓
6. Customer Service receives response and continues
```

---

## Configuration

### Service URLs

We use different URLs for different environments:

**Local Development** (`clients-default.properties`):
```properties
clients.fraud.url=http://localhost:8081
```

**Docker** (`clients-docker.properties`):
```properties
clients.fraud.url=http://fraud:8081
```

**Kubernetes** (`clients-kube.properties`):
```properties
clients.fraud.url=http://fraud:8081
```

### How to Enable Feign

**Location:** `customer/src/main/java/com/amigoscode/customer/CustomerApplication.java`

```java
@SpringBootApplication
@EnableFeignClients(
    basePackages = "com.amigoscode.clients"  // Scan for Feign interfaces
)
public class CustomerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CustomerApplication.class, args);
    }
}
```

---

## NotificationClient (Unused)

### What It Is

Defined but never actually used. The system uses **RabbitMQ** for notifications instead.

```java
@FeignClient(
    name = "notification",
    url = "${clients.notification.url}"
)
public interface NotificationClient {

    @PostMapping("api/v1/notification")
    void sendNotification(NotificationRequest request);
}
```

### Why We Don't Use It

| HTTP Call (Feign) | RabbitMQ |
|-------------------|----------|
| Caller waits for response | Fire and forget |
| If notification fails, registration fails | Registration succeeds even if notification is delayed |
| Slower (blocking) | Faster (non-blocking) |

**Current approach:**
```
Customer Service → RabbitMQ → Notification Service
```

**Alternative (available but unused):**
```
Customer Service → NotificationClient → Notification Service
```

---

## Testing

### Direct HTTP Test

```bash
# Test Fraud Service directly
curl http://localhost:8081/api/v1/fraud-check/1

# Response:
{"isFraudster": false}
```

### Register Customer (Full Flow)

```bash
# This will call FraudClient internally
curl -X POST http://localhost:8080/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Test",
    "lastName": "User",
    "email": "test@example.com"
  }'
```

### Mock in Tests

```java
@WebMvcTest(CustomerController.class)
public class CustomerControllerTest {
    
    @MockBean
    private FraudClient fraudClient;
    
    @Test
    public void shouldRegisterCustomer() {
        // Mock the Feign client
        when(fraudClient.isFraudster(any()))
            .thenReturn(new FraudCheckResponse(false));
        
        // Test the registration...
    }
}
```

---

## Error Handling

### What Happens When Things Go Wrong

| Problem | What Happens |
|---------|-------------|
| Fraud Service is down | Exception thrown, customer registration fails |
| Fraud Service is slow | Waits 60 seconds (default), then fails |
| Fraudster detected | `IllegalStateException("fraudster")` thrown |

### Default Timeouts

| Timeout | Default | Description |
|---------|---------|-------------|
| Connect | 10 seconds | Time to establish connection |
| Read | 60 seconds | Time to wait for response |

---

## Improvements We Should Make

### 1. Add Circuit Breaker

**Problem:** If Fraud Service is down, nobody can register.

**Solution:** Use Resilience4j to return a safe default when Fraud Service is down.

```java
@FeignClient(
    name = "fraud",
    url = "${clients.fraud.url}",
    fallback = FraudClientFallback.class
)
public interface FraudClient {
    
    @CircuitBreaker(name = "fraud", fallbackMethod = "fallback")
    @GetMapping("api/v1/fraud-check/{customerId}")
    FraudCheckResponse isFraudster(@PathVariable Integer customerId);
}

@Component
public class FraudClientFallback implements FraudClient {
    @Override
    public FraudCheckResponse isFraudster(Integer customerId) {
        // If Fraud Service is down, assume legitimate customer
        return new FraudCheckResponse(false);
    }
}
```

### 2. Configure Shorter Timeouts

**Problem:** 60 seconds is too long to wait.

**Solution:** Add to `application.yml`:

```yaml
feign:
  client:
    config:
      default:
        connectTimeout: 5000    # 5 seconds
        readTimeout: 10000      # 10 seconds
```

### 3. Add Retry Logic

**Problem:** Temporary network glitches fail the request.

**Solution:** Retry 2-3 times before giving up.

```java
@Retry(name = "fraud")
@GetMapping("api/v1/fraud-check/{customerId}")
FraudCheckResponse isFraudster(@PathVariable Integer customerId);
```

### 4. Enable Logging

**Problem:** Hard to debug when things go wrong.

**Solution:** Add to `application.yml`:

```yaml
feign:
  client:
    config:
      default:
        loggerLevel: full

logging:
  level:
    com.amigoscode.clients.fraud.FraudClient: DEBUG
```

**Log output:**
```
[FeignClient] ---> GET http://fraud:8081/api/v1/fraud-check/1 HTTP/1.1
[FeignClient] <--- HTTP/1.1 200 OK (45ms)
```

### 5. Use Eureka Instead of Hardcoded URLs

**Problem:** URLs are hardcoded in properties files.

**Solution:** Let Eureka provide the URL:

```java
@FeignClient(name = "fraud")  // No URL - Eureka finds it
public interface FraudClient {
    // ...
}
```

**Benefits:**
- Automatic load balancing
- No URL configuration needed
- Works with multiple instances

### 6. Remove or Document NotificationClient

Since it's unused:
- **Option A:** Delete the file
- **Option B:** Add comment: "Available for testing only"

---

## Quick Reference

### Injecting a Feign Client

```java
@Service
@AllArgsConstructor
public class CustomerService {
    private final FraudClient fraudClient;  // Spring injects automatically
}
```

### Making a Call

```java
// Simple method call - Feign handles HTTP
FraudCheckResponse response = fraudClient.isFraudster(customerId);

if (response.isFraudster()) {
    throw new IllegalStateException("fraudster");
}
```

### Checking if Feign is Working

```bash
# 1. Check Fraud Service is running
curl http://localhost:8081/actuator/health

# 2. Test the endpoint
curl http://localhost:8081/api/v1/fraud-check/1

# 3. Check Eureka for registered services
curl http://localhost:8761/eureka/apps
```

---

## Summary

| Question | Answer |
|----------|--------|
| **How many Feign clients?** | 2 defined, 1 used |
| **Which one is used?** | FraudClient |
| **What does it do?** | Checks if customer is fraudulent |
| **Where is it configured?** | `clients-*.properties` files |
| **What improvements needed?** | Circuit breaker, timeouts, retry, logging |

**Key Point:** Feign makes HTTP calls as simple as calling a Java method!
