# W-70 — Reconciliation

| Field | Value |
|---|---|
| **Work item** | `W-70` · issue [#92](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/92) |
| **Kind** | Data |
| **Stream / track** | I — Migration |
| **Size / skill** | S · DATA |
| **Owner** | krushna |
| **Blocked by** | `W-68` |
| **Blocks** | `W-71` |
| **Decisions** | `W-67` R4 (finance signs the LOP log), R7 (ledger is the evidence) |
| **Gaps addressed** | `BUG-003` surfaced not fixed, `DEBT-003` fixed |
| **Status** | Ready |

---

## 1. Problem

The build order says the migration is done when the numbers match, not when the script
finishes, and finance must sign the loss-of-pay log (`docs/target-state/09-build-order.md:311`).
The branch has both scripts under `code/migration/reconciliation/`:

| Script | What it does today | Gap |
|---|---|---|
| `financial_reconciliation.py` | Source staging vs `payroll.employee_payrun` count, gross, deductions, net; header vs lines (`:82-126`) | Prints a log only (`:128-137`). Nothing machine-readable, no exit code on variance, no per-employee comparison, no row counts for the other 18 tables |
| `lop_mismatch_generator.py` | HRMS unpaid days vs Payroll LOP days per employee per period into `core.lop_mismatch_log` (`:74-128`) | Writes `float(h_days)` into a `NUMERIC(4,1)` column (`:135-139`); inserts every run with `uuid4`, so a re-run doubles the log |

**Baseline** (branch, after `W-68` loaders on the compose stack)

| Command | Output |
|---|---|
| `python reconciliation/financial_reconciliation.py; echo $?` | report in the log, exit 0 even on variance |
| `psql -c "select count(*) from core.lop_mismatch_log"` after two runs | twice the first count |

## 2. Scope

**In scope**

- Row counts per target table against staging, per tenant.
- Financial totals and per-employee comparison, Decimal only.
- LOP mismatch log, idempotent, with the finance sign-off column the DDL already has.
- One JSON result file per run for `W-71` to collect, and a non-zero exit on variance.

**Out of scope**

- Fixing any variance. A variance is a finding (`W-67` R4).
- A screen for finance. Finance reads a CSV export; a screen is a later ticket if asked.
- Document checks — `W-69` verifies blobs itself.

## 3. Implementer tasks

### Task 1 — `code/migration/reconciliation/financial_reconciliation.py`

| Change | Detail |
|---|---|
| Row counts | For each of the 61 loaded tables (mapping §3, `W-68` Task 3, `W-69`): staging count after transform vs `select count(*) where tenant_id = …`. Quarantined rows are expected misses and are subtracted. The table list is shared with `W-68` Task 4 so both stay in step |
| Per employee, pay | Join staging `employee_payruns` to `payroll.employee_payrun` by `(payrun_id, employee_id)` through `id_map.json`; any line where `net_pay`, `gross_earnings` or `total_deductions` differ by more than `0.00` is listed. Same for `prior_payroll_month` |
| Per employee, CTC | For every migrated `payroll.ctc_structure`: sum of `employee_earning.monthly_amount` = legacy `ctc_structure.monthlySalary`, and = the employee's last legacy `employee_payruns.monthlySalary`. A difference is listed as `CTC_MISMATCH` |
| Per employee, leave | For every `core.leave_allocation`: `entitlement_days − consumed` = legacy remaining (`leave_balances.*_allotted − *_used` for the founder tenant, `employee_leave_allocation.annual_days + carried_forward_days − consumed_days` elsewhere) |
| Per employee, tax | For every `payroll.employee_inv_tax_summary`: `tax_to_be_paid` equals the legacy row; count of `employee_tds` active rows equals legacy |
| Decimal | Keep `to_decimal` (`:40-44`); compare with `Decimal`, never `float`. `grep -n "float(" reconciliation/` returns nothing |
| Output | Write `staging/reconciliation_<tenant>_<timestamp>.json`: `{counts: [...], totals: {...}, per_employee_variances: [...], lop_entries: n, clean: bool}` |
| Exit | 0 when every delta is `0.00` and every count matches; 1 otherwise. The log keeps the human table (`:128-137`) |
| Ledger | One `core.migration_ledger` row `step_name = 'reconcile'`, `status = DONE` or `FAILED` |

### Task 2 — `code/migration/reconciliation/lop_mismatch_generator.py`

| Change | Detail |
|---|---|
| Types | Pass `Decimal` values, not `float(...)` (`:135-139`); psycopg2 adapts `Decimal` to `NUMERIC` |
| Idempotent | `id = uuid5(namespace, tenant_id:employee_number:pay_period)` and `ON CONFLICT (id) DO UPDATE` on the day and deduction columns. Never touch `status` on conflict, so a row finance already approved stays approved |
| Export | `staging/lop_mismatch_<tenant>.csv` with the same columns, for finance |
| Filter | Only rows with `variance_days > 0` are written. The branch writes every row with any LOP on either side (`:132`), which buries the mismatches finance has to read (`09-build-order.md:311`). The matching rows go to the JSON result as a count, not to the log |

### Task 3 — `code/migration/tests/test_reconciliation.py`

| Assertion |
|---|
| Clean fixture (from `W-68` Task 4): exit 0, JSON `clean: true`, every count delta 0 across the 61 tables, every total delta `0.00` |
| One employee's `net_pay` changed by `0.01` in Postgres: exit 1, `per_employee_variances` has one entry naming that employee |
| One `employee_earning.monthly_amount` changed: exit 1, one `CTC_MISMATCH` entry |
| One `leave_consumption` row deleted: exit 1, one leave variance entry |
| LOP fixture with 2 mismatches and 3 matches: 2 rows in `core.lop_mismatch_log`; run twice, still 2 rows |
| Approve one row by SQL, run again: `status` still `APPROVED_BY_FINANCE` |

## 4. Standing rules

| Rule | Impact |
|---|---|
| New table | None. `core.lop_mismatch_log` ships with `W-68`'s `V177`, with `tenant_id`, RLS, `(tenant_id, pay_period)` index, `NUMERIC(4,1)` and `NUMERIC(12,2)` |
| Flyway | None |
| Money | `Decimal` end to end; the `float()` calls removed |
| Module boundary | Python only |

## 5. Gaps

| Gap | Treatment |
|---|---|
| `BUG-003` half-day LOP parsed as zero | Surfaced — appears as `0.5` variances in the log for finance. Not corrected |
| `DEBT-003` | Fixed — Task 3 |

## 6. Verification

```bash
cd code/migration && python -m pytest tests/test_reconciliation.py -q     # expected: 4 passed
grep -rn "float(" reconciliation/                                          # expected: no output
# against the compose stack, after the W-68 loaders:
python reconciliation/financial_reconciliation.py; echo "exit=$?"         # expected: exit=0, "Net Pay Disbursed ... 0.0000"
python reconciliation/lop_mismatch_generator.py
psql -c "select status, count(*) from core.lop_mismatch_log group by 1"    # expected: PENDING_FINANCE_SIGN_OFF | n
python reconciliation/lop_mismatch_generator.py
psql -c "select count(*) from core.lop_mismatch_log"                        # expected: same n
ls staging/reconciliation_*.json staging/lop_mismatch_*.csv                 # expected: one of each per tenant
```

| Check | Expected |
|---|---|
| Finance opens the CSV | every row has employee number, period, HRMS days, Payroll days, variance, deduction |
| Founder's tenant on non-prod data | `clean: true` in the JSON |

## 7. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Legacy payrun totals never equalled their lines (`load_payruns.py` recomputes headers from lines) | High | The header-vs-lines check reports it; the source-vs-target check uses lines on both sides, so the migration itself still reconciles to `0.00` |
| Employees in `employee_payruns` with no merged employee | Medium | Counted as a variance with reason `NO_EMPLOYEE`, listed in the JSON |

## 8. Rollback

None needed. The scripts only read, except the mismatch log, which `purge-tenant.sql`
clears with everything else.
