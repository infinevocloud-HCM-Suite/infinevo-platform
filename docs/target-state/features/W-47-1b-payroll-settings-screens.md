# Feature: Payroll settings screens

| Field | Value |
|---|---|
| **Feature ID** | `W-47.1b` · from ticket #63 · `PAY-03`, `PAY-04`, `PAY-08` |
| **Spec file** | `docs/target-state/features/W-47-1b-payroll-settings-screens.md` |
| **Owner** | sayeed |
| **Apps touched** | `code/frontend/src/payroll/settings` only. No backend, no migration |
| **Related gaps** | DEBT-026 (prevented), BUG-006 (deferred) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-28 |
| **Blocked by** | `W-45`, then every endpoint it renders: `W-28`, `W-18.1`, `W-31.1`, `W-31.2`, `W-27.1`, `W-27.2`. All six specs are Ready; none is on `main` |
| **Size** | **M** |

Split from `W-47.1` on 2026-09-28; see `W-47-1a-salary-structure-screens.md` "Why `W-47.1` is two tickets".

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an administrator sets the tenant's pay schedule, working-day basis, EPF, ESI, professional tax and FBP plan from the browser, and every number reaches the server as entered | 1 |
| Frontend area | `src/payroll/settings` | 1 |

Within cap.

---

## 1. Problem

Six settings endpoints are specified and none has a screen. One of them is the screen
decision `D-60` assigns here by name: the pay schedule and the loss-of-pay basis live on
two tables in two modules, and `W-47` "builds the one admin screen that shows both"
(`07-decisions.md:72`).

The frozen settings screens are the markup source. Their logic is not ported, because the
target moved every number server-side:

| Frozen screen (legacy) | Why not ported as-is |
|---|---|
| `legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/paySchedules.js:26-32` (work-week days), `:244` (`POST /api/paySchedule`) | day list kept; the payable flags it does not have are on `core.lop_policy` (`W-18-1-lop-policy.md:117`) |
| `legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/statutoryComponents/index.js:88-97` | the `15000` wage ceiling and the rates are browser constants; `W-31.1` makes them columns (`W-31-1-epf-esi-settings.md` §6, `wage_ceiling`) |
| `legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/statutoryComponents/viewProfessionalTax.js:54,108` | one org-level PT row; `W-31.2` has shared state slabs plus a per-state override (`W-31-2-professional-tax.md:123-127`) |
| `legacy/Payroll-Fend-react/src/pages/mainPages/allSettingsPages/salaryComponents/` FBP is a flag on each component | the plan itself (window, lock, reminders) is `W-27.1`'s row |

## 2. Scope

**In scope**

- Pay schedule screen: work-week days, pay-day rule, pay day of month, input cut-off day, first period start (`W-28` §4) **and, on the same screen**, the LOP policy card: working-day basis, weekends payable, holidays payable, rounding, effective from (`W-18.1` §4). Two services, two saves, one screen, per `D-60`
- A read-only "period preview" on that screen: pick a month, show start, end, cut-off and pay date from `GET .../pay-schedule/period`
- EPF settings and ESI settings screens, every column of `W-31.1` §6 as a form; `source=DEFAULT` shown as a banner "defaults, not yet saved"
- Professional tax screen: one card per state of the tenant's work locations; slabs table; "Override" drawer; "Remove override"; history drawer
- FBP plan screen: enabled, window dates, notification flags, reminder days; lock and unlock buttons; the list of FBP-flagged components (read-only, edited in `W-47.1a`)
- FBP tab on the `W-46.1` employee page, registered as `W-47.1a` registers the Salary tab: the employee's declaration on the version in force as of a date, editable by the officer
- Registering routes and a `settings` slice from `src/payroll/index.js`

**Out of scope**

- The employee's own FBP declaration under `/me` — a portal panel; see §14 decision 2
- The salary component catalogue — `W-47.1a`
- Tax declaration window settings — `W-47.3`, with the rest of tax
- Any backend change

## 3. Flow

