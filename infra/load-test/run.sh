#!/bin/sh
# ---------------------------------------------------------------------------
# run.sh — Runner for Infinevo Platform Load Testing (PLAT-14 / W-63)
# ---------------------------------------------------------------------------
set -eu

# Allow optional 'run-scenario' prefix from CLI instructions
if [ "${1:-}" = "run-scenario" ]; then
  shift
fi

SCENARIO="${1:-concurrent-users}"

# If running directly on a machine where script is local vs in container
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCENARIOS_DIR="${SCRIPT_DIR}/scenarios"

shift || true

case "$SCENARIO" in
  concurrent-users)
    echo "Starting 100 concurrent virtual users load test..."
    exec k6 run "${SCENARIOS_DIR}/concurrent-users.js" "$@"
    ;;
  payrun-batch)
    echo "Starting 100-employee payrun batch test..."
    exec k6 run "${SCENARIOS_DIR}/payrun-batch.js" "$@"
    ;;
  regression)
    echo "Starting full regression load test suite (concurrent-users + payrun-batch)..."
    k6 run "${SCENARIOS_DIR}/concurrent-users.js" "$@"
    k6 run "${SCENARIOS_DIR}/payrun-batch.js" "$@"
    ;;
  *)
    if [ -f "$SCENARIO" ]; then
      exec k6 run "$SCENARIO" "$@"
    else
      echo "Usage: $0 [run-scenario] [concurrent-users|payrun-batch|regression] [k6-options...]" >&2
      exit 1
    fi
    ;;
esac
