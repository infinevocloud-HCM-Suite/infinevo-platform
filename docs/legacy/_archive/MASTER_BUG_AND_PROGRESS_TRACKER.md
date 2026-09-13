# Master Bug & Progress Tracker — Infinevo Cloud HRMS & Payroll Suite

> **Document Version:** 1.0  
> **Last Updated:** 2026-08-12  
> **Classification:** Internal — Engineering Operations  
> **Maintained By:** Engineering Lead / QA Lead  
> **Review Cadence:** Updated on every sprint close and bug triage session

---

## Section 1 — Master Bug & Technical Debt Matrix

> **Status Legend:**  
> 🔴 Open — Not started or actively failing  
> 🟡 In Progress — Work has begun  
> 🟢 Resolved — Fix deployed and verified  
> ⚪ Pending — Placeholder / future discovery  

---

### Active Bugs & Blockers

| Bug ID | Component | Description & Impact | Root Cause | Priority | Status | Target Phase |
|---|---|---|---|---|---|---|
| **BUG-001** | Auth / Security | **Session mismatch in cross-application navigation.** Employees logged into HRMS are not recognized by Payroll and vice versa. No SSO. Users must authenticate twice, and tokens are incompatible between services. | Dual auth systems: HRMS uses custom JWT (`jjwt 0.12.5`); Payroll uses Keycloak OAuth2. No shared identity provider. | `P0 - Blocker` | 🔴 Open | Phase 1 |
| **BUG-002** | Multi-Tenancy | **Cross-tenant data exposure risk.** Core HRMS tables (`our_users`, `attendance`, `timesheets`, `leave_requests`, `projects`) have no `org_id` column. A single compromised query or auth bypass exposes all customer data across all organizations. | Missing `org_id` tenant discriminator and absent Hibernate `@TenantId` filters on primary entities. | `P0 - Blocker` | 🔴 Open | Phase 1 |
| **BUG-003** | Payroll / LOP Calculation | **Half-day LOP silently parsed as zero, causing overpayment.** HRMS stores LOP days as `Double` (supporting `0.5` half-day). Payroll's `LeaveResponseDTO` maps `totalLeaves` to `Integer`, truncating `0.5` → `0`. Employee receives full salary despite taking an unpaid half-day. Direct financial defect on every pay run. | Data type mismatch: `Double` (HRMS DTO) vs. `Integer` (Payroll DTO). No validation or precision standard enforced on LOP/financial fields. | `P0 - Blocker` | 🔴 Open | Phase 1 |
| **BUG-004** | DB Governance | **Uncontrolled schema drift across environments.** `spring.jpa.hibernate.ddl-auto=update` is active in both backends. Schema changes made by developers locally may propagate to production silently on next deployment. No rollback capability. No audit trail of schema changes. | Missing database migration framework (Flyway / Liquibase). `ddl-auto=update` is inappropriate for any environment beyond local development. | `P1 - High` | 🔴 Open | Phase 1 |
| **BUG-005** | Pay Run Resilience | **Pay run fails completely if HRMS is unavailable.** `EmployeePayRunServiceImpl` makes a synchronous blocking WebClient call to `POST /public/get-employee-leaves` during pay run finalization. If HRMS is down, overloaded, or times out, the entire pay run aborts with a runtime exception. | Tight synchronous coupling between Payroll and HRMS at a critical financial workflow junction. No circuit breaker, no fallback, no retry strategy. | `P1 - High` | 🔴 Open | Phase 2 |
| **BUG-006** | Frontend Performance | **Dashboard lag and excessive rendering latency on Payroll Frontend.** `Payroll-Fend-react/src/pages/mainPages/dashboardPage/index.js` is >270KB — a monolithic file containing most of the payroll module UI. Initial load time degrades significantly on slower connections. No code splitting or lazy loading implemented. | Single-file architecture with no module decomposition. All payroll feature UIs (salary, pay runs, employees, claims) bundled into one component file. | `P2 - Medium` | 🔴 Open | Phase 3 |
| **BUG-007** | Data Model Debt | **Duplicate entity definitions causing maintenance confusion and data mapping risk.** Two sets of parallel entities exist: `LeaveRequest.java` + `LeaveRequests.java`; `Timesheet.java` + `Timesheets.java`. Any change to the domain must be applied in two places. Risk of data written to wrong entity. | Incomplete migration from legacy to newer entity design. Old entities were never deprecated or removed after the new ones were introduced. | `P2 - Medium` | 🔴 Open | Phase 3 |

