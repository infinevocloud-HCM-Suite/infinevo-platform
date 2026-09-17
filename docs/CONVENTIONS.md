# CONVENTIONS — Infinevo Cloud

> Rules that code must follow. Financial precision rules were lifted from
> `MASTER_BUG_AND_PROGRESS_TRACKER.md` §3.2 on 2026-09-11; that file is archived.

---

## 1. Hard rules

These are enforced by the harness where possible and by review otherwise.
Root `CLAUDE.md` restates them; this file is the authority.

| # | Rule | Enforcement |
|---|---|---|
| 1 | **Founder approval before any implementation.** Plans are written and stopped on; code follows approval, never precedes it | `plan-feature` skill halts; review |
| 2 | **Read `.claude/work/active-work.md` before starting** any task | Root `CLAUDE.md`; review |
| 3 | **Never edit `docs/` or `*.properties` during feature work.** Docs change only via `sync-docs` with an approved diff | `guard-edit` hook (`PreToolUse`, blocks) |
| 4 | **Flyway for migrations — never `ddl-auto`.** The property is never set, in any environment — Hibernate then defaults to `none` and Flyway owns the schema. Every schema change is a versioned script (`D-46`) | `ci.yml` "ddl-auto set nowhere"; `infra/docker/smoke.sh`; `check-done.mjs` gate 5; see DEBT-002 |
| 5 | **Upstream remotes are read-only.** Never push to the four origin repos — they are production source. Changes flow one way, upstream → new repo | `sync-upstream` skill never pushes |
| 6 | **Repo state beats chat memory.** If a conversation and the repo disagree, the repo is right. Verify before asserting | Review |
| 7 | **`tenant_id` is mandatory on every new entity and query** once the tenant column lands. No exceptions, including lookup and reference tables | `tenant-audit` skill; review |

---

## 2. Financial precision (`BigDecimal`)

All monetary and leave-day values **must** follow these rules. Violations are treated as
blocking defects — they cause real financial error, not cosmetic drift.

### Rule 1 — Field type

```java
// REQUIRED for all financial fields
@Column(precision = 19, scale = 4)   // monetary amounts (salary, deductions, net pay)
private BigDecimal netPay;

@Column(precision = 10, scale = 2)   // leave days / LOP (must support 0.5, 0.25)
private BigDecimal lopDays;

// PROHIBITED for financial values
private Double netPay;        // floating-point imprecision
private Float monthlySalary;  // floating-point imprecision
private Integer totalLeaves;  // cannot represent half-days
```

> `Integer` for leave days is exactly what caused BUG-003 — half-day LOP silently
> truncated to zero, overpaying employees on every pay run.

### Rule 2 — Rounding

```java
// monetary amounts (pay, deductions, taxes)
amount.setScale(2, RoundingMode.HALF_UP);

// leave days
days.setScale(2, RoundingMode.HALF_UP);
```

Round once, at the boundary — never mid-calculation.

### Rule 3 — Arithmetic

```java
// use BigDecimal operations
BigDecimal total = statutoryDeductions.add(adHocDeductions);
BigDecimal netPay = grossSalary.subtract(total).add(reimbursements);

// never convert to double to do maths
double bad = gross.doubleValue() - deductions.doubleValue();   // PROHIBITED
```

### Rule 4 — Comparison

```java
if (amount.compareTo(BigDecimal.ZERO) > 0) { ... }   // correct
if (amount.equals(BigDecimal.ZERO)) { ... }          // WRONG — scale-sensitive
```

`new BigDecimal("0.00").equals(BigDecimal.ZERO)` is `false`. Always use `compareTo`.

### Rule 5 — Division

Division requires an explicit scale and rounding mode, or it throws on non-terminating
decimals:

```java
BigDecimal perDay = monthlySalary.divide(
        BigDecimal.valueOf(paidDays), 2, RoundingMode.HALF_UP);
```

---

## 3. Backend conventions

| Convention | Detail |
|---|---|
| **Layering** | `controller → service (interface) → serviceimpl → repository → entity`. Business logic belongs **only** in `serviceimpl/`. Controllers unpack, delegate, repack |
| **Tenancy** | Every business endpoint takes `@RequestHeader("organizationId")` and every query is scoped by it. See DEBT-022 for the 57 methods that currently are not |
| **Identifiers** | Entities carry both a DB primary key (`Long id`) and a random 10-digit business ID (`employeeId`, `payrunId`). APIs expose the business ID; foreign keys use the PK. Do not confuse them |
| **Response shape** | Currently hand-built `Map` per controller with inconsistent keys (DEBT-008). New endpoints should use `status` / `message` / `data` until a shared wrapper exists |
| **Table naming** | Payroll preserves class names verbatim (`PhysicalNamingStrategyStandardImpl`); HRMS converts CamelCase to snake_case. See `ARCHITECTURE.md` §9 |
| **Indexes** | New entities must declare `@Index` on tenant and lookup columns. See DEBT-018 — there are currently zero |
| **Tests** | `W-04`. Unit tests end in `Test` and run under Surefire with no Spring context. Integration tests end in `IT`, run under Failsafe, and extend `com.infinevo.shared.test.AbstractIntegrationTest` from the `shared` test-jar — a real PostgreSQL 16 Testcontainer, database `infinevo`, connected as the non-owner `app_user` so row-level security is exercised. **Never H2 or any in-memory database.** Test data builders live in the module that owns the entity (`core`, `hrms`, `payroll`); `shared` owns none |

## 4. Frontend conventions

| Convention | Detail |
|---|---|
| **Forms** | Formik + Yup |
| **Notifications** | SweetAlert2 via `msgHelper.js` (`successMsg` / `errorMsg`) — not Ant Design `message` |
| **API access** | Payroll FE calls axios directly from screens (no service layer for most modules). New code should prefer `shared/services/` |
| **Auth token** | Read via the interceptor, not `localStorage.getItem("__t")` inline. ~100 files currently do the latter |
| **Lint** | HRMS FE: `npm run lint`. Payroll FE has no lint script — use `npx eslint src --ext .js,.jsx` |

## 5. Naming hazards

Existing typos are load-bearing — they appear in real package paths. Grep with care and
do not "fix" them without a planned refactor:

`timeshhet/` · `leaveAndAttedance/` · `EmployyePortalContoller.java` ·
`attendence.js` · `benifits.js` · `salaryRevisonDetails.js` · `leaveAttendence/`
