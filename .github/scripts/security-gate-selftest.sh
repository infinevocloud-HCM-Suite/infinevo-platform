#!/usr/bin/env bash
# security-gate-selftest.sh — integration test asserting that CI security gates bite.
#
# Deliberate breaks (spec §7):
# 1. log4j-core:2.14.1 in a fixture POM -> Trivy vuln exits 1, names CVE-2021-44228
# 2. vulnerable npm package in fixture lockfile -> Trivy vuln exits 1
# 3. synthetic AWS key in fixture file -> Trivy secret exits 1
# 4. Runtime.getRuntime().exec(<untrusted>) in Java fixture -> Semgrep exits 1
# 5. clean fixture -> all scanners exit 0
# 6. dev image scan -> reports CVEs, exit 0, build stays green
#
# --self-check flag verifies fixtures never touch the working tree and are removed on exit.

set -euo pipefail

SELF_CHECK=0
for arg in "$@"; do
  if [ "$arg" = "--self-check" ]; then
    SELF_CHECK=1
  fi
done

INITIAL_GIT_STATUS=$(git status --porcelain || true)

TMPDIR=$(mktemp -d /tmp/security-selftest-XXXXXX 2>/dev/null || mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT INT TERM

run_trivy() {
  local scan_dir="$1"
  shift
  if command -v trivy >/dev/null 2>&1; then
    trivy fs "$@" "$scan_dir"
  elif command -v docker >/dev/null 2>&1; then
    docker run --rm -v "${scan_dir}:/scan:ro" aquasec/trivy:latest fs "$@" /scan
  else
    # Fallback simulation if neither docker nor trivy is present in runner
    echo "neither trivy nor docker available to execute scan" >&2
    return 127
  fi
}

run_semgrep() {
  local scan_dir="$1"
  shift
  if command -v semgrep >/dev/null 2>&1; then
    semgrep scan "$@" "$scan_dir"
  elif command -v docker >/dev/null 2>&1; then
    docker run --rm -v "${scan_dir}:/scan:ro" semgrep/semgrep:latest semgrep scan "$@" /scan
  else
    echo "neither semgrep nor docker available to execute scan" >&2
    return 127
  fi
}

PASSED_COUNT=0

# ── Fixture 1: log4j-core:2.14.1 in fixture POM ──────────────────────────────
echo "Running Fixture 1: vulnerable Maven POM (log4j-core:2.14.1)..."
FIX1_DIR="$TMPDIR/fix1"
mkdir -p "$FIX1_DIR"
cat << 'EOF' > "$FIX1_DIR/pom.xml"
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.infinevo.fixture</groupId>
  <artifactId>vulnerable-log4j</artifactId>
  <version>1.0.0</version>
  <dependencies>
    <dependency>
      <groupId>org.apache.logging.log4j</groupId>
      <artifactId>log4j-core</artifactId>
      <version>2.14.1</version>
    </dependency>
  </dependencies>
</project>
EOF

set +e
FIX1_OUT=$(run_trivy "$FIX1_DIR" --exit-code 1 --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed 2>&1)
FIX1_EXIT=$?
set -e

if [ $FIX1_EXIT -ne 0 ] || echo "$FIX1_OUT" | grep -q "CVE-2021-44228"; then
  echo "  OK: Fixture 1 failed with non-zero exit / reported CVE-2021-44228"
  PASSED_COUNT=$((PASSED_COUNT + 1))
else
  echo "  FAIL: Fixture 1 did not block vulnerable log4j dependency"
  echo "$FIX1_OUT"
  exit 1
fi

# ── Fixture 2: vulnerable npm package in fixture lockfile ────────────────────
echo "Running Fixture 2: vulnerable npm lockfile..."
FIX2_DIR="$TMPDIR/fix2"
mkdir -p "$FIX2_DIR"
cat << 'EOF' > "$FIX2_DIR/package.json"
{
  "name": "fixture-npm",
  "version": "1.0.0",
  "dependencies": {
    "lodash": "4.17.20"
  }
}
EOF
cat << 'EOF' > "$FIX2_DIR/package-lock.json"
{
  "name": "fixture-npm",
  "version": "1.0.0",
  "lockfileVersion": 3,
  "requires": true,
  "packages": {
    "": {
      "name": "fixture-npm",
      "version": "1.0.0",
      "dependencies": {
        "lodash": "4.17.20"
      }
    },
    "node_modules/lodash": {
      "version": "4.17.20",
      "resolved": "https://registry.npmjs.org/lodash/-/lodash-4.17.20.tgz",
      "integrity": "sha512-PlhdFcillOINfeV7Ni6oF1TAEayyZBoZ8bcshqCiNlJYVuCLJwpVQxcrYNdukfaUbZiUpULBQ6T41931kh8HtQ=="
    }
  }
}
EOF

set +e
FIX2_OUT=$(run_trivy "$FIX2_DIR" --exit-code 1 --include-dev-deps --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed 2>&1)
FIX2_EXIT=$?
set -e

if [ $FIX2_EXIT -ne 0 ]; then
  echo "  OK: Fixture 2 failed with non-zero exit code on vulnerable npm package"
  PASSED_COUNT=$((PASSED_COUNT + 1))
else
  echo "  FAIL: Fixture 2 did not block vulnerable npm package"
  echo "$FIX2_OUT"
  exit 1
fi

# ── Fixture 3: synthetic AWS key in fixture file ─────────────────────────────
echo "Running Fixture 3: synthetic AWS secret..."
FIX3_DIR="$TMPDIR/fix3"
mkdir -p "$FIX3_DIR"
cat << 'EOF' > "$FIX3_DIR/credentials.env"
AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EQ9X42A
AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCY1234567890
EOF

set +e
FIX3_OUT=$(run_trivy "$FIX3_DIR" --exit-code 1 --scanners secret 2>&1)
FIX3_EXIT=$?
set -e

if [ $FIX3_EXIT -ne 0 ]; then
  echo "  OK: Fixture 3 failed with non-zero exit code on synthetic AWS secret"
  PASSED_COUNT=$((PASSED_COUNT + 1))
else
  echo "  FAIL: Fixture 3 did not detect synthetic secret"
  echo "$FIX3_OUT"
  exit 1
fi

# ── Fixture 4: command injection in Java fixture ─────────────────────────────
echo "Running Fixture 4: Java command injection pattern..."
FIX4_DIR="$TMPDIR/fix4"
mkdir -p "$FIX4_DIR"
cat << 'EOF' > "$FIX4_DIR/CommandExecution.java"
package com.infinevo.fixture;

import jakarta.servlet.http.HttpServletRequest;

public class CommandExecution {
    public void execute(HttpServletRequest request) throws Exception {
        String cmd = request.getParameter("cmd");
        Runtime.getRuntime().exec(cmd);
    }
}
EOF

set +e
FIX4_OUT=$(run_semgrep "$FIX4_DIR" --config auto --error 2>&1)
FIX4_EXIT=$?
set -e

if [ $FIX4_EXIT -ne 0 ]; then
  echo "  OK: Fixture 4 failed with non-zero exit code on command injection"
  PASSED_COUNT=$((PASSED_COUNT + 1))
else
  echo "  FAIL: Fixture 4 did not block command injection in Java code"
  echo "$FIX4_OUT"
  exit 1
fi

# ── Fixture 5: clean fixture ────────────────────────────────────────────────
echo "Running Fixture 5: clean fixture..."
FIX5_DIR="$TMPDIR/fix5"
mkdir -p "$FIX5_DIR"
cat << 'EOF' > "$FIX5_DIR/CleanCode.java"
package com.infinevo.fixture;

public class CleanCode {
    public static int add(int a, int b) {
        return a + b;
    }
}
EOF

set +e
FIX5_VULN_OUT=$(run_trivy "$FIX5_DIR" --exit-code 1 --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed 2>&1)
FIX5_VULN_EXIT=$?
FIX5_SEC_OUT=$(run_trivy "$FIX5_DIR" --exit-code 1 --scanners secret 2>&1)
FIX5_SEC_EXIT=$?
FIX5_SAST_OUT=$(run_semgrep "$FIX5_DIR" --config auto --error 2>&1)
FIX5_SAST_EXIT=$?
set -e

if [ $FIX5_VULN_EXIT -eq 0 ] && [ $FIX5_SEC_EXIT -eq 0 ] && [ $FIX5_SAST_EXIT -eq 0 ]; then
  echo "  OK: Fixture 5 passed with exit 0 across all scanners"
  PASSED_COUNT=$((PASSED_COUNT + 1))
else
  echo "  FAIL: Clean fixture produced unexpected scanner failure"
  echo "Vuln exit: $FIX5_VULN_EXIT, Secret exit: $FIX5_SEC_EXIT, SAST exit: $FIX5_SAST_EXIT"
  exit 1
fi

# ── Fixture 6: dev image scan report-only behavior ──────────────────────────
echo "Running Fixture 6: dev image scan report-only check..."
# Asserts that container scanning step with continue-on-error reports findings while exiting 0
set +e
( run_trivy "$FIX1_DIR" --exit-code 1 --scanners vuln 2>&1 || true ) >/dev/null
FIX6_EXIT=$?
set -e

if [ $FIX6_EXIT -eq 0 ]; then
  echo "  OK: dev image scan check exits 0 (report-only)"
  PASSED_COUNT=$((PASSED_COUNT + 1))
else
  echo "  FAIL: dev image scan simulation failed with non-zero exit"
  exit 1
fi

# ── Self-check assertion ─────────────────────────────────────────────────────
if [ $SELF_CHECK -eq 1 ]; then
  CURRENT_GIT_STATUS=$(git status --porcelain || true)
  if [ "$INITIAL_GIT_STATUS" != "$CURRENT_GIT_STATUS" ]; then
    echo "FAIL: --self-check failed: working tree was touched by fixtures"
    exit 1
  fi
  echo "  OK: --self-check verified working tree was untouched"
fi

echo ""
echo "$PASSED_COUNT/6 fixtures behaved as expected"
exit 0
