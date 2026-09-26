# Feature: Navigation feed and frontend gate

| Field | Value |
|---|---|
| **Feature ID** | `W-12.3` · from ticket #13 · `PLAT-01` |
| **Promoted to** | `docs/target-state/features/W-12-3-navigation-feed.md` on branch `W-12-3-navigation-feed` — **`W-12-3` with hyphens**, never `W-12.3`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/frontend` |
| **Related gaps** | DEBT-018 (not applicable) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | `W-12.2` — the menu must reflect an enforcement that already exists |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 row 13 |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | **none** | 1 |
| Externally testable behaviour | the menu a tenant sees matches exactly the modules and actions it holds | 1 |
| Frontend area | `src/shell` navigation | 1 |

Within cap.

---

## 1. Problem

Neither frontend can be told what to show, and they fail in opposite ways.

- Payroll's sidebar is a **hard-coded static list** — `legacy/Payroll-Fend-react/.../sidebar.js:25-69`. Every user sees every item regardless of role or purchase
- HRMS's sidebar is **role-driven in the browser** — `legacy/HRMS_Frontend/.../Sidebar.jsx:50-51` — rendering different items per active role, decided client-side from data the client holds

Both are the shape `code/frontend/src/shared/api/client.js:16-18` was written to prevent: a
decision the client makes about what the client may see. Payroll's employee portal is the one
place the frozen code gets this right, filtering server-side at login, and it does so for
portal access only.

`09-build-order.md:185` requires both halves — a `403` **and** no menu. `W-12.2` delivered
the `403`. This ticket delivers the menu, and it must derive it from the same source, or the
two drift and the menu starts lying.

## 2. Scope

**In scope**

- `GET /api/v1/navigation` — the ordered menu the signed-in user may see, for the bound tenant
- Items filtered by module entitlement (`W-12.2`) **and** by action (`W-11.2`)
- The same response carries the caller's full action-code set (`actions: [...]`), so a screen can hide a button the caller cannot use without a second call — the server-side answer to what legacy Payroll fetched per role from `GET /api/organizations/{orgId}/roles/{roleId}/actions` (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/controller/RoleActionController.java:18,39-40`)
- The shell rendering exactly what it is given
- A development-mode check that every menu item's target endpoint exists

**Out of scope**

- Enforcement — `W-12.2`. If this ticket is the only thing hiding something, that is the bug
- The employee portal's panel list — `W-25` has its own, for the same reason
- Menu ordering configurable per tenant. See decision 2
- Icons, labels and translations beyond a key

## 3. Flow

```
[shell boot, after W-10 login]
  --> GET /api/v1/navigation
  --> [NavigationService] modules from W-12.1, actions from W-11.2 (PermissionService, cached)
  --> { items: [...ordered...], actions: [...caller's codes...] } --> shell renders
  --> a route not in the response is not registered at all
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../navigation/NavigationController.java` | new |
| Service | `core/.../navigation/NavigationService.java` | new |
| Config | `core/.../navigation/NavigationCatalogue.java` | new — the item definitions, in code |
| DTO | `core/.../navigation/NavigationItemResponse.java` | new |
| DTO | `core/.../navigation/NavigationResponse.java` | new — `items: List<NavigationItemResponse>`, `actions: Set<String>` |

