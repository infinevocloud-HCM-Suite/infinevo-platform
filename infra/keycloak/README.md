# Production Keycloak Realm (`infra/keycloak`)

This directory contains the canonical production Keycloak realm definition and validation tooling for Infinevo Cloud (`W-10.1`).

| File | Purpose |
|---|---|
| `infinevo-realm.json` | Canonical production realm export baked into the production container image. |
| `check-realm.mjs` | Static validation script run in CI (`ci.yml` `static` job) to prevent leaking users or secrets. |

---

## 1. One-Time Import Behavior

The Keycloak production container image (`infra/docker/keycloak.Dockerfile`) copies `infinevo-realm.json` to `/opt/keycloak/data/import/` and boots with:
```bash
kc.sh start --optimized --import-realm
```

> [!IMPORTANT]
> **Keycloak's `--import-realm` imports only when the realm does not exist.**
> If the `infinevo` realm already exists in the target database, Keycloak skips import entirely (`IGNORE_EXISTING` strategy) and logs:
> ```text
> KC-SERVICES0030: Full model import requested. Strategy: IGNORE_EXISTING
> ```
> Therefore, updating `infinevo-realm.json` in git will apply to **new** environments on first start, but will **not** modify or overwrite an existing live realm.

---

## 2. Managing a Live Realm

To modify configuration (e.g. token lifespan, mail settings, client redirect URIs) in an environment where Keycloak has already been initialized:

1. **Option A: Keycloak Admin Console**
   - Log in to the Keycloak Admin Console at `https://<host>/auth/admin/master/console/` as `admin`.
   - Switch to the `infinevo` realm and apply the configuration change.

2. **Option B: Keycloak Admin CLI (`kcadm.sh`)**
   - Execute commands via `kcadm.sh` inside the running container:
     ```bash
     /opt/keycloak/bin/kcadm.sh config credentials \
       --server http://localhost:8080/auth \
       --realm master --user admin --password "$KEYCLOAK_ADMIN_PASSWORD"

     # Example: update realm attribute or setting
     /opt/keycloak/bin/kcadm.sh update realms/infinevo -s resetPasswordAllowed=true
     ```

3. **Synchronize back to `infinevo-realm.json`:**
   - Always make the corresponding change in `infra/keycloak/infinevo-realm.json` and commit it, so subsequent environment deployments inherit the updated configuration.
   - Run `node infra/keycloak/check-realm.mjs` to ensure no credentials or localhost URLs were accidentally committed.

---

## 3. Environment Variable Expansion

Keycloak 25.0 expands `${ENV_VAR}` placeholders at import time. In `infinevo-realm.json`:
- `${KC_WEB_ORIGIN}` expands to the frontend origin (e.g., `https://<frontdoor-host>`).
- `${KC_SMTP_HOST}`, `${KC_SMTP_PORT}`, `${KC_SMTP_USER}`, `${KC_SMTP_FROM}`, `${KC_SMTP_PASSWORD}` expand to Brevo SMTP connection parameters injected by Azure Container Apps.
