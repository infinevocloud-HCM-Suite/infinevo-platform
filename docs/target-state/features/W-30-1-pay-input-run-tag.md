# Feature: Pay input run tag — inputs an off-cycle run collects

| Field | Value |
|---|---|
| **Feature ID** | `W-30.1` · from ticket #37 · `PAY-06`/`PAY-07` part 1 of 2 |
| **Promoted to** | `docs/target-state/features/W-30-1-pay-input-run-tag.md` on the developer's `dev-<name>` branch — **`W-30-1` with hyphens**, never `W-30.1`; `guard-edit` blocks the dotted form |
| **Owner** | unassigned |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | DEBT-018 (honoured), DEBT-037 (proposed in `.claude/outputs/2026-09-25-analyze-w30-off-cycle.md`, fixed by replacement) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | `W-19` — this is an expand on its two tables and its service |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | `V060` — one nullable column on each `W-19` table, one index each, the trigger body replaced. Expand only | 1 |
| Externally testable behaviour | an input can be tagged with a run id; the tagged rows are read by that id and by nothing else; locking the run refuses further tagged rows | 1 |
| Frontend area | none | 1 |

Within cap.

## Why two tickets

`W-30` as written on the work plan is "off-cycle run, one-time payout, bonus"
(`08-work-plan.md:89`). Two of the three already have owners: a one-time payout is a
`PayInputKind` (`W-19` §4) that `W-29.3` turns into an `EARNING` line (`W-29.3` §3), and a
bonus is a variable earning `W-29.2` computes and `W-29.3` pays. What is left is the off-cycle
run, and `12-core-contracts.md:168` (decision 3) fixes its one rule: **an off-cycle run collects
only inputs tagged with its run id.** `W-19` §13 point 5 says the tag column is `W-30`'s to
add. That column is in `core`; the run is in `payroll`. Two modules, two tickets. This one is
the column. `W-30.2` is the run.

---

## 1. Problem

Legacy has a one-time payout table that nothing reads, and an off-cycle run that stores
whatever is typed into it.

- `one_time_payout` rows are written and never consumed: the only references to
  `OneTimePayout` are its own entity, mapper, repository and service
  (`legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/payRun/oneTimePayout/OneTimePayoutServiceImpl.java`);
  `EmployeePayRunServiceImpl` never mentions it. `W-19` §1 records the same: *"no evidence it
  reaches a standard run"*
- An off-cycle run keeps its own earnings and deductions in its own tables
  (`legacy/.../entity/payRun/offCyclePayrun/OffCyclePayrunEmployeeEarning.java:10-29`), so a
  bonus paid off-cycle and a bonus paid in the month are two different kinds of record that
  no report can add up

In the new platform every pay-affecting value is a `core.pay_input` row (`W-19`). A regular
run reads its period's rows (`W-29.3` §3). Nothing yet says which rows an off-cycle run reads,
and nothing stops a regular run from also paying them. That is this ticket.

## 2. Scope

**In scope**

- `run_ref uuid NULL` on `core.pay_input` — the id of the run that will pay this row. `NULL`
  means "the regular run for `period`"
- `run_ref uuid NULL` on `core.pay_input_period_lock` — a lock row for one run instead of one period
- `PayInputCommand.runRef` (optional); `PayInputService.forRun(runRef)`; `PayInputService.lockRun(runRef)`
- The trigger: a tagged row is refused when its **run** is locked; an untagged row when its
  **period** is locked, as today
- `forPeriod(period)` and `forEmployee(employee, period)` return **untagged rows only**

**Out of scope**

- Validating that `run_ref` names a real run — `core` cannot see `payroll.payrun`, and must not
  (`CLAUDE.md`, module rule). `W-30.2` §4 is the supported writer and validates before it calls
- Creating, locking or computing the run — `W-30.2`
- Any calculation, as `W-19` §2

## 3. Flow

```
[payroll off-cycle run W-30.2] --> PayInputService.record(cmd with runRef)
   --> run locked? --> RunLockedException (409). Not redirected: there is no "next" off-cycle run
   --> [core.pay_input, run_ref = runRef]. The period lock is NOT consulted for a tagged row

[payroll off-cycle run W-30.2] --> PayInputService.forRun(runRef)  --> every tagged row for that run, one query
[payroll off-cycle run W-30.2] --> PayInputService.lockRun(runRef) --> [core.pay_input_period_lock, run_ref set]

[payroll regular run W-29.3]   --> PayInputService.forPeriod(period) --> untagged rows only  <-- the one behaviour change
```

**Why a tagged row ignores the period lock.** The common off-cycle case is a bonus paid in a
month whose regular run is already locked. Redirecting it to next month (`W-19` §4) would defeat
the run; refusing it would too. The run has its own lock, and that is the one that applies.

**Why a locked run refuses instead of redirecting.** Decision 3's "next open period" exists so a
late input is never lost. An input for a locked off-cycle run is not late; it is addressed to
a run that has finished collecting. The caller gets `409` and chooses another run.

## 4. Backend changes

