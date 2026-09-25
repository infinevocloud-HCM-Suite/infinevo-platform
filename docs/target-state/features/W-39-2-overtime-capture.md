# Feature: Overtime capture (basic)

| Field | Value |
|---|---|
| **Feature ID** | `W-39.2` · from ticket `W-39` · `CORE-21` |
| **Promoted to** | `docs/target-state/features/W-39-2-overtime-capture.md` on branch `dev-sayeed` — **`W-39-2` with hyphens**, never `W-39.2`; `guard-edit` blocks the dotted form |
| **Owner** | sayeed, branch `dev-sayeed`, after `W-19` |
| **Apps touched** | `code/backend/core`, `code/backend/migration` |
| **Related gaps** | BUG-002 (fixed for this table), DEBT-018 (honoured), DEBT-013 (discounted) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-25 |
| **Blocked by** | `W-19` — this ticket writes through `PayInputService`, which does not exist until `W-19` is on `main`. `W-13.1` (`7d0bab6`) and `W-11.3` (`3350cf2`) are already there |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | 1 script, one table — `core.overtime_request` | 1 |
| Externally testable behaviour | an administrator records approved overtime and exactly one `OVERTIME` row appears in the pay input ledger for the right employee and period | 1 |
| Frontend area | none | 1 |

Within cap. `W-39` was split on 2026-09-24; attendance is `W-39.1`, this is the other half.

---

## 1. Problem

A Payroll-only customer cannot pay overtime. `D-35` puts basic overtime capture in Core,
entered by an administrator (`07-decisions.md:47`, `01-platform-shape.md:82`,
`02-data-model.md:105`). `D-28` says overtime is a manual form, approved, then paid, and is
independent of attendance (`07-decisions.md:40`). Nothing in `code/` holds overtime today.

The only source is HRMS, and it cannot be ported as it stands:

- **It never reaches payroll.** Approved overtime becomes comp-off leave, not pay —
  `legacy/HRMS_Backend/.../service/OvertimeRequestService.java:112-119` (6 hours is a day,
  4 hours is a half day). `W-19-pay-input-ledger.md:51` records it as *never reaches payroll at all*.
- **No tenant.** `legacy/HRMS_Backend/.../entity/OvertimeRequest.java:16-40` has no tenant
  column — `BUG-002` (`GAP_INVENTORY.md:28`).
- **Employee as a string, name copied in.** `employee_id` is a `String` and `employee_name`
  a copy (`OvertimeRequest.java:23-27`).
- **Hours are a `double`, computed and never stored.** `durationHours` is
  `Duration.between(startTime, endTime).toMinutes() / 60.0` (`OvertimeRequest.java:80-88`).
- **No amount, no rate, anywhere.** The form takes category, start, end, project and notes
  (`legacy/HRMS_Frontend/src/components/Leaves/OverTimeForm.jsx:38-48`).
- **Two-stage approval by role string.** Manager then HR, checked by comparing role names
  (`OvertimeRequestController.java:104-118`). That is the employee request experience, which
  stays in HRMS (`W-40`, `D-35`), and will run on `W-15`'s engine, not on two status columns.

What moves across is the idea only: a dated record of approved overtime hours per employee.

## 2. Scope

**In scope**

- `core.overtime_request`: one row per approved overtime entry — employee, date, hours,
  optional amount, remarks, status
- An administrator records one entry; the service writes one `OVERTIME` row to the pay input
  ledger through `PayInputService.record` (`W-19` §4) in the same transaction
- An administrator cancels an entry; the service reverses the ledger row. There is no edit —
  cancel and re-enter, the same rule the ledger has (`W-19-pay-input-ledger.md:134`)
- List by date range, optionally by employee
- Audit capture through `@Audited`, as the employee sections do (`EmployeePersonal.java:42`)

**Out of scope**

- Employee-submitted overtime and manager approval — `W-40`, HRMS, on `W-15`
- Any pricing. This ticket stores what the administrator typed; it never computes an amount
  from a rate. Whether the pay run prices an hours-only row, and from which salary
  component, is `W-29`'s decision when `W-26` exists
