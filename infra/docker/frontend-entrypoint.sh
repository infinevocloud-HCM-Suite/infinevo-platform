#!/bin/sh
# Frontend entrypoint — generates env.js from environment variables at container start.
#
# This is what makes the same image serve every environment (04-runtime-containers.md:101).
# Vite's import.meta.env.VITE_* is baked at build time; window.__ENV is set at runtime.
# The app reads window.__ENV first, falling back to import.meta.env for local Vite dev.

set -e

cat > /usr/share/nginx/html/env.js <<EOF
window.__ENV = {
  API_BASE_URL: "${API_BASE_URL:-}",
  KEYCLOAK_URL: "${KEYCLOAK_URL:-}",
  KEYCLOAK_REALM: "${KEYCLOAK_REALM:-infinevo}",
  KEYCLOAK_CLIENT_ID: "${KEYCLOAK_CLIENT_ID:-infinevo-web}"
};
EOF

echo "env.js written with API_BASE_URL=${API_BASE_URL:-<empty>}"

exec "$@"