All under `code/backend/core/src/main/java/com/infinevo/core/payinput/`, every file `W-19`'s.

| Layer | File | Change |
|---|---|---|
| Entity (change) | `PayInput.java` | `UUID runRef` nullable |
| Entity (change) | `PayInputPeriodLock.java` | `UUID runRef` nullable; `period` stays `NOT NULL` — a run lock carries the run's period for reporting |
| Command (change) | `PayInputCommand.java` | `UUID runRef` optional |
| Service (change) | `PayInputService`, `PayInputServiceImpl` | `record` checks the run lock when `runRef` is set and the period lock otherwise; `forRun(UUID runRef)`; `lockRun(UUID runRef, YearMonth period)`; `forPeriod` and `forEmployee` add `AND run_ref IS NULL` |
| Repository (change) | `PayInputRepository`, `PayInputPeriodLockRepository` | `findByTenantIdAndRunRef`, `existsByTenantIdAndRunRef` |
| Exception | `RunLockedException` (`409`) | new, envelope per `CONVENTIONS.md` §3 |
| DTO (change) | `PayInputRequest`, `PayInputResponse` | `runRef` in and out |

**The seam, extended** (`12-core-contracts.md` §3):

| Method | Does |
|---|---|
| `record(PayInputCommand)` | as `W-19`; with `runRef`: refused by `RunLockedException` when a lock row exists for `(tenant, runRef)`, otherwise inserted with `run_ref` set. **No redirect.** Idempotency unchanged: `(tenant_id, source_module, source_ref)` |
| `forRun(UUID runRef)` | every tagged row for that run, all employees, one query, plus totals by employee and kind — the batch read `W-30.2` needs |
| `lockRun(UUID runRef, YearMonth period)` | inserts the lock row with `run_ref`; a second call is a no-op |
| `forPeriod`, `forEmployee` | **untagged rows only.** `W-29.3` needs no change: it already reads only what these return |

**API contract** — one field added, no new path.

| Method | Path | Change |
|---|---|---|
| POST | `/api/v1/pay-inputs` | `runRef` optional in the body; `409` `RunLocked` when that run is locked |
| GET | `/api/v1/pay-inputs?employeeId=&period=` | unchanged: untagged rows. `?runRef=` returns that run's rows instead; `period` is ignored when `runRef` is given |

No lock endpoint for a run: only `W-30.2`'s service locks a run, through the Java seam. The
`W-19` period-lock endpoint stays as it is.

## 5. Frontend changes

None.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V060__pay_input_run_ref.sql` | `core.pay_input`, `core.pay_input_period_lock`, the `W-19` trigger function | yes | additive — two nullable columns, two indexes, one function body |

`V060` follows the payroll lane's block (`DEV-TRACKER.md` § lanes, `V042`–`V059`); it is a
`core` script and belongs to whichever lane builds it. Use the lane's number at branch time if
it has moved.

```
ALTER TABLE core.pay_input             ADD COLUMN run_ref uuid NULL;
ALTER TABLE core.pay_input_period_lock ADD COLUMN run_ref uuid NULL;
```

`run_ref` has **no foreign key**: the run lives in `payroll.payrun`, and `core` does not
reference a module schema (`02-data-model.md` §4: the schema is present only when Payroll is
bought).

Indexes:

- `idx_pay_input_tenant_run (tenant_id, run_ref) WHERE run_ref IS NOT NULL` — the `forRun` read
- `uk_pay_input_period_lock_tenant_run UNIQUE (tenant_id, run_ref) WHERE run_ref IS NOT NULL`
- `W-19`'s `UNIQUE (tenant_id, period)` on the lock table is **replaced** by
  `uk_pay_input_period_lock_tenant_period UNIQUE (tenant_id, period) WHERE run_ref IS NULL`,
  otherwise the first run lock in a month would collide with the period lock. Drop and create
  in the same script; no row is touched

Trigger: `CREATE OR REPLACE` the `W-19` function so it branches —

```
IF NEW.run_ref IS NULL THEN  refuse when a lock row exists for (NEW.tenant_id, NEW.period) with run_ref IS NULL
ELSE                         refuse when a lock row exists for (NEW.tenant_id, NEW.run_ref)
```

Same trigger, same `BEFORE INSERT`, same name. `W-19` §6 still holds: the lock is a database
constraint, not a service check.

- [x] `tenant_id` already on both tables; both new indexes lead with it
- [x] Index on `tenant_id` plus lookup columns (DEBT-018) — the two above
- [x] No money column touched; `amount numeric(19,4)` and `quantity numeric(10,2)` are `W-19`'s
- [x] Expand / contract — two nullable columns, no default, no backfill, no destructive step. The index swap on the lock table is metadata only
- [x] RLS: both tables already carry `tenant_isolation`; no policy changes. `REVOKE UPDATE, DELETE` from `W-19` is untouched — a tag is set at insert and never changed

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../payinput/PayInputRunTagTest.java` | `record` with `runRef` on a locked run throws `RunLockedException`, not a redirect; `record` with `runRef` on a **locked period** succeeds; `forPeriod` totals exclude tagged rows |
| Integration | `core/.../payinput/PayInputRunLockIT.java` | `lockRun` then an insert **bypassing the service** with that `run_ref` is refused by the trigger; an untagged insert for the same period is accepted; `lockRun` twice leaves one row |
| Integration | `core/.../payinput/PayInputForRunIT.java` | three employees, two runs, tagged and untagged rows: `forRun(A)` returns only A's rows in one statement (`W-55` style); `forPeriod` returns only the untagged ones |
| Integration | `core/.../payinput/PayInputRunLockIndexIT.java` | a period lock and a run lock for the same `(tenant, period)` both insert; a second run lock for the same run does not |
| Integration (change) | `W-19`'s `PayInputRlsIT.java` | one case added: tenant A's `forRun(runRef)` never returns tenant B's rows for the same `runRef` value |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`. `W-19`'s existing tests
must stay green untouched — `forPeriod` on a ledger with no tagged rows behaves exactly as before.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT table_name, column_name, is_nullable FROM information_schema.columns
    WHERE table_schema='core' AND column_name='run_ref' ORDER BY 1;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT indexname, indexdef FROM pg_indexes
    WHERE schemaname='core' AND tablename='pay_input_period_lock' ORDER BY 1;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT count(*) FROM pg_constraint WHERE conrelid='core.pay_input'::regclass AND contype='f' AND conname LIKE '%run_ref%';"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT tgname FROM pg_trigger WHERE tgrelid='core.pay_input'::regclass AND NOT tgisinternal;"
