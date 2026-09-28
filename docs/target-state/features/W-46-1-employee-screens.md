# Feature: Employee screens

| Field | Value |
|---|---|
| **Feature ID** | `W-46.1` · from ticket #58 · `CORE-04`, `CORE-06` |
| **Spec file** | `docs/target-state/features/W-46-1-employee-screens.md` |
| **Owner** | biren |
| **Apps touched** | `code/frontend/src/core/employee` only. No backend, no migration |
| **Related gaps** | BUG-002 (closed by `W-13.1`), DEBT-026 (prevented), BUG-006 (deferred) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-28 |
| **Blocked by** | `W-45` only. Every endpoint it calls is on `main` |
| **Size** | **M** |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an administrator creates an employee, fills the five detail sections and sets the reporting manager from the browser, and an employee the tenant does not own never appears | 1 |
| Frontend area | `src/core/employee` | 1 |

Within cap.

---

## 1. Problem

The employee master is on `main` and nothing renders it. `GET /api/v1/employees` is paged and searchable (`EmployeeController.java:28`, `EmployeeQueryService.java:65-72`), the five detail sections have `GET`/`PUT` each (`EmployeeDetailController.java`), and the reporting line has `PUT`/`GET` plus manager chain and org chart (`ReportingLineController.java`). The `core.employee` menu item already points at `/employees` (`NavigationCatalogue.java:53`), so today every administrator sees a menu entry that opens `NotFound`.

Two frozen designs exist and neither is the target:

| Frozen screen | What it does | Why it is not ported as-is |
|---|---|---|
| `legacy/HRMS_Frontend/src/components/employee/Employee.jsx:457-464` | MUI table: employee id, first name, last name, employment status, department, job title | MUI is retired (`03-code-structure.md:116`) |
| `legacy/HRMS_Frontend/src/components/employee/EmployeeDetails.jsx:348-391` | one page with personal, contact, work, identification, documents blocks | free-text department and job title; emergency contacts folded into contact |
| `legacy/Payroll-Fend-react/src/pages/mainPages/employee/addEmployee.js:147-152` | four-step wizard: Basic, Salary, Personal, Payment | Salary is Payroll (`W-47`); the wizard persists a draft in `localStorage`, which `W-45` §5b forbids |
| `legacy/Payroll-Fend-react/src/pages/mainPages/employee/index.js:442-520` | Ant table: name, work email, department, status | closest to the target list; ported in shape |
| `legacy/Payroll-Fend-react/src/pages/mainPages/employee/editBasicDetails.js:32,77-81` | reads `organizationId` and `__t` from `localStorage`, calls `axios` directly | the habit `W-45` stops |

## 2. Scope

**In scope**

- Employee list: paged, sorted, prefix search, status filter, department / designation / location shown by name (masters fetched once, `EmployeeResponse.java:18-20`)
- Create employee: the root record only (`EmployeeRequest.java:34-47`)
- Employee page with tabs: Overview (root record, edit in place), Personal, Contact, Identification, Employment, Bank, Reporting line
- Reporting line tab: current lines, manager chain, set primary manager with an effective date
- Status change (terminate with a date, reactivate) through `PUT /employees/{id}`
- Soft delete with confirm, and an "include deleted" toggle shown only to holders of `core.employee.delete`
- The `portalEnabled` switch on Overview (`W-25` §2 says this belongs with the employee screens)
- Registering routes and the `employee` slice from `src/core/index.js`

**Out of scope**

- Salary, CTC, statutory profile tabs — `W-47.1`
- Documents tab — needs `W-21` on `main`; a later ticket adds the tab to this page
- Login link (`PUT /employees/{id}/login`) — `W-13.4` is not on `main`; `W-24.2` invitations owns the flow
- Org chart visual — the endpoint exists; a tree view is `W-44` or a later core ticket
- Bulk import of employees (`importBasicDetails.js`) — no endpoint exists
- Any backend change. A missing field is a defect in `W-13.x`, not work here

## 3. Flow

```
[admin] --> /employees                --> employeeService.list({q, status, page, size, sort})
        --> /employees/new            --> employeeService.create(body)         --> /employees/:id
        --> /employees/:id            --> employeeService.get(id) + orgMasterService.all()
              tab Personal            --> employeeService.section(id, 'personal') / saveSection(...)
              tab Contact / Identification / Employment / Bank    (same, one call per tab)
              tab Reporting line      --> reportingLineService.lines(id), managerChain(id), set(id, body)
              Terminate               --> employeeService.update(id, {...employee, status: 'TERMINATED', terminationDate})
              Delete                  --> employeeService.remove(id)            --> /employees
```

