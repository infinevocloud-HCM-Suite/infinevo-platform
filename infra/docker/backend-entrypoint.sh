#!/bin/sh
# Entrypoint for the Infinevo backend image.
#
# One image, two roles. INFINEVO_ROLE selects which Spring Boot jar to run:
#   app    — web role, serves HTTP (default)
#   worker — batch role, consumes queue, runs scheduled jobs, health only
#
# See docs/target-state/04-runtime-containers.md section 3.

set -e

ROLE="${INFINEVO_ROLE:-app}"

case "$ROLE" in
  app)
    JAR="/app/app.jar"
    ;;
  worker)
    JAR="/app/worker.jar"
    ;;
  *)
    echo "ERROR: INFINEVO_ROLE must be 'app' or 'worker', got '${ROLE}'" >&2
    exit 1
    ;;
esac

if [ ! -f "$JAR" ]; then
  echo "ERROR: jar not found at ${JAR}" >&2
  exit 1
fi

echo "Starting Infinevo ${ROLE} role..."
exec java \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  ${JAVA_OPTS:-} \
  -jar "$JAR" \
  "$@"
