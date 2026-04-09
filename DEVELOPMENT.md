# Development Guide

This guide explains how to set up an efficient local development environment for Amigosservices with hot-reload capabilities.

---

## Development Workflow Overview

We use a **hybrid development approach**:
- **Infrastructure services** run in Docker (PostgreSQL, RabbitMQ, Zipkin)
- **Application services** run in your IDE for fast iteration
- **Spring Boot DevTools** provides automatic restart on code changes

```
┌─────────────────────────────────────────────────────────────┐
│                     Your Machine                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                  IDE (IntelliJ/Eclipse)               │  │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌──────────┐   │  │
│  │  │ Customer│ │  Fraud  │ │Notification│ │ API GW  │   │  │
│  │  │ Service │ │ Service │ │  Service   │         │   │  │
│  │  │ (8080)  │ │ (8081)  │ │  (8082)    │ (8083)  │   │  │
│  │  └────┬────┘ └────┬────┘ └─────┬──────┘ └────┬────┘   │  │
│  │       │            │            │             │         │  │
│  │       └────────────┴────────────┴─────────────┘         │  │
│  │                    Spring Boot DevTools                  │  │
│  │              (Auto-restart on code changes)              │  │
│  └──────────────────────────────────────────────────────┘  │
│                              │                               │
│                              ▼                               │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                   Docker Containers                   │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐            │  │
│  │  │PostgreSQL│  │ RabbitMQ │  │  Zipkin  │            │  │
│  │  │  (5432)  │  │(5672/    │  │  (9411)  │            │  │
│  │  │          │  │ 15672)   │  │          │            │  │
│  │  └──────────┘  └──────────┘  └──────────┘            │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## Prerequisites

- Java 17 JDK
- Maven 3.8+
- Docker & Docker Compose
- IDE (IntelliJ IDEA recommended)

---

## Quick Start

### 1. Start Infrastructure

```bash
./start-infra.sh
```

This command:
- Starts PostgreSQL, RabbitMQ, Zipkin, and pgAdmin
- Waits for PostgreSQL to be healthy
- Creates the three application databases: `customer`, `fraud`, `notification`
- Verifies RabbitMQ and Zipkin are ready

**Expected output:**
```
==========================================
  Infrastructure Ready! 🚀
==========================================

Services:
  🐘 PostgreSQL    : localhost:5432 (amigoscode/password)
  🐰 RabbitMQ AMQP : localhost:5672
  🐰 RabbitMQ Mgmt : http://localhost:15672 (guest/guest)
  📊 Zipkin        : http://localhost:9411
  🗄️  pgAdmin       : http://localhost:5050
