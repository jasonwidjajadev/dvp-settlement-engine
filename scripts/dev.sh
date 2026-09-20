#!/usr/bin/env bash
set -e

docker compose --env-file .env up -d

set -a
source .env
set +a

./mvnw spring-boot:run