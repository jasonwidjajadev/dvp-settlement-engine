#!/usr/bin/env bash
set -e

docker compose --env-file .env up -d

set -a
source .env
set +a

./mvnw spring-boot:run


# Server:
# http://localhost:8080

# Swagger UI:
# http://localhost:8080/swagger-ui/index.html

# OpenAPI:
# http://localhost:8080/v3/api-docs