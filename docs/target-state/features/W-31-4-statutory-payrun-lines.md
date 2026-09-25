# Feature: Statutory lines in the pay run

| Field | Value |
|---|---|
| **Feature ID** | `W-31.4` · from ticket #38 (`W-31`) · `PAY-08` part 4 of 4 · the `STATUTORY` contributor `W-29.2` reserved |
| **Promoted to** | `docs/target-state/features/W-31-4-statutory-payrun-lines.md` on the developer's `dev-<name>` branch — **`W-31-4` with hyphens**, never `W-31.4`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/payroll` |
| **Related gaps** | BUG-012 (proposed in `W-29.2` §12, fixed here), BUG-015 (proposed below, fixed here), DEBT-037 (proposed in `W-31.2`, fixed here) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | `W-31.3` — the version's PF and ESI lines · `W-31.2` — `ProfessionalTaxService.resolve` · `W-29.3` — `paid_days` and `basis` on the context |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | **none** — no table, no column | 1 |
| Externally testable behaviour | computing a run deducts the employee's PF, ESI and professional tax, reports the employer's shares, and the figures match a hand calculation to the rupee | 1 |
| Frontend area | none — `W-47` | 1 |

Within cap. One bean added to `W-29.2`'s loop; `PayRunComputationService` is not edited
(`W-29-2-pay-run-computation.md:38-40`).

---

## 1. Problem

In the frozen pay run the statutory money is wrong in both directions.