```
[admin] --> /payroll/settings/pay-schedule --> payScheduleService.get() + lopPolicyService.get()
        --> save schedule                  --> payScheduleService.save(body)
        --> save basis                     --> lopPolicyService.save(body)          (new version)
        --> preview month                  --> payScheduleService.period('2026-10')
[admin] --> /payroll/settings/epf | esi    --> statutoryService.get('epf') / save('epf', body)
[admin] --> /payroll/settings/professional-tax --> ptService.list() --> card per state
        --> override                       --> ptService.override(stateCode, body) / removeOverride(stateCode)
        --> history                        --> ptService.history(stateCode)
[admin] --> /payroll/settings/fbp          --> fbpService.plan() / savePlan(body) / lock() / unlock() / components()
[admin] --> /employees/:id  tab FBP        --> fbpService.declaration(employeeId, asOf) / setDeclaration(employeeId, lines)
```

## 4. Backend changes

None. Every call below is specified; none is on `main` yet.

| Method | Path | Action | Spec |
|---|---|---|---|
| GET/PUT | `/api/v1/payroll/pay-schedule` | `payroll.structure.read` / `payroll.settings.manage` | `W-28-pay-schedule.md:113-114` |
| GET | `/api/v1/payroll/pay-schedule/period?period=` | `payroll.structure.read` | `W-28-pay-schedule.md:115` |
| GET/PUT | `/api/v1/lop-policy` | `core.lop_policy.read` / `.manage` | `W-18-1-lop-policy.md:116-117` |
| GET/PUT | `/api/v1/payroll/settings/epf`, `/esi` | `payroll.salary.read` / `payroll.settings.manage` | `W-31-1-epf-esi-settings.md:95-98` |
| GET | `/api/v1/payroll/settings/professional-tax`, `/{stateCode}`, `/{stateCode}/history` | `payroll.salary.read` | `W-31-2-professional-tax.md:123-124,127` |
| PUT/DELETE | `/api/v1/payroll/settings/professional-tax/{stateCode}`, `.../override` | `payroll.settings.manage` | `W-31-2-professional-tax.md:125-126` |
| GET/PUT | `/api/v1/payroll/fbp/plan`; POST `.../lock`, `.../unlock`; GET `/api/v1/payroll/fbp/components` | `payroll.structure.read` / `payroll.settings.manage` | `W-27-1-fbp-plan.md:92-96` |
| GET/PUT | `/api/v1/payroll/employees/{employeeId}/fbp-declaration` | `payroll.fbp.read` / `payroll.salary.manage` | `W-27-2-fbp-declaration.md:107-108` |

## 5. Frontend changes

Follows the `W-45` contract (`W-45-frontend-shell.md` §5, §5b) and the `W-47.1a` file shape.

