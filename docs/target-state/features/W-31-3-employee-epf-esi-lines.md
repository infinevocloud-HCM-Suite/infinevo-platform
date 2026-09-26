# Feature: Employee provident fund and state insurance lines on a salary version

| Field | Value |
|---|---|
| **Feature ID** | `W-31.3` · from ticket #38 (`W-31`) · `PAY-08` part 3 of 4 · the two tables `W-26.2` §13 decision 1 handed here |
| **Promoted to** | `docs/target-state/features/W-31-3-employee-epf-esi-lines.md` on the developer's `dev-<name>` branch — **`W-31-3` with hyphens**, never `W-31.3`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll`, `code/backend/migration` |
| **Related gaps** | DEBT-027 (fixed — the formula moves server-side), DEBT-034 (fixed for these tables), DEBT-018, DEBT-022 |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | `W-31.1` — the rates · `W-26.2` — the salary version, its `BASIC` earning and the statutory profile |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | 2 scripts, one table each — `V068`, `V069`. Aggregate exception, same as `W-26.2` | 1 — exception granted 2026-09-25 |
| Externally testable behaviour | saving a salary version stores the employee's PF and ESI lines, computed from the tenant's rates and the employee's profile, and they match a hand calculation to the rupee | 1 |
| Frontend area | none — `W-47` | 1 |

---

## 1. Problem

