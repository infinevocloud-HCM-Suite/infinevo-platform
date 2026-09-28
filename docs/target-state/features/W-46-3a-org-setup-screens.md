# Feature: Organisation setup screens

| Field | Value |
|---|---|
| **Feature ID** | `W-46.3a` · from ticket #60 · `CORE-05` |
| **Spec file** | `docs/target-state/features/W-46-3a-org-setup-screens.md` |
| **Owner** | unassigned |
| **Apps touched** | `code/frontend/src/core/org` only. No backend, no migration |
| **Related gaps** | DEBT-022 (closed by `W-14.1`) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-28 — **split from `W-46.3`**: holidays are `W-46.3b`, which waits on `W-17`; this half waits on `W-45` only |
| **Blocked by** | `W-45` only. Every endpoint it calls is on `main` (`235aab2`) |
| **Size** | **S** |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an administrator maintains departments, designations and work locations from the browser, and a master in use cannot be deleted | 1 |
| Frontend area | `src/core/org` | 1 |

Within cap. The unsplit `W-46.3` crossed two behaviours with two different blockers.

---

## 1. Problem

The three org masters are on `main` with full CRUD (`DepartmentController`, `DesignationController`, `WorkLocationController`, `W-14-1` §4) and three menu items already point at `/org/departments`, `/org/designations`, `/org/work-locations` (`NavigationCatalogue.java:55-81`). They open `NotFound`.

| Frozen screen | Ported? |
|---|---|
| `legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/departments/index.js`, `designations/index.js` | **yes** in shape: one Ant table with inline add and edit |
| `legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/workLocations/addWorkLocations.js`, `editWorkLocations.js` | **yes**: the address form and the filing-address flag |
| `.../departments/importDepartments.js`, `designations/importDesignations.js`, `workLocations/importWorkLocations.js` | **no** — no import endpoint exists (`W-14-1` out of scope) |
| `legacy/Payroll-Fend-react/src/pages/mainPages/organizationRegister/setupNewOrganization.js` | **no** — the setup wizard is `W-24.1`'s checklist, whose endpoint is spec-only; see §14 |
| HRMS | nothing — HRMS has free-text departments (`01-platform-shape.md:66`) |

## 2. Scope

**In scope**

- Departments and designations: table with name, code, active, created; add and edit inline; deactivate; delete with confirm
- Work locations: table with name, code, city, state, filing address; add and edit form with the address fields; delete with confirm
- The `409` on delete (master assigned, or the filing address) shown verbatim from the envelope, with "deactivate instead" offered
- Dispatching `invalidateMasters` from `W-46.1`'s slice on every write, so the employee screens refetch names

**Out of scope**

- Holidays — `W-46.3b`
- Setup checklist and invitations screens — `W-24.1` / `W-24.2` are spec-only; see §14
- Organisation profile (name, address, tax details) — no tenant self-edit endpoint exists (`W-65-3` §2 says the same)
- Bulk import — no endpoint
- Any backend change

## 3. Flow

```
[admin] --> /org/departments        --> departmentService.list() / create / update / remove
        --> /org/designations       --> designationService.*
        --> /org/work-locations     --> workLocationService.list()
        --> /org/work-locations/new, /:id/edit --> create / update
        any write                   --> dispatch(invalidateMasters())
```

## 4. Backend changes

None. Menu items exist (`NavigationCatalogue.java:55-81`).

## 5. Frontend changes

`W-45` contract throughout.

| File | Change |
|---|---|
| `src/core/org/departmentService.js`, `designationService.js`, `workLocationService.js` | **new.** `createService('/v1/departments')` and siblings, `list({activeOnly})` |
| `src/core/org/MasterTable.jsx` | **new.** One generic Ant `Table` with editable rows for the two simple masters: name, code, active `Switch`, actions. Props: service, title. Add row at the top. Delete asks for confirmation; a `409` shows the message and offers deactivate |
| `src/core/org/Departments.jsx`, `Designations.jsx` | **new.** `MasterTable` with the matching service |
| `src/core/org/WorkLocations.jsx` | **new.** Table: name, code, city, state, filing-address `Tag`, active, actions |
| `src/core/org/WorkLocationForm.jsx` | **new.** Name, code, address line 1 and 2, city, state (from `reference` states when `W-09` exposes them; free text until then), state code, PIN, country (default `IN`), filing address `Switch`, active. Fields from `addWorkLocations.js` and `W-14-1` §4 |
| `src/core/index.js` | `routes` gains the five below |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/org/departments` | `Departments` | inside `AppShell`; present when the feed carries `core.org.departments` |
| `/org/designations` | `Designations` | `core.org.designations` |
| `/org/work-locations` | `WorkLocations` | `core.org.locations` |
| `/org/work-locations/new` | `WorkLocationForm` | same item; hidden without `core.org.manage` |
| `/org/work-locations/:id/edit` | `WorkLocationForm` | same |

Write actions render only when `useCan('core.org.manage')`; readers with `core.org.read` see the tables.

## 6. Database changes

None.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `src/core/org/*Service.test.js` | paths and methods; no `axios` |
| Component | `MasterTable.test.jsx` | add row posts name and code; a `409` on delete shows the message and the deactivate option; no actions without `core.org.manage` |
| Component | `WorkLocationForm.test.jsx` | required fields; filing-address flag sent; edit loads the record |
| Unit | `src/core/employee/employeeSlice.test.js` (existing) | `invalidateMasters` dispatched from a write clears the cache |

## 8. Verification

```bash
cd code/frontend && npm ci && npm run lint && npm test
docker compose -f infra/docker/compose.yml up -d
```

| Check | Expected |
|---|---|
| tables | admin@acme.local sees Acme's seeded masters; Globex's are absent |
| add | a new department appears; `psql`: one more row in `core.department` for Acme |
| delete in use | deleting a department an employee holds shows the `409` message; deactivate works |
| filing address | deleting the filing-address location shows the `409` |
| employee screen | after renaming a department, `/employees` shows the new name without reload of the app |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Scope creep into the setup wizard | medium | named out of scope; §14 says where it goes |
| State list not yet in `reference` | low | free-text state with a two-letter code until `W-09` exposes it; one `Select` swap later |

## 10. Rollback

Frontend only. Revert the branch.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | no script |
| `Money` / `BigDecimal` | no money |
| Index | none |
| Expand / contract | nothing |
| No module references another | `src/core/org` imports `@shared/*`, `@shell/screens`, `@core/employee` (the slice action) |

## 12. Gap inventory

| ID | Overlap | Decision |
|---|---|---|
| DEBT-022 (57 Payroll queries not org-scoped) | masters | **closed by `W-14.1`**; §8 isolation check |

## 13. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `src/core/org` | services, `MasterTable`, `Departments`, `Designations`, tests |
| 2 | `src/core/org` | `WorkLocations`, `WorkLocationForm`, tests; `src/core/index.js` registration |

## 14. Decisions and open questions

| # | Question | Answer |
|---|---|---|
| 1 | Why split `W-46.3`? | Holidays wait on `W-17`; masters wait on nothing. One ticket would idle the buildable half |
| 2 | Where does the **setup checklist** screen go? | **Unowned today.** `W-24-1` §2 says "a frontend ticket consumes this" and no frontend ticket names it. Recommend a `W-46.6` (checklist page plus user and employee invitation screens from `W-24.2`), after `W-24.1` and `W-24.2` land. Not folded in here because both endpoints are spec-only |