---

### Reserved — Future Discovery Placeholders

| Bug ID | Component | Description | Root Cause | Priority | Status | Target Phase |
|---|---|---|---|---|---|---|
| **BUG-008** | AI Agent / AuditBot | Reserved — potential anomaly detection edge case or HITL gate bypass discovered during AuditBot UAT | TBD | `P1 - High` | ⚪ Pending | Phase 2 |
| **BUG-009** | DB Migration / Flyway | Reserved — potential data migration conflict discovered when applying `org_id` column to tables with existing data | TBD | `P1 - High` | ⚪ Pending | Phase 1 |
| **BUG-010** | Load Testing / Scale | Reserved — bottleneck or failure discovered during K6 load test at 1,000 concurrent users | TBD | `P1 - High` | ⚪ Pending | Phase 4 |
| **BUG-011** | Bank Payout API | Reserved — integration defect discovered during ICICI/HDFC/Axis UAT testing | TBD | `P1 - High` | ⚪ Pending | Phase 4 |
| **BUG-012** | Security / VAPT | Reserved — vulnerability or compliance gap discovered during third-party penetration test | TBD | `P0 - Blocker` | ⚪ Pending | Phase 4 |

---

## Section 2 — Technical Debt Register

| Debt ID | Component | Description | Impact | Priority | Target Phase |
|---|---|---|---|---|---|
| **DEBT-001** | Payroll FE | `dashboardPage/index.js` — 271KB monolithic file | Poor maintainability, slow load, untestable | High | Phase 3 |
| **DEBT-002** | Both Backends | No Flyway / Liquibase — `ddl-auto=update` in all environments | Schema drift, no rollback, production risk | High | Phase 1 |
| **DEBT-003** | Both Backends | Zero automated test coverage across all 4 projects | Cannot safely refactor or release with confidence | Critical | Phase 3 |
| **DEBT-004** | Both Backends | All secrets hardcoded in `.properties` files | Security liability, audit failure risk | Critical | Phase 1 |
| **DEBT-005** | HRMS Backend | `UsersManagementService.java` (26KB) — God Service pattern | Impossible to maintain; violates SRP | Medium | Phase 3 |
| **DEBT-006** | HRMS Backend | Duplicate entity classes: `LeaveRequest` + `LeaveRequests`, `Timesheet` + `Timesheets` | Maintenance burden, data consistency risk | Medium | Phase 3 |
| **DEBT-007** | Both Backends | No API versioning (`/api/v1/` prefix absent) | Breaking changes have no safe deprecation path | Medium | Phase 2 |
| **DEBT-008** | Both Backends | Inconsistent error response format across controllers | API consumers cannot handle errors predictably | Medium | Phase 2 |
| **DEBT-009** | HRMS Backend | `exception/` and `exceptions/` — two parallel exception packages | Confusion about which to use; duplicate handlers | Low | Phase 3 |
| **DEBT-010** | Both Backends | CORS policy not hardened — `*` origins may be active | Security header exposure | High | Phase 1 |
| **DEBT-011** | Both Backends | Cloudinary public URLs for sensitive employee documents | Document URLs permanent; no expiry or auth | High | Phase 2 |
| **DEBT-012** | HRMS Frontend | No Redux — all state in React Context | Performance degradation at scale | Medium | Phase 3 |
| **DEBT-013** | Both Backends | Package name typos: `timeshhet/`, `leaveAndAttedance/`, `EmployyePortalContoller.java` | Confusing for new developers | Low | Phase 3 |
| **DEBT-014** | Payroll Backend | `master_config` entity — unclear purpose, undocumented | Risk of misuse or deletion | Low | Phase 3 |

---

## Section 3 — Operational Guidelines & Quality Assurance Standards

