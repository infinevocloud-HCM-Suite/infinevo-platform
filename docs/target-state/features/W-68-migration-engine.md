# W-68 — Migration engine: extract, transform, load

| Field | Value |
|---|---|
| **Work item** | `W-68.1` [#88](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/88) · `W-68.2` [#89](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/89) · `W-68.3` [#90](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/90) |
| **Kind** | Data |
| **Stream / track** | I — Migration |
| **Size / skill** | L · DATA |
| **Owner** | mohit (lead) · karma 3e, 3f, 3h · krushna 3i–3k · sayeed 3l–3m |
| **Blocked by** | `W-67` (rules R1–R7 signed) |
| **Blocks** | `W-69`, `W-70`, `W-71` |
| **Decisions** | `W-67` R1–R7; mapping `.claude/outputs/2026-10-16-legacy-target-field-mapping.md` |
| **Gaps addressed** | `DEBT-003` (tests), `DEBT-018` (indexes), `BUG-002`, `BUG-007` — decided in `W-67` |
| **Status** | Ready |

> One spec for the three tracker rows. They are three steps of one pipeline with one
> testable behaviour: **run it twice, get the same database.** Each step is one
> implementer task below. The file is named `W-68-migration-engine.md` because
> `W-68-hr-overtime-list.md` already uses the bare number for a different ticket.
>
> Scope after the founder's decision of 2026-10-16 (`W-67` R6): **91 legacy tables into
> 61 target tables.** The branch covers 15 into 20. The rest is new loaders, written to
> the same pattern.

---

## 1. Problem

The branch `origin/core-migration-non-prod-data` (commit `3b504526`) holds a Python
pipeline under `code/migration/` that extracts 33 legacy tables, merges employees and
loads 20 target tables. It has never been reviewed, tested or merged, and it carries four
defects that stop it being mergeable:

| Defect | Where |
|---|---|
| Real non-prod PII (PAN, Aadhaar, UAN, bank) committed as 34 JSON files, 3.1 MB | `code/migration/staging/*.json` |
| MySQL password and DigitalOcean IP as fallback values | `code/migration/extractors/extract_hrms.py` `main()` |
| Flyway number already taken on `main` | branch `core/V175__migration_ledger.sql` vs `main` `payroll/V175__default_reimbursement_components.sql`; `main` is at `V176` |
| Two rule breaches ordered fixed by `W-67` | invented joining date `2025-01-01` (`merge_employees.py:113`, `:197`, `load_postgres.py:124`); leave request mapped to "any active leave type" (`load_leave.py:338-342`) |

And it stops at 33 tables. The founder wants the customer's whole record carried.

**Baseline** (branch, run locally against the compose stack)

| Command | Output |
|---|---|
| `git show origin/core-migration-non-prod-data --stat \| grep code/migration \| wc -l` | 49 files |
| `python -m pytest code/migration` | no tests collected |
| tables extracted / loaded | 33 / 20 |

## 2. Scope

**In scope**

- Bring `code/migration/` from the branch onto the developer's branch, with the four defects above fixed.
- `W-68.1` extract: every one of the 91 legacy tables in mapping §3, source from env vars only.
- `W-68.2` transform: `merge_employees.py` with `W-67` R1 as written, plus the id-map every later loader uses.
- `W-68.3` load: thirteen loaders (3a–3m below; four come from the branch, nine are new), the ledger, re-run safety.
- One Flyway script: the ledger and mismatch tables, plus two nullable columns on `core.employee`.
- One pytest that proves "run twice, same result" across every loader.

**Out of scope**

- Document files — `W-69`. Every loader that needs a `document_id` writes the row with `document_id NULL` and a `legacy_url` in the id-map; `W-69` fills it.
- Reconciliation scripts — `W-70`.
- Dockerfile, Bicep job, Key Vault, DigitalOcean access — `W-71`.
- The 45 legacy tables `W-67` R6 excludes.

## 3. The pipeline

```
MySQL hrmstestdb ──extract_hrms.py──────┐
                                        ├─► staging/*.json ─► merge_employees.py ─► stg_merged_employees.json
MySQL payroll_test_db ──extract_payroll.py──┘                                        + staging/id_map.json
                                                                                                │
   load_tenant → load_org → load_employees → load_users → load_salary → load_statutory → load_payruns
   → load_tax → load_leave → load_timesheets → load_attendance → load_claims → load_notifications → load_documents(W-69)
                                    each loader: one row in core.migration_ledger
```

`staging/id_map.json` is `{legacy_table: {legacy_id: target_uuid}}`, written by the
transform step and appended by every loader. Every foreign key in a later loader is
resolved through it; an id not in the map goes to that loader's quarantine file.

Config is environment only: `LEGACY_HRMS_DB_*`, `LEGACY_PAYROLL_DB_*`, `TARGET_DB_*`,
`KEYCLOAK_*` (`code/migration/config/config.example.yaml`). No value has a fallback that
names a host, a user or a password.

## 4. Implementer tasks

### Task 1 — `W-68.1` extract (`code/migration/extractors/`)

| Change | Detail |
|---|---|
| Remove fallbacks | `extract_hrms.py` `main()` and `extract_payroll.py` `main()`: every `os.environ.get(..., default)` for host, db, user, password becomes `os.environ[...]`. Missing variable exits 2 with the variable name |
| Keep | Read-only session (`extract_hrms.py:42-60`), SSL for non-local hosts, `SELECT *` per table, counts in the summary |
| Tables | `TABLES_TO_EXTRACT` becomes the 91 tables of mapping §3 — HRMS 30, Payroll 61 — plus the four excluded twins for counting only: `leave_request`, `employee_leave_balances`, `timesheet`, `employee_investment_proof`. Their counts go to the summary; no file is written for them |
| Binary columns | `employee_document.document_content`, `leave_documents.file_data` are not extracted (`SELECT` names columns for those two tables); the URL is enough for `W-69` |
| Organisation list | After extraction, write `staging/organisations.md`: one row per Payroll `organization` with name, legacy id, created date, email, active, deleted, employee count, pay run count. The founder marks keep/drop from it into `config/org_mapping.json` (`W-67` R5). Any organisation missing from the map is skipped by every loader, with its count in the summary |
| `.gitignore` | Add `code/migration/staging/` |
| History | `git filter-repo --path code/migration/staging --invert-paths` on the developer's branch before the first push, so no JSON reaches `main`'s history |

### Task 2 — `W-68.2` transform (`code/migration/transformers/`)

| Change | Detail |
|---|---|
| Joining date | A record with no `joining_date` (HRMS `work.doj`) and no `date_of_joining` (Payroll) goes to `stg_quarantined_duplicates.json` with `reason: "NO_DOJ"`. Delete the `or "2025-01-01"` at `merge_employees.py:113` and `:197` |
| Payroll personal detail | Read `stg_payroll_employee_personal_detail.json` (`personal_detail_id` on the Payroll employee) as the fallback for DOB, personal email, PAN, address, and the only source for `father_name`, `differently_abled_type`, `is_eligible_for_full_tax_exemption` (`W-67` R1) |
| Full field set | Carry every HRMS `contact`, `identification`, `work` column mapping §3.3 names (emergency contacts, doctor, ID proofs, pay grade, shift, time zone, termination date). The branch drops them |
| Id map | Write `staging/id_map.json` with `hrms.employee`, `payroll.employee` (both the integer id and the `employeeId` UUID) → unified uuid5; `payroll.organization` → tenant |
| Keep | Match keys, cohorts, uuid5, DOB sanitising, employee-number suffix — all cited in `W-67` R1 |

### Task 3 — `W-68.3` load (`code/migration/loaders/`, `code/backend/migration/`)

**Flyway** — rename `core/V175__migration_ledger.sql` to `core/V177__migration_ledger.sql`.
Content: the two tables as on the branch (both with `tenant_id`, RLS `tenant_isolation`,
tenant-leading index, `NUMERIC(4,1)` and `NUMERIC(12,2)`), plus:

```sql
ALTER TABLE core.employee
    ADD COLUMN designation_id   UUID REFERENCES core.designation(id),
    ADD COLUMN work_location_id UUID REFERENCES core.work_location(id);
CREATE INDEX idx_employee_tenant_designation ON core.employee (tenant_id, designation_id);
CREATE INDEX idx_employee_tenant_work_location ON core.employee (tenant_id, work_location_id);
```

Nullable, expand only. No Java entity change in this ticket; a screen that shows them is
a later ticket.

**Common rules for every loader**

| Rule | Detail |
|---|---|
| Ledger | One `core.migration_ledger` row per loader per tenant: `step_name`, `records_processed`, `records_skipped`, `status RUNNING → DONE \| FAILED`, `started_at`, `finished_at`, `input_checksum` (sha256 of the staging files it reads). A loader that finds its own `DONE` row with the same checksum skips itself |
| Upsert | Every insert is `ON CONFLICT (…) DO UPDATE` on a deterministic id: `uuid5(namespace, tenant_id:legacy_table:legacy_id)`. A second run changes nothing |
| Quarantine | A row whose parent is missing from the id-map is written to `staging/quarantine_<loader>.json` with a reason, counted in `records_skipped`, never loaded |
| Money and days | `Decimal` only, `to_decimal` from `load_payruns.py:40-48`. `grep -rn "float(" code/migration/loaders` returns nothing |
| Audit columns | `created_by = updated_by = 'migration'`; `created_at` = legacy `created_at` where one exists, else run start |
| Tenant | Every row carries `tenant_id` from the id-map. The loader sets `app.current_tenant_id` per tenant so RLS applies even to the migration user |

**Loaders**, in run order. Columns per mapping §3; "1:1" means snake_case of the legacy name.

| # | Loader | Legacy tables → target tables | Note |
|---|---|---|---|
| 3a | `load_tenant.py` (branch `load_postgres.py` split) | `organization` → `core.tenant`, `core.subscription` | Skip `isDeleted`; `W-67` R5 |
| 3b | `load_org.py` | `department`, `designation`, `workLocations` → `core.department`, `core.designation`, `core.work_location`; `incomeTaxDetails` → `payroll.tax_deductor`; `paySchedule` → `payroll.pay_schedule` + `core.lop_policy` | `code` = upper-snake slug of the name, unique per tenant |
| 3c | `load_employees.py` (branch, extended) | merged → `core.employee` (+ `department_id`, `designation_id`, `work_location_id`, `termination_date`), `employee_personal`, `employee_contact`, `employee_identification`, `employee_employment`, `employee_bank`, `payroll.employee_statutory_profile`; `report` → `core.reporting_line` (`kind` DIRECT, INDIRECT, APPROVER_1..3) | HRMS `work.department` text matched to a Payroll department by case-insensitive name within the tenant |
| 3d | `load_users.py` (branch `load_keycloak.py`) | `ourusers`, `companyUser` → Keycloak + `core.user_account`, `core.user_tenant`; `user_roles`, `organizationUserRoleMapping` → `core.user_role` by role code; `userInvitations`, `employeeInvitation` (pending) → `core.user_invitation`, `core.employee_invitation` | Role code map in `config/role_map.json`; unknown code → quarantine |
| 3e | `load_salary.py` | `earnings`, `deductions`, `benefits`, `reimbursements` → `payroll.earning`, `deduction`, `benefit`, `reimbursement`; `FBP` → `payroll.fbp`; active `ctc_structure` → `payroll.ctc_structure`; `employee_earnings`, `employee_benefits`, `employee_reimbursements`, `employee_fbp_components`, `employee_variable_earnings`, `ctc_epf_components`, `ctc_esi_components` → `payroll.employee_earning`, `employee_benefit`, `employee_reimbursement`, `employee_fbp_component`, `ctc_epf_component`, `ctc_esi_component` | Only `ctc_structure.is_active AND NOT is_deleted`. Check: sum of monthly earnings = `monthlySalary` or quarantine with `CTC_SUM_MISMATCH` |
| 3f | `load_statutory.py` | `epf` → `payroll.epf_setting`; `esi` → `payroll.esi_setting`; `professionalTax` + `slabRateConfiguration` + `slabDetail` + `org_pt_override` → `payroll.org_pt_override` + `org_pt_override_slab` **only where the slabs differ from `reference.pt_slab` for that state** | One row per tenant |
| 3g | `load_payruns.py` (branch, extended) | `payruns`, `employee_payruns` → `payroll.payrun`, `employee_payrun`; synthetic `payroll.employee_payrun_line` rows (`TDS`, `LOP_DEDUCTION`, `BONUS`, `CLAIM_DEDUCTION`, `CLAIM_REIMBURSEMENT`) from the five total columns; `payroll.prior_payroll_month` per employee per period (`gross_earnings`, `tds`, `net_pay`; `epf_employee`, `esi_employee`, `professional_tax` = 0 with a note in `W-70`); `off_cycle_pay_run` + 3 child tables → `payroll.payrun run_type OFF_CYCLE` + `employee_payrun`; `one_time_payout` → `payroll.scheduled_earning` (paid) | Keep the branch's header-from-lines recomputation |
| 3h | `load_tax.py` | `IncomeTaxDeclaration`, `ProofOfInvestment` → `payroll.income_tax_declaration`; `employee_investment_declaration` + `employee_inv_house_rent`, `home_loan`, `let_out_property` (+`_detail`), `other_income`, `pre_tax_deduction`, `prev_employment`, `section6a`, `tax_summary` → the nine `payroll.employee_inv*` tables; `employee_tds` → `payroll.employee_tds`; `employee_proof_of_investment`, `employee_poi_item`, `employee_poi_item_comment`, `employee_poi_property_detail` → `payroll.employee_proof_*` | Current financial year only: `fiscal_year = FY of the run date`, parameter `MIGRATION_FY` |
| 3i | `load_leave.py` (branch, rewritten) | Founder tenant: HRMS `leave_types` (+ five seeded codes + `LEGACY`), `leave_balances` (split), `leave_requests` (+ approval instance and two steps), `holidays`. Other tenants: Payroll `leave_type` → `core.leave_type` + `leave_policy` + `leave_policy_eligibility`; `employee_leave_allocation` → `core.leave_allocation`; `employee_leave_balance_consumption` → `core.leave_consumption`; `holidays` → `core.holiday` + `holiday_calendar_location`; `attendance_preferences` → `hrms.attendance_preference` + `core.lop_policy` | `W-67` R2 |
| 3j | `load_timesheets.py` | `projects`, `tasks`, `assignments`, `timesheets`, `project_entry`, `task_entry`, `day_entry` → `hrms.project`, `task`, `assignment`, `timesheet`, `timesheet_project_entry`, `timesheet_task_entry`, `timesheet_day_entry` | 1:1 |
| 3k | `load_attendance.py` | `attendance`, `clock_sessions`, `overtime_requests` → `core.attendance`, `hrms.clock_session`, `core.overtime_request` | 1:1; `source = 'MIGRATED'` |
| 3l | `load_claims.py` | `employee_reimbursement_request` → `payroll.employee_reimbursement_request`; `employee_deduction` → `payroll.employee_deduction` | All statuses; `document_id` NULL until `W-69` |
| 3m | `load_notifications.py` | `notifications`, `timesheet_notifications` → `core.notification` (`event = 'LEGACY'`, `channel = 'IN_APP'`, `status = 'DELIVERED'`, `read_at`) | |

### Task 4 — test (`code/migration/tests/`)

One pytest module per loader plus `test_pipeline.py`, Testcontainers for MySQL 8 and
Postgres 16 with `main`'s Flyway applied. Fixture (`tests/fixtures/*.json`): 2 tenants, 20
HRMS employees, 15 Payroll employees with 10 overlapping by email, 1 Payroll duplicate, 1
with no joining date, 1 bank row each, 4 earning masters and 3 CTC structures, 3 payruns
with 5 lines each, 1 off-cycle run, 1 declaration with 3 `inv` rows, 4 leave types, 6
requests, 2 timesheets with 4 day entries, 3 attendance rows, 2 claims, 2 notifications.

| Assertion |
|---|
| Cohort counts 10 / 10 / 4, quarantine 2 (duplicate, NO_DOJ) |
| `core.employee` has 24 rows, ids are uuid5 of `tenant_id:work_email`; `designation_id` and `work_location_id` set on the Payroll-sourced rows |
| Every target table in mapping §3 marked Carry or Add has rows > 0 after the run — a table-by-table list in the test, so adding a loader without a test fails |
| `payroll.employee_payrun.net_pay` sum equals the fixture to the paisa; `prior_payroll_month` count = employees × periods |
| CTC: `employee_earning.monthly_amount` sum per structure = fixture `monthlySalary` |
| `core.leave_request.leave_type_id` is the `LEGACY` type for HRMS rows and the mapped type for Payroll-tenant rows |
| Second run of the whole pipeline: every row count unchanged, no `updated_at` changes, ledger shows every step `DONE` once and skipped once |

## 5. Standing rules

| Rule | Impact |
|---|---|
| `tenant_id` + RLS on every new table | `core.migration_ledger`, `core.lop_mismatch_log` — both have it (branch `V175`, lines 9, 24, 39, 58) |
| Flyway only, schema named | One script, `core/V177`, every statement prefixed `core.`. Creates two tables, adds two nullable columns and two indexes |
| Money | `NUMERIC(12,2)` in DDL, `Decimal` in Python |
| Index on `tenant_id` + lookup | `(tenant_id, step_name)`, `(tenant_id, pay_period)`, `(tenant_id, designation_id)`, `(tenant_id, work_location_id)` |
| Expand / contract | Create and add only. No drop, no alter of existing columns |
| Module boundary | Python only. No Java module changes. The two tables live in `core` because every module's loader writes to them |

## 6. Gaps

| Gap | Treatment |
|---|---|
| `DEBT-003` no tests | Fixed — Task 4 |
| `DEBT-018` indexes | Fixed — present in the DDL |
| `BUG-002`, `BUG-007` | Decided in `W-67` R2, R5 |

## 7. Verification

```bash
cd code/migration && pip install -r requirements.txt pytest testcontainers
python -m pytest tests/ -q                      # expected: all passed, one module per loader
grep -rn "Str0ngP\|157.245\|64.227" code/migration   # expected: no output
grep -rn "float(" code/migration/loaders        # expected: no output
git ls-files code/migration/staging             # expected: no output
ls code/backend/migration/src/main/resources/db/migration/*/V175* code/backend/migration/src/main/resources/db/migration/*/V177*
                                                # expected: payroll/V175..., core/V177__migration_ledger.sql
cd code/backend && mvn -q -pl migration flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/infinevo
                                                # expected: "Successfully applied 1 migration" on a V176 database
```

| Check | Expected |
|---|---|
| CI `ci.yml` runs the migration pytest | job green |
| `python extractors/extract_hrms.py && python extractors/extract_payroll.py` against the compose MySQL seeded with the non-prod dump | summary lists 91 tables extracted, 4 counted only |
| Second local run of every loader | log shows `SKIP (DONE, checksum match)` 13 times |
| `psql -c "select step_name,status from core.migration_ledger order by started_at"` | 13 rows per tenant, all `DONE` |
| `psql -c "select count(*) from information_schema.tables t where t.table_schema in ('core','hrms','payroll') and exists (select 1 ...)"` — the test's table list | 61 target tables with rows |

## 8. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Loaders target columns that `main` has since renamed | High — branch cut before `V150` | Task 4 runs against `main`'s Flyway; every mismatch is fixed in this ticket |
| Legacy component names do not match the target's seeded component codes | High | `config/component_map.json` per tenant, built by the developer from the dump; an unmapped name quarantines the CTC row, never guesses |
| `git filter-repo` not available on the developer's machine | Medium | `pip install git-filter-repo`; the founder checks `git log --all -- code/migration/staging` is empty before merge |
| Keycloak loader needs a live realm in the test | Medium | Task 4 mocks the Admin REST calls; the live path is exercised in `W-71` |
| 13 loaders land in one pull request | Medium | The developer may push 3a–3d, then 3e–3h, then 3i–3m as three commits on the same branch; `/merge` reads the branch once |

## 9. Rollback

Not merged: delete the branch. Merged: `V177` is expand-only; a later Flyway script drops
the two tables and two columns if the engine is abandoned. No application code reads them.
