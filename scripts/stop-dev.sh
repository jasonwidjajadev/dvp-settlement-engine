#!/usr/bin/env bash
set -e

docker compose --env-file .env down

# start work
# → ./scripts/dev.sh

# stop Spring Boot
# → Ctrl+C

# keep PostgreSQL running
# → do nothing

# fully shut down local stack
# → docker compose --env-file .env down