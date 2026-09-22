#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# private-path-probes.sh — the six private-path probes of W-51 section 5 step 6.
#
# Runs INSIDE caj-db-migration-{env}, on snet-cae, after provision.sh. Each probe
#   1. resolves its target hostname and ASSERTS THE ADDRESS STARTS 10.
#   2. completes a real data-plane call
#   3. prints "PROBE-<NAME>: OK" on success
# and the script exits non-zero if any of them fails.
#
# WHY THE 10.x ASSERTION IS THE LOAD-BEARING PART. Every one of these services still has a
# public FQDN. Without the assertion a probe that fell back to the public endpoint - because
# a private DNS zone was not linked, or a private endpoint was created on the wrong
# sub-resource - would print OK and the perimeter would look proven when it is not.
# W-51 section 5 records that the gate greps this script's own output (finding F-6, waived
# by the founder on 2026-09-19), so the assertion below is the only thing standing between
# a green verification and an unproven private path. Do not weaken it to a warning.
#
# RUN AS THREE DIFFERENT IDENTITIES. 6a runs as id-migration, 6c/6d and the send half of 6e
# as id-app, the receive half of 6e as id-worker. That is deliberate: section 3f assigns the
# roles per identity, and logging in as one identity for all six would prove one grant five
# times over. Each `login_as` re-authenticates the CLI against a different managed identity
# attached to the job.
#
# NO ACCOUNT KEY, NO SAS, ANYWHERE. Every storage call below uses --auth-mode login.
# ---------------------------------------------------------------------------

# Deliberately NOT `set -e`: a failing probe must be reported by name and the remaining
# probes still attempted, so one broken private endpoint does not mask four others. Failures
# are accumulated in FAILURES and turned into the exit status at the end.
set -uo pipefail

FAILURES=0

fail() {
  echo "PROBE-$1: FAIL — $2" >&2
  FAILURES=$((FAILURES + 1))
}

# ── Hostname must resolve into the VNet ──────────────────────────────────────
# dig is used rather than getent because the private endpoint chain is
# <name>.<service>.<suffix> CNAME <name>.privatelink.<service>.<suffix> A 10.x, and the
# last A record in that chain is the one that matters. `tail -n 1` takes it.
resolve_ipv4() {
  dig +short "$1" 2>/dev/null | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' | tail -n 1
}

# Returns 0 only if $2 resolves to a 10.x address. $1 is the probe name, used in the message.
assert_private_address() {
  local probe="$1" host="$2" ip
  ip="$(resolve_ipv4 "$host")"
  if [[ -z "$ip" ]]; then
    fail "$probe" "${host} does not resolve at all — the private DNS zone is missing or not linked to the VNet"
    return 1
  fi
  case "$ip" in
    10.*)
      echo "  ${host} -> ${ip} (private)"
      return 0
      ;;
    *)
      fail "$probe" "${host} resolves to ${ip}, which is not a 10.x VNet address — this probe would have used the PUBLIC endpoint"
      return 1
      ;;
  esac
}

# ── Assume one of the job's user-assigned identities ─────────────────────────
# `--username` rather than `--client-id`: the pinned Azure CLI in
# infra/docker/migration-runner.Dockerfile predates the rename, and --username is still
# accepted by newer versions. Revisit if that base image is bumped past the deprecation.
login_as() {
  local label="$1" client_id="$2"
  if [[ -z "$client_id" ]]; then
    echo "  cannot log in as ${label}: no client id supplied" >&2
    return 1
  fi
  az account clear >/dev/null 2>&1 || true
  if ! az login --identity --username "$client_id" >/dev/null 2>&1; then
    echo "  az login --identity failed for ${label} (${client_id})" >&2
    return 1
  fi
  az account set --subscription "$AZURE_SUBSCRIPTION_ID" >/dev/null 2>&1 || true
  return 0
}

KEY_VAULT_HOST="${KEY_VAULT_NAME}.vault.azure.net"
BLOB_HOST="${STORAGE_ACCOUNT_NAME}.blob.core.windows.net"
QUEUE_HOST="${STORAGE_ACCOUNT_NAME}.queue.core.windows.net"

echo "================================================================="
echo " W-51 private-path probes — environment [${ENVIRONMENT}]"
echo "================================================================="

# ── 6a. PROBE-KV-MIGRATION ───────────────────────────────────────────────────
# id-migration reads psql-admin-pw. This is the grant the job itself depends on: if it
# fails, provision.sh had no password either.
echo "--- PROBE-KV-MIGRATION: id-migration reads psql-admin-pw from ${KEY_VAULT_NAME}"
if assert_private_address "KV-MIGRATION" "$KEY_VAULT_HOST"; then
  if login_as "id-migration" "$MIGRATION_CLIENT_ID"; then
    # The value is discarded immediately. Only its presence is evidence; printing it would
    # put a live administrator password in a Log Analytics workspace.
    if az keyvault secret show --vault-name "$KEY_VAULT_NAME" --name "psql-admin-pw" \
         --query value -o tsv >/dev/null 2>&1; then
      echo "PROBE-KV-MIGRATION: OK"
    else
      fail "KV-MIGRATION" "id-migration could not read psql-admin-pw — Key Vault Secrets User is missing or the vault ACL denied a 10.x caller"
    fi
  else
    fail "KV-MIGRATION" "could not assume id-migration"
  fi
