# Feature: Tax declaration screens

| Field | Value |
|---|---|
| **Feature ID** | `W-47.3` · from ticket #65 · `PAY-09` |
| **Spec file** | `docs/target-state/features/W-47-3-tax-declaration-screens.md` |
| **Owner** | unassigned |
| **Apps touched** | `code/frontend/src/payroll/tax` only. No backend, no migration |
| **Related gaps** | DEBT-026 (prevented), BUG-006 (deferred) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-28 |
| **Blocked by** | `W-45`, `W-25` (the `/me` portal the employee panel mounts in), then `W-32.1`, `W-32.2`, `W-32.3`, `W-32.4`. All Ready specs; none on `main`. Tax calculator (`W-33`), proof of investment (`W-34`) and Form 16 (`W-36`) have no spec and are out of scope |
| **Size** | **L** |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an employee fills and submits their income-tax declaration for a financial year while the window is open, an officer sets the window and reads any employee's declaration, and a closed window refuses the edit in the browser as the server does | 1 |
| Frontend area | `src/payroll/tax` | 1 |

Within cap. Calculator, proof and Form 16 are separate tickets once their backends are
specified (§14).

---

## 1. Problem

The declaration is specified in four parts (`W-32.1`–`.4`) and has no screen. Both frozen
screens are stubs: the admin form has no data load and no save (`legacy/Payroll-Fend-react/src/pages/mainPages/employee/investmentDeclaration.js:20-91`, `handleSubmit` at `:97` logs to the console; zero `axios` calls in 1,085 lines), and the employee copy under `userPortal/userInvestment/` duplicates it (DEBT-026).

`W-32.1` names the split: "`W-47` (window), `W-25` (the `/me` panel)"
(`W-32-1-tax-declaration-window.md:85`). `W-25` lists five panels and tax declaration is
not one (`W-25` §4). This ticket owns both screens; the panel bean is §14 decision 1.

## 2. Scope

**In scope**

- Settings screen: declaration window per financial year — dates, lock, default regime, regime change allowed, PAN-for-rent rule, the two notify flags (`W-32.1` §4)
- Employee declaration under `/me`: one page, financial-year picker, header (regime and the three yes/no questions), then four sections as `Collapse` panels: **Housing** (house rent rows, home loan rows, let-out property rows with lines), **Deductions** (section 6A rows picked from the item catalogue, pre-tax rows, previous employment rows), **Other income**, **Summary** (declared totals; computed figures shown when present, "not yet computed" otherwise). Submit and Reopen buttons following `editable` and `window_open` from the header
- Officer view: `/payroll/tax-declarations/:employeeId/:fy`, the header read-only (the only officer endpoint `W-32.x` defines)
- Registering routes, the `tax` slice and the `taxDeclaration` portal panel component from `src/payroll/index.js`

**Out of scope**

- Tax calculator, regime comparison, TDS, Form 16 — `W-33`, `W-36`; the frozen `taxCalculator.js` and `compareTaxRegimes.js` are not ported until those exist
- Proof of investment: upload, review, chase list — `W-34`
- Officer editing of an employee's sections — `W-32.2`–`.4` expose `/me` paths only; `W-34`'s review screen is where an officer touches a section
- Any backend change

## 3. Flow

```
[admin]    --> /payroll/settings/tax-declaration/:fy --> taxSettingsService.get(fy) / save(fy, body)
[employee] --> /me  panel "Tax declaration"           --> declarationService.header(fy)          (creates DRAFT on first read)
           --> header save                            --> declarationService.saveHeader(fy, body)
           --> Housing                                --> declarationService.housing(fy) / saveHouseRent / saveHomeLoan / saveLetOut
           --> Deductions                             --> declarationService.items(fy) + deductions(fy) / save6a / savePreTax / savePrevEmployment
           --> Other income                           --> declarationService.otherIncome(fy) / saveOtherIncome
           --> Summary                                --> declarationService.summary(fy)
           --> Submit / Reopen                        --> declarationService.submit(fy) / reopen(fy)  --> re-read header
[officer]  --> /payroll/tax-declarations/:employeeId/:fy --> declarationService.headerOf(employeeId, fy)
```

## 4. Backend changes

None.

