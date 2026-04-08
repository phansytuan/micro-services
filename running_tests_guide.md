# How to Run the API Controller Tests

## Overview

These are **unit tests** using `@WebMvcTest` + MockMvc + Mockito.  
They do **not** require a running database, RabbitMQ, or Eureka server — all external dependencies are mocked.

---

## Prerequisites

| Requirement | Version | Check Command |
|---|---|---|
| Java (JDK) | 17 | `java -version` |
| Maven | 3.6+ | `mvn -version` |

> [!NOTE]
> No Docker, no Postgres, no RabbitMQ needed. The `@WebMvcTest` slice starts a minimal Spring context — only the web layer.

---

## One-Time Setup

This was already applied to the project. Listed here for reference.

### 1. Parent POM — Surefire Plugin (already done ✅)

The default Maven Surefire `2.12.4` does **not** support JUnit 5.  
`2.22.2` was added to `pom.xml` (parent) `<pluginManagement>`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>2.22.2</version>
</plugin>
```

This applies to **all modules** automatically — no per-service change needed.

### 2. Test dependencies (already in parent POM ✅)

`spring-boot-starter-test` is declared in the parent `<dependencies>` block, 
which pulls in JUnit 5, Mockito, AssertJ, and MockMvc for every module.

---

## Running the Tests

All commands are run from the **project root**:

```
/home/tuandev/java-projects/microservices-ready/project/
```

---

### Run a Single Service

```bash
# Customer service — 8 tests
mvn -pl customer test -Dtest=CustomerControllerTest

# Fraud service — 8 tests
mvn -pl fraud test -Dtest=FraudControllerTest

# Notification service — 9 tests
mvn -pl notification test -Dtest=NotificationControllerTest
```

### Run All Three Services Together

```bash
mvn -pl customer,fraud,notification test
```

### Run a Single Test Method

```bash
# Example: run only TC-FRAUD-001
mvn -pl fraud test -Dtest="FraudControllerTest#shouldReturnFalseForLegitimateCustomer"
```

### Run All Tests in the Entire Project

```bash
mvn test
```

> [!WARNING]  
> Running `mvn test` at the root will also attempt to run tests in other modules (e.g. `eureka-server`, `apigw`) which have no tests. This is safe — they will simply report `Tests run: 0`.

---

## Expected Output

A passing run looks like this:

```
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

A failing run shows:

```
[ERROR] Tests run: 8, Failures: 1, Errors: 0, Skipped: 0
[ERROR] shouldReturnFalseForLegitimateCustomer  Time elapsed: 0.012 s  <<< FAILURE!
```

---

## Test Files Location

| Service | Test File |
|---|---|
| Customer | `customer/src/test/java/com/amigoscode/customer/CustomerControllerTest.java` |
| Fraud | `fraud/src/test/java/com/amigoscode/fraud/FraudControllerTest.java` |
| Notification | `notification/src/test/java/com/amigoscode/notification/NotificationControllerTest.java` |

---

## Running from IntelliJ IDEA

1. Open the project root in IntelliJ.
2. Right-click on any test file → **Run 'CustomerControllerTest'**  
   *(or use the green ▶ gutter icon next to any `@Test` method)*
3. The test runner tab shows pass/fail per test method with coloured indicators.

> [!TIP]
> IntelliJ automatically detects `maven-surefire-plugin` version from the POM, so no extra IDE configuration is needed.

---

## Surefire HTML Reports

After a test run, HTML reports are generated per module at:

```
customer/target/surefire-reports/
fraud/target/surefire-reports/
notification/target/surefire-reports/
```

Open `com.amigoscode.customer.CustomerControllerTest.txt` (or `.xml`) to see 
per-test timing and failure detail.

---

## Test Design Notes

| Behaviour | Why |
|---|---|
| No DB / Eureka / RabbitMQ needed | `@WebMvcTest` loads only the web layer; services are `@MockBean` |
| `eureka.client.enabled=false` in `@WebMvcTest` properties | Prevents Eureka autoconfiguration from failing the context load |
| Exception tests use `assertThrows(NestedServletException.class, ...)` | In `@WebMvcTest` without a `@ControllerAdvice`, MockMvc re-throws unhandled controller exceptions rather than returning HTTP 500 |