- **The backend stores whatever the browser sends.** `CtcStructureServiceImpl.java:236-271`
  copies `percentage`, `monthlyAmount` and `annualAmount` from the request into
  `CtcEpfComponent` and `CtcEsiComponent`; nothing computes them server-side
  (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/employee/CtcStructureServiceImpl.java`)
- **The formula is in three screens** (DEBT-027). `calculateStatutoryBenefits`
  (`legacy/Payroll-Fend-react/src/pages/mainPages/employee/salaryDetails.js:241-269`) hard-codes
  12%, 0.5% and an EDLI cap of 75 (`:248-250`), ignores the tenant's EPF settings, and sets
  ESI to a flat `482` when annual CTC is at most 2,00,000 (`:262-266`) — neither the rate
  nor the ceiling of the ESI Act
- **The percentage is a `String`.** `CtcEpfComponent.java:27`, `CtcEsiComponent.java:26`
- **No employee share is stored.** Legacy codes are `EPF_EMPLOYER`, `EDLI`, `ADMIN`
  (`CtcEpfComponent.java:25`), so the employee's own 12% exists nowhere until a payslip
  displays it

## 2. Scope

**In scope**

- `payroll.ctc_epf_component`, `payroll.ctc_esi_component` — one row per statutory line per
  salary version, derived on every create, revise and edit of a version
- `StatutoryLineDeriver` — the rules in §3, the only place they live
- The lines on `W-26.2`'s `SalaryVersionResponse` and in its CTC reconciliation

**Out of scope**

- Pay-run deduction and month-by-month earned-wage scaling — `W-31.4`. The amounts here are full-month, the same convention as `W-29.2`
- Professional tax — nothing per version; it is a slab lookup at run time (`W-31.2`)
- Screens — `W-47`

## 3. Flow

```
W-26.2 EmployeeSalaryServiceImpl.create / revise / update
   --> SalarySplitCalculator resolves the earnings, benefits, reimbursements   (W-26.2)
   --> StatutoryLineDeriver.derive(version, statutoryProfile, epf, esi, employee)   (this ticket)
   --> W-26.2's "sum of is_included_in_ctc equals annual_ctc" check now includes the employer statutory
       lines whose is_included_in_ctc is true
   --> rows written with the version, in the same transaction; a revision derives afresh, never copies
GET .../salary?asOf= --> SalaryVersionResponse gains statutory[]
```

**Provident fund**, when `profile.is_eligible_for_pf` and `epf.is_enabled`
(`W-26.2` §6 `employee_statutory_profile`; `W-31.1` §6 `epf_setting`):

| Line | `component_code` | Share | Wage base | Amount |
|---|---|---|---|---|
| Employee PF | `EPF_EMPLOYEE` | employee | `basic`, capped at `wage_ceiling` if `restrict_employee_to_ceiling` | `wage × employee_rate` |
| Employer EPS | `EPS_EMPLOYER` | employer | `min(basic, wage_ceiling)` — always capped, the EPS rule | `wage × eps_rate`; **0** if not `profile.is_eligible_for_eps` or age on `effective_from` ≥ `eps_senior_age` |
| Employer PF | `EPF_EMPLOYER` | employer | `basic`, capped if `restrict_employer_to_ceiling` | `wage × employer_rate − EPS_EMPLOYER` |
| EDLI | `EDLI` | employer | `min(basic, wage_ceiling)` | `wage × edli_rate` — the legacy cap of 75 (`salaryDetails.js:250`) is this formula at 15,000 |
| Admin charge | `EPF_ADMIN` | employer | as employer PF | `wage × admin_charge_rate` |

`basic` is the `monthly_amount` of the version's earning whose catalogue `earning_type` is
`BASIC` (`W-26.1` §6; `W-26.2` §4 requires one). Age is from `core` `EmployeePersonalService`
(`EmployeePersonalResponse.dateOfBirth`, `:18`); no personal row or no date, and EPS applies. `profile.contributes_eps_on_higher_wages` lifts the EPS cap.

**State insurance**, when `profile.is_eligible_for_esi`, `esi.is_enabled`, and the version's
monthly gross — Σ enabled earnings' `monthly_amount` — is at most `esi.wage_ceiling`:

| Line | `component_code` | Share | Amount |
|---|---|---|---|
| Employee ESI | `ESI_EMPLOYEE` | employee | `gross × employee_rate` |
| Employer ESI | `ESI_EMPLOYER` | employer | `gross × employer_rate` |

Above the ceiling, no ESI rows at all. The flat `482` (`salaryDetails.js:263`) is not ported.

All rates are percentages: `amount = base × rate ÷ 100` with `Money.multiply` and
`Money.divide` at scale 4 (`shared/.../money/Money.java:32,68,73`). No rounding here — a
pay-run line rounds once, `W-31.4`. `annual_amount = monthly_amount × 12`.

`is_included_in_ctc`: employer PF lines follow `epf.include_employer_in_ctc`; EDLI and
admin follow `epf.include_edli_admin_in_ctc`; employer ESI follows `esi.include_employer_in_ctc`;
employee shares are never in CTC — they are part of the earnings they come out of.

## 4. Backend changes

Under `code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/lines/`, plus one
change in `salary/`.

| Layer | File | Change |
|---|---|---|
| Entity | `CtcEpfComponent.java`, `CtcEsiComponent.java` | new, `@Table(schema = "payroll")`, `UUID` ids |
| Repository | two | new; `findByTenantIdAndCtcStructureId`, `deleteByTenantIdAndCtcStructureId` |
| Service | `StatutoryLineDeriver.java` | new — the tables in §3; pure, no repository; takes `Money` and returns a list of lines |
| Service (change) | `salary/EmployeeSalaryServiceImpl` (`W-26.2`) | calls the deriver after the split and before the CTC check, on create, revise and update; deletes and rewrites the rows on update |
| DTO (change) | `salary/SalaryVersionResponse` | gains `statutory[]` of `{component_code, share, wage_base, rate, monthly_amount, annual_amount, is_included_in_ctc}` |
| Enumeration | `StatutoryComponentCode` (the seven codes), `ContributionShare` (`EMPLOYEE`, `EMPLOYER`) | new |

**API contract** — no new endpoint. `W-26.2`'s four salary reads gain `statutory[]`.

Validation added to `W-26.2`'s POST/PUT: none new. If the tenant has no EPF or ESI row,
`W-31.1`'s defaults apply, which are `is_enabled=false`, so no lines.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Table | Tenant-aware? | Reversible? |
|---|---|---|---|
| `payroll/V068__ctc_epf_component.sql` | `payroll.ctc_epf_component` | yes | additive |
| `payroll/V069__ctc_esi_component.sql` | `payroll.ctc_esi_component` | yes | additive |

Both, one shape, from `CtcEpfComponent.java:25-31` and `CtcEsiComponent.java:24-30` with the
`String` percentage fixed:
`id uuid PK` · `tenant_id uuid NOT NULL REFERENCES core.tenant` ·
`ctc_structure_id uuid NOT NULL REFERENCES payroll.ctc_structure(id)` ·
`component_code varchar(32) NOT NULL` · `share varchar(16) NOT NULL CHECK (share IN ('EMPLOYEE','EMPLOYER'))` ·
`wage_base numeric(19,4) NOT NULL` · `rate numeric(7,4) NOT NULL` ·
`monthly_amount numeric(19,4) NOT NULL` · `annual_amount numeric(19,4) NOT NULL` ·
`is_included_in_ctc boolean NOT NULL` · four audit columns.

`ctc_epf_component.component_code` is checked in `('EPF_EMPLOYEE','EPF_EMPLOYER','EPS_EMPLOYER','EDLI','EPF_ADMIN')`;
`ctc_esi_component` in `('ESI_EMPLOYEE','ESI_EMPLOYER')`.

`component_label` and `calculation_type` (`CtcEpfComponent.java:26,28`) are not stored: the
label is the code's, and the calculation is `wage_base × rate`.

- [x] `tenant_id` on both, leading index column
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — `uk_ctc_epf_component_tenant_structure_code (tenant_id, ctc_structure_id, component_code)` unique, and the ESI twin
- [x] Money `numeric(19,4)`, rates `numeric(7,4)`; nothing floating, nothing text
- [x] Expand / contract — two new tables; `W-26.2`'s tables untouched

RLS and `tenant_isolation` in the exact `CASE` form in both scripts.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../statutory/lines/StatutoryLineDeriverTest.java` | **the hand calculation** in §8, every row; unrestricted employee share on basic 25,000 is 3,000; not eligible for EPS puts the whole 12% in `EPF_EMPLOYER`; age 58 on `effective_from` does the same; gross 21,000 gets ESI and 21,000.01 does not; disabled EPF setting gives no PF rows; no rounding — 12% of 15,000.5000 is 1,800.0600 |
| Integration | `payroll/.../statutory/lines/SalaryStatutoryLinesIT.java` | create a version: seven rows exist and `statutory[]` returns them; revise: the new version has its own rows from the rates in force, the old version's rows unchanged; update a future version: rows rewritten, count unchanged |
| Integration | `payroll/.../statutory/lines/SalaryCtcReconciliationIT.java` | with `include_employer_in_ctc=true`, a version whose earnings alone equal `annual_ctc` is refused with the employer PF in the difference message; with it `false`, accepted |
| Integration | `payroll/.../statutory/lines/StatutoryLinesRlsIT.java` | as `app_user`, tenant A reads none of tenant B's rows |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

The worked example. Basic 25,000, gross 45,000, eligible for PF, EPS and ESI, age 30,
EPF settings: both restrict flags `true`, ceiling 15,000, rates 12 / 12 / 8.33 / 0.5 / 0.5;
ESI enabled, ceiling 21,000.

| Line | Share | Wage base | Rate | Monthly |
|---|---|---|---|---|
| `EPF_EMPLOYEE` | employee | 15,000.0000 | 12.0000 | 1,800.0000 |
| `EPS_EMPLOYER` | employer | 15,000.0000 | 8.3300 | 1,249.5000 |
| `EPF_EMPLOYER` | employer | 15,000.0000 | 12.0000 − 8.3300 | 550.5000 |
| `EDLI` | employer | 15,000.0000 | 0.5000 | 75.0000 |
| `EPF_ADMIN` | employer | 15,000.0000 | 0.5000 | 75.0000 |
| `ESI_EMPLOYEE`, `ESI_EMPLOYER` | | gross 45,000 > 21,000 | | **no rows** |

Same employee with `restrict_employee_to_ceiling=false`: `EPF_EMPLOYEE` 3,000.0000, employer
rows unchanged.

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT table_name, column_name, data_type FROM information_schema.columns
    WHERE table_schema='payroll' AND table_name IN ('ctc_epf_component','ctc_esi_component')
      AND column_name IN ('rate','wage_base','monthly_amount','annual_amount') ORDER BY 1,2;"
cd code/backend && mvn -q verify
grep -rn "String percentage\|Math.round\|doubleValue" code/backend/payroll/src/main/java/com/infinevo/payroll/statutory | wc -l
```

| Check | Expected |
|---|---|
| Columns | eight rows, all `numeric` |
| Suite | green, no skips; `StatutoryLineDeriverTest` asserts `1249.5000` and `550.5000` |
| Legacy shapes | `0` |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Rounding to the rupee here, then again in the pay run — a rupee lost per line | **high** — legacy rounds at every step (`salaryDetails.js:248-254`) | Scale 4 here, asserted by the `1,249.5000` row; `W-31.4` rounds once per line |
| The deriver is called after the CTC check, so employer PF never counts in CTC | medium | `SalaryCtcReconciliationIT` |
| A revision copies the old rows instead of deriving from today's rates | medium | `SalaryStatutoryLinesIT` changes the rate between v1 and v2 and asserts v2 differs |
| `basic` taken from the request instead of the resolved `monthly_amount` | low | The deriver takes the resolved version, never the request |

## 10. Rollback

Nothing is deployed. Both scripts are additive.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | both tables |
| Flyway only, `ddl-auto` nowhere | `V068`, `V069` |
| `Money`/`BigDecimal` for money | `Money` in the deriver, `numeric(19,4)` in the tables |
| Index on `tenant_id` plus lookup columns | one unique per table |
| Expand / contract | new tables only |
| No module references another module | `payroll`, `core` (employee date of birth), `shared` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-027 statutory formula copy-pasted across three screens | **Fixed** — one server-side deriver |
| DEBT-034 (proposed, `W-31.1`) | **Fixed** for these two tables |
| DEBT-018, DEBT-022 | **Honoured**, **fixed** |

## 13. Decisions — settled 2026-09-25

| # | Question | Answer |
|---|---|---|
| 1 | Store the employee share, which legacy never did? | **Yes.** `W-31.4` deducts it (founder decision 2); a payslip shows what the version says, not a recomputation |
| 2 | ESI eligibility on annual CTC ≤ 2,00,000 as the screen did? | **No.** The Act's test is monthly gross against the ceiling in `esi_setting` |
| 3 | Round here? | **No.** Once, on the pay-run line |