## 4. Backend changes

None.

## 5. Frontend changes

Follows the `W-45` contract: services over `apiClient`, slices and routes registered from `src/core/index.js`, no `axios`, no storage, tokens from `theme.useToken()`, Formik + Yup forms, outcomes through `msgHelper` (`W-45-frontend-shell.md` §5, §5b).

| File | Change |
|---|---|
| `src/core/employee/employeeService.js` | **new.** `createService('/v1/employees')` spread, plus `section(id, name)` → `GET /v1/employees/{id}/{name}`, `saveSection(id, name, body)` → `PUT`, `list(params)` passing `q`, `status`, `page`, `size`, `sort`, `includeDeleted` |
| `src/core/employee/reportingLineService.js` | **new.** `lines(id, asOf)`, `managerChain(id, asOf)`, `set(id, {managerId, kind, effectiveFrom})` |
| `src/core/employee/orgMasterService.js` | **new.** `all()` → three `GET`s (`/v1/departments`, `/v1/designations`, `/v1/work-locations`) with `activeOnly=true`, returned as id→name maps. Shared with `W-46.3a`, which owns the CRUD services; this file is read-only lookups |
| `src/core/employee/employeeSlice.js` | **new.** `{ masters: {departments, designations, workLocations}, loadedAt }`. Masters cached for the session; invalidated by `W-46.3a` on any master write. Registered in `src/core/index.js` `reducers` |
| `src/core/employee/EmployeeList.jsx` | **new.** Ant `Table` server-paged: number, name, work email, department, designation, location, status `Tag`, joined. Search `Input.Search` on `q`; `Select` on status (`ACTIVE`, `INACTIVE`, `ON_LEAVE`, `TERMINATED`, `EmploymentStatus.java`); "Include deleted" `Switch` rendered only when `useCan('core.employee.delete')`. Row click opens the employee page. "New employee" button when `useCan('core.employee.create')` |
| `src/core/employee/EmployeeCreate.jsx` | **new.** One form, no wizard: employee number, first / middle / last name, gender, date of joining, status (default `ACTIVE`), work email, mobile, department, designation, work location, portal enabled (default on). Yup rules mirror the `W-13.1` §4 validation. On `201` navigate to `/employees/:id` |
| `src/core/employee/EmployeePage.jsx` | **new.** Header: name, number, status tag, actions (Edit, Terminate, Reactivate, Delete) gated by `useCan`. Ant `Tabs`, one component per tab, each loading its section on first open |
| `src/core/employee/tabs/OverviewTab.jsx` | **new.** `Descriptions` of the root record with an inline edit `Form` for the same fields as create |
| `src/core/employee/tabs/SectionTab.jsx` | **new.** One generic tab driven by a field list per section (`sectionFields.js`): Personal (dob, gender, marital status, blood group), Contact (present and permanent address, mobile, emergency contact name and phone), Identification (PAN, Aadhaar, passport, driving licence — masked at rest, revealed on focus), Employment (pay grade, workstation, time zone, shift start and end, reporting-manager notes), Bank (account number, IFSC, bank name, account type, payment mode). `GET` on mount, `PUT` on save. A `403` on save shows the `FORBIDDEN` message; the tab is read-only when `useCan('core.employee.update')` is false |
| `src/core/employee/tabs/ReportingLineTab.jsx` | **new.** Current lines table (manager, kind, effective from / to), manager chain as `Breadcrumb`, "Set primary manager" `Modal` with employee `Select` (searches `employeeService.list({q})`), kind (`PRIMARY`, `INDIRECT`, `APPROVER_LEVEL_1..3`), effective date. A `409` (cycle) is shown verbatim from the envelope |
| `src/core/employee/TerminateModal.jsx` | **new.** Termination date required; sends the full root record with `status: TERMINATED` |
| `src/core/index.js` | `routes` gains the three below; `reducers` gains `employee` |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/employees` | `EmployeeList` | inside `AppShell`; present when the feed carries `core.employee` (`NavigationCatalogue.java:53`) |
| `/employees/new` | `EmployeeCreate` | same; the button is hidden without `core.employee.create` |
| `/employees/:id` | `EmployeePage` | same |

**Ported logic.** The Payroll list columns (`index.js:442-520`) and the HRMS section grouping (`EmployeeDetails.jsx:348-391`) are the sources. Nothing else is ported: the wizard draft, the `localStorage` reads and the free-text department go.

## 6. Database changes

None.

## 7. Tests

Vitest + jsdom, the runner `W-12.3` put in CI.

| Type | File | Covers |
|---|---|---|
| Unit | `src/core/employee/employeeService.test.js` | `list` passes every param; `section` and `saveSection` hit `/v1/employees/{id}/{name}`; no `axios` import |
| Unit | `src/core/employee/reportingLineService.test.js` | the three calls hit the right paths |
| Unit | `src/core/employee/employeeSlice.test.js` | masters stored once; `invalidateMasters` clears them |
| Component | `src/core/employee/EmployeeList.test.jsx` | search sends `q`; status select sends `status`; "include deleted" absent without the action; a row navigates |
| Component | `src/core/employee/EmployeeCreate.test.jsx` | required fields block submit; `201` navigates to the new id |
| Component | `src/core/employee/tabs/SectionTab.test.jsx` | each of the five sections loads on open and saves its fields; read-only without `core.employee.update` |
| Component | `src/core/employee/tabs/ReportingLineTab.test.jsx` | set primary calls `PUT` with `kind: PRIMARY`; a `409` renders the envelope message |
| Lint | existing `lint-rules.test.js` | unchanged; `npm run lint` covers `src/core/employee/**` |

## 8. Verification

```bash
cd code/frontend && npm ci && npm run lint && npm test
docker compose -f infra/docker/compose.yml up -d
# browser: log in as admin@acme.local, open /employees
```

| Check | Expected |
|---|---|
| lint | zero warnings on `src/core/employee/**` |
| tests | green |
| list | Acme's seeded employees; search "ra" narrows; status `TERMINATED` filter empties |
| create | a new employee appears at `/employees/:id`; `psql`: `select count(*) from core.employee where tenant_id = <acme>` grew by one |
| sections | fill Contact, reload, values persist; Identification shows masked PAN |
| reporting line | set a primary manager; manager chain shows them; setting the employee as their own manager shows the `409` message |
| isolation | log in as admin@globex-full.local: none of Acme's employees are listed, and typing an Acme id into the URL shows the not-found envelope |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The wizard pattern returns and drafts land in browser storage | medium | one form, no draft; the `W-45` lint rule rejects `localStorage` |
| Section tabs grow into one 2,000-line file, as the frozen `adminProofEdit.js` did (DEBT-026) | medium | one generic `SectionTab` driven by a field list; a section with special handling gets its own small component |
| Masters cached in the slice go stale after `W-46.3a` edits | medium | `invalidateMasters` action; `W-46.3a` dispatches it on every write |
| Built before `W-45` lands and rebuilt after | high if started early | blocked on `W-45` in the tracker |

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
| No module references another | `src/core/employee` imports `@shared/*` and `@shell/screens` only; `W-45` §5b lint enforces it |
| No write to `legacy/` | read only |

## 12. Gap inventory

| ID | Overlap | Decision |
|---|---|---|
| BUG-002 (HRMS entities have no tenant) | list isolation | **closed by `W-13.1`**; §8 isolation check proves it from the browser |
| DEBT-026 (admin/employee screen pairs duplicated) | section forms | **prevented**: `SectionTab` is reused by `W-46.5` for the two `update_own` sections |
| BUG-006 (no code splitting) | bundle | **deferred**; measure after this ticket, the first real screen set |

## 13. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `src/core/employee` | services, slice, `sectionFields.js`, tests |
| 2 | `src/core/employee` | `EmployeeList`, `EmployeeCreate`, tests |
| 3 | `src/core/employee` | `EmployeePage`, the tabs, `TerminateModal`, tests |
| 4 | `src/core/index.js` | routes and reducer registration |

## 14. Decisions

| # | Question | Answer |
|---|---|---|
| 1 | Wizard or one form for create? | **One form** for the root record; sections are tabs on the page. The Payroll wizard existed because salary was collected at the same time; salary is `W-47.1` |
| 2 | Where do Salary and Documents tabs go? | **Added to `EmployeePage` by `W-47.1` and the `W-21` screen ticket**, each registering a tab. `EmployeePage` takes tabs from a list so a later ticket adds one without editing this one |
| 3 | Identification shown in clear? | **Masked**, revealed per field on focus. The endpoint returns the values; the screen decides how loudly |