fi

# ── 6b. PROBE-PG ─────────────────────────────────────────────────────────────
# provision.sh has already run by the time this executes (the entrypoint runs it first and
# aborts on failure). This re-asserts the same connection so the evidence line names the
# resolved private address, and so a server that accepted provision.sh over a PUBLIC
# endpoint is caught here.
echo "--- PROBE-PG: provision.sh connected to ${PGHOST} over the private endpoint"
if assert_private_address "PG" "$PGHOST"; then
  if [[ -z "${PGPASSWORD:-}" ]]; then
    fail "PG" "PGPASSWORD is not set — the entrypoint did not fetch psql-admin-pw"
  elif [[ "$(psql -h "$PGHOST" -p "${PGPORT:-5432}" -U "$PGUSER" -d "$PGDATABASE" \
             -t -A -c 'SELECT 1' 2>/dev/null)" == "1" ]]; then
    echo "PROBE-PG: OK"
  else
    fail "PG" "could not execute SELECT 1 against ${PGHOST} as ${PGUSER}"
  fi
fi

# ── 6c. PROBE-KV-APP ─────────────────────────────────────────────────────────
# A different identity against the same vault. Proves id-app's own Key Vault Secrets User
# assignment (section 3f row 1) rather than re-proving id-migration's.
echo "--- PROBE-KV-APP: id-app reads psql-app-pw from ${KEY_VAULT_NAME}"
if assert_private_address "KV-APP" "$KEY_VAULT_HOST"; then
  if login_as "id-app" "$APP_CLIENT_ID"; then
    if az keyvault secret show --vault-name "$KEY_VAULT_NAME" --name "psql-app-pw" \
         --query value -o tsv >/dev/null 2>&1; then
      echo "PROBE-KV-APP: OK"
    else
      fail "KV-APP" "id-app could not read psql-app-pw — Key Vault Secrets User is missing for id-app, or the secret has not been seeded"
    fi
  else
    fail "KV-APP" "could not assume id-app"
  fi
fi

# ── 6d. PROBE-BLOB ───────────────────────────────────────────────────────────
# Write and read back. A read-only probe would pass against a stale blob from a previous
# run; the round trip is what proves Storage Blob Data Contributor and the blob private
# endpoint together.
PROBE_BLOB_NAME="w51-probe-$(date +%Y%m%d%H%M%S)-$$.txt"
PROBE_BLOB_BODY="w51 blob probe ${ENVIRONMENT} $(date -u +%FT%TZ)"
echo "--- PROBE-BLOB: id-app writes and reads back ${PROBE_BLOB_NAME} in documents"
if assert_private_address "BLOB" "$BLOB_HOST"; then
  # Still logged in as id-app from 6c; re-assert in case 6c's login failed.
  if login_as "id-app" "$APP_CLIENT_ID"; then
    printf '%s' "$PROBE_BLOB_BODY" > "/tmp/${PROBE_BLOB_NAME}"
    if az storage blob upload \
         --account-name "$STORAGE_ACCOUNT_NAME" --blob-endpoint "$BLOB_ENDPOINT" \
         --auth-mode login --container-name documents \
         --name "$PROBE_BLOB_NAME" --file "/tmp/${PROBE_BLOB_NAME}" --overwrite \
         >/dev/null 2>&1; then
      READ_BACK="$(az storage blob download \
        --account-name "$STORAGE_ACCOUNT_NAME" --blob-endpoint "$BLOB_ENDPOINT" \
        --auth-mode login --container-name documents \
        --name "$PROBE_BLOB_NAME" --file /dev/stdout -o none 2>/dev/null)"
      if [[ "$READ_BACK" == "$PROBE_BLOB_BODY" ]]; then
        echo "PROBE-BLOB: OK"
      else
        fail "BLOB" "read back '${READ_BACK}' but wrote '${PROBE_BLOB_BODY}'"
      fi
      # Best effort: leaving probe blobs behind would grow the container without bound.
      az storage blob delete \
        --account-name "$STORAGE_ACCOUNT_NAME" --blob-endpoint "$BLOB_ENDPOINT" \
        --auth-mode login --container-name documents --name "$PROBE_BLOB_NAME" \
        >/dev/null 2>&1 || true
    else
      fail "BLOB" "id-app could not upload to documents — Storage Blob Data Contributor is missing, or the blob private endpoint is not reachable"
    fi
    rm -f "/tmp/${PROBE_BLOB_NAME}"
  else
    fail "BLOB" "could not assume id-app"
  fi
fi