### 3.1 Severity Level Definitions

| Level | Tag | Definition | SLA — Resolution Target |
|---|---|---|---|
| **Blocker** | `P0 - Blocker` | Prevents a core financial workflow from functioning, causes data corruption, exposes security vulnerability, or violates tenant isolation. No workaround exists. | Must be fixed before any Phase progression. Zero P0s permitted at production launch. |
| **High** | `P1 - High` | Significantly degrades a feature, causes incorrect data in non-critical paths, or blocks a secondary workflow. A workaround may exist but is not acceptable long-term. | Must be resolved within the current Phase. |
| **Medium** | `P2 - Medium` | Technical debt, code quality issue, or minor UX degradation. System remains functional. | Must be resolved before production launch but can be deferred within phases. |
| **Low** | `P3 - Low` | Cosmetic issues, typos, minor inconsistencies. No functional impact. | Resolved opportunistically. |

---

### 3.2 `BigDecimal` Financial Precision Rules

All financial values in the Infinevo Cloud platform **must** adhere to the following precision standards. Violations constitute a `P0 - Blocker` defect.

#### Rule 1 — Field Type

```java
// ✅ REQUIRED for all financial fields
@Column(precision = 19, scale = 4)   // For monetary amounts (salary, deductions, net pay)
private BigDecimal netPay;

@Column(precision = 10, scale = 2)   // For leave days / LOP (supports 0.5, 0.25)
private BigDecimal lopDays;

// ❌ PROHIBITED — never use for financial values
private Double netPay;       // Floating-point imprecision
private Float monthlySalary; // Floating-point imprecision
private Integer totalLeaves; // Cannot represent half-days
```

#### Rule 2 — Rounding Mode

```java
// Monetary amounts (pay, deductions, taxes)
amount.setScale(2, RoundingMode.HALF_UP);

// High-volume tax slab calculations
taxAmount.setScale(2, RoundingMode.HALF_EVEN);  // Banker's rounding

// Leave day calculations
lopDays.setScale(2, RoundingMode.HALF_UP);
```

#### Rule 3 — Arithmetic Operations

```java
// ✅ CORRECT — BigDecimal arithmetic
BigDecimal perDayPay = monthlySalary.divide(paidDays, 4, RoundingMode.HALF_UP);
BigDecimal lopDeduction = perDayPay.multiply(lopDays).setScale(2, RoundingMode.HALF_UP);
BigDecimal netPay = totalEarnings.subtract(lopDeduction).subtract(totalDeductions);

// ❌ INCORRECT — never convert to double for arithmetic
double perDay = monthlySalary.doubleValue() / paidDays.intValue(); // Precision lost
```

#### Rule 4 — REST / API Contracts

```json
// ✅ Correct JSON representation (string to preserve precision)
{
  "lopDays": "0.50",
  "netPay": "42500.75",
  "totalDeductions": "5200.00"
}

// ❌ Incorrect (floating-point JSON number)
{
  "lopDays": 0.5,
  "netPay": 42500.75
}
```

> **Note:** Use `@JsonSerialize(using = ToStringSerializer.class)` on `BigDecimal` fields to ensure JSON serialization as strings, preserving scale and preventing floating-point representation issues.

#### Fields Requiring Immediate `BigDecimal` Conversion (BUG-003)

| Service | Entity / DTO | Field | Current Type | Required Type |
|---|---|---|---|---|
| HRMS | `EmployeeMonthlyLop` | `lopDays` | `Double` | `BigDecimal` |
| Payroll | `LeaveResponseDTO` | `totalLeaves` | `Integer` | `BigDecimal` |
| Payroll | `EmployeePayRun` | `lop`, `netPay`, `monthlySalary` | `Double` / `BigDecimal` (mixed) | `BigDecimal` (uniform) |
| Payroll | `CtcStructure` | `annualCtc`, `monthlySalary` | varies | `BigDecimal` |
| Payroll | `Earning` | `amountOrPercentage` | `Double` | `BigDecimal` |
| Payroll | `Deduction` | `amount` | `Double` | `BigDecimal` |

