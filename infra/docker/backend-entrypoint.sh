#!/bin/sh
# Entrypoint for the Infinevo backend image.
#
# One image, three roles. INFINEVO_ROLE selects which Spring Boot jar to run:
#   app       — web role, serves HTTP (default)
#   worker    — batch role, consumes queue, runs scheduled jobs, health only
#   migration — Flyway runner, applies migrations as migration_user and exits (run-once)
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
  migration)
    # W-54 / #138. Run-once role: caj-flyway-{env} starts the image with this role, Flyway
    # applies every script, the context closes and the JVM exits. The job reads the exit
    # code, so nothing here may mask it — note `set -e` above and `exec` below.
    # It is never the default: an unset INFINEVO_ROLE must stay 'app'.
    JAR="/app/migration.jar"
    ;;
  *)
    echo "ERROR: INFINEVO_ROLE must be 'app', 'worker' or 'migration', got '${ROLE}'" >&2
    exit 1
    ;;
esac

if [ ! -f "$JAR" ]; then
  echo "ERROR: jar not found at ${JAR}" >&2
  exit 1
fi

echo "Starting Infinevo ${ROLE} role..."
# One invocation for all three roles, deliberately — considered and kept for the run-once
# migration role too:
#   exec           — the JVM becomes PID 1, so its exit code IS the container's exit code.
#                    That is what caj-flyway-{env} reads to decide the deploy stops, and it
#                    matters more for the run-once role than for the two servers.
#   UseContainerSupport / MaxRAMPercentage=75 — sizing from the cgroup limit is right for a
#                    short-lived job as much as for a server; Flyway's own footprint is
#                    small, and a fixed -Xmx would have to be guessed per job profile.
#   JAVA_OPTS      — the per-role escape hatch if one ever does need different flags.
# Nothing here is role-conditional, so the app and worker command lines are byte-identical
# to what they were before this role was added.
exec java \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  ${JAVA_OPTS:-} \
  -jar "$JAR" \
  "$@"