| File | Change |
|---|---|
| `src/payroll/settings/payScheduleService.js`, `lopPolicyService.js`, `statutoryService.js`, `ptService.js`, `fbpService.js` | **new.** One function per row of §4. `lopPolicyService` calls a `core` path from `payroll`; that is an HTTP call, not an import, and is allowed |
| `src/payroll/settings/settingsSlice.js` | **new.** `{ paySchedule, lopPolicy, epf, esi, fbpPlan }` loaded on first open, replaced on save. Nothing in browser storage |
| `src/payroll/settings/SettingsLayout.jsx` | **new.** Left `Menu` with the five entries below, content on the right. The frozen `settingsLayout/` shape (`legacy/docs/FEATURE_MAP.md:418,431`), rebuilt on tokens |
| `src/payroll/settings/PayScheduleScreen.jsx` | **new.** Two cards. **Schedule:** seven day `Checkbox`es (ISO 1–7), `pay_day_rule` radio (`LAST_DAY_OF_PERIOD`, `LAST_WORKING_DAY`, `SPECIFIC_DAY`), `pay_day_of_month` shown only for `SPECIFIC_DAY`, `input_cutoff_day`, `first_period_start`. **Loss-of-pay basis:** `working_day_basis` (`ACTUAL_DAYS`, `ORG_DAYS`, `FIXED_30`), `weekends_payable`, `holidays_payable`, `lop_rounding`, `effective_from` (required, defaults to the first of next month; a save is a new version). Each card saves alone. Third card **Preview:** month picker, four dates from the period endpoint; a `409` (no schedule) shows "save the schedule first" |
| `src/payroll/settings/EpfScreen.jsx`, `EsiScreen.jsx` | **new.** One `Form` each over every `W-31.1` §6 column. Rates and the ceiling are decimal strings validated to 4 places; the screen holds **no** constant, no sample calculation (the frozen `EPFSampleModal.js` is not ported). `source=DEFAULT` renders an `Alert` above the form |
| `src/payroll/settings/ProfessionalTaxScreen.jsx` | **new.** `Collapse`, one panel per `PtStateResponse`: state name, source `Tag` (`REFERENCE` / `OVERRIDE`), registration number, slabs `Table` (from, to, amount, female exempt, deduction months). Buttons: Override (drawer with the `PUT` body of `W-31-2-professional-tax.md:125`), Remove override (confirm, `DELETE`), History (drawer, newest first) |
| `src/payroll/settings/FbpPlanScreen.jsx` | **new.** `is_enabled`, window dates, `notify_on_release`, `notify_on_lock`, `reminder_days_before_close` (tag input of integers); Lock / Unlock buttons showing `is_locked` and `locked_at`; read-only table of FBP components with a link to `/payroll/components` |
| `src/payroll/settings/FbpDeclarationTab.jsx` | **new.** Exported through `src/payroll/index.js` `employeeTabs` (`W-47.1a` §14 decision 2), action `payroll.fbp.read`. "As of" date, then one row per FBP component: kind, name, `max_limit`, `annual_amount` input; pool and unallocated from the response. Save `PUT`s all lines; a `404` (no salary version) says so |
| `src/payroll/index.js` | `routes` gains the five paths below; `reducers` gains `settings`; `employeeTabs` gains the FBP tab |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/payroll/settings/pay-schedule` | `PayScheduleScreen` | inside `AppShell` and `SettingsLayout`; present when the feed carries `payroll.settings` (§14 decision 1) |
| `/payroll/settings/epf` · `/esi` · `/professional-tax` · `/fbp` | the four screens | same |
| `/employees/:id` (tab `fbp`) | `FbpDeclarationTab` | inside the `W-46.1` page; rendered when `useCan('payroll.fbp.read')` |

## 6. Database changes

None.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | one `*.test.js` per service | each hits its path; `lopPolicyService` hits `/v1/lop-policy`; no `axios` |
| Component | `PayScheduleScreen.test.jsx` | schedule save sends `working_days` as ISO numbers; basis save sends `effective_from`; the two saves are independent; period `409` renders the hint |
| Component | `EpfScreen.test.jsx` | `DEFAULT` banner shown; `wage_ceiling` `"15000"` sent as a string; a rate `"12.00001"` blocked; **no numeric literal `15000` in the component source** (grep assertion in the test) |
| Component | `ProfessionalTaxScreen.test.jsx` | one panel per state; override drawer `PUT`s slabs; remove calls `DELETE .../override` |
| Component | `FbpPlanScreen.test.jsx` | lock button calls `/lock`; reminder days sent as an integer array |
| Component | `FbpDeclarationTab.test.jsx` | save `PUT`s `lines[]` of `{kind, component_id, annual_amount}`; `404` message renders |
| Lint | existing `lint-rules.test.js` | unchanged |

## 8. Verification

```bash
cd code/frontend && npm ci && npm run lint && npm test
grep -rn "15000\|0\.12\|8\.33" src/payroll/settings   # no output
docker compose -f infra/docker/compose.yml up -d
# browser: log in as admin@acme.local
```

| Check | Expected |
|---|---|
| lint, tests, grep | clean |
| pay schedule | tick Mon–Fri, `LAST_WORKING_DAY`, cut-off 25, first period 2026-04-01, save; basis card `ACTUAL_DAYS`, weekends payable on, save; preview October 2026 shows four dates; `psql`: one row in `payroll.pay_schedule` and a new `core.lop_policy` version for Acme |
| EPF | banner "defaults" on first open; save with ceiling 15000; reload: banner gone, `source` no longer `DEFAULT` |
| PT | a state card per Acme work location; override one slab; `Tag` flips to `OVERRIDE`; remove; flips back |
| FBP | enable, set a window, lock; open an employee's FBP tab, declare an amount, reload: persists |
| isolation | admin@globex-full.local sees Globex's own schedule; the HRMS-only seed tenant gets `NotEntitled` on every `/payroll/settings/*` path |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A rate or ceiling constant comes back into the browser "as a default" | medium | the §8 grep and the `EpfScreen` test; defaults come from the `GET` with `source=DEFAULT` |
| The basis flags are saved on the schedule form, recreating the two-homes defect `D-60` closed | medium | two services, two forms, two save buttons; the `PayScheduleScreen` test asserts each `PUT` body has only its own keys |
| Six backend tickets land over weeks and this ticket idles | high | build in the tracker's order (`W-28` and `W-18.1` first); each screen is one file, mergeable when its endpoint is on `main` if the founder prefers partial merges |

## 10. Rollback

Frontend only. Revert the branch.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | no script |
| `Money` / `BigDecimal` | rates, ceilings and slab amounts are strings in the form and JSON numbers on the wire; no arithmetic in the browser |
| Index | none |
| Expand / contract | nothing |
| No module references another | `src/payroll/settings` imports `@shared/*` and `@shell/screens` only; it calls a `core` HTTP path, which is the sanctioned direction (`01-platform-shape.md:28`) |
| No write to `legacy/` | read only |

## 12. Gap inventory

| ID | Overlap | Decision |
|---|---|---|
| DEBT-026 (duplicated admin/employee screens) | FBP declaration | **prevented**: the officer tab and the future `/me` panel share `FbpDeclarationForm` |
| BUG-006 (bundle size) | five more screens | **deferred**; measured after `W-46.1` |
| `GAP_INVENTORY.md` browser-side statutory constants (`W-31.1` §1) | EPF, ESI, PT | **closed** by `W-31.x` on the server; §8 grep proves the screen carries none |

## 13. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `src/payroll/settings` | five services, slice, `SettingsLayout`, tests |
| 2 | `src/payroll/settings` | `PayScheduleScreen` (three cards), tests |
| 3 | `src/payroll/settings` | `EpfScreen`, `EsiScreen`, `ProfessionalTaxScreen`, tests |
| 4 | `src/payroll/settings` | `FbpPlanScreen`, `FbpDeclarationTab`, tests |
| 5 | `src/payroll/index.js` | routes, reducer, tab |

## 14. Decisions and open questions

| # | Question | Answer |
|---|---|---|
| 1 | No `payroll.settings` menu item exists (`NavigationCatalogue.java:52-84`) | **Open for the founder**, same as `W-47.1a` §14 decision 1. One `core` line: `payroll.settings` → `/payroll/settings/pay-schedule` → `GET /api/v1/payroll/pay-schedule`, `payroll.structure.read`. Recommendation: one small `core` ticket or an exception adding it under `W-28`, whose endpoint it points at |
| 2 | Who builds the employee's own FBP panel under `/me`? | **Open.** `W-27.2` says the panel is `W-25`'s (`W-27-2-fbp-declaration.md:69`); `W-25` lists five panels and FBP is not one of them (`W-25` §4). A panel needs a `PortalPanelProvider` bean in `payroll` (backend) plus the panel component. Recommendation: `W-27.2` adds the bean (one class, its own module); the component is `FbpDeclarationForm` from this ticket, mounted by `W-25` |
| 3 | Effective date for the basis save | **Required, defaulting to the first of next month.** `W-18.1` versions the policy; a same-day change mid-period would move the divisor under a run in progress |
