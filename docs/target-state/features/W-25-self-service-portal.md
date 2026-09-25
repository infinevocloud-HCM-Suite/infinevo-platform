# Feature: Employee self-service portal

| Field | Value |
|---|---|
| **Feature ID** | `W-25` · ticket #29 · `CORE-19` |
| **Promoted to** | `docs/target-state/features/W-25-self-service-portal.md` on branch `W-25-self-service-portal` |
| **Owner** | unassigned |
| **Apps touched** | `code/frontend` · `code/backend/core` (`/me/panels`, `PortalPanelProvider`, three core providers) · one placeholder provider class each in `payroll` and `hrms` · read-only endpoints already built by other tickets |
| **Related gaps** | BUG-001 (already fixed by `W-10`), DEBT-013 (discounted) |
| **Status** | **Approved** |
| **Approved by** | founder |
| **Approved on** | 2026-09-23 |
| **Blocked by** | every panel it renders — `W-13.2`, `W-16.3`, `W-21`, `W-12.2`; payslips wait for `W-36`; `W-11.3` for the `core.leave.read_own` and `core.document.read_own` codes |
| **Corrected** | 2026-09-25 — aligned to 12-core-contracts.md §5 row 20 (§2 `/me/panels` row, §3 `PortalPanelProvider` seam) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` — creates no table; the two placeholder providers in `payroll` and `hrms` are one class each, replaced in place by `W-36` and the HRMS timesheet ticket | 1 — placeholders noted 2026-09-25 |
| Flyway migration | **none** | 1 |
| Externally testable behaviour | the portal renders the right panels for all three purchase combinations, and a hidden panel's endpoint refuses too | 1 |
| Frontend area | `src/shell` portal routes | 1 |

Within cap. This is the one stream-C ticket that is mostly frontend. The two placeholder
providers exist so the placeholder panels have a descriptor to render (decision 2); each is
a single class with no logic.

---

## 1. Problem

One product has a portal, the other has scattered self-service screens, and they cannot see
each other.

- Payroll's portal is real: `legacy/Payroll-Bend-SBoot/.../controller/EmployyePortalContoller.java`, mounted at `/api/employees-portal`, with eight panels — home, profile, salary details, payslips, tax calculator, proof of investment, investments, reimbursement, deductions
- Access is a flag an administrator sets, `BasicDetails.portalEnabled`, toggled by `/api/employees-portal/{employeeId}/enable-portal`
- The frontend guards routes with `AuthGuard({ allowedRole: 'employee' })`
- HRMS has no portal, only four action-gated routes — `/my-timesheet`, `/my-attendance`, `/apply-leaves`, `/my-leave-balance` — behind `ActionProtectedRoute`

The good news, and it is worth stating plainly because it is unusual in this codebase: the
Payroll portal **is** gated server-side. Login calls
`/api/organization-user-role-mapping/my-organizations`, the backend filters to organisations
where `employeePortalEnabled` is true, and an empty result blocks the login. The frontend's
`localStorage` checks are a second layer, not the only one.

What is missing is a portal that spans both products. An employee of a tenant holding HRMS and
Payroll has two logins, two portals and no page showing both — BUG-001, already fixed at the
identity layer by `W-10`, but never exploited by a screen.

The typo `EmployyePortalContoller.java` is real and load-bearing in the frozen tree. It is not
renamed and it is not carried forward.

## 2. Scope

**In scope**

- One portal in the new frontend, at `/me`, with panels assembled from the tenant's entitlement
- Panels: my profile, my leave, my documents, my payslips (Payroll only), my timesheet (HRMS only)
- Each panel is a `PortalPanelProvider` bean registered by the module that owns it; `core` lists them, filtered by entitlement and by the caller's actions
- Server-side entitlement on every panel's data endpoint, asserted by test
- A panel whose module the tenant has not bought is not rendered **and** its endpoint returns `403`
- **Gated by actions, not by the `employee` role.** A panel appears when the caller holds its `*_own` action, whoever granted it

**Out of scope**

- Building the endpoints themselves — each belongs to its own ticket, and this one consumes them
- The entitlement mechanism — `W-12.2` builds it; this ticket is its first real consumer
- Editing anything. The portal reads. Applying for leave is `W-16.3`'s screen, not this one
- The screen an administrator uses to flip `is_portal_enabled` — that belongs with the employee screens; this ticket only reads the flag
- Payslips beyond a placeholder until `W-36` renders one

## 3. Flow

```
[employee logs in via Keycloak (W-10)]
  --> [GET /api/v1/me/panels]
        --> every PortalPanelProvider bean
        --> keep if provider.module() is entitled (W-12.2)
        --> keep if caller holds provider's *_own action (W-11.2)
        --> keep if is_portal_enabled
  --> shell renders only the returned panels
  --> each panel --> its own endpoint --> 403 if the module or the action is not held
