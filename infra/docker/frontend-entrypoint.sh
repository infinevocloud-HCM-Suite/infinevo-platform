#!/bin/sh
# Frontend entrypoint — generates env.js from environment variables at container start.
#
# This is what makes the same image serve every environment (04-runtime-containers.md:101).
# Vite's import.meta.env.VITE_* is baked at build time; window.__ENV is set at runtime.
# The app reads window.__ENV first, falling back to import.meta.env for local Vite dev.

set -e

# Escape a value for embedding in a double-quoted JavaScript string literal (review F-5).
#
# Without this, a value containing a double quote produced syntactically invalid JS:
# window.__ENV was then never defined and client.js fell back to /api silently, with no
# error anywhere. A value containing </script> ended the script tag and injected markup
# into every page the SPA serves.
#
# Order matters: backslashes first, or the escapes we add below get escaped again.
# Newlines are stripped rather than escaped — a multi-line URL is a mistake, not a value.
js_escape() {
  printf '%s' "$1" \
    | tr -d '\n\r' \
    | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's#</#<\\/#g'
}

# Keycloak values are emitted with EMPTY defaults, not the dev realm's names (review F-8).
# Nothing in code/frontend/src reads them yet; W-10 wires the frontend to Keycloak. When
# it does, a missing variable must produce a visibly empty value rather than silently
# falling back to `infinevo` / `infinevo-web`, which are the LOCAL realm's names.
API_BASE_URL_JS=$(js_escape "${API_BASE_URL:-}")
KEYCLOAK_URL_JS=$(js_escape "${KEYCLOAK_URL:-}")
KEYCLOAK_REALM_JS=$(js_escape "${KEYCLOAK_REALM:-}")
KEYCLOAK_CLIENT_ID_JS=$(js_escape "${KEYCLOAK_CLIENT_ID:-}")

cat > /usr/share/nginx/html/env.js <<EOF
window.__ENV = {
  API_BASE_URL: "${API_BASE_URL_JS}",
  KEYCLOAK_URL: "${KEYCLOAK_URL_JS}",
  KEYCLOAK_REALM: "${KEYCLOAK_REALM_JS}",
  KEYCLOAK_CLIENT_ID: "${KEYCLOAK_CLIENT_ID_JS}"
};
EOF

echo "env.js written with API_BASE_URL=${API_BASE_URL:-<empty>}"

exec "$@"