| Method | Path | Action | Spec |
|---|---|---|---|
| GET/PUT | `/api/v1/payroll/tax-declaration/settings/{fy}` | `payroll.settings.manage` | `W-32-1-tax-declaration-window.md:133-134` |
| GET/PUT | `/api/v1/me/tax-declaration/{fy}` | `payroll.tax_declaration.read_own` / `.declare_own` | `W-32-1-tax-declaration-window.md:135-136` |
| POST | `.../{fy}/submit`, `.../{fy}/reopen` | `payroll.tax_declaration.declare_own` | `W-32-1-tax-declaration-window.md:137-138` |
| GET | `/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}` | `payroll.tax_declaration.read` | `W-32-1-tax-declaration-window.md:139` |
| GET | `.../{fy}/housing`; PUT `.../house-rent`, `.../home-loan`, `.../let-out-property` | `read_own` / `declare_own` | `W-32-2-tax-declaration-housing.md:105-108` |
| GET | `.../{fy}/section6a-items`, `.../{fy}/deductions`; PUT `.../section6a`, `.../pre-tax-deductions`, `.../previous-employment` | `read_own` / `declare_own` | `W-32-3-tax-declaration-deductions.md:109-113` |
| GET/PUT | `.../{fy}/other-income`; GET `.../{fy}/summary` | `read_own` / `declare_own` | `W-32-4-tax-declaration-other-income-summary.md:102-104` |

Errors the screens must show by code: `NOT_EDITABLE`, `ALREADY_SUBMITTED`,
`WINDOW_CLOSED`, `LOCKED`, `OFFICER_ENTERED`, and the `400` naming a row and its limit.

## 5. Frontend changes

Follows the `W-45` contract (`W-45-frontend-shell.md` §5, §5b).

| File | Change |
|---|---|
| `src/payroll/tax/taxSettingsService.js`, `declarationService.js` | **new.** One function per row of §4; every `/me` call takes `fy` first |
| `src/payroll/tax/taxSlice.js` | **new.** `{ fy, header, sections: {housing, deductions, otherIncome, summary}, items }`; a section is loaded on first expand and replaced by its save response. Nothing in browser storage; a half-filled section is saved or lost, as `W-45` §5b requires |
| `src/payroll/tax/financialYear.js` | **new.** `currentFy(today)` and `fyOptions(n)` giving `2026-27`-style labels; the April–March rule mirrors `W-32.1`'s `FinancialYear` so the picker and the server agree |
| `src/payroll/tax/TaxWindowScreen.jsx` | **new.** Under `W-47.1b`'s `SettingsLayout` menu as "Tax declaration". FY picker, then the `W-32.1` `PUT` fields; `exists: false` renders the defaults banner. Lock is a `Switch` on the same form (`is_locked` is a column, not an endpoint) |
| `src/payroll/tax/DeclarationPage.jsx` | **new.** The portal panel component. FY picker; status `Tag` (`DRAFT` / `SUBMITTED`); banner when `!window_open` or `!editable`; header form; four `Collapse` panels below; Submit (enabled when `editable`), Reopen (enabled when `SUBMITTED` and `window_open`). Every save re-reads the header so `editable` is never stale |
| `src/payroll/tax/sections/HousingSection.jsx` | **new.** Three editable tables, one per `W-32.2` `PUT`; each with its own save. Landlord PAN required client-side only when the header says the rule applies **and** the annual rent exceeds the threshold returned by the server — the threshold is never a constant here |
| `src/payroll/tax/sections/DeductionsSection.jsx` | **new.** Section 6A: rows of item (`Select` from the items catalogue, grouped by `category`), description, amount, with `max_limit` shown beside the picker; pre-tax rows (`kind`, amount); previous-employment rows read-only when `entered_by` is the officer, with the `OFFICER_ENTERED` message on an attempted save |
| `src/payroll/tax/sections/OtherIncomeSection.jsx` | **new.** Rows `kind`, description, amount |
| `src/payroll/tax/sections/SummarySection.jsx` | **new.** `Descriptions` of `declared`; `computed` block when non-null, else one line "computed after the tax calculator runs" |
| `src/payroll/tax/OfficerDeclarationView.jsx` | **new.** Header `Descriptions` for an employee and FY; link back to the `W-46.1` employee page |
| `src/payroll/index.js` | `routes` gains the two admin paths; `reducers` gains `tax`; `portalPanels` gains `{ code: 'taxDeclaration', component: DeclarationPage }` for `W-25`'s shell to mount |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/payroll/settings/tax-declaration` | `TaxWindowScreen` | inside `SettingsLayout`; present with `payroll.settings` (`W-47.1b` §14 decision 1) |
| `/payroll/tax-declarations/:employeeId/:fy` | `OfficerDeclarationView` | inside `AppShell`; opened from the employee page, `useCan('payroll.tax_declaration.read')` |
| `/me` (panel `taxDeclaration`) | `DeclarationPage` | rendered by `W-25`'s portal when `/me/panels` lists it (§14 decision 1) |

**Ported logic.** Section grouping and field labels come from
`investmentDeclaration.js:20-91`; nothing else, since the file has no behaviour.

## 6. Database changes