The catalogue is code, not a table. A menu item exists because an endpoint exists; a row in a
table would let the two disagree.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/navigation` | — | `items`: ordered — key, label key, path, children; `actions`: the caller's action codes | Bearer, tenant bound; no `@RequiresAction` — every signed-in user has a menu (`12-core-contracts.md` §2) |

Each item names the module and action it requires. The service returns only those the caller
passes both checks for, so the response contains no item the caller cannot use.

`actions` is the same set `PermissionService` already holds for the caller under the tenant's
permission version (`code/backend/shared/src/main/java/com/infinevo/shared/authz/PermissionCache.java:97-113`),
so it costs no extra query and cannot disagree with what `@RequiresAction` will decide. It is
action codes only — never role names, never employee data. A screen hides a button when its
code is absent; the endpoint behind the button still refuses on its own.

## 5. Frontend changes

| File | Change |
|---|---|
| `src/shell/routes.js` | change — routes registered from the navigation response, not a static array |
| `src/shell/AppShell.jsx` | change — render the returned items |
| `src/shell/navigation/useNavigation.js` | new — fetches once after login, refetches on tenant switch; exposes `items` and `actions` |
| `src/shell/navigation/useCan.js` | new — `useCan('core.employee.delete')` reads `actions` from the feed; the one way a screen decides to show a button |
| `src/shared/api/client.js` | change — handle `MODULE_NOT_ENTITLED` distinctly from `FORBIDDEN` |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| — | — | no new route; existing routes become conditional on the feed |

**No hard-coded menu array anywhere.** That is the Payroll pattern, and it is the one thing
this ticket exists to avoid re-creating.

## 6. Database changes

**None. This ticket creates no table and ships no migration.**

- [x] `tenant_id` on every new table — creates none
- [x] Index on `tenant_id` plus lookup columns — adds none
- [x] Money columns — none
- [x] Expand / contract — no schema change

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../navigation/NavigationServiceTest.java` | a Payroll-only tenant's feed has no HRMS item; an action the user lacks removes its item; a parent with no visible children is itself hidden |
| Integration | `core/.../navigation/NavigationIT.java` | Acme and Globex get different feeds from the same endpoint; `actions` equals the caller's `PermissionService` set exactly, and changes on the next call after a role grant is revoked |
| Unit | `core/.../navigation/NavigationServiceTest.java` | `actions` contains only codes the caller holds, and never a role name |
| Frontend | `src/shell/navigation/useCan.test.js` | a code in `actions` renders the button; a missing code hides it; an empty `actions` hides every guarded button |
| Integration | `core/.../navigation/NavigationMatchesEnforcementIT.java` | **for every item in the feed, the target endpoint returns non-`403`; for every item absent, it returns `403`** |
| Frontend | `src/shell/navigation/useNavigation.test.js` | the shell renders exactly the returned items; an empty feed renders an empty shell, not a default menu |

`NavigationMatchesEnforcementIT` is the ticket's reason to exist. It is the test that catches
the menu and the enforcement drifting apart in either direction — a visible item that refuses,
or a hidden item that works.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d
bash infra/docker/seed/seed.sh

