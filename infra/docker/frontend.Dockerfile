# Production frontend image — W-49.
#
# Two stages: static build, unprivileged nginx-alpine runtime.
# See docs/target-state/04-runtime-containers.md section 5.
#
# Build context is the REPOSITORY ROOT:
#   docker build -f infra/docker/frontend.Dockerfile -t infinevo-frontend .
#
# Runs as non-root user (uid 101) on port 8080.
# Environment configuration is injected at container start via env.js.
# No secrets in this image.

# ── Stage 1: build the production bundle ─────────────────────────────────────
FROM node:24-alpine AS build

WORKDIR /build

COPY code/frontend/package.json code/frontend/package-lock.json ./
RUN npm ci

COPY code/frontend/ .
RUN npm run build

# ── Stage 2: serve with unprivileged nginx ──────────────────────────────────
FROM nginxinc/nginx-unprivileged:alpine AS runtime

USER root

# Copy nginx configuration (SPA fallback, gzip, cache headers, health endpoint on 8080)
COPY infra/docker/nginx/default.conf /etc/nginx/conf.d/default.conf

# Copy built static assets with ownership for the unprivileged nginx user (uid 101)
COPY --chown=101:101 --from=build /build/dist /usr/share/nginx/html

# Copy entrypoint script (generates env.js from environment variables at start)
COPY --chown=101:101 infra/docker/frontend-entrypoint.sh /entrypoint.sh

# Ensure nginx user (101) owns the web root so entrypoint can write env.js at runtime
RUN chown -R 101:101 /usr/share/nginx/html && chmod +x /entrypoint.sh

USER 101

EXPOSE 8080

ENTRYPOINT ["/entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]
