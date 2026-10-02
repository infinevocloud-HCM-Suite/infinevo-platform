# Feature: Prior payroll setup step

| Field | Value |
|---|---|
| **Feature ID** | `W-38.3` · from ticket #50 (`W-38`) · `PAY-17` |
| **Promoted to** | `docs/target-state/features/W-38-3-prior-payroll-setup-step.md` — **`W-38-3` with hyphens**, never `W-38.3`; `guard-edit` blocks the dotted form |
| **Owner** | krushna |
| **Apps touched** | `code/backend/core` (one catalogue line), `code/backend/payroll`, `infra/docker/seed` (two seed rows) |
| **Related gaps** | none |
| **Status** | **Ready** |
| **Written by** | founder, 2026-10-02 |
| **Blocked by** | `W-38.1` (`payroll.prior_payroll_month`, the status endpoint) |
| **Size** | S |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll`, plus one entry in `core`'s `SetupStepCatalogue` | 1 — **exception accepted by the founder 2026-10-02**: every payroll step is listed in `core` (`SetupStepCatalogue.java:33-40`) |
| Flyway migration | none | 1 |
| Externally testable behaviour | the setup checklist shows "Prior payroll"; it is done once months are imported or the first run is in April, and it can be skipped with a reason | 1 |
| Frontend area | none — `W-46.6` renders whatever the API returns (`W-46-6-setup-checklist-screen.md:179`) | 1 |

---

## 1. Problem

- The frozen checklist had the step, and marked it done when any pay run existed
  (`legacy/Payroll-Bend-SBoot/.../serviceimpl/OrgSetupStepsServiceImpl.java:75`). That means
  "has run payroll", not "loaded history"
- The new catalogue leaves it out until `W-38` gives the checker something to read, and keeps
  display order 4 free for it (`code/backend/core/src/main/java/com/infinevo/core/setup/SetupStepCatalogue.java:20-27`)
- `PayrollSetupStepConfiguration` says the same (`payroll/.../setup/PayrollSetupStepConfiguration.java:33-35`)

## 2. Scope

**In scope**

- `PRIOR_PAYROLL` in the catalogue, order 4, module `PAYROLL`
- Its checker in `payroll`
- `setup_step_skipped` on `W-38.1`'s status response, for `W-47.6`'s warning

**Out of scope**

- Skip itself — `W-24.1`'s endpoint, unchanged (`W-24-1-setup-checklist.md:125`)
- Organisation tax, order 5 — `W-36.3`
- Any screen

## 3. Flow

```
GET /api/v1/setup-checklist   (W-24.1, unchanged)
   --> priorPayrollSetupStepChecker.isComplete(tenant)
         prior_payroll_month has a row for the tenant                         → true
         the tenant's earliest REGULAR run not CANCELLED has period month 04  → true
         else                                                                 → false

GET /api/v1/payroll/prior-payroll/status?fy=   (W-38.1, one field added)
   --> setup_step_skipped = the tenant's PRIOR_PAYROLL row is_skipped
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Catalogue (change) | `core/.../setup/SetupStepCatalogue.java` | add `new StepDefinition("PRIOR_PAYROLL", "Prior payroll", PlatformModule.PAYROLL, 4)`; the class comment drops prior payroll from "not listed yet" |
| Checker (change) | `payroll/.../setup/PayrollSetupStepConfiguration.java` | add bean `priorPayrollSetupStepChecker`: `PriorPayrollMonthRepository.existsByTenantId` or the earliest run rule above; the class comment drops prior payroll |
| Seed (change) | `infra/docker/seed/05-tenant-setup-steps.sql` | one `PRIOR_PAYROLL` row, order 4, for each seeded tenant with `PAYROLL` (Acme and Globex) — `SeedSetupStepsMatchCatalogueTest` fails otherwise (`core/src/test/.../setup/SeedSetupStepsMatchCatalogueTest.java:5-9`) |
| Repository (change) | `payroll/.../payrun/PayRunRepository.java` | `findFirstByTenantIdAndRunTypeAndStatusNotOrderByPeriodAsc` |
| Service (change) | `payroll/.../priorpayroll/PriorPayrollServiceImpl.java` (`W-38.1`) | `status` adds `setup_step_skipped`, read through `core`'s setup checklist service |

No new endpoint. A tenant already on the platform gets the step as new, and keeps its other
steps' progress (`SetupStepCatalogue.java:25-27`, `W-24.1` §13 decision 1).

## 5. Frontend changes

None.

## 6. Database changes

None. The step row is created by `W-24.1`'s sync on first read; `core.tenant_setup_step`
(`core/V035__tenant_setup_step.sql`) already holds any step code.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/.../setup/PriorPayrollSetupStepCheckerTest.java` | an imported row ⇒ done; no import, first run `2026-04` ⇒ done; first run `2026-10` ⇒ not done; first run `2026-04` cancelled and next `2026-05` ⇒ not done; no run, no import ⇒ not done |
| Unit | `core/.../setup/SetupChecklistServiceTest.java` (extend) | the list has 8 steps with `PRIOR_PAYROLL` at order 4 for a payroll tenant, none for an HRMS-only tenant |
| Unit | `core/.../setup/SeedSetupStepsMatchCatalogueTest.java` (existing) | green with the two new seed rows |
| Unit | `core/.../setup/SetupStepCheckerRegistryTest.java` (existing) | no catalogue step without a checker |
| Integration | `payroll/.../priorpayroll/PriorPayrollStatusIT.java` | skip `PRIOR_PAYROLL` with a reason through `W-24.1`'s endpoint ⇒ `status` has `setup_step_skipped` true |

## 8. Verification

```bash
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| Suite | green, no skips; `SeedSetupStepsMatchCatalogueTest` and `SetupStepCheckerRegistryTest` pass |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The checker ports legacy "any run exists" | medium — it is the port habit | §3 rule; the unit test's `2026-10` case |
| A brand-new business that started mid-year never sees it done | expected | it skips the step with a reason; that is the design |

## 10. Rollback

Revert the branch. The step row left in `core.tenant_setup_step` is ignored by a catalogue
without the code.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | none |
| `Money` / `BigDecimal` | none |
| Index | none |
| Expand / contract | nothing |
| No write to `legacy/` | none |
| No module references another | `payroll` uses `core`; `core` lists the step by code and module only, never imports `payroll` |

## 12. Gap inventory

None.

## 13. Decisions — founder, 2026-10-02

| # | Question | Answer |
|---|---|---|
| 1 | When is the step done? | **When any month is imported, or when the tenant's first regular run is in April.** Otherwise it is to do, or skipped with a reason |
| 2 | Is it mandatory? | **No.** Skippable, like every step |
