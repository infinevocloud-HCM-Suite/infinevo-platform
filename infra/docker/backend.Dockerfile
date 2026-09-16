# Production backend image — W-49.
#
# Three stages: dependencies (cached), build, slim JRE runtime.
# One image, two roles: INFINEVO_ROLE=app (default) or INFINEVO_ROLE=worker.
# See docs/target-state/04-runtime-containers.md section 4.
#
# Build context is the REPOSITORY ROOT (same as the dev Dockerfile and compose.yml):
#   docker build -f infra/docker/backend.Dockerfile -t infinevo-backend .
#
# No secrets in this image. All configuration via environment variables.

# ── Stage 1: resolve dependencies (cached unless a POM changes) ──────────────
FROM maven:3.9-eclipse-temurin-21 AS deps

WORKDIR /build

# Copy only POMs so that a source change does not re-download every dependency.
COPY code/backend/pom.xml .
COPY code/backend/shared/pom.xml      shared/
COPY code/backend/core/pom.xml        core/
COPY code/backend/hrms/pom.xml        hrms/
COPY code/backend/payroll/pom.xml     payroll/
COPY code/backend/app/pom.xml         app/
COPY code/backend/worker/pom.xml      worker/
COPY code/backend/migration/pom.xml   migration/

RUN mvn -B -q dependency:go-offline -DskipTests || true

# ── Stage 2: compile and package ─────────────────────────────────────────────
FROM deps AS build

COPY code/backend/ .

# Package the full reactor. Both app and worker produce repackaged fat jars.
# Tests are skipped here — they ran in CI's backend job before the images job.
RUN mvn -B -q package -DskipTests

# ── Stage 3: slim runtime ───────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user (04-runtime-containers.md section 4).
RUN addgroup -g 1000 infinevo && \
    adduser -u 1000 -G infinevo -D -h /app infinevo

WORKDIR /app

# Copy both jars. The entrypoint selects which one to run.
COPY --from=build --chown=infinevo:infinevo /build/app/target/app-*.jar        /app/app.jar
COPY --from=build --chown=infinevo:infinevo /build/worker/target/worker-*.jar   /app/worker.jar

# Entrypoint script: reads INFINEVO_ROLE and launches the corresponding jar.
COPY --chown=infinevo:infinevo infra/docker/backend-entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

USER infinevo

# Default port for the app role. Worker uses 8082 (set in its application.yml).
EXPOSE 8080

ENTRYPOINT ["/app/entrypoint.sh"]
