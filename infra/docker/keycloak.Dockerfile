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

# No realm is baked into this image, deliberately.
#
# infra/docker/keycloak/dev-realm.json is a LOCAL development realm whose own README
# says every credential in it "must never appear in a deployed environment". It carries
# three accounts with the literal password local_dev_pw and localhost:5173 redirect URIs.
# Copying it here put it in the one image this repository deploys, and `--import-realm`
# created those accounts on first boot against a real database (review F-1).
#
# The local stack does not need it here: compose.yml:111 mounts the same file read-only
# into the stock Keycloak image, which is where a development realm belongs.
#
# W-10 supplies the production realm. Until then this image starts with no realm but
# the built-in `master`, configured entirely from environment variables.

RUN /opt/keycloak/bin/kc.sh build

# ── Stage 2: optimized runtime ───────────────────────────────────────────────
FROM quay.io/keycloak/keycloak:25.0 AS runtime

COPY --from=builder /opt/keycloak/ /opt/keycloak/

ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true
ENV KC_HTTP_ENABLED=true
ENV KC_PROXY_HEADERS=xforwarded

# Non-root, asserted here rather than inherited (review F-7).
# The quay base image already defaults to uid 1000, but nothing in this repository said
# so, and a base image change would have flipped it silently. Note the primary group is
# root (gid 0) — a Red Hat base image convention, flagged by some scanners; W-59 rules
# on whether that needs changing.
USER 1000

# HTTP port (8080) and Management/health port (9000)
EXPOSE 8080 9000

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized"]