- Deriving overtime from clock hours — ruled out by `D-28`
- Comp-off in place of pay — the legacy behaviour, dropped
- Screens — `W-46`
- Bulk import

## 3. Flow

```
Administrator --> POST /api/v1/overtime            --> OvertimeService.record  --> core.overtime_request
                                                                                --> PayInputService.record(OVERTIME)  --> core.pay_input
Administrator --> DELETE /api/v1/overtime/{id}     --> OvertimeService.cancel  --> status = CANCELLED
                                                                                --> PayInputService.reverse(payInputId) --> core.pay_input (offset row)
Administrator --> GET /api/v1/overtime?from&to[&employeeId] --> OvertimeService.list
```

Both writes are one transaction. If the ledger refuses, the overtime row is not saved.

## 4. Backend changes

Package `com.infinevo.core.overtime`, following `com.infinevo.core.attendance` (`W-39.1`).

| Layer | File | Change |
|---|---|---|
| Controller | `OvertimeController.java` | New. Three endpoints below |
| Service | `OvertimeService.java`, `OvertimeServiceImpl.java` | New. Record, cancel, list, validation; calls `PayInputService` |
| Entity | `OvertimeRequest.java` | New. `@Audited`, `@Table(schema = "core")` |
| Repository | `OvertimeRequestRepository.java` | New. By date range; by employee and range; by id and tenant |
| DTO | `OvertimeEntry.java`, `OvertimeResponse.java` | New records. Response carries `payInputId` and `postedPeriod` |
| Enumeration | `OvertimeStatus.java` | `APPROVED`, `CANCELLED`. `PENDING` and `REJECTED` are `W-40`'s to add, expand-and-contract |
| Enumeration | `OvertimeSource.java` | `ADMIN` now; `REQUEST` reserved for `W-40` |

**API contract**

| Method | Path | Request | Response | `@RequiresAction` |
|---|---|---|---|---|
| `POST` | `/api/v1/overtime` | `{employeeId, overtimeDate, hours, amount?, remarks?}` | `201`, the row plus `payInputId`, `postedPeriod` | `core.overtime.manage` |
| `GET` | `/api/v1/overtime?from=&to=&employeeId=` | `from`, `to` required, span ≤ 93 days; `employeeId` optional | `200`, rows ordered by date desc, employee | `core.overtime.read` |
| `DELETE` | `/api/v1/overtime/{id}` | — | `204`; the row stays with `status = CANCELLED` | `core.overtime.manage` |

The codes exist on `main` — `M/core/V025__catalogue_correction.sql:62-63`. No
`@RequiresModule` on the controller, so a Payroll-only tenant reaches it (`D-35`).

**The ledger call**

| Field of `PayInputCommand` (`W-19` §4) | Value |
|---|---|
| `employeeId` | the entry's employee |
| `period` | `YearMonth.from(overtimeDate)` |
| `kind` | `OVERTIME` |
| `quantity` | `hours` |
| `amount` | `amount`, or null when not given |
| `sourceModule` | `core` |
| `sourceRef` | `overtime_request:<id>` — so a retried `POST` cannot post twice (`W-19` §2 idempotency) |

The ledger may post to a later period if the requested one is locked (`W-19` §2 late input).
The overtime row stores `posted_period` as the ledger reports it, and the response says so.
Cancel calls the seam behind `POST /api/v1/pay-inputs/{id}/reverse` (`W-19` §4) with reason
`overtime cancelled`; if `W-19` lands that as a method with another name, use that name.

**Rules the service enforces**