```

The panel list comes from the server. The frontend never decides what an employee may see;
it renders what it is given.

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../portal/PortalController.java` | new — `GET /api/v1/me/panels` only |
| Service | `core/.../portal/PortalPanelService.java` | new — collects the `PortalPanelProvider` beans and filters them |
| Contract | `core/.../portal/PortalPanelProvider.java` | new — the interface a panel's owning module implements |
| Providers | `core/.../portal/ProfilePanelProvider.java`, `LeavePanelProvider.java`, `DocumentPanelProvider.java` | new — the three core panels, in `core` |
| Providers | `payroll/.../portal/PayslipPanelProvider.java`, `hrms/.../portal/TimesheetPanelProvider.java` | new — placeholders until `W-36` and the HRMS timesheet ticket; each returns its panel descriptor and nothing else |

No entity, no repository, no table.

**`PortalPanelProvider` bean contract** (`12-core-contracts.md` §3, "Portal panel"). The
module registers the bean; `core` discovers it by type. `core` never names a payroll or HRMS
class — `maven-enforcer` would reject it.

| Method | Returns | Meaning |
|---|---|---|
| `code()` | `String` | the panel id the shell routes on, e.g. `profile`, `leave`, `documents`, `payslips`, `timesheet` |
| `module()` | `PlatformModule` or `null` | the module the panel belongs to; `null` means core, every tenant |
| `panel(UUID employeeId)` | `PanelDescriptor` | `code`, `displayOrder`, the endpoint the panel reads, and the `*_own` action it needs |

**Panels, endpoints and the action each needs.** The portal is gated by actions, not by the
`employee` role: the seeded role happens to hold these codes, but the check is on the code.

| Panel | `module()` | Endpoint | Action | In `V020__action.sql` today? |
|---|---|---|---|---|
| `profile` | core | `GET /api/v1/me/employee` (`W-13.2`) | `core.employee.read_own` | yes, `:58` |
| `leave` | core | `GET /api/v1/me/leave-requests`, `/me/leave-balances` (`W-16.2`, `W-16.3`) | `core.leave.read_own` | **no** — exists as `hrms.leave.read_own` (`:81`); renamed by `W-11.3` (`12-core-contracts.md` §4) |
| `documents` | core | `GET /api/v1/me/documents` (`W-21`) | `core.document.read_own` | **no** — added by `W-11.3` |
| `payslips` | `PAYROLL` | `GET /api/v1/me/payslips` (`W-36`) | `payroll.payslip.read_own` | yes, `:121` |
| `timesheet` | `HRMS` | `GET /api/v1/me/timesheet` (HRMS timesheet ticket) | `hrms.timesheet.read_own` | yes, `:98` |