---

### 3.3 Definition of Done (DoD) — Bug Resolution Criteria

A bug is considered **Resolved** only when **all** of the following conditions are met:

#### For All Bugs (P0, P1, P2)

- [ ] Root cause identified and documented in this tracker
- [ ] Fix implemented and peer-reviewed (minimum 1 reviewer)
- [ ] Unit tests written specifically covering the bug scenario
- [ ] No regressions introduced (existing test suite passes)
- [ ] Fix deployed to staging environment
- [ ] QA verification sign-off on staging

#### Additional Criteria for `P0 - Blocker`

- [ ] Integration test added that would have caught this bug
- [ ] Fix deployed to production
- [ ] Post-deployment smoke test passed
- [ ] Incident report written (root cause, impact duration, affected customers, prevention steps)
- [ ] Engineering Lead sign-off

#### Additional Criteria for Financial Bugs (BUG-003 and related)

- [ ] End-to-end pay run test with fractional LOP (0.5 days) produces mathematically correct net pay
- [ ] Verified in staging with real employee dataset (minimum 50 employees)
- [ ] Finance/CFO sign-off on corrected calculation
- [ ] Historical pay runs reviewed — if prior pay runs were affected, correction payslips issued

---

### 3.4 Progress Tracking Guidelines

**Bug Status Transitions:**

```
⚪ Pending → 🔴 Open        (when bug is confirmed and triaged)
🔴 Open    → 🟡 In Progress  (when developer picks up the ticket)
🟡 In Progress → 🟢 Resolved (when all DoD criteria are met)
🟢 Resolved → 🔴 Open       (if regression is detected — reopen)
```

**Update Rules:**
- This document must be updated at the **close of every sprint**
- Any new bug found during development, testing, or production must be added with a new `BUG-NNN` ID
- Status must never be set to `🟢 Resolved` by the developer who wrote the fix — requires QA or peer sign-off
- All P0 bugs must be discussed in the daily standup until resolved

**Bug Discovery Sources:**
- Developer code review
- QA test execution
- AuditBot pre-pay-run scan findings
- Third-party penetration testing (Phase 4)
- K6 load test failure analysis (Phase 4)
- Production incident reports

---

## Section 4 — Phase-Wise Resolution Summary

| Phase | Bugs Targeted | Debt Items Targeted | Exit Gate |
|---|---|---|---|
| **Phase 1** (Weeks 1–6) | BUG-001, BUG-002, BUG-003, BUG-004 + BUG-009 (if found) | DEBT-002, DEBT-004, DEBT-010 | Zero P0s open; multi-tenancy verified; BigDecimal enforced |
| **Phase 2** (Weeks 7–12) | BUG-005 + BUG-008 (if found) | DEBT-007, DEBT-008, DEBT-011 | Pay run resilient to HRMS outage; payslips generated; AuditBot live |
| **Phase 3** (Weeks 13–18) | BUG-006, BUG-007 | DEBT-001, DEBT-003, DEBT-005, DEBT-006, DEBT-009, DEBT-012, DEBT-013 | >70% test coverage; FE bundle <500KB; no duplicate entities |
| **Phase 4** (Weeks 19–24) | BUG-010, BUG-011, BUG-012 (if found) | DEBT-014 | VAPT clean; load test passed; bank payout verified |

---

## Appendix — Bug ID Registry

| Bug ID | Status | Phase |
|---|---|---|
| BUG-001 | 🔴 Open | 1 |
| BUG-002 | 🔴 Open | 1 |
| BUG-003 | 🔴 Open | 1 |
| BUG-004 | 🔴 Open | 1 |
| BUG-005 | 🔴 Open | 2 |
| BUG-006 | 🔴 Open | 3 |
| BUG-007 | 🔴 Open | 3 |
| BUG-008 | ⚪ Pending | 2 |
| BUG-009 | ⚪ Pending | 1 |
| BUG-010 | ⚪ Pending | 4 |
| BUG-011 | ⚪ Pending | 4 |
| BUG-012 | ⚪ Pending | 4 |
| BUG-013+ | ⚪ Reserved | TBD |