```

### 2. Import Project into IDE

**IntelliJ IDEA:**
1. File → Open → Select the project root folder
2. IntelliJ will detect the Maven project automatically
3. Wait for Maven import to complete (check bottom right status)

**VS Code:**
1. File → Open Folder → Select project root
2. Install "Extension Pack for Java" and "Spring Boot Extension Pack"

### 3. Start Services in Order

**Critical:** Services must start in this exact order:

1. **EurekaServerApplication** (`eureka-server` module)
   - Wait for: `"Started EurekaServerApplication in X.XXX seconds"`
   - Verify: http://localhost:8761 shows Eureka dashboard

2. **ApiGWApplication** (`apigw` module)
   - Should register with Eureka automatically
   - Verify: Appears in Eureka dashboard

3. **FraudApplication** (`fraud` module)
   - Port: 8081
   - Verify: http://localhost:8081/actuator/health returns `{"status":"UP"}`

4. **CustomerApplication** (`customer` module)
   - Port: 8080
   - Requires: Eureka, PostgreSQL, RabbitMQ
   - Verify: http://localhost:8080/actuator/health

5. **NotificationApplication** (`notification` module)
   - Port: 8082
   - Requires: Eureka, PostgreSQL, RabbitMQ
   - Verify: http://localhost:8082/actuator/health

**Pro tip:** In IntelliJ, you can create a Compound Run Configuration to start all services in sequence.

---

## Hot Reload with Spring Boot DevTools

### What Gets Auto-Restarted

Spring Boot DevTools monitors the classpath for changes and automatically restarts the application when it detects:

| Change Type | Restarts? | Notes |
|-------------|-----------|-------|
| Java source file | ✅ Yes | After compilation (Ctrl+Shift+F9) |
| Resource file (application.yml) | ✅ Yes | Immediately |
| Static resources (HTML, CSS, JS) | ❌ No | Served directly, no restart needed |
| Test files | ❌ No | Separate test runner |
| Dependencies (pom.xml) | ❌ No | Manual restart required |

### How to Trigger a Reload

**In IntelliJ IDEA:**

1. **Build Project** (Ctrl+F9) - Compiles changed files, triggers restart
2. **Build Module** (Ctrl+Shift+F9) - Compiles specific module only
3. **Auto-build** (Settings → Build → Compiler → Build project automatically)

**Typical workflow:**
```
1. Edit CustomerController.java
2. Press Ctrl+F9 (Build Project)
3. Service restarts automatically (~2-3 seconds)
4. Test your changes immediately
```

### DevTools Features

**Automatic Restart:**
```
2026-04-08 10:30:15.123  INFO 12345 --- [  restartedMain] c.a.customer.CustomerApplication         : Started CustomerApplication in 2.345 seconds
2026-04-08 10:30:45.456  INFO 12345 --- [  restartedMain] c.a.customer.CustomerApplication         : Started CustomerApplication in 2.123 seconds (JVM running for 30.234)
```

**LiveReload (Browser Extension):**
- DevTools includes a LiveReload server that can trigger browser refreshes
- Install the LiveReload browser extension
- Enable in `application.yml`: `spring.devtools.livereload.enabled: true`

**Remote Debug:**
- DevTools enables remote debugging by default
- Connect your IDE debugger to the running process for breakpoint debugging

---

## Debugging

### Local Debugging

Since services run in your IDE, debugging works seamlessly:

1. Set breakpoints in your code
2. Start service in Debug mode (Shift+F9 in IntelliJ)
3. Trigger the API call
4. Debugger stops at your breakpoint

**Debug a specific service:**
```bash
# Start other services normally
# Then debug CustomerApplication with IDE debugger on port 5005 (default)
```

### Remote Debugging (if needed)

Add to Run Configuration VM Options:
```
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
```

Each service should use a different port:
- Customer: 5005
- Fraud: 5006
- Notification: 5007

---

## Database Management

### Viewing Data

**Option 1: pgAdmin (Web UI)**
- URL: http://localhost:5050
- Login: pgadmin4@pgadmin.org / admin
- Add server: Hostname=`postgres`, Port=`5432`, Username=`amigoscode`, Password=`password`

**Option 2: Command Line**
```bash
# Connect to PostgreSQL
docker compose -f docker-compose.infra.yml exec postgres psql -U amigoscode -d customer

# List tables
\dt

# Query customers
SELECT * FROM customer;

# Exit
\q
```

**Option 3: IDE Database Tools**
- IntelliJ Database tool window
- VS Code with SQL extensions

### Database Schema Changes

The services use JPA/Hibernate with `ddl-auto: create-drop` by default, so:
- Schema is recreated on each service restart
- **Data is lost** when services restart
- For persistent data, change to `ddl-auto: update` in `application.yml`

---

## RabbitMQ Management

### Monitoring Messages

**Web UI:** http://localhost:15672 (guest/guest)

**Useful views:**
- **Queues**: See message backlog and consumer count
- **Exchanges**: View `internal.exchange` bindings
- **Channels**: Monitor active connections

**Command Line:**
```bash
# List queues
docker compose -f docker-compose.infra.yml exec rabbitmq rabbitmqctl list_queues

# List bindings
docker compose -f docker-compose.infra.yml exec rabbitmq rabbitmqctl list_bindings
```

### Testing Notifications

Publish a test message directly:
```bash
curl -X POST http://localhost:8080/api/v1/notification \
  -H "Content-Type: application/json" \
  -d '{
    "toCustomerId": 1,
    "toCustomerName": "Test User",
    "message": "Test notification"
  }'
```

Then check RabbitMQ UI to see the message flow.

---

## Testing Your Changes

### Run Unit Tests

```bash
# All tests
mvn test

# Single service
mvn -pl customer test

# Single test class
mvn -pl customer test -Dtest=CustomerControllerTest