- **The employer's PF is deducted from the employee.** `calculateEpfEmployer` sums the
  `EPF_EMPLOYER` rows (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/payruns/EmployeePayRunServiceImpl.java:948-988`)
  and the result *is* `totalDeductions` (`:295`). BUG-012
- **The employee's own PF and all of ESI are never deducted.** Nothing between `:295` and
  `:310` touches them; they reach the payslip only as display rows copied from the CTC
  (`:1380-1438`). BUG-015
- **PT is the only correct deduction** (`:305-310`), and it uses today's date, not the
  period's (DEBT-037)
- **The earned-wage flag does nothing.** `considerEarnedSalaryForEpf` (`Epf.java:18`) is
  stored and never read by the run

## 2. Scope

**In scope**

- `StatutoryLineContributor`, `@Order(400)`: after `STRUCTURE` (100), `LOP` (200) and
  `PAY_INPUT` (300); before `TAX` (`W-36`, 500)
- Employee PF, employee ESI, professional tax as `DEDUCTION` lines; employer PF, EPS, EDLI,
  admin and employer ESI as `BENEFIT` lines — reported in `total_benefits`, never in net
  (`W-29.2` §3)
- Earned-wage scaling for PF when the tenant asks for it; rupee rounding, once per line

**Out of scope**

- Tax deducted at source — `W-36`
- Any change to the rates, slabs or version lines — `W-31.1`–`.3`
- Employer cost totals on the run — `W-37` reads `total_benefits`
- Screens — `W-47`

## 3. Flow

```
PayRunComputationServiceImpl loop (W-29.2 §3), per included employee, ctx carries:
   version (W-26.2 + W-31.3 statutory[]), employee (W-29.3), basis and paid_days (W-29.3), period

StatutoryLineContributor.contribute(ctx):
   epf = StatutorySettingsService.epf(tenant); esi = ...esi(tenant)                  (W-31.1)
   factor = epf.consider_earned_wage ? paid_days ÷ basis.payableDays : 1              (W-29.3)
   for each version.statutory line:
       base   = line.wage_base × factor            -- earned wage, then the ceiling again if the line is capped
       amount = round(base × line.rate ÷ 100)     -- §3 rounding
       EMPLOYEE share --> DEDUCTION line, source STATUTORY, code = line.component_code
       EMPLOYER share --> BENEFIT   line, source STATUTORY
   ESI lines only if the version's gross is within esi.wage_ceiling — already decided by W-31.3's presence of rows
   PT: state = core WorkLocationService.get(employee.workLocationId).stateCode
       grossForPt = Σ EARNING lines already on ctx − the LOP line
       amount = ProfessionalTaxService.resolve(tenant, state, grossForPt, employee.gender, period.end)   (W-31.2)
       amount > 0 --> DEDUCTION line, code PROFESSIONAL_TAX
```

**Rounding, once per line, to the rupee** — the statutory rules, not a convention:

| Line | Rule |
|---|---|
| `EPF_EMPLOYEE`, `EPF_EMPLOYER`, `EPS_EMPLOYER`, `EDLI`, `EPF_ADMIN` | nearest rupee, `HALF_UP`; `EPF_EMPLOYER` is `round(employer total) − round(EPS)` so the two add up to the employer's 12% |
| `ESI_EMPLOYEE`, `ESI_EMPLOYER` | **up** to the next rupee, `CEILING` |
| `PROFESSIONAL_TAX` | the slab amount, already whole |

The line's `amount` is `numeric(19,4)` holding a whole number. `net_pay` rounding in
`W-29.2` §3 is unaffected.

**Earned wage.** `prorate_restricted_wage` (`W-31.1` §6) decides whether the ceiling is
scaled too: `true` → cap at `wage_ceiling × factor`; `false` → cap at `wage_ceiling` after
scaling (legacy's "halve then cap", `editEPF.js:186-194`). With `consider_earned_wage=false`
the factor is 1 and the version's amounts are used as they are.

The employee's PT eligibility is `W-26.2`'s `is_eligible_for_pt`, read from the profile on
the context; legacy `emp.getEligibleForPt()` (`:300`).

## 4. Backend changes

All under `code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/payrun/`.

| Layer | File | Change |
|---|---|---|
| Contributor | `StatutoryLineContributor.java` | new, `@Order(400)`, implements `W-29.2`'s `PayLineContributor`; §3 |
| Service | `StatutoryLineRounder.java` | new — the rounding table, pure |
| Context (change) | `payrun/PayRunEmployeeContext` (`W-29.2`, extended by `W-29.3`) | gains `statutoryProfile` — one read per employee, from `W-26.2`'s `EmployeeStatutoryProfileService` |
| Enumeration (change) | `payrun/LineSource` | `STATUTORY` already exists (`W-29.2` §6 `CHECK`); nothing added |

**API contract** — no new endpoint. `W-29.2`'s `GET .../employees/{employeeId}/lines`
returns the new lines with `source = STATUTORY`.

## 5. Frontend changes

None.

## 6. Database changes

None. `employee_payrun_line.source` already admits `'STATUTORY'`
(`W-29-2-pay-run-computation.md` §6). `component_id` is null on these lines — they come
from no catalogue row; `component_code` and `component_name` are the statutory code and
its label.

- [x] creates no table
- [x] no Flyway script
- [x] `Money` throughout; `numeric(19,4)` lines as `W-29.2`
- [x] nothing to index, nothing to drop

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../statutory/payrun/StatutoryLineRounderTest.java` | 1,249.5000 → 1,250; employer 1,800 − 1,250 = 550; ESI 168.7500 → 169 (`CEILING`), 168.0001 → 169; PT passes through |
| Unit | `payroll/.../statutory/payrun/StatutoryLineContributorTest.java` | **the hand calculation** in §8, every row; the employer rows are `BENEFIT`; an employee not eligible for PT gets no PT line; `consider_earned_wage` with 20 of 30 paid days scales the base by two thirds before the cap; `prorate_restricted_wage` scales the cap too; no version statutory rows → no PF or ESI lines, PT still resolved |
| Integration | `payroll/.../statutory/payrun/PayRunStatutoryIT.java` | **the acceptance test**: lock and compute a one-employee run with the §8 setup; `total_deductions = 2,000.0000`, `total_benefits = 1,950.0000`, `net_pay = 45,000.00 − 2,000.00 = 43,000.00` with no reimbursements; lines readable with `source = STATUTORY`; `implements PayLineContributor` count is 4 |
| Integration | `payroll/.../statutory/payrun/PayRunStatutoryPeriodIT.java` | a February run computed in March for a Tamil Nadu employee produces no PT line; a March run does — the period decides, not the clock |
| Integration | `payroll/.../statutory/payrun/PayRunStatutoryQueryCountIT.java` | 20 employees compute with one settings read, one PT slab read per distinct state, and no per-employee work-location query beyond the first per location (`W-55`) |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

Worked example — the `W-31.3` §8 employee, July, Karnataka, all 31 days paid,
`consider_earned_wage=true`, eligible for PT, male:

| Line | Kind | Amount |
|---|---|---|
| `EPF_EMPLOYEE` | DEDUCTION | 1,800 |
| `PROFESSIONAL_TAX` | DEDUCTION | 200 — Karnataka, gross 45,000 |
| `ESI_EMPLOYEE` | — | no line, gross above 21,000 |
| `EPS_EMPLOYER` | BENEFIT | 1,250 |
| `EPF_EMPLOYER` | BENEFIT | 550 |
| `EDLI` | BENEFIT | 75 |
| `EPF_ADMIN` | BENEFIT | 75 |
| **total_deductions** | | **2,000.0000** |
| **total_benefits** | | **1,950.0000** — not in net |
| **net_pay** | | **43,000.00** |

```bash
cd code/backend && mvn -q verify
grep -rn "implements PayLineContributor" code/backend/payroll/src/main/java | wc -l
grep -rn "LocalDate.now()" code/backend/payroll/src/main/java/com/infinevo/payroll/statutory | wc -l
grep -rn "EPF_EMPLOYER" code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/payrun | grep -i "deduction" | wc -l
git -C code/backend diff --stat main -- payroll/src/main/java/com/infinevo/payroll/payrun/PayRunComputationServiceImpl.java
```

| Check | Expected |
|---|---|
| Suite | green, no skips; `PayRunStatutoryIT` asserts `43000.00` |
| Contributors | `4` — structure, LOP, pay input, statutory |
| `LocalDate.now()` | `0` |
| Employer PF as a deduction | `0` |
| `PayRunComputationServiceImpl` | no diff — the loop is not edited |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Employer PF lands in `total_deductions` "because legacy did" | **high** | The grep in §8; `PayRunStatutoryIT` asserts 2,000 not 3,950 |
| Rounded twice — once in `W-31.3`, once here | medium | `W-31.3` stores scale 4; `StatutoryLineRounderTest` starts from `1,249.5000` |
| PT resolved on today's date | medium | `PayRunStatutoryPeriodIT` |
| Settings and slabs read once per employee, 20 employees → 60 queries | medium | Cached on the run for its duration, `PayRunStatutoryQueryCountIT` |
| The contributor edits the loop or the service to "pass more context" | low | The `git diff --stat` check in §8 expects nothing |

## 10. Rollback

Nothing is deployed. No schema change. A computed run recomputes from `LOCKED` data.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | creates no table |
| Flyway only, `ddl-auto` nowhere | no script |
| `Money`/`BigDecimal` for money | `Money`; rounding through `BigDecimal.setScale` in one class |
| Index on `tenant_id` plus lookup columns | nothing new |
| Expand / contract | nothing |
| No module references another module | `payroll`, `core` (work location, employee), `shared` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| BUG-012 employer PF deducted (proposed, `W-29.2` §12) | **Fixed** |
| BUG-015 employee PF and ESI never deducted (proposed below) | **Fixed** — founder decision 2, 2026-09-25 |
| DEBT-037 today's date and hard-coded female rule (proposed, `W-31.2` §12) | **Fixed** |
| BUG-005 HRMS coupling | **Discounted** — no HRMS call |

**Proposed GAP entry** — not yet in `legacy/docs/GAP_INVENTORY.md`, evidence in §1:

| ID | Category | Finding |
|---|---|---|
| BUG-015 | Payroll / Pay run | The employee's PF share and both ESI shares are never deducted: `totalDeductions` is employer PF (`EmployeePayRunServiceImpl.java:295`) plus PT (`:310`); ESI and employee PF appear only as payslip display rows copied from the CTC (`:1380-1438`) |

## 13. Decisions — settled 2026-09-25

| # | Question | Answer |
|---|---|---|
| 1 | Deduct employee PF and ESI, or keep legacy's employer-only deduction? | **Deduct employee PF, employee ESI and PT; employer shares are shown, not deducted** — founder, 2026-09-25 |
| 2 | Order 400? | **Yes.** PF on earned wage needs `W-29.3`'s `paid_days`; PT gross needs the LOP line; `TAX` must see the PF deduction for section 80C, so it comes after |
| 3 | Employer lines as `BENEFIT`? | **Yes.** `W-29.2` §3 already sums `BENEFIT` into `total_benefits` outside net; no new kind |
