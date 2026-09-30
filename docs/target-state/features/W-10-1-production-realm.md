# W-10.1 — Production Keycloak realm

| Field | Value |
|---|---|
| **Work item** | `W-10.1` · follow-up to `W-10` (#11) |
| **Kind** | Infra |
| **Stream / track** | C — Core platform · identity |
| **Size / skill** | M — INFRA |
| **Owner** | karma |
| **Blocked by** | nothing. `W-10`, `W-49`, `W-54`, `W-56` are on `main` |
| **Blocks** | invitations working in Azure (`W-24.2`), the set-password mail `W-46.7` tells the invitee to expect |
| **Capabilities** | `CORE-02` |
| **Decisions** | `D-21` (one realm, tenant resolved server-side) |
| **Gaps addressed** | none in `GAP_INVENTORY.md`; this closes a hole left by `W-10` |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-30 |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | a fresh Azure Keycloak starts with the `infinevo` realm, the web client and working mail | 1 |
| Frontend area | none | 1 |

---

## 1. Problem

**The repository has no production realm.** Azure Keycloak starts empty, so login, invitations and password mail depend on whatever is typed into its console by hand.

| Fact | Where |
|---|---|
| The folder for the production realm holds a placeholder only | `infra/keycloak/.gitkeep` — "Realm export and theme. W-10 fills this." |
| The deployed image bakes no realm, on purpose, until this exists | `infra/docker/keycloak.Dockerfile:55-66` |
| `W-10` rewrote the **local** realm only | `W-10-identity.md:54`; `infra/docker/keycloak/dev-realm.json` |
| The pipeline already assumes a realm named `infinevo` | `.github/workflows/deploy.yml:666-667` (issuer and key URLs) |
| The web container is given no realm and no client id, waiting for this | `.github/workflows/deploy.yml:695-702`; `infra/docker/frontend-entrypoint.sh:24-32` |
| Mail and password reset are realm settings. The local realm has both since `288f9fa`; production has no realm to hold them | `infra/docker/keycloak/dev-realm.json:7-16` |
| `W-24.2` creates users with "set your password" and asks Keycloak to mail the link | `KeycloakProvisioningServiceImpl.java:143,181` |

**Baseline** — record before changing anything.

| Command | Exit | Output |
|---|---|---|
| `ls infra/keycloak` | 0 | `.gitkeep` only |
| `grep -c "import-realm" infra/docker/keycloak.Dockerfile` | 1 | `0` |
| `curl -s -o /dev/null -w "%{http_code}" https://<fd-host>/auth/realms/infinevo/.well-known/openid-configuration` | 0 | record the code; `404` is expected where nobody made a realm by hand |

## 2. Scope

**In scope**

- `infra/keycloak/infinevo-realm.json`: the production realm, with no users and no secrets in it
- The Keycloak image imports it on first start
- Mail through Brevo SMTP, the password from Key Vault
- Password reset on
- The web client's redirect address and the web container's realm and client id, set by the pipeline
- A check, in CI, that the realm file stays free of users, secrets and `localhost`

**Out of scope**

- Changing a realm that already exists. Import runs only when the realm is absent; §3 says what to do instead. A tool that reconciles a live realm is a later ticket if it is ever needed
- The first `platform-admin` user — `W-65.2` owns the bootstrap session
- A login theme — unowned; `infra/README.md:9` lists it under `W-10`, nothing has asked for one
- The app signing in to Keycloak as the master admin — see §9 row 1
- The local realm. `dev-realm.json` is not touched here (`W-24.2` already turned its reset and mail on)

## 3. What gets built

```
Key Vault: brevo-smtp-key ──┐
                            ├─> keycloak container env (KC_SMTP_*, KC_WEB_ORIGIN)
deploy.yml: FD host ────────┘            │
                                         v
image: /opt/keycloak/data/import/infinevo-realm.json  ── start --optimized --import-realm
                                         │  (${...} placeholders resolved from env)
                                         v
realm infinevo: client infinevo-web · roles · tenant-id mapper · smtpServer · reset on
```

| File | Change |
|---|---|
| `infra/keycloak/infinevo-realm.json` | **new.** From `dev-realm.json`: realm name, roles, the `tenant-id` mapper, password policy, token lifetimes, the user-profile component. **Different:** no `users`; `sslRequired: "external"`; `resetPasswordAllowed: true`; client `infinevo-web` stays public with PKCE, redirect `${KC_WEB_ORIGIN}/*` and web origin `${KC_WEB_ORIGIN}`; `smtpServer` with host, port, user, from and password as `${KC_SMTP_HOST}`, `${KC_SMTP_PORT}`, `${KC_SMTP_USER}`, `${KC_SMTP_FROM}`, `${KC_SMTP_PASSWORD}`, `starttls: true`, `auth: true` |
| `infra/keycloak/README.md` | **new, one page.** What the file is; that import happens once; how to change a live realm (console or `kcadm.sh`, then make the same change in the file) |
| `infra/keycloak/check-realm.mjs` | **new.** Fails when the file has a `users` key, a `credentials` or `secret` key anywhere, the string `localhost`, or `sslRequired` other than `external`; and when `resetPasswordAllowed` is not `true` |
| `infra/keycloak/.gitkeep` | deleted |
| `infra/docker/keycloak.Dockerfile` | `COPY infra/keycloak/infinevo-realm.json /opt/keycloak/data/import/`; `CMD ["start", "--optimized", "--import-realm"]`; the comment at `:55-66` rewritten — the production realm is now the one baked in, the dev realm still is not |
| `infra/azure/modules/containerapps.bicep` | keycloak container: secret `brevo-smtp-key` from Key Vault, in the form the container already uses for `keycloak-admin-pw` (`:569-573`); env `KC_SMTP_HOST` (`smtp-relay.brevo.com`), `KC_SMTP_PORT` (`587`), `KC_SMTP_USER` (param `brevoSmtpLogin`), `KC_SMTP_FROM` (`notifications@infinevocloud.com`, the address the worker sends from), `KC_SMTP_PASSWORD` (secretRef) |
| `infra/azure/main.bicep`, `infra/azure/parameters/*` | pass `brevoSmtpLogin` through |
| `infra/azure/deploy.sh` | `brevo-smtp-key` added to the seeded list at `:384`, handled as `brevo-api-key` is: a placeholder until the founder stores the real key |
| `.github/workflows/deploy.yml` | keycloak app: `KC_WEB_ORIGIN=https://${FD_HOST}`. web app: `KEYCLOAK_REALM=infinevo`, `KEYCLOAK_CLIENT_ID=infinevo-web`; the comment at `:695-702` removed — this is the ticket it waited for |
| `.github/workflows/ci.yml` | the `static` job runs `node infra/keycloak/check-realm.mjs` |

**Not touched:** `infra/docker/compose.yml`, `dev-realm.json`, any backend or frontend source, any database, Front Door, the app and worker containers.

**The one thing to know about import.** `--import-realm` creates the realm when it does not exist and does nothing when it does. A later edit to the file reaches a new environment and never an existing one. The README says so in its first paragraph.

## 4. Proving it

| # | Deliberate break | What must happen |
|---|---|---|
| 1 | Add a `users` array to the realm file | `check-realm.mjs` exits 1 and names the key |
| 2 | Put `http://localhost:5173/*` in the client's redirect list | `check-realm.mjs` exits 1 |
| 3 | Start the image with `KC_WEB_ORIGIN` unset | record what Keycloak does — refuse to start, or import a literal `${KC_WEB_ORIGIN}`. If it imports the literal, add a start-up guard to the image so it refuses instead, and say so in the merge message |
| 4 | Start the image twice against one database | the second start logs that the realm exists and imports nothing; a value changed in the console between the two starts survives |

**First task, before anything else:** confirm on Keycloak 25.0 that `${ENV}` placeholders in an import file are resolved from the container's environment. The whole design rests on it. If they are not, stop and report — the fallback is rendering the file in the entrypoint, which is a different spec.

## 5. Verification

```bash
node infra/keycloak/check-realm.mjs
docker build -f infra/docker/keycloak.Dockerfile -t infinevo-keycloak:w10-1 .
docker compose -f infra/docker/compose.yml up -d postgres mail
docker run --rm -d --name kc-w10-1 --network <compose network> -p 8090:8080 \
  -e KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak \
  -e KC_DB_USERNAME=keycloak_user -e KC_DB_PASSWORD=local_keycloak_pw \
  -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=local_keycloak_pw \
  -e KC_HOSTNAME_STRICT=false \
  -e KC_WEB_ORIGIN=http://web.test \
  -e KC_SMTP_HOST=mail -e KC_SMTP_PORT=1025 -e KC_SMTP_USER=x \
  -e KC_SMTP_FROM=noreply@infinevo.local -e KC_SMTP_PASSWORD=x \
  infinevo-keycloak:w10-1
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8090/auth/realms/infinevo/.well-known/openid-configuration
az bicep build --file infra/azure/main.bicep
az bicep lint  --file infra/azure/main.bicep
```

| Check | Expected |
|---|---|
| `check-realm.mjs` | exit 0 |
| image build | succeeds |
| discovery document | `200` |
| `kcadm.sh get realms/infinevo --fields smtpServer,resetPasswordAllowed` | host `mail`, port `1025`, `resetPasswordAllowed: true`; the password shown masked |
| `kcadm.sh get clients -r infinevo -q clientId=infinevo-web` | `publicClient: true`, redirect `http://web.test/*` |
| `kcadm.sh get users -r infinevo` | `[]` |
| reset mail | "Forgot password" on the realm's login page for a user created by hand puts one mail in mailpit (`localhost:8025`) |
| `az bicep build`, `az bicep lint` | exit 0, no new warnings |

Deploying is the founder's step and is not a check here.

## 6. Gap disposition

No `GAP_INVENTORY.md` row applies. The hole this closes is `W-10`'s own: its scope line reads "Realm export rewritten" (`W-10-identity.md:54`) and only the local file was.

## 7. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Placeholders are not resolved on 25.0 | low | proved first (§4); the ticket stops if not |
| An environment already has a hand-made `infinevo` realm, so the import is skipped and nothing changes | **certain wherever someone configured one** | the README names it; the founder applies the mail and reset settings in the console once, or deletes the hand-made realm before the deploy |
| `brevo-smtp-key` is still the placeholder at first deploy | medium | Keycloak starts; mail fails with an auth error in its log. `verify-live.sh` is not extended here; the merge message lists the secret as a founder step |
| Brevo rejects the `from` address | low | it is the address the worker already sends from (`worker/.../application.yml:73`) |

## 8. Rollback

Revert the branch and redeploy the previous Keycloak image. **A revert does not remove:** the `infinevo` realm already imported into the Keycloak database, the `brevo-smtp-key` secret in Key Vault. Both are harmless left in place.

## 9. Decisions and open questions

| # | Question | Answer |
|---|---|---|
| 1 | The app signs in to Keycloak as the **master admin** to create invited users (`KeycloakProvisioningServiceImpl.java:214-221`). Should it use a narrower account? | **Open — founder's call.** A service-account client in the `infinevo` realm with only `manage-users` would be the smaller key. It needs a backend change in `core`, so it is not folded in here. If wanted, it is a `W-24.3`, and this realm file gains one confidential client |
| 2 | Realm name `infinevo` or `HRMS` (`D-21`)? | `infinevo`. The pipeline, the local realm and the frontend defaults all use it (`deploy.yml:666`, `dev-realm.json:2`, `shell/auth/keycloak.js:30`). `D-21`'s point is one realm, not its name |
| 3 | Do invited users need the `tenant_id` attribute the mapper reads? | No. With no claim the filter binds the user's single tenant from `core.user_tenant` (`TenantContextFilter.java:119-122`), and acceptance writes that row. The mapper stays for users who carry the attribute |

## 10. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `infra/keycloak`, `infra/docker` | prove placeholders (§4); realm file, README, `check-realm.mjs`; Dockerfile import; §5 local run |
| 2 | `infra/azure` | Bicep secret, env and parameter; `deploy.sh` seed entry; `az bicep build` and `lint` |
| 3 | `.github/workflows` | `deploy.yml` env on keycloak and web; `ci.yml` check step |