| Rule | Refusal |
|---|---|
| Employee not found in this tenant, or `is_deleted` | `400 VALIDATION_FAILED` |
| `overtimeDate` in the future | `400 VALIDATION_FAILED` |
| `hours` missing, ≤ 0, or > 24 | `400 VALIDATION_FAILED` |
| `amount` present and ≤ 0 | `400 VALIDATION_FAILED` — the ledger has the same check |
| `from` after `to`, or span over 93 days | `400 VALIDATION_FAILED` |
| Cancel a row already `CANCELLED` | `409 CONFLICT` |
| Ledger write fails for any reason | whole request rolled back, nothing saved |

More than one entry per employee per day is allowed. Overtime is not attendance; two
separate stints on one day are two rows.

**Decision — hours and amount, no rate.** The ledger takes both `quantity` and `amount`, both
optional (`W-19-pay-input-ledger.md:156`). The platform has no salary structure yet
(`W-26`, not started), so there is nothing to price hours against. An administrator for a
Payroll-only tenant knows the rate; they type the amount when they know it and leave it blank
when the pay run should price it. The service copies the two figures into the ledger and
does no arithmetic, which is the same rule the ledger holds itself
(`09-build-order.md:199`, *keep it dumb*).

## 5. Frontend changes

None. Screens are `W-46`.

## 6. Database changes

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `core/V041__overtime_request.sql` — reserved in `DEV-TRACKER.md:46` | `core.overtime_request` | yes | forward-only; a new table, nothing destroyed |

```sql
CREATE TABLE core.overtime_request (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES core.tenant(tenant_id),
    employee_id     UUID NOT NULL REFERENCES core.employee(id),
    overtime_date   DATE NOT NULL,
    hours           NUMERIC(10,2) NOT NULL,
    amount          NUMERIC(19,4),
    status          VARCHAR(16) NOT NULL DEFAULT 'APPROVED',
    source          VARCHAR(16) NOT NULL DEFAULT 'ADMIN',
    remarks         VARCHAR(255),
    pay_input_id    UUID REFERENCES core.pay_input(id),
    posted_period   CHAR(7),
    cancelled_at    TIMESTAMPTZ,
    created_at / created_by / updated_at / updated_by   -- as V013__work_location.sql:18-21
    CONSTRAINT overtime_request_hours_check  CHECK (hours > 0 AND hours <= 24),
    CONSTRAINT overtime_request_amount_check CHECK (amount IS NULL OR amount > 0),
    CONSTRAINT overtime_request_status_check CHECK (status IN ('APPROVED','CANCELLED')),
    CONSTRAINT overtime_request_source_check CHECK (source IN ('ADMIN','REQUEST'))
);
CREATE INDEX idx_overtime_request_tenant_date          ON core.overtime_request (tenant_id, overtime_date DESC);
CREATE INDEX idx_overtime_request_tenant_employee_date ON core.overtime_request (tenant_id, employee_id, overtime_date DESC);
CREATE INDEX idx_overtime_request_tenant_pay_input     ON core.overtime_request (tenant_id, pay_input_id);
-- + ENABLE ROW LEVEL SECURITY and the tenant_isolation policy, CASE form, copied from V013__work_location.sql:51-60
```

`pay_input_id` is nullable only because the row is inserted before the ledger answers; the
service sets it in the same transaction and the unit test asserts no `APPROVED` row is ever
committed without one.