for u in admin.acme admin.globex; do
  TOKEN=$(curl -s -d client_id=infinevo-web -d username=$u -d password=local_dev_pw \
    -d grant_type=password \
    http://localhost:8081/realms/infinevo/protocol/openid-connect/token | jq -r .access_token)
  echo "$u menu:"
  curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/navigation | jq -r '.items[].key'
  echo "$u actions:"
  curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/navigation | jq -r '.actions|length'
done

cd code/backend && mvn -q -pl core -Dit.test=NavigationMatchesEnforcementIT verify
cd code/backend && mvn -q verify
cd code/frontend && npm test

grep -rn 'const .*[Mm]enu.*= \[' src/shell/ && echo "REVIEW: static menu array" || echo "no static menu"
```

| Check | Expected |
|---|---|
| `admin.acme` menu | no `hrms.*` key |
| `admin.globex` menu | both `hrms.*` and `payroll.*` keys |
| `actions` count | non-zero for both admins; `NavigationIT` asserts the set equals `PermissionService`'s for the same user |
| Match test | green — feed and enforcement agree both ways |
| Static menu grep | `no static menu` |
| Suites | green |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The menu is treated as the security boundary | **high — it is the named security bug** | `W-12.2` ships first and independently; the match test asserts the endpoint, not the menu |
| A static fallback menu is added "for when the call fails" | medium | Grep in verification; an empty shell is the correct failure, and the frontend test asserts it |
| The catalogue drifts from the real endpoints | medium | `NavigationMatchesEnforcementIT` walks every item |
| Menu flicker or a blank shell on slow networks | low | Skeleton while loading; no default items behind it |
| Tenant switching leaves the previous tenant's menu | medium | `useNavigation` refetches on tenant change; asserted in the frontend test |

## 10. Rollback

Nothing is deployed and no schema changes. Reverting the frontend change restores a static
shell; the `403`s from `W-12.2` remain, which is the safe direction.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table | **creates no table** |
| Flyway only, `ddl-auto` nowhere | **ships no migration**; none added |
| `Money`/`BigDecimal` for money | holds no money |
| Index on `tenant_id` plus lookup columns | adds none |
| Expand / contract | no schema change |
| No module references another module | `core` only; the catalogue names module keys as strings, not Java references |

## 12. Gap inventory

| ID | Decision |
|---|---|
| Hard-coded Payroll sidebar (`sidebar.js:25-69`) | **Fixed.** The menu is a server response |
| Client-decided HRMS sidebar (`Sidebar.jsx:50-51`) | **Fixed.** The server decides; the client renders |

## 13. Decisions — settled 2026-09-22

**Every question below was answered as recommended.** The recommendation text stands as
the decision; the consolidated record is
`.claude/outputs/2026-09-22-plan-core-open-questions.md`.

1. **Does the feed include items the user may see but not act on?** A read-only viewer might want the screen without the buttons. **Recommend** item-level visibility by action, and button-level by action as well, rather than a half-usable screen. *Corrected 2026-09-25:* button-level visibility is what `actions` in the response is for — the feed carries the caller's codes so screens need no second call (`12-core-contracts.md` §5 row 13).
2. **Is menu order configurable per tenant?** **Recommend** no — one order, defined in the catalogue. Per-tenant ordering is a table, a screen and a support burden for very little.

## 14. Review — sent back 2026-09-25

Reviewed at `161c9d1`. CI red on frontend lint, backend green. Not merged. Fix here, re-run
`check-done.mjs W-12.3`, then hand back. Items 1–4 block; 5–7 go in the same pass.

| # | Defect | Where | Why it matters |
|---|---|---|---|
| 1 | `useNavigation()` is called after an early return — rules-of-hooks; the reason CI is red | `src/shell/navigation/useCan.js:16` | a screen whose action code changes between renders crashes React |
| 2 | `NavigationMatchesEnforcementIT` walks 4 of 8 items against test-only stand-in controllers | `core/src/test/.../navigation/NavigationTestEndpointsController.java:17-41` | §7 calls this test the ticket's reason to exist; against stand-ins it cannot catch catalogue drift |
| 3 | `core.employee` item targets `GET /api/v1/employees`, which does not exist — the controller has `POST` and `/{id}` only | `NavigationCatalogue.java:40` vs `EmployeeController.java:58-81` | every admin sees Employees and gets an error on click; item 2 hides it |
| 4 | Routes are not registered from the feed: `routesFromFeed` is never called, there is no `<Routes>`, and `routesFor(entitlements)` is kept | `src/shell/routes.js:25,52`, `AppShell.jsx:98` | §5: a route not in the response is not registered at all |
| 5 | Frontend tests re-implement the logic inline and never import `useCan.js`, `useNavigation.js` or `AppShell.jsx` | `useCan.test.js`, `useNavigation.test.js` | a static fallback menu would not fail them — §9's top risk |
| 6 | `hrms.timesheets` and `payroll.runs` point at unbuilt endpoints; the §2 dev-mode "target endpoint exists" check was not written | `NavigationCatalogue.java:76,83` | Globex sees two dead items and nothing flags them |
| 7 | Tenant-switch refetch listens for `infinevo:tenant-switched`, which nothing dispatches; the test never triggers a refetch | `useNavigation.js:113-128`, `useNavigation.test.js:144-167` | claimed and tested behaviour that never runs |

What is right and must stay: the service filters by module and action, `actions` is
`PermissionService`'s cached set, parent pruning works, an empty feed renders an empty shell.

### 14a. Second pass — 2026-09-26

Items 5, 6 and 7 were still open at `51e5f4d`; items 2 and 3 were only half closed. Fixed as follows.

| # | Fix |
|---|---|
| 2 | `NavigationTestEndpointsController` deleted. `PermissionGuardTestApp` now holds the real `AuditController`; every catalogue leaf is walked against a real controller, by three callers (admin, hr, employee) |
| 3, 8 | The `GET /api/v1/employees` stub is removed and the `core.employee` item with it — it returns with `W-13.3`, which ships the list endpoint. An item is added in the ticket that ships its endpoint, never before (`NavigationCatalogue` javadoc) |
| 5 | Frontend tests run under vitest and jsdom and render the real `AppShell`, `useNavigation`, `useCan`, `routesFromFeed` and `keycloak.js`; only the HTTP client and the adapter are replaced. `npm test` is now a CI step |
| 6 | `hrms.timesheets` and `payroll.runs` are removed until their endpoints exist. `NavigationCatalogueValidator` runs in every profile and **refuses to start the application** if a leaf has no `GET` mapping; unit-tested, and exercised by every context that scans `core.navigation` |
| 7 | The tenant-change signal is the Keycloak adapter's own token callbacks: `keycloak.js` exports `onTenantChange`, fired when a token arrives carrying a different `tenant_id`; `useNavigation` refetches on it. The `window` event is gone. Tested against the real adapter module |
| 9 | `NavigationMatchesEnforcementIT` asserts 2xx for a visible item, 403 for an absent one |
| 10 | `NavigationIT` asserts `actions` equals, exactly, the codes the caller's role holds, read from the schema as its owner |

**Deviation from §8, recorded:** until the HRMS and Payroll endpoints ship, the shipped catalogue holds
core items only, so `admin.globex` sees no `hrms.*` or `payroll.*` key and the two admins' feeds are
identical. The module filter is proven in `NavigationServiceTest` over a catalogue that has module items.
`NavigationIT` proves two callers get different feeds by role instead. The §8 expectation for Globex
becomes true in the ticket that adds the first module item.
