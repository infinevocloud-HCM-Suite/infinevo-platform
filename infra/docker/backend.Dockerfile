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

# `|| echo` rather than `|| true` (review F-11): go-offline is genuinely unreliable on a
# multi-module reactor, so a failure here must not fail the build — but it must be VISIBLE
# in the build log, because stage 2 will then re-resolve everything and take minutes for a
# reason nobody can see.
RUN mvn -B -q dependency:go-offline -DskipTests \
    || echo "WARN: dependency:go-offline did not complete; stage 2 will resolve from the network"

# ── Stage 2: compile and package ─────────────────────────────────────────────
FROM deps AS build

COPY code/backend/ .

# Package the full reactor. Both app and worker produce repackaged fat jars.
# Tests are skipped here — they ran in CI's backend job before the images job.
RUN mvn -B -q package -DskipTests

# Resolve each fat jar to a fixed name here, where a wrong match can still fail loudly.
# `COPY app-*.jar` in the runtime stage fails with an opaque error if the glob ever
# matches a second artifact, and silently copies the wrong one if it matches a sources or
# javadoc jar (review F-11). Assert exactly one match instead.
RUN set -eu; \
    for module in app worker; do \
      count=$(ls -1 "${module}"/target/"${module}"-*.jar 2>/dev/null | wc -l); \
      [ "$count" -eq 1 ] || { echo "ERROR: expected exactly one ${module} jar, found ${count}"; exit 1; }; \
      cp "${module}"/target/"${module}"-*.jar "/build/${module}.jar"; \
    done

# ── Stage 3: slim runtime ───────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user (04-runtime-containers.md section 4).
RUN addgroup -g 1000 infinevo && \
    adduser -u 1000 -G infinevo -D -h /app infinevo

WORKDIR /app

# Copy both jars. The entrypoint selects which one to run.
COPY --from=build --chown=infinevo:infinevo /build/app.jar     /app/app.jar
COPY --from=build --chown=infinevo:infinevo /build/worker.jar  /app/worker.jar

# Entrypoint script: reads INFINEVO_ROLE and launches the corresponding jar.
COPY --chown=infinevo:infinevo infra/docker/backend-entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

USER infinevo

# Both roles this image can run: app on 8080, worker on 8082 (review F-12).
# EXPOSE is documentation — it publishes nothing — but it is the image's self-description,
# and listing only 8080 contradicted the worker role this same image exists to serve.
EXPOSE 8080 8082

ENTRYPOINT ["/app/entrypoint.sh"]
