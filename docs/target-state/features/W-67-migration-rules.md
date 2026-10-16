# W-67 — Migration rules

| Field | Value |
|---|---|
| **Work item** | `W-67` · issue [#87](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/87) |
| **Kind** | Data — decision record, no code |
| **Stream / track** | I — Migration |
| **Size / skill** | S · DATA |
| **Owner** | founder |
| **Blocked by** | Tracker says streams C–E. Founder decision 2026-10-16: rules are agreed now against non-prod data so the engine can be rehearsed early; production cutover (`W-72`) still waits for C–E |
| **Blocks** | `W-68`, `W-69`, `W-70`, `W-71`, `W-72` |
| **Decisions** | `D-35` (attendance in `core`), `D-08` (tax masters in `reference`) |
| **Gaps addressed** | `BUG-002` discounted, `BUG-003` deferred to `W-70`, `BUG-007` decided here, `BUG-009` closed here |
| **Status** | Ready |

---

## 1. Problem

The build order says the rules must be agreed in writing before a row moves
(`docs/target-state/09-build-order.md:305`). The tracker lists five open questions
(`docs/trackers/MIGRATION-TRACKER.md:38-50`). No document answers them. The branch
`origin/core-migration-non-prod-data` already applies one answer to each inside Python
(`code/migration/transformers/merge_employees.py`), so today the rules exist only as code
nobody signed. This spec writes them down, keeps the ones that are right, and changes the
ones that are not.

**Baseline** (on the branch, non-prod data)

| Command | Output |
|---|---|
| `python code/migration/transformers/merge_employees.py` | 3 cohort counts, quarantine count, log of DOB and employee-number fixes |
| `grep -c tenant_id code/migration/config/org_mapping.json` | 152 legacy organisations mapped |

## 2. Scope

**In scope** — the five tracker questions, plus three the branch raised that the tracker did not (R5, R6, R7).

**Out of scope** — the engine (`W-68`), documents (`W-69`), the checks (`W-70`), the
Azure job (`W-71`), the production window (`W-72`).

## 3. The rules

### R1 — One person across two systems

| Rule | Decision | Where the branch does it |
|---|---|---|
| Match keys | Payroll `employee.work_mail` against HRMS `contact.work_email`, then against HRMS `contact.personal_email`, then PAN. Never phone, never name | `merge_employees.py:221-230`, `:108-111` |
| Cohorts | 1 = in both, 2 = HRMS only, 3 = Payroll only. All three load | `merge_employees.py:232-370` |
| Identity | `uuid5(namespace, tenant_id + work_email)`; employee number when no email. Same input, same id, every run | `merge_employees.py:57-58` |
| Field precedence | HRMS wins names, gender, DOB, DOJ, addresses, emergency contacts, PAN, Aadhaar. Payroll `employee` + `employee_personal_detail` fill any of those that HRMS leaves empty, and win employee number only when HRMS has none, all statutory fields (UAN, PF, ESI, eligibility flags), bank, father's name, disability and tax-exemption flags | `merge_employees.py:243-275`; `employee_personal_detail` is not yet extracted — `W-68` |
| Duplicate source rows | A second Payroll row with the same work email in the same organisation is quarantined to `stg_quarantined_duplicates.json`, not loaded. Finance reviews the file | `merge_employees.py:160-215` |
| Duplicate employee number in a tenant | Suffix `-P<id>` or `-H<id>`. Logged | `merge_employees.py:385-395` |
| Bad dates | DOB after 2010 or on/after DOJ becomes null, logged | `merge_employees.py:60-72` |

**Change required in `W-68`:** a missing joining date must fail the row into quarantine,
not default to `2025-01-01` (`merge_employees.py:113`, `:197`,
`loaders/load_postgres.py:124`). An invented date reaches leave accrual and service length.

### R2 — Leave authority

| Question | Decision |
|---|---|
| Source per tenant | HRMS holds leave for the founder's tenant only (no organisation column, `BUG-002`). **Founder tenant: HRMS. Every other tenant: Payroll** `leave_type`, `employee_leave_allocation`, `employee_leave_balance_consumption`, `holidays` — the target tables were modelled on them, columns map 1:1 (mapping §3.8) |
| Which HRMS balance table | `leave_balances` (`legacy/docs/DB_SCHEMA.md:216`), five fixed columns. `W-68` seeds five `core.leave_type` codes (ANNUAL, SICK, CASUAL, MATERNITY, PATERNITY) for the founder tenant and splits each row into five `core.leave_allocation` rows; used days become `core.leave_consumption`. `employee_leave_balances` is not migrated |
| Which HRMS request table | `leave_requests` (newer, entity `LeaveRequests`). `leave_request` (older) is not migrated; `W-68` logs its row count |
| Leave type on a request | **HRMS `leave_requests` has no leave type column.** Every migrated HRMS request is attached to a seeded `core.leave_type` with code `LEGACY`, name "Legacy leave (type not recorded)". Never "any active type" (`loaders/load_leave.py:338-342`) |
| Approvals | `hr_status` wins over `reporting_manager_status` (`loaders/load_leave.py:335-336`). Manager and HR comment, date and status become one `core.approval_instance` with two `core.approval_step` rows, so the history of who approved is visible |
| Holidays | Same split as the source: HRMS `holidays` for the founder tenant, Payroll `holidays` per organisation for the rest, into one default `core.holiday_calendar` per tenant |

### R3 — Timesheets, attendance, projects

**Decision: migrate, from the newer HRMS tables.** `timesheets`, `project_entry`,
`task_entry`, `day_entry`, `projects`, `tasks`, `assignments`, `attendance`,
`clock_sessions`, `overtime_requests` map column for column onto `hrms.timesheet*`,
`hrms.project`, `hrms.task`, `hrms.assignment`, `core.attendance`, `hrms.clock_session`,
`core.overtime_request` (mapping §3.8–3.9). The older `timesheet` table (0 rows in
non-prod) is not migrated; `W-68` logs its row count. Migrated timesheets and overtime
are history: status as it was, no `pay_input` is generated from them.

### R4 — Loss of pay

| Rule | Decision |
|---|---|
| Source of truth for paid history | Payroll `employee_payruns` (`lwp_days`, `lwp_amount`). Money already paid is not recomputed |
| The check | Per employee per period, HRMS unpaid leave days against Payroll LOP days, written to `core.lop_mismatch_log` (`reconciliation/lop_mismatch_generator.py:74-128`) |
| Who signs | Finance signs the log before `W-72`. A variance is a finding for finance, never a correction the migration applies |
| `BUG-003` | Half-day rounding is visible in the log as 0.5-day variances; no fix in migration |

### R5 — Tenants

| Rule | Decision |
|---|---|
| One tenant per legacy Payroll `organization` | `config/org_mapping.json` is the map, 152 rows. HRMS has no organisation column (`BUG-002`), so every HRMS row maps to the founder's tenant unless its matched Payroll row says otherwise |
| Which organisations become tenants | **No automatic rule.** Rehearsals carry all organisations in the dump. Before `W-72`, `W-68` Task 1 prints the production organisation list as a table (name, legacy id, created, email, active, deleted, employees, pay runs) and the founder marks each row keep or drop by hand into `config/org_mapping.json`; a row absent from the map is not migrated. Non-prod reference: 152 organisations, 15 names duplicated, 72 with no employees and no pay runs (decided 2026-10-16) |
| Tenant defaults | country `IN`, subscription `ACTIVE`, retention 84 / 12 months (`loaders/load_postgres.py:77-90`) |

### R6 — What migrates and what does not

**Decision 2026-10-16: carry every legacy table that holds a record the customer can
see.** 91 of the 136 legacy tables migrate into 61 target tables. The row-by-row list is
`.claude/outputs/2026-10-16-legacy-target-field-mapping.md`; this table is the rule.

| Legacy data | Decision | Note |
|---|---|---|
| Departments, designations, work locations | Migrate | 1:1 masters. `core.employee` has no designation or location column; `W-68` adds both in its Flyway script |
| Bank details | Migrate | `employee_bank_detail` → `core.employee_bank`, 1:1 |
| Reporting managers (`report`) | Migrate | → `core.reporting_line`, one row per manager kind |
| Salary component masters and each employee's CTC | Migrate the **active** structure | Masters first, then `ctc_structure` and the seven `employee_*` component tables. Revisions are not carried; the active row's `effective_date` is kept |
| EPF, ESI, PT settings, pay schedule, FBP, declaration windows, tax deductor | Migrate | One row per tenant each |
| Pay run history | Migrate totals | `payroll.payrun` + `employee_payrun`; synthetic `employee_payrun_line` rows for TDS, LOP, bonus and claims; `payroll.prior_payroll_month` per employee per month so `W-38.2` sees prior income. Legacy never stored component lines, so none are invented |
| Off-cycle runs, one-time payouts, claims, salary deductions | Migrate all, paid and open | Open ones keep their status |
| Tax declarations, proofs, TDS | Migrate the **current financial year** at cutover | 1:1 tables. Earlier years are not carried |
| Timesheets, projects, tasks, attendance, overtime | Migrate | R3 |
| Notifications | Migrate | → `core.notification`, read flag kept |
| Logins, passwords, permissions, roles, action mappings | Do not migrate | Keycloak and `core.role` are seeded; every user gets an invitation. Role **membership** is carried by code match |
| Reminder and setup config, wizard flags | Do not migrate | Seeded from the country template or recomputed |
| Tax calculation results (7 tables) | Do not migrate | Recomputed from the carried declaration |
| Tax law masters (11 tables) | Do not migrate | Flyway seeds `reference.*` (`D-08`) |
| Superseded proof tables (3), older leave request and timesheet tables (2), second balance table (1) | Do not migrate | Their newer twins are carried |
| PT history, leave import log | Do not migrate | Operational logs |
| Audit trail | Cannot migrate | The old system had none |

### R7 — Restartability and evidence

Every loader writes a row to `core.migration_ledger`, and every target insert is
`ON CONFLICT` upsert, so a run may be repeated (`loaders/load_postgres.py:97`,
`:184-275`). A rehearsal is clean when the ledger has no failed step and `W-70`
reports a 0.00 INR delta.

## 4. Gaps

| Gap | Treatment |
|---|---|
| `BUG-002` no tenant column in HRMS | Discounted — R5 assigns tenant by matched Payroll row |
| `BUG-003` half-day LOP | Deferred — visible in the R4 log, `W-70` |
| `BUG-007` duplicate entities | Decided — R2 picks one of each pair, R3 drops both timesheet tables |
| `BUG-009` org_id conflict placeholder | Closed — R5 is the rule |

## 5. Standing rules

Creates no table, no Flyway script, no module, no money column. The ledger and mismatch
tables belong to `W-68` and `W-70`.

## 6. Verification

Done: the founder initialled R1–R7 on 2026-10-16. The R5 keep/drop marking of production
organisations is a `W-72` go/no-go item (check 4), not a gate on this ticket.

| Rule | Agreed by | Date |
|---|---|---|
| R1 | founder | 2026-10-16 |
| R2 | founder | 2026-10-16 |
| R3 | founder | 2026-10-16 |
| R4 | founder | 2026-10-16 |
| R5 | founder | 2026-10-16 |
| R6 | founder | 2026-10-16 |
| R7 | founder | 2026-10-16 |

## 7. Risks

| Risk | Mitigation |
|---|---|
| A real tenant among the duplicate names is dropped | R5 list is written before `W-72`, and rehearsal keeps all 152 |
| Payroll-only employees have no DOB or address | Cohort 3 loads with nulls; HR completes after cutover |
| The branch was built against a schema older than `main` | `W-68` reconciles every column before rehearsal |

## 8. Rollback

A decision record has no rollback. Changing a rule after `W-68` is built means a new
rehearsal, which `W-71` already requires.
