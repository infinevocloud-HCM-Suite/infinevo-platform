# Data migration job image — W-71 / W-68, Stage A.
#
# Container image for caj-data-migration-{env} Container Apps Job.
# Runs the legacy data migration from DigitalOcean MySQL (or Blob mysqldump)
# to Azure PostgreSQL Flexible Server, Keycloak, and Blob Storage.
#
# Contains:
#   - Python 3.12 runtime with DuckDB, psycopg2, pymysql, Azure SDKs
#   - MariaDB/MySQL client and server (for local dump replay sidecar)
#   - PostgreSQL client (psql 15/16)
#   - Azure CLI (for managed identity auth, Key Vault secret fetch, blob transfer)
#
# Build context is the REPOSITORY ROOT:
#   docker build -f infra/docker/data-migration.Dockerfile -t data-migration .
#
# NO SECRET IN THIS IMAGE (D-49, W-51, W-56).
# All credentials are read from Azure Key Vault at runtime by the entrypoint
# using the job's managed identity (id-migration-{env}).

FROM python:3.12-slim-bookworm AS runtime

# Install system dependencies:
# - default-mysql-server & default-mysql-client: embedded MariaDB sidecar for restoring .sql.gz dumps
# - postgresql-client: psql for verifying PostgreSQL target connectivity
# - curl, ca-certificates, gnupg, lsb-release: repository management & network probes
# - procps, gzip, bash: script and process orchestration
# - azure-cli: Microsoft official Debian package
RUN apt-get update && apt-get install -y --no-install-recommends \
      default-mysql-server \
      default-mysql-client \
      postgresql-client \
      curl \
      ca-certificates \
      gnupg \
      lsb-release \
      procps \
      gzip \
      bash \
    && mkdir -p /etc/apt/keyrings \
    && curl -sLS https://packages.microsoft.com/keys/microsoft.asc | gpg --dearmor -o /etc/apt/keyrings/microsoft.gpg \
    && chmod go+r /etc/apt/keyrings/microsoft.gpg \
    && echo "deb [arch=amd64 signed-by=/etc/apt/keyrings/microsoft.gpg] https://packages.microsoft.com/repos/azure-cli/ bookworm main" > /etc/apt/sources.list.d/azure-cli.list \
    && apt-get update && apt-get install -y --no-install-recommends azure-cli \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Non-root user: uid/gid 1000 infinevo (D-49).
# Matches backend.Dockerfile and migration-runner.Dockerfile.
RUN groupadd -g 1000 infinevo && \
    useradd -u 1000 -g infinevo -d /app -m -s /bin/bash infinevo

WORKDIR /app

# Install Python requirements
COPY code/migration/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r /app/requirements.txt

# Copy migration codebase and entrypoint
COPY --chown=infinevo:infinevo code/migration/ /app/migration/
COPY --chown=infinevo:infinevo infra/docker/data-migration-entrypoint.sh /app/entrypoint.sh

# Prepare directories with infinevo ownership:
# /tmp/mysql for MariaDB datadir, /app/.azure for Azure CLI token cache, staging dir
RUN mkdir -p /tmp/mysql /app/.azure /app/migration/staging && \
    chown -R infinevo:infinevo /tmp/mysql /app && \
    chmod +x /app/entrypoint.sh /app/migration/run_all.sh

# Azure CLI config directory under /app (writable by infinevo)
ENV AZURE_CONFIG_DIR=/app/.azure
ENV PYTHONUNBUFFERED=1

USER infinevo

# No EXPOSE: this image runs as a batch Container Apps Job and exits upon completion.
ENTRYPOINT ["/app/entrypoint.sh"]
