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
#
# Served under /auth, baked in at build time (:37-47). Everything - the admin console,
# the realm endpoints, OIDC discovery - is one level down:
#   http://<host>:8080/auth/realms/<realm>/.well-known/openid-configuration
# Health and metrics are NOT: they stay at /health/ready and /metrics on port 9000.

# ── Stage 1: build optimized image ───────────────────────────────────────────
FROM quay.io/keycloak/keycloak:25.0 AS builder

ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true
ENV KC_DB=postgres
ENV KC_HTTP_ENABLED=true
ENV KC_PROXY_HEADERS=xforwarded

# Front Door routes /auth/* to this container (infra/azure/modules/frontdoor.bicep:379)
# and rewrites nothing, so the origin receives the prefix intact. Keycloak has to believe
# it lives under /auth or every path it serves - and every issuer, JWKS and redirect URL
# it mints - is one level too high, and the route 404s (review F-6/F-8).
#
# This is BAKED IN, not passed at runtime. http-relative-path is a build-time option
# (`kc.sh build --help-all` lists it under Build time; the help even uses /auth as its
# example), and :97 starts with --optimized, which reads build-time options only from
# what this stage persisted. A runtime KC_HTTP_RELATIVE_PATH is ignored with a warning -
# which is exactly how the previous attempt looked correct while doing nothing.
ENV KC_HTTP_RELATIVE_PATH=/auth

# The management interface (port 9000: health, metrics) inherits http-relative-path unless
# told otherwise, which would drag /health/ready to /auth/health/ready. That port is private
# - it is never behind Front Door - so it has no reason to follow the public prefix. Pinned
# to / so probes stay at a fixed path whatever the public routing does.
ENV KC_HTTP_MANAGEMENT_RELATIVE_PATH=/

# The production realm is baked into this image (W-10.1).
#
# infra/keycloak/infinevo-realm.json holds the canonical production realm:
# zero users, no secrets, external SSL, and environment variable placeholders for
# Brevo SMTP and web origins.
#
# The local development realm (infra/docker/keycloak/dev-realm.json) is still NOT
# baked here: compose.yml:111 mounts it into the stock image for local stack development.

RUN /opt/keycloak/bin/kc.sh build

# ── Stage 2: optimized runtime ───────────────────────────────────────────────
FROM quay.io/keycloak/keycloak:25.0 AS runtime

COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY infra/keycloak/infinevo-realm.json /opt/keycloak/data/import/

ENV KC_DB=postgres
ENV KC_HEALTH_ENABLED=true
ENV KC_HTTP_ENABLED=true
ENV KC_PROXY_HEADERS=xforwarded

# KC_HTTP_RELATIVE_PATH is deliberately absent here. It came across in the COPY above,
# baked by the builder stage; repeating it as a runtime variable would achieve nothing
# under --optimized and would suggest the deployment can change it. It cannot - moving
# the prefix means rebuilding the image.

# Non-root, asserted here rather than inherited (review F-7).
# The quay base image already defaults to uid 1000, but nothing in this repository said
# so, and a base image change would have flipped it silently. Note the primary group is
# root (gid 0) — a Red Hat base image convention, flagged by some scanners; W-59 rules
# on whether that needs changing.
USER 1000

# HTTP port (8080) and Management/health port (9000)
EXPOSE 8080 9000

# Start-up guard (W-10.1 spec section 4 row 3). Keycloak leaves an unresolved ${VAR} in
# an imported realm as literal text, and imports the realm only once, so a first start
# without these variables would store "${KC_WEB_ORIGIN}/*" as the redirect URI for good.
# Refuse instead. deploy.yml sets KC_WEB_ORIGIN; containerapps.bicep sets the KC_SMTP_*.
# A Bicep deployment replaces the env list, so after one Keycloak stays down until the
# pipeline's revision step runs again - loud, rather than a silently broken realm.
ENTRYPOINT ["/bin/sh", "-c", "for v in KC_WEB_ORIGIN KC_SMTP_HOST KC_SMTP_PORT KC_SMTP_FROM KC_SMTP_USER KC_SMTP_PASSWORD; do [ -n \"$(printenv \"$v\")\" ] || { echo \"refusing to start: $v is not set; the infinevo realm would import it as literal text (W-10.1)\" >&2; exit 1; }; done; exec /opt/keycloak/bin/kc.sh \"$@\"", "kc"]
CMD ["start", "--optimized", "--import-realm"]
