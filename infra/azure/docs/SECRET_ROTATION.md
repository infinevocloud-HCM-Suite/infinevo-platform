# Secret Rotation Runbook (W-56)

> How each platform secret in `kv-infinevo-shared` is rotated, and what it costs while it
> happens. Database passwords take a short planned window; Keycloak and Brevo do not.

---

## 1. Overview

Every credential lives in Azure Key Vault (`kv-infinevo-shared`) and is resolved at runtime:

- **Service containers (`app`, `worker`, `keycloak`)** — Key Vault secret references bound
  through a User-Assigned Managed Identity holding `Key Vault Secrets User`.
- **Frontend SPA (`web`)** — zero secrets (`configuration.secrets: []`).
- **Database authentication** — `app_user` is the app's login role; `worker_user` is the
  worker's own login and inherits `app_user`'s grants and RLS policies through membership.

---

## 2. Canonical Secret Inventory

Ten secrets. `deploy.sh` seeds them all.

| Secret Name | Owner | Cadence | Description |
|---|---|---|---|
| `psql-admin-pw` | `deploy.sh` | Annual / emergency | Flexible Server administrator (`infinevo_admin`) |
| `psql-app-pw` | `deploy.sh` / `rotate-secrets.sh` | 90 days | Password for `app_user` |
| `psql-worker-pw` | `deploy.sh` / `rotate-secrets.sh` | 90 days | Password for `worker_user` |
| `psql-migration-pw` | `deploy.sh` | 90 days | Password for `migration_user` (Flyway runner) |
| `psql-readonly-pw` | `deploy.sh` | 90 days | Password for `readonly_user` (reporting) |
| `psql-keycloak-pw` | `deploy.sh` | 90 days | Password for `keycloak_user` |
| `keycloak-admin-pw` | `deploy.sh` | Initial bootstrap | Keycloak admin bootstrap password |
| `keycloak-client-secret` | `deploy.sh` / `rotate-secrets.sh` | 90 days | OAuth2 client secret for `infinevo-platform` |
| `brevo-api-key` | `deploy.sh` / `rotate-secrets.sh` | 90 days | Transactional email provider API key |
| `jwt-signing-secret` | `deploy.sh` / `rotate-secrets.sh` | 90 days | Internal token and cookie signing secret |

---

## 3. Rotation Procedures

### 3.1 PostgreSQL role passwords — windowed

**Expected outage: seconds, planned, quarterly.** Flexible Server holds one password per
role, so the `ALTER ROLE` invalidates the open HikariCP connections and in-flight requests
on that container fail until it restarts on the new secret. W-56 revision 5 accepts that
rather than carrying alternating roles to avoid it. Announce the window, then:

```bash
bash infra/azure/rotate-secrets.sh --env dev --target postgres
```

Per role (`app_user` on `app`, `worker_user` on `worker`):

1. **Transient Key Vault IP rule (`D-53`)** — the runner's `/32` egress address is added
   and revoked from an `EXIT` trap.
2. **Preflight** — read `psql-admin-pw` and prove `infinevo_admin` can authenticate.
   Nothing is written if it cannot; a half-done rotation is worse than one that refuses.
3. **`ALTER ROLE <role> WITH PASSWORD`** — the window opens here.
4. **Write the new value to Key Vault** (`psql-app-pw` / `psql-worker-pw`).
5. **Restart the revision** — it re-resolves the secret and reconnects.
6. **Verify** the revision reaches `Healthy`, failing loudly within 300s if it does not.
   The window closes.
7. **Revoke** the transient Key Vault IP rule.

If zero downtime ever becomes a requirement, the deferred dual-role scheme in the W-56
spec §0 is the answer and is purely additive.

### 3.2 Keycloak client secret — zero downtime

Keycloak holds a primary and a secondary client secret at once, so no window is needed.

1. Generate a secondary client secret via the Admin console or Admin API.
2. Update Key Vault `keycloak-client-secret`.
3. Roll the `app` revision.
4. Verify token exchanges succeed on the new revision.
5. Promote the secondary secret to primary and delete the old one.

### 3.3 Brevo API key — zero downtime

Brevo allows several live API keys, so again no window.

1. Generate a new API key in the Brevo console.
2. Update Key Vault `brevo-api-key`.
3. Roll the `app` and `worker` revisions.
4. Verify transactional email delivery.
5. Revoke the old key in the Brevo console.

### 3.4 Keycloak admin password

Keycloak reads `KEYCLOAK_ADMIN_PASSWORD` only when the database is empty. Changing the
Key Vault value on a running instance does nothing. Rotate with
`kcadm.sh set-password -r master --username admin --new-password "<new-pw>"`, then update
`keycloak-admin-pw` so the record matches.

---

## 4. Key Vault network isolation (D-53)

Key Vault runs `defaultAction: 'Deny'`. Data-plane calls from an operator or CI runner need
a transient `/32` rule. `rotate-secrets.sh` discovers its egress IP, adds the rule, and
registers `trap cleanup EXIT ERR` so the rule is revoked on every catchable exit path.

---

## 5. Rollback

1. **Revision rollback** — if a restarted revision fails its probes, Container Apps leaves
   the previous healthy revision serving 100% of traffic.
2. **Key Vault version rollback** — revert the secret to its previous version GUID.
3. **Password rollback** — reset the role with `psql-admin-pw` to match the Key Vault value
   you reverted to, then restart the revision. Same short window, run backwards.
4. **Emergency reset** — on any desynchronisation, connect as `infinevo_admin` with
   `psql-admin-pw` and set the role password directly to the current Key Vault value.
