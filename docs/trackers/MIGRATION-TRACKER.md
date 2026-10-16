# Migration Tracker

> Moving live data out of the four frozen applications into the new tables — stream I.
> **Nothing here touches production data until every other stream works.**
> **GitHub is authoritative.** Status legend: [README.md](README.md).
> Last refreshed: **2026-10-16**.

## Summary

| | |
|---|---|
| Tickets | 8 |
| Spec approved | **8** |
| Code on `core-migration-non-prod-data` | **8** |
| Ready for non-prod rehearsal | **Yes** |
| Blocked by | Rehearsal validation on Azure |

**Stage A & B Containerization & Infrastructure Ready.** Pipeline implemented, Flyway `V177` prepared, secrets wired, and document upload integrated.

---

## 1. The tickets

| # | Ticket | What it is | Blocked by | Labels |
|---|---|---|---|---|
| #87 | `W-67` Migration rules | Employee deduplication rules, the leave authority decision, the timesheet data decision, duplicate entity resolution, the loss-of-pay reconciliation approach. **Written and agreed before a single row moves** | none (signed 2026-10-16) | `signed` founder · spec `W-67-migration-rules.md` |
| #88 | `W-68.1` Migration engine — extract and staging | Pull from the two frozen MySQL databases into staging | `W-67` | `code done` mohit · extractors without hardcoded fallbacks, SSL read-only |
| #89 | `W-68.2` Migration engine — transform and merge | Reshape and merge the two sources into one | `W-68.1` | `code done` mohit · 3-cohort merge transformer |
| #90 | `W-68.3` Migration engine — load and restartability | Load into Postgres, and survive being restarted halfway | `W-68.2` | `code done` mohit (lead) · core & payroll loaders, Flyway `V177__migration_ledger.sql` · krushna (loaders 3i–3k) |
| #91 | `W-69` Document migration | Rewrite document references, copy the blobs, verify them | `W-21`, `W-68` | `code done` sayeed · `load_documents.py` with Cloudinary download, W-21 validation, Azure Blob upload |
| #92 | `W-70` Reconciliation | Row counts, financial totals, per-employee comparison, and a **loss-of-pay mismatch log for finance** | `W-68` | `code done` krushna · `financial_reconciliation.py` (0.00 INR delta) & `lop_mismatch_generator.py` |
| #93 | `W-71` Rehearsals | Rehearsal automation, result capture, repeat until boring | `W-70` | `code done` karma · `caj-data-migration-{env}` Bicep, Dockerfile, entrypoint, secret seeding, rehearsal log |
| #94 | `W-72` Cutover | Freeze window, switch, smoke test, **rollback runbook** | `W-71`, `W-62` restore performed | `assigned` founder · runbook `W-72-cutover.md` 2026-10-16 |

---

## 2. The five questions `W-67` answers

Deliberately left open as target-state questions, because they only affect what data is
carried across — not what gets built.

| Question | Why it is hard |
|---|---|
| Which of the two live timesheet systems holds data worth keeping | Both exist and both have rows |
| Which duplicate leave and balance entities hold live rows | The frozen system has competing entities for the same thing |
| Which side seeds Core's leave tables | The pay run currently reads HRMS's copy |
| Whether both products genuinely hold live employee data | Determines whether this is a merge or a copy |
| How the two existing loss-of-pay calculations are reconciled | Two systems compute it differently, and the answer is money |

---

## 3. What must be true before any of this starts

| Needed | Why |
|---|---|
| Streams C, D and E complete | There is nowhere to load into until the tables exist |
| `W-21` Document store | `W-69` rewrites references into it |
| The target schema stable | Every migration script is written against it |

---

## 4. Standing risks

| Risk | Detail |
|---|---|
| **Divergence started on 2026-09-13** | The four frozen applications are snapshots taken that day. Production fixes made after it **do not arrive automatically** (`D-17` — no code or data sync). Payroll was under active development to the day of the freeze. Keep a written list of post-freeze production fixes, or they are lost at cutover |
| Two databases, one target | `hrmstestdb` (MySQL) and `payrollDB` (MySQL) merge into one Postgres with four schemas |
| Employee identity | The same person may exist in both systems with different identifiers. `W-67` decides the rule before `W-68` implements it |
| An untested restore is not a backup | `W-62` in [INFRA-TRACKER.md](INFRA-TRACKER.md) must be genuinely `done` before cutover, not `code done` |
| Rehearse until boring | `W-71` is not a formality. A cutover that has been rehearsed once has not been rehearsed |

---

## 5. Completed stream I workstreams on `core-migration-non-prod-data`

| Workstream | Ticket / Task | Artifacts | Status |
|---|---|---|---|
| Leave & Holiday Loader | `W-68.3` Task 3i | `code/migration/loaders/load_leave.py` | `code done` · Rule R2 per-tenant split, 5 seeded codes + LEGACY, approval instances & steps, leave consumption ledger |
| Timesheets Loader | `W-68.3` Task 3j | `code/migration/loaders/load_timesheets.py` | `code done` · 7 tables (projects, tasks, assignments, timesheets, day entries with `hours > 0` filter) |
| Attendance Loader | `W-68.3` Task 3k | `code/migration/loaders/load_attendance.py` | `code done` · 3 tables (attendance, clock sessions, overtime requests, `source = 'CLOCK'`, `remarks = 'MIGRATED'`) |
| Financial Reconciliation | `W-70` Task 1 | `code/migration/reconciliation/financial_reconciliation.py` | `code done` · 61-table row counts, Decimal 0.00 INR delta proof, JSON report, exit 1 on variance |
| LOP Mismatch Generator | `W-70` Task 2 | `code/migration/reconciliation/lop_mismatch_generator.py` | `code done` · Decimal-only, deterministic uuid5, status preserved on conflict, filter variance > 0, Finance CSV export |
| Reconciliation & Loader Tests | `W-70` Task 3 / `W-68` Task 4 | `code/migration/tests/test_reconciliation.py`, `tests/test_loaders_krushna.py` | `code done` · 7/7 pytest tests passed |

---

## Related

- How the frozen system works: `legacy/docs/ARCHITECTURE.md`
- The old schema, all 131 tables: `legacy/docs/DB_SCHEMA.md`
- Known defects and debt in the frozen system: `legacy/docs/GAP_INVENTORY.md`
- Current to target: [../target-state/06-current-to-target.md](../target-state/06-current-to-target.md)
- Other trackers: [INFRA-TRACKER.md](INFRA-TRACKER.md) · [DEV-TRACKER.md](DEV-TRACKER.md) · [HARNESS-TRACKER.md](HARNESS-TRACKER.md)