cd code/backend && mvn -q verify
git diff --stat main -- code/backend/core code/backend/migration
```

| Check | Expected |
|---|---|
| `run_ref` | two rows, `pay_input` and `pay_input_period_lock`, both `YES` nullable |
| Lock indexes | two partial `UNIQUE` rows: `... (tenant_id, period) WHERE (run_ref IS NULL)` and `... (tenant_id, run_ref) WHERE (run_ref IS NOT NULL)`; the old unconditional one gone |
| FK on `run_ref` | `0` |
| Trigger | still exactly one `BEFORE INSERT` trigger on `core.pay_input` |
| Suite | green, no skips; every `W-19` test unchanged and passing |
| Diff | only the `payinput` package, its tests, and `V060` |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `forPeriod` keeps returning tagged rows and a bonus is paid twice, once off-cycle and once at month end | **high — the expensive one** | `AND run_ref IS NULL` in the query, `PayInputForRunIT` asserts the split, `W-30.2`'s IT asserts the regular run's lines exclude the off-cycle bonus |
| A tagged input for a locked period is redirected to next month by the `W-19` path | medium | `record` branches on `runRef` before the period check; `PayInputRunTagTest` |
| The index swap is written as `DROP` in one script and `CREATE` in another and a deploy lands between | low | one script, one transaction — Flyway runs each script transactionally on Postgres |
| A caller tags a `LOP_DAYS` row to an off-cycle run | low | Allowed by the ledger — it is dumb (`W-19` §9). `W-30.2`'s contributor ignores `LOP_DAYS` on an off-cycle run |
| `core` grows a check that the run exists | low | It cannot: `maven-enforcer` stops `core` referencing `payroll`. The javadoc on `runRef` says who validates |

## 10. Rollback

Nothing is deployed. Additive columns and indexes; a ledger with no tagged rows is exactly a
`W-19` ledger.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS on every new table outside `reference` | creates no table; both tables already isolated |
| Flyway only, `ddl-auto` nowhere | one script, `V060` |
| `Money`/`BigDecimal` for money | no money column touched |
| Index on `tenant_id` plus lookup columns | two new partial indexes, `tenant_id` leading |
| Expand / contract | nullable columns, no backfill, no destructive step |
| No module references another module | `run_ref` has no FK on purpose; `core` never reads `payroll.payrun` |

## 12. Gap inventory

| ID | Decision |
|---|---|
| DEBT-037 one-time payouts never reach a payslip (proposed, `.claude/outputs/2026-09-25-analyze-w30-off-cycle.md`) | **Fixed by replacement** — a one-time payout is a ledger row; tagged, the off-cycle run pays it; untagged, the regular run does (`W-29.3`) |
| DEBT-018 missing tenant indexes | **Honoured** |

## 13. Decisions — settled 2026-09-25

1. **Tag column or a separate off-cycle input table?** A column. The founder decided
   2026-09-25 that an off-cycle run is a `run_type` on the existing pay run with zero new tables;
   this is the ledger side of that decision.
2. **Does a tagged input obey the period lock?** No — the run lock applies instead (§3).
3. **Does a tagged input for a locked run redirect?** No — `409`. There is no next run to
   redirect to.
4. **Foreign key to `payroll.payrun`?** No. `core` cannot reference a module schema.
   `W-30.2`'s endpoint validates the run before writing; a stray `run_ref` written through the
   `core` endpoint is orphaned and harmless — no run collects it, and `forPeriod` never pays it.
