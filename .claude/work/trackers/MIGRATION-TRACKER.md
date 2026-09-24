# Migration Tracker

> Moving live data out of the four frozen applications into the new tables — stream I.
> **Nothing here touches production data until every other stream works.**
> **GitHub is authoritative.** Status legend: [README.md](README.md).
> Last refreshed: **2026-09-22**.

## Summary

| | |
|---|---|
| Tickets | 8 |
| Spec approved | **0** |
| Code on main | **0** |
| Feature done | **0** |
| Blocked by | Streams C, D and E complete |

**Not started, and correctly so.** Migration is last by design. Two tickets carry a
`needs-founder` label — they are decisions, not code.

---

## 1. The tickets

| # | Ticket | What it is | Blocked by | Labels |
|---|---|---|---|---|
| #87 | `W-67` Migration rules | Employee deduplication rules, the leave authority decision, the timesheet data decision, duplicate entity resolution, the loss-of-pay reconciliation approach. **Written and agreed before a single row moves** | Streams C–E complete | `needs-founder` |
| #88 | `W-68.1` Migration engine — extract and staging | Pull from the two frozen MySQL databases into staging | `W-67` | |
| #89 | `W-68.2` Migration engine — transform and merge | Reshape and merge the two sources into one | `W-68.1` | |
| #90 | `W-68.3` Migration engine — load and restartability | Load into Postgres, and survive being restarted halfway | `W-68.2` | |
| #91 | `W-69` Document migration | Rewrite document references, copy the blobs, verify them | `W-21`, `W-68` | |
| #92 | `W-70` Reconciliation | Row counts, financial totals, per-employee comparison, and a **loss-of-pay mismatch log for finance** | `W-68` | |
| #93 | `W-71` Rehearsals | Rehearsal automation, result capture, repeat until boring | `W-70` | |
| #94 | `W-72` Cutover | Freeze window, switch, smoke test, **rollback runbook** | `W-71` | `needs-founder` |

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

## Related

- How the frozen system works: `legacy/docs/ARCHITECTURE.md`
- The old schema, all 131 tables: `legacy/docs/DB_SCHEMA.md`
- Known defects and debt in the frozen system: `legacy/docs/GAP_INVENTORY.md`
- Current to target: [../target-state/06-current-to-target.md](../../../docs/target-state/06-current-to-target.md)
- Other trackers: [INFRA-TRACKER.md](INFRA-TRACKER.md) · [DEV-TRACKER.md](DEV-TRACKER.md) · [HARNESS-TRACKER.md](HARNESS-TRACKER.md)