| Standing rule | This ticket |
|---|---|
| `tenant_id` and RLS on every new table | `core.overtime_request` has both, in the same script |
| Flyway only, `ddl-auto` nowhere | one script; none added |
| Money as `Money` / `BigDecimal` | `amount NUMERIC(19,4)`, entity field `Money` (`shared/.../money/Money.java`); `hours NUMERIC(10,2)` as `BigDecimal`, the day-count shape at `CONVENTIONS.md:37`. The legacy `double` at `OvertimeRequest.java:80` is not carried |
| Index on `tenant_id` plus lookup columns (`DEBT-018`) | three indexes lead with `tenant_id`; both FKs are covered; the date indexes are descending, as `migration/README.md` rule 4 requires of a table that grows with time |
| Expand / contract | new table only |
| No module references another | `core` only. `W-40` (HRMS) will write `source = 'REQUEST'` through `OvertimeService`, never the table. The ledger is reached through `PayInputService`, also `core` |

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `core/.../overtime/OvertimeServiceTest.java` | every rule in §4; the `PayInputCommand` built from an entry has kind `OVERTIME`, `sourceRef = overtime_request:<id>`, `quantity = hours`, `amount` passed through unchanged; the service exposes no rate and does no arithmetic |
| Integration | `core/.../overtime/OvertimeLedgerIT.java` | `POST` leaves exactly one `core.pay_input` row for the employee and period; `DELETE` leaves a second row with `reverses_id` set and the two net to zero; a `POST` whose ledger write is forced to fail leaves no overtime row |
| Integration | `core/.../overtime/OvertimeRlsIT.java` | tenant A's rows invisible to tenant B through the API and the repository |
| Integration | `core/.../overtime/OvertimePermissionIT.java` | `403` without `core.overtime.manage` on `POST` and `DELETE`; `core.overtime.read` alone can `GET` |

All extend `AbstractIntegrationTest` with `@EnabledIfDockerAvailable`.

## 8. Verification

```bash
docker compose -f infra/docker/compose.yml up -d postgres
docker compose -f infra/docker/compose.yml up --build migrate
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT relrowsecurity FROM pg_class WHERE oid='core.overtime_request'::regclass;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT indexname FROM pg_indexes WHERE schemaname='core' AND tablename='overtime_request' ORDER BY 1;"
docker compose -f infra/docker/compose.yml exec -T postgres psql -U migration_user -d infinevo -c \
  "SELECT column_name, data_type, numeric_precision, numeric_scale FROM information_schema.columns
    WHERE table_schema='core' AND table_name='overtime_request' AND column_name IN ('hours','amount');"
cd code/backend && mvn -q verify
```

| Check | Expected |
|---|---|
| RLS on `core.overtime_request` | `t` |
| Indexes | `overtime_request_pkey`, `idx_overtime_request_tenant_date`, `idx_overtime_request_tenant_employee_date`, `idx_overtime_request_tenant_pay_input` |
| `hours` | `numeric`, 10, 2 |
| `amount` | `numeric`, 19, 4 — never `double precision` |
| Suite | green, no skips; the four test classes in §7 run |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `W-19` lands with a different method name or shape for reversal | medium | The contract is `W-19` §4; this ticket adapts to what merged, and the developer reads `PayInputService` on `main` before starting |
| Only `tenant-admin` can use this. `hr` holds `core.attendance.*` but not `core.overtime.*`; `payroll-officer` holds neither (`V025:150-173,187-207`) | certain | Not fixed here — a grant means re-creating `core.seed_system_roles`, a catalogue ticket. Founder decides whether `hr` should manage overtime before `W-46` builds the screen |
| The pay run finds an hours-only row and has no rate | certain until `W-26` | Documented in §4; `W-29` decides. Nothing here has to change when it does |
| `W-40` later needs pending and rejected states | certain | `status` has a `CHECK` that `W-40` widens with an `ALTER`; `source = 'REQUEST'` is reserved now |
| Someone adds a rate lookup "just to help" | medium | Out of scope in §2; the unit test asserts the service does no arithmetic |

## 10. Rollback

Revert the application commit. The table stays and is unused; Flyway is forward-only, so no
script is reverted. Ledger rows already written stay, as the ledger requires; they can be
reversed through `W-19`'s endpoint if they must not count.

## 11. Gap inventory

| ID | Decision |
|---|---|
| BUG-002 no tenant column (`GAP_INVENTORY.md:28`) | **Fixed for this table** — `tenant_id` and RLS |
| DEBT-018 missing tenant indexes (`GAP_INVENTORY.md:68`) | **Honoured** |
| DEBT-013 package typos (`GAP_INVENTORY.md:51`) | **Discounted** — the legacy overtime code has none |
| Legacy comp-off conversion (`OvertimeRequestService.java:112-119`) | **Dropped by decision** — `D-28`, overtime is paid |
