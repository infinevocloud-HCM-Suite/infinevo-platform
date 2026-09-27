# W-57.2 — Front Door origin check (`X-Azure-FDID`)

| Field | Value |
|---|---|
| **Work item** | `W-57.2` · issue [#77](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/77) (part 2 of 2) |
| **Kind** | Security |
| **Stream / track** | Track S — Security and operations |
| **Wave** | 2 |
| **Size / skill** | S — SEC |
| **Owner** | unassigned |
| **Blocked by** | `W-57.1` (shares `ResourceServerConfig`) · `W-51` (merged) |
| **Blocks** | `W-64` Penetration test |
| **Capabilities** | `PLAT-09` |
| **Decisions** | `D-54` origin is an IP boundary until `W-57` · `D-51` Front Door Standard · `D-22` |
| **Gaps addressed** | none in `GAP_INVENTORY.md`; closes the `W-51` deferral (`W-51-networking-and-identity.md:166-168,187,665-668`) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-27 |

## Size cap

| Axis | This ticket |
|---|---|
| Backend module | `shared` only |
| Flyway migration | none |
| Externally testable behaviour | one: a request to `app` whose `X-Azure-FDID` header is missing or wrong is refused with `403` when the check is armed |
| Frontend area | none |

The Bicep change (pass the Front Door ID into the container) is the other half of the same
behaviour, not a second one.

---

## 1. Problem

The Container Apps ingress admits any request from an Azure Front Door backend address
(`infra/azure/modules/containerapps.bicep:56-79,205,418,487`). That is every Front Door in Azure, not
ours. `D-54` records it as "an IP boundary, not authentication" and defers the header check to `W-57`.
Front Door adds `X-Azure-FDID: <profile id>` to every request it forwards, and the profile id is a GUID
no one else can present, so checking it in the application closes the gap.

Today nothing reads the header (`git grep -n FDID -- code/` prints nothing) and the Front Door id is
not available to the app: `frontdoor.bicep` outputs name and host only (`infra/azure/modules/frontdoor.bicep:541-545`)
and the `app` environment carries no such variable (`containerapps.bicep:263-289`).

**Baseline**

| Command | Exit | Output |
|---|---|---|
| `git grep -n "FDID" -- code/` | 1 | nothing |
| `git grep -n "frontDoorId" -- infra/` | 1 | nothing |
| `az bicep build --file infra/azure/main.bicep` | 0 | builds clean |

## 2. Scope

**In scope**

- `frontdoor.bicep` outputs `profile.properties.frontDoorId`; `main.bicep` passes it to `containerapps.bicep`; the `app` container gets env `FRONT_DOOR_ID`.
- `shared/security`: `FrontDoorOriginFilter`, armed when `infinevo.security.front-door-id` is set, refusing a request whose `X-Azure-FDID` is missing or different with `403` and the shared error envelope. Health paths are exempt: the Container Apps probe does not come through Front Door.
- Property `infinevo.security.front-door-id: ${FRONT_DOOR_ID:}` in `app`'s `application.yml`. Empty means disarmed: local compose and tests have no Front Door.
- Startup log line stating armed or disarmed, at `WARN` when disarmed and the `azure` profile is active.
- `07-decisions.md`: `D-54` row gains "closed by `W-57.2`". `W-51` §out-of-scope rows `:187,193` point here.

**Out of scope**

- The `worker` container: it has no ingress (`containerapps.bicep:329`).
- Keycloak's container: it is reached through Front Door too, but its config is not ours to filter in Java. Its `ipSecurityRestrictions` stays the boundary; noted for `W-64`.
- Replacing `ipSecurityRestrictions`. Both layers stay (`D-54`: defence in depth).
- Refusing the header for the signed download endpoint. It is an application endpoint like any other and is checked.

## 3. What gets built

```
Front Door ──X-Azure-FDID: <guid>──► Container Apps ingress (IP allow list, W-51)
                                        └──► FrontDoorOriginFilter (armed iff FRONT_DOOR_ID set)
                                                header == FRONT_DOOR_ID → continue to ResourceServerConfig
                                                else                   → 403 origin_not_front_door
                                                /actuator/health/**    → skip
```

| File | Change |
|---|---|
| `infra/azure/modules/frontdoor.bicep` | `output frontDoorId string = profile.properties.frontDoorId` after `:545` |
| `infra/azure/main.bicep` | New param on the `containerApps` module call (`:329`): `frontDoorId: frontDoor.outputs.frontDoorId`. **Check the dependency direction**: `containerApps` is declared before `frontDoor` (`:329` vs `:449`) and Front Door's origin needs the app FQDN. If that makes a cycle, pass the id as a `main.bicep` parameter that `deploy.sh` reads with `az afd profile show --query frontDoorId` after the first deploy, and document the two-pass in `infra/README.md`. Decide by trying the build; record which in §10 |
| `infra/azure/modules/containerapps.bicep` | `param frontDoorId string = ''`; `app` env gains `{ name: 'FRONT_DOOR_ID', value: frontDoorId }` beside `:263-289`. Not on `worker`. Update the comment at `:79` |
| `code/backend/shared/src/main/java/com/infinevo/shared/security/FrontDoorOriginFilter.java` | **New.** `OncePerRequestFilter`, ordered before the security filter chain (`SecurityProperties.DEFAULT_FILTER_ORDER - 10`, the mirror of `TenantBindingAutoConfiguration.java:55`). Constant-time compare (`MessageDigest.isEqual`). Never logs the header value |
| `code/backend/shared/src/main/java/com/infinevo/shared/security/FrontDoorOriginProperties.java` | **New.** `@ConfigurationProperties("infinevo.security")`, `frontDoorId` nullable |
| `code/backend/shared/src/main/java/com/infinevo/shared/security/ResourceServerConfig.java` | Register the filter bean conditionally on the property being non-blank (`@ConditionalOnProperty`); log armed/disarmed at startup |
| `code/backend/app/src/main/resources/application.yml` | `infinevo.security.front-door-id: ${FRONT_DOOR_ID:}` |
| `code/backend/shared/src/test/java/com/infinevo/shared/security/FrontDoorOriginFilterTest.java` | **New.** Armed: missing header `403`, wrong header `403`, right header passes, health path passes with no header. Disarmed: bean absent, any request passes |
| `docs/target-state/07-decisions.md`, `features/W-51-networking-and-identity.md` | `D-54` closed by `W-57.2`; deferral rows point here |

**Not touched:** `core`, `hrms`, `payroll`, `worker`, `migration`, `frontend`, `infra/docker/`
(compose never sets `FRONT_DOOR_ID`, so local stays disarmed), Keycloak, any table.

**Design points settled here**

1. **Disarmed by absence, armed by presence.** The only environment with a Front Door is Azure, and `deploy.sh` is the only thing that knows the id. A missing value in Azure is loud (the `WARN`), never a crash: a crash-looping app after a Bicep slip is worse than one deploy with the IP boundary alone.
2. **`403`, not `401`.** The request may carry a perfectly good token; what is wrong is where it came from.
3. **Before the security chain, not inside it.** Refusing a forged origin before the JWT is even decoded keeps the check cheap and keeps `ResourceServerConfig`'s `permitAll` list untouched by `W-57.1`'s audit, which tests token presence, not origin.
4. **Health is exempt** because the Container Apps liveness probe hits the ingress directly. Everything else, including `PublicEndpoints`, is checked.

## 4. Proving it

| # | Deliberate break | What must happen |
|---|---|---|
| 1 | Run `app` with `FRONT_DOOR_ID=11111111-...` and `curl -H "X-Azure-FDID: 22222222-..." /api/v1/tenants/me` with a valid token | `403` `{ "code": "origin_not_front_door" }` |
| 2 | Same, header omitted | `403` |
| 3 | Same, `curl /actuator/health/liveness` with no header | `200` |
| 4 | Unset `FRONT_DOOR_ID`, active profile `azure` | startup log `WARN FrontDoorOriginFilter disarmed: FRONT_DOOR_ID not set`; requests pass |
| 5 | Remove the `frontDoorId` output from `frontdoor.bicep` | `az bicep build` fails on `main.bicep`: property does not exist |
| 6 | Add the env to `worker` | Reviewer refuses: worker has no ingress (`containerapps.bicep:329`) |

## 5. Verification

```bash
cd code/backend
./mvnw -B test -pl shared -Dtest=FrontDoorOriginFilterTest
./mvnw -B clean verify
az bicep build --file infra/azure/main.bicep --stdout > /dev/null
az bicep lint  --file infra/azure/main.bicep
git grep -n "frontDoorId\|FRONT_DOOR_ID" -- infra/ code/
```

| Check | Expected | Result |
|---|---|---|
| `FrontDoorOriginFilterTest` | 6 tests, 0 failures | |
| `mvnw clean verify` | `BUILD SUCCESS`; `W-57.1`'s `PublicEndpointAuditIT` still passes (filter disarmed in tests) | |
| `az bicep build` / `lint` | exit 0, no warnings above the current count | |
| `git grep` | `frontdoor.bicep` output, `main.bicep` param pass, `containerapps.bicep` param + env, `application.yml`, the two Java files | |
| Live check (founder, after deploy) | `curl https://<fd-host>/api/v1/tenants/me -H "Authorization: Bearer …"` is `200`; the same against the Container App FQDN directly is refused by the IP boundary, and from an allowed address without the header is `403` | not run by the harness (#124) |
| `node .claude/scripts/check-done.mjs W-57.2` | 5/5 | |

## 6. Gap disposition

| Gap | Disposition |
|---|---|
| none in `GAP_INVENTORY.md` | The deferral being closed is `D-54` / `W-51-networking-and-identity.md:166-168`, a target-state item, not a legacy gap |

## 7. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Bicep cycle: Front Door needs the app FQDN, the app needs the Front Door id | Medium | Two options in §3; whichever builds is recorded in §10. `frontDoorId` is a stable property of the profile, so the two-pass reading in `deploy.sh` is a one-line `az afd profile show` |
| Header spoofed by a client behind Front Door | None | Front Door overwrites `X-Azure-FDID` on the way through; only a request that bypasses Front Door can set it, and the IP boundary already stops those unless they come from another Front Door |
| Local developers see `403` | None | Disarmed unless `FRONT_DOOR_ID` is set; compose never sets it |
| Never proven live | Certain until #124 | Same standing as every `W-50`/`W-51` item; listed in §5 as the founder's check |

## 8. Rollback

Revert the commit. Unsetting `FRONT_DOOR_ID` on the running Container App disarms the check without
a redeploy of code. No resource, secret or table is created.

## 9. Done when

1. `FrontDoorOriginFilter` on `main` with the six tests passing.
2. The `app` Container App receives `FRONT_DOOR_ID` from Bicep; `az bicep build` and `lint` clean.
3. Startup log states armed or disarmed; disarmed under `azure` profile is `WARN`.
4. `D-54` and `W-51`'s deferral rows point to this ticket as closed.
5. `check-done.mjs W-57.2` 5/5, CI green.

## 10. Decisions — settled at writing, 2026-09-27

1. **Filter or Spring Security `RequestMatcher`?** Filter. A matcher inside `authorizeHttpRequests` answers `403` only after the JWT is decoded, and interleaves with `W-57.1`'s list.
2. **Fail closed or fail open when the id is missing in Azure?** Open with a `WARN`, because the IP boundary stays and a crash loop is the worse outcome for a check no one has run live yet. Revisit at `W-64` once the perimeter is tested.
3. **Bicep wiring direction** — decided by the developer at build time per §3; record here.