# ── 6e. PROBE-QUEUE ──────────────────────────────────────────────────────────
# id-app SENDS, id-worker RECEIVES. Two identities, two roles, two separate grants -
# and a data plane the blob role does not cover. If Storage Queue Data Message Sender or
# Storage Queue Data Message Processor is missing from rbac.bicep, this is where it shows.
PROBE_QUEUE_BODY="w51-queue-probe-${ENVIRONMENT}-$(date +%s)-$$"
echo "--- PROBE-QUEUE: id-app sends to payrun, id-worker receives"
if assert_private_address "QUEUE" "$QUEUE_HOST"; then
  QUEUE_SENT=0
  if login_as "id-app" "$APP_CLIENT_ID"; then
    if az storage message put \
         --account-name "$STORAGE_ACCOUNT_NAME" --queue-endpoint "$QUEUE_ENDPOINT" \
         --auth-mode login --queue-name payrun --content "$PROBE_QUEUE_BODY" \
         >/dev/null 2>&1; then
      QUEUE_SENT=1
    else
      fail "QUEUE" "id-app could not put a message on payrun — Storage Queue Data Message Sender is missing, or the queue private endpoint is not reachable (the blob endpoint does NOT cover queues)"
    fi
  else
    fail "QUEUE" "could not assume id-app"
  fi

  if [[ "$QUEUE_SENT" -eq 1 ]]; then
    if login_as "id-worker" "$WORKER_CLIENT_ID"; then
      # Up to 32 messages, because a previous failed run may have left its probe message
      # behind and the queue is FIFO - taking only the first would read the wrong one.
      RECEIVED="$(az storage message get \
        --account-name "$STORAGE_ACCOUNT_NAME" --queue-endpoint "$QUEUE_ENDPOINT" \
        --auth-mode login --queue-name payrun --num-messages 32 \
        --query "[].content" -o tsv 2>/dev/null)"
      if echo "$RECEIVED" | grep -Fq "$PROBE_QUEUE_BODY"; then
        echo "PROBE-QUEUE: OK"
      else
        fail "QUEUE" "id-worker did not receive the probe message — Storage Queue Data Message Processor is missing, or the message is not yet visible"
      fi
      # Drain what we dequeued so the probe does not accumulate messages for W-52's worker.
      az storage message clear \
        --account-name "$STORAGE_ACCOUNT_NAME" --queue-endpoint "$QUEUE_ENDPOINT" \
        --auth-mode login --queue-name payrun >/dev/null 2>&1 || true
    else
      fail "QUEUE" "could not assume id-worker"
    fi
  fi
fi

# ── 6f. PROBE-REDIS ──────────────────────────────────────────────────────────
# TLS on 6380 only - redis.bicep disables the non-TLS 6379 port, and a PING that succeeded
# on 6379 would prove the wrong thing.
#
# AUTHENTICATION. Azure Cache for Redis Basic has no managed-identity data-plane path, and
# id-migration holds no control-plane role, so it cannot call `az redis list-keys`. If a
# `redis-primary-key` secret exists in Key Vault the probe authenticates with it and
# requires PONG. If it does not, the probe still connects over TLS and accepts the server's
# NOAUTH refusal as proof that a Redis server answered on the private endpoint — a
# connection failure or timeout still fails. That is weaker than the other five and is
# recorded as such; W-53 is the first genuine exercise of this path.
echo "--- PROBE-REDIS: redis-cli PING over TLS on ${REDIS_HOST}:6380"
if assert_private_address "REDIS" "$REDIS_HOST"; then
  REDIS_KEY=""
  if login_as "id-migration" "$MIGRATION_CLIENT_ID"; then
    REDIS_KEY="$(az keyvault secret show --vault-name "$KEY_VAULT_NAME" \
      --name "redis-primary-key" --query value -o tsv 2>/dev/null || true)"
  fi

  if [[ -n "$REDIS_KEY" ]]; then
    REDIS_REPLY="$(redis-cli --tls -h "$REDIS_HOST" -p 6380 -a "$REDIS_KEY" --no-auth-warning PING 2>&1)"
  else
    REDIS_REPLY="$(redis-cli --tls -h "$REDIS_HOST" -p 6380 PING 2>&1)"
  fi

  case "$REDIS_REPLY" in
    *PONG*)
      echo "PROBE-REDIS: OK"
      ;;
    *NOAUTH*|*"Authentication required"*)
      if [[ -n "$REDIS_KEY" ]]; then
        fail "REDIS" "authenticated with redis-primary-key and was still refused: ${REDIS_REPLY}"
      else
        echo "  no redis-primary-key in ${KEY_VAULT_NAME}; the server answered over TLS and demanded auth, which is the connectivity evidence this probe can obtain"
        echo "PROBE-REDIS: OK"
      fi
      ;;
    *)
      fail "REDIS" "redis-cli did not reach ${REDIS_HOST}:6380 over TLS: ${REDIS_REPLY:-<no reply>}"
      ;;
  esac
fi

echo "================================================================="
if [[ "$FAILURES" -gt 0 ]]; then
  echo "FAIL: ${FAILURES} of 6 private-path probes did not pass." >&2
  exit 1
fi
echo "All six private-path probes passed."
echo "================================================================="
