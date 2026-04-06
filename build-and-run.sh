#!/bin/bash
set -e

echo "======================================"
echo "  Microservices - Build & Run Script  "
echo "======================================"

# Step 1: Build all service Docker images using Jib (no Docker daemon required for build)
echo ""
echo ">>> Step 1: Building Docker images with Maven + Jib..."
echo "    (This builds images directly into your local Docker daemon)"
echo ""

mvn clean package -DskipTests jib:dockerBuild

echo ""
echo ">>> Step 1 complete. Images built:"
docker images | grep amigoscode

# Step 2: Start infrastructure first
echo ""
echo ">>> Step 2: Starting infrastructure services..."
echo ""

docker compose up -d postgres rabbitmq zipkin pgadmin

# Step 3: Wait for PostgreSQL and create service databases
echo ""
echo ">>> Step 3: Waiting for PostgreSQL to become healthy..."
echo ""

until [ "$(docker inspect -f '{{.State.Health.Status}}' postgres 2>/dev/null)" = "healthy" ]; do
  sleep 2
done

create_db_if_missing() {
  local db_name=$1
  docker compose exec -T postgres psql -U amigoscode -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='${db_name}'" | grep -q 1 \
    || docker compose exec -T postgres psql -U amigoscode -d postgres -c "CREATE DATABASE \"${db_name}\";" > /dev/null
}

echo ">>> Creating application databases if missing..."
create_db_if_missing customer
create_db_if_missing fraud
create_db_if_missing notification

# Step 4: Start application services
echo ""
echo ">>> Step 4: Starting application services..."
echo ""

docker compose up -d eureka-server apigw customer fraud notification

# Step 5: Wait for services to start
echo ""
echo ">>> Step 5: Waiting for services to start (60s)..."
sleep 60

# Step 6: Show status
echo ""
echo ">>> Step 6: Container status:"
docker compose ps

# Step 7: Verify key endpoints
echo ""
echo ">>> Step 7: Verifying endpoints..."
echo ""

check_url() {
  local name=$1
  local url=$2
  local status
  status=$(curl -s -L -o /dev/null -w "%{http_code}" --max-time 5 "$url" || true)

  if [[ "$status" =~ ^(200|204|301|302|307|308|401|403|404)$ ]]; then
    echo "  ✅  $name is reachable at $url (HTTP $status)"
  else
    echo "  ❌  $name not yet ready at $url"
  fi
}

check_url "Eureka Server"  "http://localhost:8761"
check_url "API Gateway"    "http://localhost:8083"
check_url "Customer Svc"   "http://localhost:8080"
check_url "Fraud Svc"      "http://localhost:8081"
check_url "Notification"   "http://localhost:8082"
check_url "Zipkin UI"      "http://localhost:9411"
check_url "RabbitMQ Mgmt"  "http://localhost:15672"
check_url "pgAdmin"        "http://localhost:5050"

echo ""
echo "======================================"
echo "  Service URLs Summary"
echo "======================================"
echo "  Eureka Dashboard : http://localhost:8761"
echo "  API Gateway      : http://localhost:8083"
echo "  Customer Service : http://localhost:8080"
echo "  Fraud Service    : http://localhost:8081"
echo "  Notification Svc : http://localhost:8082"
echo "  Zipkin Tracing   : http://localhost:9411"
echo "  RabbitMQ Console : http://localhost:15672  (guest/guest)"
echo "  pgAdmin          : http://localhost:5050   (pgadmin4@pgadmin.org/admin)"
echo "  PostgreSQL       : localhost:5432          (amigoscode/password)"
echo "======================================"
echo ""
echo "To view logs:       docker compose logs -f [service-name]"
echo "To stop all:        docker compose down"
echo "To stop + wipe DB:  docker compose down -v"
echo ""
