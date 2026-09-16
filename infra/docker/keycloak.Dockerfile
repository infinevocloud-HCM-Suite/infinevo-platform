# Production Keycloak image — W-49.
#
# Two stages: optimized build, runtime.
# See docs/target-state/04-runtime-containers.md section 1.
#
# Build context is the REPOSITORY ROOT:
#   docker build -f infra/docker/keycloak.Dockerfile -t infinevo-keycloak .
#
# No secrets in this image.
#
# Required environment variables at runtime:
#   KC_DB_URL               JDBC connection URL (e.g. jdbc:postgresql://postgres:5432/keycloak)
#   KC_DB_USERNAME          Database user
#   KC_DB_PASSWORD          Database password
#   KEYCLOAK_ADMIN          Initial admin username
#   KEYCLOAK_ADMIN_PASSWORD Initial admin password
#   KC_HOSTNAME             Public hostname (e.g. auth.infinevo.com or localhost)
#
# Proxy & TLS settings:
#   Front Door terminates TLS (05-azure-architecture.md:49,79).
#   KC_HTTP_ENABLED=true and KC_PROXY_HEADERS=xforwarded enable HTTP ingress behind Front Door.

# ── Stage 1: build optimized image ───────────────────────────────────────────
FROM quay.io/keycloak/keycloak:25.0 AS builder

ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true
ENV KC_DB=postgres
ENV KC_HTTP_ENABLED=true
ENV KC_PROXY_HEADERS=xforwarded

# Import realm export (W-10 will replace this with the full production realm export)
COPY infra/docker/keycloak/dev-realm.json /opt/keycloak/data/import/dev-realm.json

RUN /opt/keycloak/bin/kc.sh build

# ── Stage 2: optimized runtime ───────────────────────────────────────────────
FROM quay.io/keycloak/keycloak:25.0 AS runtime

COPY --from=builder /opt/keycloak/ /opt/keycloak/

ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true
ENV KC_HTTP_ENABLED=true
ENV KC_PROXY_HEADERS=xforwarded

# HTTP port (8080) and Management/health port (9000)
EXPOSE 8080 9000

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]
