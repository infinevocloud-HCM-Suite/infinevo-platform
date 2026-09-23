# `W-13` Employee master (#14) — split proposal, no spec written

| | |
|---|---|
| Ticket | #14 `W-13` Employee master, size **L**, kind **merge**, `CORE-04` |
| Verdict | **Cannot be one spec.** Breaks three of the four size-cap axes |
| Asked of the founder | Approve a split shape before any spec is written |
| **Outcome** | **Three-ticket split approved 2026-09-22**, with the aggregate exception to the migration axis. Approvers go to `W-14`'s `reporting_line`; search is paginated **and** free-text |

The `plan-feature` skill stops here by design: *"If the ticket breaks it, propose the split
and stop."*

## Why it breaks

| Axis | Limit | `W-13` as ticketed |
|---|---|---|
| Backend module | 1 | `core` only — **within cap** |
| Flyway migration | 1 | **6 tables**, and `migration/README.md:127-129` allows one table per script — so 6 scripts |
| Externally testable behaviour | 1 | create/read/update the record · search and listing — **at least 2** |
| Frontend area | 1 | none in scope — within cap |

The six tables are fixed by the design, not by me — `02-data-model.md:295`:
`employee`, `employee_personal`, `employee_contact`, `employee_identification`,
`employee_employment`, `employee_bank`.

## The size is real, not bureaucratic

| Source | Entities | Columns |
|---|---|---|
| HRMS tree — `Employee`, `personal`, `Contact`, `Identification`, `Work`, `Report` | 6 | ~67 |
| Payroll tree — `BasicDetails`, `EmployeePersonalDetail`, `EmployeeBankDetail` | 3 | ~56 |

`02-data-model.md:68` calls `employee` **"the highest-risk merge in the project"** in its own
words. Eight concepts exist on one side only:

| Concept | HRMS | Payroll |
|---|---|---|
| Reporting hierarchy (`Report.java`, 8 columns) | yes | no |
| Bank and payment details | no | yes |
| Statutory eligibility — PF, PT, LWF, ESI, EPS | no | yes |
| Emergency contacts and family doctor | yes | no |
| Full identification set — aadhaar, passport, UAN, immigration | yes | PAN only |
| Soft delete `isDeleted` | no | yes |
| Portal enablement | no | yes |
| Free-text search | yes | no — paginated only |

Citations: `legacy/HRMS_Backend/.../entity/Report.java:1-106`,
`legacy/Payroll-Bend-SBoot/.../entity/employee/EmployeeBankDetail.java:1-121`,
`.../BasicDetails.java:153-170`, `legacy/HRMS_Backend/.../entity/Contact.java:66-73`,
`.../Identification.java:82-93`, `.../controller/EmployeeController.java:172,187`,
`legacy/Payroll-Bend-SBoot/.../controller/employee/BasicDetailsController.java:77-100`.

## Two readings of the cap

**Strict.** One migration per spec, one table per script, so one table per ticket:
seven tickets — six tables plus search. Three of them (`employee_contact`,
`employee_identification`, `employee_bank`) are flat satellites of nine to eighteen columns
with no logic; a ticket each is more process than work.

**Aggregate.** Treat the five satellites as belonging to one aggregate root and let a ticket
ship one script per table within it. Three tickets, each one testable behaviour.

Only the founder can grant the second, because the cap is the founder's rule.

## Recommended split — three tickets

| New ticket | Tables / scripts | Behaviour proved | Size |
|---|---|---|---|
| **`W-13.1`** Employee record | `employee` (1 script) | an employee can be created, read and updated under RLS, unique within a tenant | M |
| **`W-13.2`** Employee detail | `employee_personal`, `employee_contact`, `employee_identification`, `employee_bank`, `employee_employment` (5 scripts) | every detail section round-trips against its record | M |
| **`W-13.3`** Search and listing | none | search returns the tenant's employees, paginated and free-text, and never another tenant's | S |

`W-13.1` must merge before `W-13.2`; `W-13.3` follows `W-13.1`. The five tickets that wait on
`W-13` — `W-14`, `W-19`, `W-20`, `W-21`, `W-23` — unblock at `W-13.1`, because each needs the
employee identity, not their bank account.

**Strict alternative**, if the cap is not relaxed: `W-13.1` record · `W-13.2` personal ·
`W-13.3` contact · `W-13.4` identification · `W-13.5` employment · `W-13.6` bank ·
`W-13.7` search. Seven tickets, same work, more ceremony.

## Three things the spec cannot settle on its own

1. **Where the reporting hierarchy lands.** `02-data-model.md:73` merges HRMS `work` **and `report`** into `employee_employment`, but `CORE-06` gives the hierarchy its own table, `reporting_line`, which belongs to `W-14` (#15) — `02-data-model.md:296`. `Report.java` carries both an employment note and three approver levels. Those cannot both be right. Recommend: approvers go to `W-14`'s `reporting_line`, the rest to `employee_employment`, and `sync-docs` corrects line 73.
2. **Search shape.** HRMS offers free text with no pagination, Payroll pagination with no free text. Recommend both, paginated always, since an unpaginated `/employees/all` is a production incident waiting for the first thousand-employee tenant.
3. **What is *not* here.** The merge rules for live data are `W-67`'s, as #14 itself states. These tickets build the target shape only, and must not smuggle in reconciliation logic.

## Standing rules, for whichever split is approved

| Rule | Applies as |
|---|---|
| `tenant_id` + RLS on every new table | all six tables, each in the script that creates it |
| Flyway only, `ddl-auto` nowhere | six scripts, forward-only |
| `Money`/`BigDecimal` | `amountInPercentage` is a `double` in `BasicDetails.java:166` — it becomes `numeric` on the way across |
| Index on `tenant_id` plus lookup columns | `(tenant_id, employee_number)` unique; satellites `(tenant_id, employee_id)` |
| No module references another | everything in `core` |

## Decision needed

**Approve one:** the three-ticket split with the aggregate exception (recommended), the
strict seven-ticket split, or a shape of your own. Nothing is written for #14 until you pick,
and the answer to question 1 above changes what `W-14` inherits.
