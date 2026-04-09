#!/bin/bash
# Start infrastructure services for hybrid development
# Services run in Docker, applications run in IDE

set -e

echo "=========================================="
echo "  Starting Infrastructure Services"
echo "=========================================="
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker first."
    exit 1
fi

# Detect docker compose command (v2 vs v1)
if docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
elif docker-compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker-compose"
else
    echo "❌ Docker Compose is not installed. Please install Docker Compose."
    exit 1
fi

echo ">>> Using: $DOCKER_COMPOSE"

# Start infrastructure
echo ">>> Starting PostgreSQL, RabbitMQ, Zipkin, and pgAdmin..."
$DOCKER_COMPOSE -f docker-compose.infra.yml up -d

echo ""
echo ">>> Waiting for PostgreSQL to be healthy..."
until [ "$(docker inspect -f '{{.State.Health.Status}}' postgres 2>/dev/null)" = "healthy" ]; do
  echo -n "."
  sleep 2
done
echo " ✅"

echo ""
echo ">>> Creating application databases if they don't exist..."

create_db_if_missing() {
  local db_name=$1
  if ! $DOCKER_COMPOSE -f docker-compose.infra.yml exec -T postgres psql -U amigoscode -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='${db_name}'" | grep -q 1; then
    echo "  Creating database: ${db_name}"
    $DOCKER_COMPOSE -f docker-compose.infra.yml exec -T postgres psql -U amigoscode -d postgres -c "CREATE DATABASE \"${db_name}\";" > /dev/null 2>&1
  else
    echo "  Database exists: ${db_name}"
  fi
}

create_db_if_missing customer
create_db_if_missing fraud
create_db_if_missing notification

echo ""
echo ">>> Checking RabbitMQ..."
until $DOCKER_COMPOSE -f docker-compose.infra.yml exec -T rabbitmq rabbitmq-diagnostics ping > /dev/null 2>&1; do
  echo -n "."
  sleep 2
done
echo " ✅"

echo ""
echo ">>> Checking Zipkin..."
until curl -s http://localhost:9411/health > /dev/null 2>&1; do
  echo -n "."
  sleep 2
done
echo " ✅"

echo ""
echo "=========================================="
echo "  Infrastructure Ready! 🚀"
echo "=========================================="
echo ""
echo "Services:"
echo "  🐘 PostgreSQL    : localhost:5432 (amigoscode/password)"
echo "  🐰 RabbitMQ AMQP : localhost:5672"
echo "  🐰 RabbitMQ Mgmt : http://localhost:15672 (guest/guest)"
echo "  📊 Zipkin        : http://localhost:9411"
echo "  🗄️  pgAdmin       : http://localhost:5050 (pgadmin4@pgadmin.org/admin)"
echo ""
echo "Next steps:"
echo "  1. Start EurekaServerApplication from your IDE"
echo "  2. Start ApiGWApplication from your IDE"
echo "  3. Start FraudApplication from your IDE"
echo "  4. Start CustomerApplication from your IDE"
echo "  5. Start NotificationApplication from your IDE"
echo ""
echo "💡 Tip: With Spring Boot DevTools, code changes will auto-restart services (~2-3s)"
echo ""
echo "To stop infrastructure:"
echo "  $DOCKER_COMPOSE -f docker-compose.infra.yml down"
echo ""
