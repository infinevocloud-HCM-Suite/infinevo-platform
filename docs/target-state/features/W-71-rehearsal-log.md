# W-71 Rehearsal Log — Azure Migration Job Rehearsals

This log tracks every rehearsal run of the `caj-data-migration-{env}` Container Apps Job against acceptance (`uat`) and staging environments.

**Closure Criteria (W-71 spec §4 Task 5):**
Three consecutive `clean` rows on `uat` (with zero ledger failed steps, 0.00 INR financial reconciliation delta, and verified blob integrity) close the ticket before production cutover (`W-72`).

---

## Rehearsal Execution Log

| Date (UTC) | RUN_ID | Environment | Dump Date | Duration | Ledger Failed Steps | Reconciliation Clean (0.00 INR) | LOP Mismatches Logged | Quarantined Rows | Blobs Verified | Outcome | Notes / Operator |
|---|---|---|---|---|---|---|---|---|---|---|---|
| _Pending_ | — | `uat` | — | — | — | — | — | — | — | Pending | Initial rehearsal pending container deployment |

---

## Log Columns Reference:
- **Date (UTC):** Timestamp of job trigger.
- **RUN_ID:** Rehearsal identifier corresponding to `stinfinevouat/migration-dumps/{RUN_ID}/`.
- **Environment:** `dev`, `uat`, or `prod`.
- **Dump Date:** Creation date of legacy MySQL snapshot (`hrms.sql.gz` & `payroll.sql.gz`).
- **Duration:** End-to-end execution elapsed time.
- **Ledger Failed Steps:** Count of failed steps in `core.migration_ledger` (target: `0`).
- **Reconciliation Clean:** `true` if `financial_reconciliation.py` reports 0.00 INR delta across all payrun lines.
- **LOP Mismatches Logged:** Number of discrepancies logged to `core.lop_mismatch_log`.
- **Quarantined Rows:** Number of anomalies saved to `stg_quarantined_employees.json`.
- **Blobs Verified:** Document count checksum verification status.
- **Outcome:** `Pass` / `Fail`.