Until `W-11.3` lands, the leave provider builds against `hrms.leave.read_own` and the
document provider against whatever `W-21` ships; both are one-string changes.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/me/panels` | — | ordered list of panel descriptors the caller may see | `@RequiresAction("core.employee.read_own")` — the least any portal user holds; each panel then applies its own action |

`/me/*` data endpoints belong to their own tickets and each carries its panel's `*_own` code
(`12-core-contracts.md` §2, `/me/panels` row).

## 5. Frontend changes

| File | Change |
|---|---|
| `src/shell/routes.js` | change — add the `/me` branch |
| `src/shell/portal/PortalLayout.jsx` | new — renders whatever `/me/panels` returns |
| `src/shell/portal/panels/MyProfile.jsx` | new |
| `src/shell/portal/panels/MyLeave.jsx` | new |
| `src/shell/portal/panels/MyDocuments.jsx` | new |
| `src/shell/portal/panels/MyPayslips.jsx` | new — placeholder until `W-36` |
| `src/shell/portal/panels/MyTimesheet.jsx` | new — placeholder until the HRMS timesheet ticket |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/me` | `PortalLayout` | authenticated; panels from the server |
| `/me/:panelId` | the panel | rendered only if the server returned that panel |

No panel component is imported conditionally on a `localStorage` value. The legacy portal's
`localStorage` checks are the pattern `client.js:16-18` was written to keep out.

## 6. Database changes

**None. This ticket creates no table and ships no migration.**
`02-data-model.md:309` — `CORE-19` has zero tables, by design.

- [x] `tenant_id` on every new table — creates none
- [x] Index on `tenant_id` plus lookup columns — adds none
- [x] Money columns — none; payslip figures are rendered from `W-36`'s response
- [x] Expand / contract — no schema change

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../portal/PortalPanelServiceTest.java` | all three purchase combinations return the right panel set, from stub providers; a provider whose action the caller lacks is dropped; ordering follows `displayOrder` |
| Unit | `core/.../portal/PortalActionGateTest.java` | a caller **without** the `employee` role but holding `core.employee.read_own` and `payroll.payslip.read_own` sees exactly those two panels — the role is not consulted |
| Integration | `core/.../portal/PortalEntitlementIT.java` | a Payroll-only tenant's employee gets `403` from the timesheet endpoint, not just a missing menu; a caller stripped of `payroll.payslip.read_own` gets `403` from `/me/payslips` while entitled |
| Integration | `core/.../portal/PortalPanelDiscoveryIT.java` | with `payroll` and `hrms` on the classpath, both module providers are discovered by `core` without `core` importing either |
| Frontend | `src/shell/portal/PortalLayout.test.jsx` | renders exactly the panels returned; renders nothing for an empty list |

`PortalEntitlementIT` is the test that matters. `09-build-order.md:185` calls a hidden menu
over a live endpoint a security bug, so the test asserts the endpoint, never the menu.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d
bash infra/docker/seed/seed.sh

# acme = Payroll only, globex = both (infra/docker/keycloak/README.md)
for u in employee.globex employee.acme; do
  TOKEN=$(curl -s -d client_id=infinevo-web -d username=$u -d password=local_dev_pw \
    -d grant_type=password \
    http://localhost:8081/realms/infinevo/protocol/openid-connect/token | jq -r .access_token)
  echo "$u panels:"; curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/me/panels | jq -r '.[]'
  echo "$u timesheet:"; curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
    http://localhost:8080/api/v1/me/timesheet
done

cd code/frontend && npm test
```

| Check | Expected |
|---|---|
| `employee.globex` panels | `profile, leave, documents, payslips, timesheet` (by `code`) |
| `employee.acme` panels | `profile, leave, documents, payslips` — **no timesheet** |
| `employee.acme` timesheet endpoint | `403`, not `404` and not `200` |
| Frontend tests | green |

The seed does not have an `employee.acme` user today — `dev-realm.json` seeds only
`admin.acme`. Adding one is part of this ticket, since the asymmetry is the thing being
proved.

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Panels gated in the frontend only, reproducing the legacy `localStorage` pattern | **high — it is the easiest way to build this** | `PortalEntitlementIT` asserts the endpoint; a menu-only test would pass while the bug exists |
| The portal is built before `W-12.2` exists and grows its own entitlement logic | medium | Blocked on `W-12.2` by design; `PortalPanelService` calls it and implements nothing itself |
| Placeholder panels ship and look finished | medium | Payslip and timesheet panels state plainly that the feature is not built yet |
| The three access gates disagree and one silently overrides another | **medium — decision 1 created this** | Strictest wins, asserted by `PortalPanelServiceTest`; `is_portal_enabled` false yields an empty panel list, never a partial portal |
| A Payroll-only tenant's employee sees a leave panel that has no data | low | Leave is `core`, not a module — every tenant has `CORE-07` |

## 10. Rollback

Nothing is deployed and no schema changes. Removing the `/me` route withdraws the portal;
the endpoints it reads belong to other tickets and are unaffected.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table | **creates no table** |
| Flyway only, `ddl-auto` nowhere | **ships no migration**; none added |
| `Money`/`BigDecimal` for money | holds no money; figures are rendered from responses |
| Index on `tenant_id` plus lookup columns | adds none |
| Expand / contract | no schema change |
| No module references another module | `PortalController` lives in `core` and calls `core` only; `payroll` and `hrms` implement `core`'s `PortalPanelProvider` and `core` discovers them by type; panel data is reached by HTTP from the browser, not by a Java call |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-001 no SSO (`GAP_INVENTORY.md:27`) | **Already fixed** by `W-10`. This ticket is the first screen that benefits |
| DEBT-013 package typo `leaveAndAttedance/` | **Discounted.** New code uses `core/.../portal/`; `EmployyePortalContoller.java` is not renamed and not ported |
| Frontend-decided visibility in the frozen portal | **Fixed.** The panel list is a server response |

## 13. Decisions — settled 2026-09-22

| # | Question | Decision |
|---|---|---|
| 1 | Does the per-employee portal toggle survive? | **Yes, kept.** Against my recommendation of dropping it. `W-13.1` carries `is_portal_enabled`, defaulting to `true` |
| 2 | Ship before payslips and timesheets exist? | **Yes**, with those two panels as placeholders |

**Decision 1 means three things now control portal access**, and they must not disagree
silently:

| Gate | Answers |
|---|---|
| `is_portal_enabled` on the employee | May this person use the portal at all? |
| The panel's `*_own` action (`W-11.2`) — **not the `employee` role** | Do they hold the action this panel needs? The seeded role is one way to get it; a custom role is another |
| Module entitlement (`W-12.2`) | Did the customer buy the module behind this panel? |

**The strictest wins.** `PortalPanelService` evaluates all three and returns a panel only if
all three allow it; `/me/panels` returns an empty list when `is_portal_enabled` is false,
and the shell renders an empty portal rather than an error. Any other precedence would let a
switch set to "yes" override a role set to "no", which is the wrong direction for an access
control.

`PortalPanelServiceTest` asserts each gate independently and the strictest-wins combination.
