#!/usr/bin/env bash
# Loads local seed data into the running stack. Idempotent.
#
#   infra/docker/seed/seed.sh
#
# Local only. Not a Flyway migration, and must never run against a deployed environment.
set -euo pipefail

COMPOSE="docker compose -f $(dirname "$0")/../compose.yml"

if ! $COMPOSE ps --status running --services 2>/dev/null | grep -q '^postgres$'; then
  echo "postgres is not running. Start the stack first:" >&2
  echo "  docker compose -f infra/docker/compose.yml up -d" >&2
  exit 1
fi

for f in $(dirname "$0")/*.sql; do
  echo "── $(basename "$f")"
  $COMPOSE exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres -d infinevo < "$f"
done

echo "seed complete"