# Single test method
mvn -pl customer test -Dtest="CustomerControllerTest#shouldRegisterCustomer"
```

### Integration Testing

After code changes, test the full flow:

```bash
# 1. Register a customer
curl -X POST http://localhost:8083/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Test",
    "lastName": "User",
    "email": "test@example.com"
  }'

# 2. Check fraud status
curl http://localhost:8083/api/v1/fraud-check/1

# 3. Check Zipkin for traces
open http://localhost:9411

# 4. Check RabbitMQ for notification events
open http://localhost:15672
```

---

## Troubleshooting

### DevTools Not Restarting

**Symptom:** Changes don't trigger restart

**Solutions:**
1. Ensure `spring-boot-devtools` is in the POM (check with `mvn dependency:tree`)
2. Build the project (Ctrl+F9) after changes
3. Check that file is in classpath (not excluded)
4. Verify DevTools is enabled: `spring.devtools.restart.enabled: true`

### Service Won't Start

**Symptom:** `Connection refused` or `Cannot execute request on any known server`

**Solutions:**
1. **Eureka not ready**: Start EurekaServerApplication first
2. **Database not ready**: Run `./start-infra.sh` and wait for completion
3. **Port already in use**: Check with `lsof -i :8080` and kill process

### Database Connection Errors

**Symptom:** `PSQLException: Connection to localhost:5432 refused`

**Solutions:**
1. Verify PostgreSQL is running: `docker compose -f docker-compose.infra.yml ps`
2. Check credentials in `application.yml` match docker-compose
3. Ensure database schema exists: `./start-infra.sh` creates them automatically

### RabbitMQ Connection Errors

**Symptom:** `AmqpConnectException: connect: Connection refused`

**Solutions:**
1. Verify RabbitMQ is running: `docker-compose -f docker-compose.infra.yml ps`
2. Check RabbitMQ health: `curl http://localhost:15672` (should show login page)
3. Restart infrastructure: `docker compose -f docker-compose.infra.yml restart rabbitmq`

### Port Conflicts

**Symptom:** `Port 8080 was already in use`

**Solutions:**
```bash
# Find process using port
lsof -i :8080

# Kill process
kill -9 <PID>

# Or use different port in application.yml
server.port=8085
```

---

## Performance Tips

### 1. Parallel Compilation

Enable parallel compilation in IntelliJ:
- Settings → Build → Compiler → Build project automatically ✅
- Settings → Build → Compiler → Compile independent modules in parallel ✅

### 2. Increase Memory

Add to `Help` → `Edit Custom VM Options` (IntelliJ):
```
-Xms1024m
-Xmx4096m
-XX:ReservedCodeCacheSize=512m
```

### 3. Exclude Directories

Mark these as excluded in IntelliJ:
- `target/`
- `.git/`
- `*.iml` files

### 4. Use Maven Wrapper

For consistent Maven versions:
```bash
mvn wrapper:wrapper
./mvnw clean install
```

---

## IDE-Specific Tips

### IntelliJ IDEA

**Recommended Plugins:**
- Spring Boot (bundled)
- Spring Assistant
- Lombok (bundled)
- Rainbow Brackets
- .env files support

**Run Configurations:**
1. Create Spring Boot run configurations for each service
2. Set active profiles: `--spring.profiles.active=default`
3. Group related services in folders

**Useful Shortcuts:**
- `Ctrl+F9` - Build project
- `Ctrl+Shift+F9` - Rebuild current file
- `Ctrl+Shift+F10` - Run current context
- `Shift+F9` - Debug
- `Ctrl+F2` - Stop

### VS Code

**Recommended Extensions:**
- Extension Pack for Java
- Spring Boot Extension Pack
- Lombok Annotations Support
- Maven for Java

**Launch Configuration:**
```json
{
  "type": "java",
  "name": "Customer Service",
  "request": "launch",
  "mainClass": "com.amigoscode.customer.CustomerApplication",
  "projectName": "customer",
  "args": "--spring.profiles.active=default"
}
```

---

## Next Steps

After setting up your development environment:

1. **Read the Architecture**: See [HLD.md](HLD.md)
2. **Explore the API**: See [API.md](API.md)
3. **Write Tests**: See [running_tests_guide.md](running_tests_guide.md)
4. **Check Code Style**: Follow existing patterns in the codebase

Happy coding! 🚀