None.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `declarationService.test.js`, `taxSettingsService.test.js` | every call hits its path with `fy` |
| Unit | `financialYear.test.js` | 31 March 2027 → `2026-27`; 1 April 2027 → `2027-28` |
| Unit | `taxSlice.test.js` | a section loads once per expand; save replaces it |
| Component | `TaxWindowScreen.test.jsx` | defaults banner; `PUT` body has the eight `W-32.1` fields |
| Component | `DeclarationPage.test.jsx` | `editable=false` disables every save and Submit; `SUBMITTED` + `window_open` enables Reopen; Submit `409 ALREADY_SUBMITTED` re-reads the header; **no `localStorage` write** on typing (spy) |
| Component | `HousingSection.test.jsx` | landlord PAN required only above the server threshold; `400` naming a row highlights that row |
| Component | `DeductionsSection.test.jsx` | items grouped by category with `max_limit` shown; officer-entered rows read-only; `OFFICER_ENTERED` message |
| Component | `SummarySection.test.jsx` | `computed: null` shows the placeholder line |
| Lint | existing `lint-rules.test.js` | unchanged |

## 8. Verification

```bash
cd code/frontend && npm ci && npm run lint && npm test
docker compose -f infra/docker/compose.yml up -d
```

| Check | Expected |
|---|---|
| lint, tests | clean |
| window | as admin@acme.local, `/payroll/settings/tax-declaration`, FY current: set a window covering today, save; `psql`: one row in `payroll.income_tax_declaration` |
| declare | log in as a linked Acme employee (`W-13.4`), `/me`, Tax declaration panel: header saves; house rent row saves; a 6A row over its `max_limit` shows the `400` naming it; other income saves; Summary shows declared totals and "computed after…" |
| submit | Submit → `SUBMITTED`, every field disabled; Reopen → `DRAFT` |
| closed window | admin moves the window into the past; the employee reloads: banner, saves disabled; a forced `PUT` from the console returns `409 NOT_EDITABLE` |
| officer | admin opens `/payroll/tax-declarations/<employee>/<fy>`: header shown; an employee-role user gets `NotEntitled` |
| isolation | admin@globex-full.local cannot read an Acme employee's declaration (not-found envelope) |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The browser re-implements a limit or threshold (80C cap, rent PAN threshold) | high — the frozen app did | every limit is read from the response (`max_limit`, the threshold on the header); `DeductionsSection` test asserts the picker shows the server's number; a numeric literal above 100 in `src/payroll/tax` is a review comment |
| One 1,000-line form returns | medium | five section files plus the page; each section saves alone |
| Admin and employee copies diverge (DEBT-026) | medium | one `DeclarationPage`; the officer view is read-only and small |
| `W-25`'s shell has no slot for a module-provided panel component | medium | §14 decision 1; the `portalPanels` export mirrors `employeeTabs` from `W-47.1a` |

## 10. Rollback

Frontend only. Revert the branch.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | no script |
| `Money` / `BigDecimal` | amounts are strings in forms, numbers on the wire; totals come from `/summary`, never summed in the browser |
| Index | none |
| Expand / contract | nothing |
| No module references another | `src/payroll/tax` imports `@shared/*` and `@shell/screens` only |
| No write to `legacy/` | read only |

## 12. Gap inventory

| ID | Overlap | Decision |
|---|---|---|
| DEBT-026 (admin/employee pairs; `adminProofEdit.js` 2,000 lines) | declaration forms | **prevented**: one page, one component per section |
| BUG-006 (bundle) | eight files | **deferred**; `W-47.2` measures first |

## 13. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `src/payroll/tax` | services, slice, `financialYear.js`, tests |
| 2 | `src/payroll/tax` | `TaxWindowScreen`, `OfficerDeclarationView`, tests |
| 3 | `src/payroll/tax` | `DeclarationPage` with header, submit, reopen, tests |
| 4 | `src/payroll/tax/sections` | the four sections, tests |
| 5 | `src/payroll/index.js` | routes, reducer, `portalPanels` |

## 14. Decisions and open questions

| # | Question | Answer |
|---|---|---|
| 1 | Who provides the `taxDeclaration` panel to `/me/panels`? | **Open for the founder.** `W-25` needs a `PortalPanelProvider` bean in `payroll` returning code `taxDeclaration`, action `payroll.tax_declaration.read_own`, endpoint `/api/v1/me/tax-declaration/{fy}`; and the frontend portal needs to mount a module-provided component by code. Recommendation: the bean goes into `W-32.1` (one class in its own module); the mount slot into `W-25` as a `portalPanels` map composed by the shell, the same way `W-47.1a` §14 decision 2 composes `employeeTabs` |
| 2 | Officer edits an employee's sections? | **No.** Not in `W-32.2`–`.4`; the officer's write path is `W-34` proof review |
| 3 | Where do calculator, regime compare, Form 16 and proof screens go? | **Three later tickets**, `W-47.3b` calculator and Form 16 after `W-33` and `W-36`, `W-47.3c` proof after `W-34`. Not numbered in the tracker until their backend specs exist |
