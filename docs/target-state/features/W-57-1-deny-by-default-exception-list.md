# W-57.1 — Deny-by-default: the exception list and the build-time check

| Field | Value |
|---|---|
| **Work item** | `W-57.1` · issue [#77](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/77) (part 1 of 2) |
| **Kind** | Security |
| **Stream / track** | Track S — Security and operations |
| **Wave** | 2 |
| **Size / skill** | S — SEC |
| **Owner** | unassigned |
| **Blocked by** | `W-10` (merged) |
| **Blocks** | `W-64` Penetration test · `W-57.2` (builds on `PublicEndpoints`) |
| **Capabilities** | `PLAT-09` |
| **Decisions** | `D-22` deny-by-default, no `permitAll()` on a prefix |
| **Gaps addressed** | `DEBT-023` discounted · `DEBT-033` deferred · `BUG-001` discounted |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-27 |

## Size cap

| Axis | This ticket |
|---|---|
| Backend module | `shared` only. The check itself lives in `app`'s test tree because only `app` sees every controller |
| Flyway migration | none |
| Externally testable behaviour | one: an endpoint reachable without a token that is not on the list fails `mvnw verify` |
| Frontend area | none |

`W-57` as written also carried the `X-Azure-FDID` origin check. That is a second behaviour with an
infra change of its own, so it is `W-57.2`.

---

## 1. Problem

The platform already denies by default. Only the health probe is open
(`code/backend/shared/src/main/java/com/infinevo/shared/security/ResourceServerConfig.java:42-45,73-76`),
and a test proves the other actuator paths need a token
(`code/backend/shared/src/test/java/com/infinevo/shared/security/ResourceServerConfigTest.java:85-91`).

What is missing is the part rule 3 of `11-ways-of-working.md:70,76-77` waits for: **nothing refuses a
new public endpoint.** A developer can add a `permitAll()` line, or a controller whose guard is
misconfigured, and the build stays green. `10-scoping.md:268` says items 3 and 4 of the definition of
done are checked by review until `W-57` exists.

The exception list itself is half-built on an unmerged branch. `W-21` on `dev-devashis` adds
`PublicEndpoints` in `shared/security` — exact `/api/v1` paths only, a static check that refuses a
pattern — and wires it into `ResourceServerConfig` and `TenantContextFilter`
(branch `origin/dev-devashis`, `code/backend/shared/src/main/java/com/infinevo/shared/security/PublicEndpoints.java`).
Its one entry is `GET /api/v1/documents/download`, the signed payslip link, served by
`DocumentDownloadController` with the HMAC token as the authorisation.

**Baseline** on `main`:

| Command | Exit | Output |
|---|---|---|
| `git grep -n "permitAll" -- code/backend/*/src/main` | 0 | 3 lines: `ResourceServerConfig.java:72,74`, `TenantBindingAutoConfiguration.java:86` |
| `ls code/backend/shared/src/main/java/com/infinevo/shared/security/PublicEndpoints.java` | 1 | not on `main` |
| `./mvnw -B verify -pl app -am -Dit.test=PublicEndpointAuditIT` | — | no such test |

## 2. Scope

**In scope**

- `PublicEndpoints` on `main`, in `shared/security`, in the exact shape `W-21`'s branch has it, so whichever ticket merges second rebases onto an identical file.
- `ResourceServerConfig` permits `PublicEndpoints.paths()` and nothing else beyond health and error dispatch. `TenantContextFilter` exempts the same list.
- An integration test in `app`, `PublicEndpointAuditIT`, that enumerates every mapped handler in the running application, calls each without a token, and fails if any answers other than `401` unless its path is in `PublicEndpoints` or is a health path. It also fails if a `PublicEndpoints` entry has no handler.
- A CI `static` step that fails if `permitAll(` appears in any main-source file other than `ResourceServerConfig.java` and `TenantBindingAutoConfiguration.java`.
- `docs/target-state/11-ways-of-working.md:76-77` and `10-scoping.md:268` updated to say rule 3 is now enforced. The `D-22` row gains "enforced by `PublicEndpointAuditIT`".

**Out of scope**

- `X-Azure-FDID` origin check — `W-57.2`.
- The download endpoint itself and the HMAC token — `W-21`.
- Rate limiting of public endpoints — Front Door WAF per-IP rule, `D-51`, tested in `W-64`.
- `jwt-signing-secret` consumer named in `W-56-secrets.md:122,145`. The platform's bearer tokens are Keycloak's (`W-10`); the only internal signing is `W-21`'s HMAC, which reads that secret. Nothing here signs anything. Recorded in §10.
- Per-endpoint `@RequiresAction` coverage (an authenticated endpoint with no permission code) — `W-58`'s enforcement scope, not this ticket's.

## 3. What gets built

```
request ──► ResourceServerConfig ──► health? error dispatch? PublicEndpoints? ──► permit
                                └──► anything else ──────────────────────────► bearer JWT or 401

mvnw verify ──► PublicEndpointAuditIT (app) ──► for each handler mapping: GET/POST… no token
                                                  path in PublicEndpoints ∪ health → must not be 401
                                                  anything else                      → must be 401
                                                  PublicEndpoints entry with no handler → fail
CI static  ──► permitAll( outside the two allowed files → fail
```

| File | Change |
|---|---|
| `code/backend/shared/src/main/java/com/infinevo/shared/security/PublicEndpoints.java` | **New.** Copy from `origin/dev-devashis` verbatim, including the static check that refuses `*`, `{` and any path outside `/api/v1/`. If `W-21` is already on `main`, no change |
| `code/backend/shared/src/main/java/com/infinevo/shared/security/ResourceServerConfig.java` | Add `.requestMatchers(PublicEndpoints.paths()).permitAll()` after the health matcher (`:73-74`). Javadoc at `:36-40` says application exceptions go in `PublicEndpoints`, never here |
| `code/backend/shared/src/main/java/com/infinevo/shared/tenant/TenantContextFilter.java` | `shouldNotFilter` (`:72-74`) also returns true for an exact match in `PublicEndpoints.PATHS`. The public endpoint binds its own tenant from its token |
| `code/backend/shared/src/test/java/com/infinevo/shared/security/PublicEndpointsTest.java` | **New.** A pattern or a non-`/api/v1` path in the list throws at class load; `paths()` equals `PATHS` |
| `code/backend/shared/src/test/java/com/infinevo/shared/security/ResourceServerConfigTest.java` | Add: a path in `PublicEndpoints` answers without a token; the same path with a trailing segment is `401` |
| `code/backend/app/src/test/java/com/infinevo/app/PublicEndpointAuditIT.java` | **New.** The build-time check. `@SpringBootTest` + `@AutoConfigureMockMvc` on the `AbstractIntegrationTest` base (`JobStatusGuardIT.java:9-11,25-26`). Reads `RequestMappingHandlerMapping.getHandlerMethods()` the way `NavigationCatalogueValidator` does (`code/backend/core/src/main/java/com/infinevo/core/navigation/NavigationCatalogueValidator.java:29-35`). For each pattern and method: substitute a UUID for every `{var}`, send with no `Authorization` header, assert per §4. Prints the full table of path → status on failure |
| `.github/workflows/ci.yml` | New `static` step "permitAll only in the two security configs" next to "ddl-auto set nowhere" (`:292`). `git grep -l 'permitAll(' -- 'code/backend/*/src/main'` minus the two allowed files must be empty |
| `docs/target-state/11-ways-of-working.md`, `10-scoping.md`, `07-decisions.md` (`D-22` row) | Rule 3 now enforced; name the test and the CI step |

**Not touched:** `core`, `hrms`, `payroll`, `worker`, `migration`, `frontend`, `infra/`,
Keycloak, any controller. No table, no migration, no property file.

**Design points settled here**

1. **The exception list is code, not configuration.** A YAML list can be edited in an environment without a review. `PublicEndpoints` changes only through a pull request, and the test that walks it runs in that pull request.
2. **Exact paths only.** `D-22` forbids a prefix grant. The static block in `PublicEndpoints` makes a prefix a class-load failure, so the application will not even start with one.
3. **Health stays in `ResourceServerConfig`, not in `PublicEndpoints`.** Health is infrastructure and is a pattern (`/actuator/health/**`); `PublicEndpoints` holds application endpoints and refuses patterns. The audit knows both.
4. **The audit answers `401`, not `403`.** A missing token must be an authentication failure. A `403` from an unlisted path means something authenticated the request and then refused it, which is a different bug; the audit fails on it too, with the status in the message.
5. **Error dispatch stays permitted** (`ResourceServerConfig.java:64-72`). The audit never sends an `ERROR` dispatch, so it neither tests nor loosens that.

## 4. Proving it

| # | Deliberate break | What must happen |
|---|---|---|
| 1 | Add `@GetMapping("/api/v1/ping")` returning `200` to any controller, with no `@RequiresAction`, and do not list it | The audit **passes** and its table shows `GET /api/v1/ping → 401`. The resource server still guards an unlisted path, so the default is closed. Record that line as evidence; this break is the control for the others |
| 2 | Add `.requestMatchers("/api/v1/ping").permitAll()` in `ResourceServerConfig` for the endpoint from break 1 | Audit fails: `/api/v1/ping GET is reachable without a token and is not in PublicEndpoints` |
| 3 | Add `.requestMatchers("/api/v1/ping").permitAll()` in a new `@Configuration` in `core` instead | CI `static` step fails naming the file. Audit also fails as in break 2 |
| 4 | Add `"/api/v1/ping"` to `PublicEndpoints.PATHS` without a handler | Audit fails: `PublicEndpoints entry /api/v1/ping has no handler — dead exception` |
| 5 | Add `"/api/v1/documents/**"` to `PublicEndpoints.PATHS` | `PublicEndpointsTest` fails and the application context refuses to start: `holds exact /api/v1 paths only, never a pattern` |
| 6 | Remove `HEALTH_SUBPATHS` from `ResourceServerConfig` | Existing `ResourceServerConfigTest` fails on `/actuator/health/liveness` |

Run each on a throwaway branch, paste the failing line into the merge commit message, never merge
the breaks. Break 1 is kept deliberately: it documents that the default is closed, so the audit's
job is the exceptions, not the rule.

## 5. Verification

```bash
cd code/backend
./mvnw -B clean verify                                   # whole suite, includes PublicEndpointAuditIT
./mvnw -B verify -pl app -am -Dit.test=PublicEndpointAuditIT -Dsurefire.skip=true
./mvnw -B test  -pl shared -Dtest='PublicEndpointsTest,ResourceServerConfigTest'
git grep -l 'permitAll(' -- 'code/backend/*/src/main' \
  | grep -vE 'security/ResourceServerConfig.java$|tenant/TenantBindingAutoConfiguration.java$'
```

| Check | Expected | Result |
|---|---|---|
| `mvnw clean verify` | `BUILD SUCCESS`, `PublicEndpointAuditIT` runs `1` test, `0` failures | |
| Audit output on success | one line per mapping: `METHOD path → 401` for every `/api/v1` path except `/api/v1/documents/download` (when `W-21` is on `main`) | |
| `PublicEndpointsTest` | 3 tests, 0 failures | |
| `ResourceServerConfigTest` | existing 5 + 2 new, 0 failures | |
| `git grep` line above | prints nothing, exit `1` | |
| CI `static` job | new step green on the branch; red on break 3 | |
| `node .claude/scripts/check-done.mjs W-57.1` | 5/5 gates | |

## 6. Gap disposition

| Gap | Disposition |
|---|---|
| `DEBT-023` unguarded `/api/test/**` in legacy Payroll | Discounted. Nothing under `/api/test` is ported; the audit would refuse it if it were |
| `DEBT-033` HRMS↔Payroll shared `X-API-KEY` in plaintext | Deferred. No service-to-service call exists in the platform yet; when one does, it authenticates with a Keycloak client credential, never a listed path. `W-59-scanning.md:367` routed it here; the disposition is the same |
| `BUG-001` two auth systems | Discounted. Fixed by `W-10`; this ticket adds nothing to it |

## 7. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The audit is slow: one MockMvc call per mapping | Low | Under 100 mappings today, one context start. Well under a second of calls |
| A handler mapping with a `@RequestBody` answers `400` before security | None | Spring Security runs before the dispatcher; an unauthenticated request never reaches argument binding. The test asserts `401` regardless of method or body |
| `W-21` and `W-57.1` both add `PublicEndpoints` | Certain | Identical file by instruction. The second to merge deletes its copy and keeps the other's. `ResourceServerConfig` and `TenantContextFilter` edits are the same three lines on both branches |
| The audit passes on `main` but a module endpoint added later on a branch is open | This is the point | The audit runs in every pull request's `backend` job. It is the gate |

## 8. Rollback

Revert the commit. Nothing is deployed and the ticket creates no resource, secret, table or image.

## 9. Done when

1. `PublicEndpoints` is on `main` in `shared/security` and is the only place an application path is opened.
2. `PublicEndpointAuditIT` runs in `mvnw verify` and fails on breaks 2, 3 and 4 in §4, with the evidence in the merge commit message.
3. The CI `static` step fails on a `permitAll(` outside the two allowed files.
4. `ResourceServerConfigTest` and `PublicEndpointsTest` pass with the new cases.
5. `11-ways-of-working.md`, `10-scoping.md` and the `D-22` row say rule 3 is enforced and by what.
6. `check-done.mjs W-57.1` 5/5, CI green.

## 10. Decisions — settled at writing, 2026-09-27

1. **Where does the build-time check live: Maven enforcer rule, ArchUnit, or an integration test?**
   Integration test in `app`. Enforcer sees dependencies, not mappings. ArchUnit can find `@RequestMapping` classes but cannot know what Spring Security does with them; only a running chain can. No new dependency.
2. **Is `jwt-signing-secret` consumed here?** No. `W-56-secrets.md:122` expected a consumer in `W-57`, but the platform issues no tokens of its own. `W-21`'s HMAC signer is the consumer. The `W-56` line is stale and `W-21` corrects it when it merges.
3. **Merge order with `W-21`.** Either. The file is identical by instruction; the second branch rebases and drops its copy.
