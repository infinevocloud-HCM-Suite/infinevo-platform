# Migration runner image — W-51 T4, spec section 3e.
#
# The only thing in the platform that runs infra/postgres/provision.sh against a
# PostgreSQL Flexible Server whose publicNetworkAccess is 'Disabled'. It carries the four
# tools W-51 section 2.6 names — psql, redis-cli, curl and the Azure CLI — plus dig, which
# the probes need in order to assert that every target resolves to a 10.x VNet address.
#
# Build context is the REPOSITORY ROOT, matching backend.Dockerfile:7-8:
#   docker build -f infra/docker/migration-runner.Dockerfile -t migration-runner .
# deploy.sh builds it remotely instead, with `az acr build`, so no local Docker is needed.
#
# NO SECRET IN THIS IMAGE. Every credential is read from Key Vault at run time by the
# entrypoint, using the job's managed identity. Nothing here is environment-specific, so
# one image serves dev, uat and prod.

# ── Base: Azure CLI, pinned ──────────────────────────────────────────────────
# 2.61.0 is the last Alpine-based azure-cli tag; later tags moved to Azure Linux, where the
# apk line below would not resolve. Pinned rather than :latest so a rebuild months from now
# produces the same probe behaviour — an unpinned CLI silently renaming a flag (see
# --username vs --client-id in the entrypoint) would break the job with no diff to point at.
# Bumping this tag means re-checking: (a) the package manager is still apk, (b)
# `az login --identity --username` is still accepted.
FROM mcr.microsoft.com/azure-cli:2.61.0 AS runtime

# postgresql16-client — psql, matching the Flexible Server major version in postgres.bicep
# redis            — redis-cli, built with TLS support (the probe uses 6380 only)
# bind-tools       — dig, for the 10.x assertion
# curl, bash       — probe plumbing; the CLI image has ash, and both scripts are bash
#
# Package versions are not pinned, deliberately and consistently with
# backend.Dockerfile:58 — the Alpine branch is fixed by the base tag above, which is what
# bounds them. Pinning exact -rN revisions breaks the build the day Alpine rebuilds a
# package, for no reproducibility gain the base tag does not already give.
RUN apk add --no-cache \
      postgresql16-client \
      redis \
      bind-tools \
      curl \
      bash \
      ca-certificates

# Non-root. D-49 made unprivileged the house rule; the same uid/gid as
# backend.Dockerfile:58-59 so a mounted volume behaves identically across images.
RUN addgroup -g 1000 infinevo && \
    adduser -u 1000 -G infinevo -D -h /app infinevo

WORKDIR /app

# infra/postgres/ verbatim — provision.sh and the three SQL files. W-51 section 3c keeps
# them out of scope, so they are copied, never edited.
COPY --chown=infinevo:infinevo infra/postgres/ /app/postgres/

COPY --chown=infinevo:infinevo infra/azure/probes/private-path-probes.sh /app/private-path-probes.sh
COPY --chown=infinevo:infinevo infra/docker/migration-runner-entrypoint.sh /app/entrypoint.sh

RUN chmod +x /app/entrypoint.sh /app/private-path-probes.sh /app/postgres/provision.sh

# The CLI writes its token cache and config here. Left under /app, which infinevo owns —
# the default $HOME/.azure is not writable once USER drops to a non-root uid.
ENV AZURE_CONFIG_DIR=/app/.azure

USER infinevo

# No EXPOSE: this image never listens. It is a job, it runs once and exits.
ENTRYPOINT ["/app/entrypoint.sh"]
