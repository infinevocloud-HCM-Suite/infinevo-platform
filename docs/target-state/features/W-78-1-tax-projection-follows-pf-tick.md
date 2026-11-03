# Feature: Tax projection follows the PF switch and tick, like the pay run

| Field | Value |
|---|---|
| **Feature ID** | `W-78.1` · `PAY-TAX` |
| **Owner** | claude |
| **Apps touched** | `code/backend/payroll` (`taxcalc`) |
| **Related gaps** | `W-76.3` review F-2 (2026-11-01): the pay run applies PF from the company switch and the employee tick every month (`StatutoryLineContributor.java:167`), but the tax projection still reads the employee PF saved on the salary (`TaxInputAssembler.java:419`, `:492-536`). HR unticks PF without a new salary version → no PF paid, but TDS still deducts PF as 80C → TDS too low |
| **Status** | **Done** — `c9fce7a3` |
| **Blocked by** | nothing |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | The yearly tax projection counts employee PF only for months where the pay run would deduct it | 1 |
| Frontend area | none | 1 |

## Rule

From the current month on, each projected month counts employee PF toward tax only when the company EPF setting is on **and** the employee's statutory profile is ticked for PF — the same test as `StatutoryLineContributor.java:167`. The amount stays the saved salary's `EPF_EMPLOYEE` monthly amount (rates and wage limit unchanged). Months already past keep the salary's PF, as today (the projection reads the salary for every month).

## Changes

| File | Change |
|---|---|
| `payroll/taxcalc/PfApplicability.java` (new) | The pay run's PF test: company EPF on and employee ticked |
| `payroll/taxcalc/TaxInputAssembler.java` | Asks `PfApplicability` once per employee; from this month on, skips the salary's EPF when it says no |

No table, no Flyway script, no screen.

## Tests

| File | Covers |
|---|---|
| `taxcalc/TaxInputAssemblerTest` | PF applies → coming months counted; PF does not apply → not counted; months already past still counted |

## Verification

```bash
cd code/backend && ./mvnw -B -pl payroll -am verify
```

Break it once: remove the new check and the "unticked" test must fail.
