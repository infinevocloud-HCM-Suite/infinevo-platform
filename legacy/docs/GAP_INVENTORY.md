# GAP INVENTORY — Infinevo Cloud

> ⚠️ **LEGACY — frozen reference.** This describes the four applications being
> replaced, as frozen on 2026-09-13. It is **not** a specification for new work.
> Build against `docs/target-state/`. See `legacy/docs/README.md`.

> Derived from `MASTER_BUG_AND_PROGRESS_TRACKER.md` on 2026-09-11. Original archived.
>
> **Findings only.** Target phases, week ranges, exit gates, Definition of Done, SLA and
> sign-off rules were removed — they described a plan, not the system. `Priority` and
> `Phase` are left empty for the founder to set.
>
> **Reserved rows removed.** BUG-008 through BUG-013 were placeholders under a heading
> reading *"Reserved — Future Discovery Placeholders"*, each with root cause `TBD`,
> describing defects in work that has not happened (AuditBot UAT, K6 load test,
> ICICI/HDFC/Axis bank UAT, VAPT). None describe the system as it exists.

Verified against: `HRMS_Backend@d984c64`, `HRMS_Frontend@c72116c`,
`Payroll-Bend-SBoot@39b37d6`, `Payroll-Fend-react@053ca62`.

---

## Section 1 — Defects

| ID | Component | Finding | Root cause | Priority | Phase |
|---|---|---|---|---|---|
| **BUG-001** | Auth / Security | Employees logged into HRMS are not recognised by Payroll and vice versa. No SSO; users authenticate twice and tokens are incompatible. | Dual auth systems — HRMS uses custom JWT (`jjwt` 0.12.5), Payroll uses Keycloak OAuth2. No shared identity provider. | | |
| **BUG-002** | Multi-Tenancy | Cross-tenant data exposure risk. Core HRMS tables (`ourusers`, `attendance`, `timesheets`, `leave_requests`, `projects`) have no tenant column. | No tenant discriminator and no Hibernate `@TenantId` filter on primary entities. **Verified 2026-09-11:** `grep -rli "organizationId\|tenant" HRMS_Backend/` returns **0 files**. | | |
| **BUG-003** | Payroll / LOP | ~~Half-day LOP parsed as zero, causing overpayment.~~ **RESOLVED — verify before closing.** | `IntegrateWithHrmsServiceImpl.java:33` now returns `Map<String, Double>`. Additionally superseded: pay run reads LOP from `employee_leave_balance_consumption`, not from HRMS. | | |
| **BUG-004** | DB Governance | Uncontrolled schema drift. `spring.jpa.hibernate.ddl-auto=update` is active in both backends; local schema changes can reach production silently. No rollback, no audit trail. | No migration framework (Flyway / Liquibase). | | |
| **BUG-005** | Pay Run Resilience | Pay run fails entirely if HRMS is unavailable — synchronous blocking WebClient call during finalisation, with no circuit breaker, fallback or retry. | Tight synchronous coupling at a critical financial junction. **Note:** severity reduced now that LOP comes from Payroll's own tables; confirm whether the call is still on the critical path. | | |
| **BUG-006** | Frontend Performance | **Corrected 2026-09-11.** Original claim — `dashboardPage/index.js` is >270KB causing dashboard lag — is **false**. `index.js` is 42,277 bytes / 884 lines. The 270,905-byte / 2,738-line file is `dashboardcopy.js`, which is **imported nowhere** and therefore never loaded. The real issue is dead code in the tree, not runtime lag. | Abandoned file never deleted. No code splitting or lazy loading exists, which remains a genuine concern at 116k LOC in one CRA bundle. | | |
| **BUG-007** | Data Model Debt | Duplicate entity definitions: `LeaveRequest.java` + `LeaveRequests.java`, `Timesheet.java` + `Timesheets.java`. Domain changes must be applied twice; risk of writing to the wrong entity. | Incomplete migration from legacy design. **Verified:** all four map to distinct live tables — `leave_request`, `leave_requests`, `timesheet`, `timesheets`. | | |

## Section 2 — Technical debt

| ID | Component | Finding | Impact | Priority | Phase |
|---|---|---|---|---|---|
| **DEBT-001** | Payroll FE | `dashboardcopy.js` — 271KB / 2,738 lines, dead. *(ID originally cited `index.js`; corrected 2026-09-11.)* | Dead weight in the tree; misleads anyone reading it | | |
| **DEBT-002** | Both backends | No Flyway / Liquibase — `ddl-auto=update` in all environments | Schema drift, no rollback, production risk | | |
| **DEBT-003** | All four apps | Effectively zero automated test coverage | No safety net for any refactor | | |
| **DEBT-004** | Both backends | Secrets hardcoded in `.properties` — Keycloak secret, Cloudinary keys, Brevo key, `fed.secret`, DB passwords | Credentials in git history; assume compromised | | |
| **DEBT-005** | HRMS Backend | `UsersManagementService.java` — God Service pattern | Untestable, high change risk | | |
| **DEBT-006** | HRMS Backend | Duplicate entity classes (see BUG-007) | Double maintenance | | |
| **DEBT-007** | Both backends | No API versioning — no `/api/v1/` prefix | No way to evolve contracts without breaking clients | | |
| **DEBT-008** | Both backends | Inconsistent error/response format. Each controller hand-builds a `Map` envelope; keys differ (`data` in `EarningController`, `payrollRun` in `PayRunController`) | Frontend must special-case per endpoint | | |
| **DEBT-009** | HRMS Backend | `exception/` and `exceptions/` — two parallel packages | Confusion over where handlers live | | |
| **DEBT-010** | Both backends | CORS not hardened | Possible wildcard origins | | |
| **DEBT-011** | Both backends | Cloudinary public URLs for sensitive employee documents | PII exposure via guessable URLs | | |
| **DEBT-012** | HRMS Frontend | No Redux — all state in React Context | Prop drilling, hard to scale | | |
| **DEBT-013** | Both backends | Package/class name typos: `timeshhet/`, `leaveAndAttedance/`, `EmployyePortalContoller.java` | Breaks grep; signals low review rigour | | |
| **DEBT-014** | Payroll Backend | `masterConfig` entity — unclear purpose, undocumented | Unknown blast radius if changed | | |

## Section 3 — Added 2026-09-11 (documentation audit)

| ID | Component | Finding | Impact | Priority | Phase |
|---|---|---|---|---|---|
| **DEBT-015** | Docs | `DB_SCHEMA.md` documented 74 tables against 131 real ones, and used idealised snake_case names (`our_users`, `pay_run`, `work_location`) that do not exist. **Fixed 2026-09-11** — 44 headings corrected, 52 tables added, 2 disabled entities flagged, naming rules documented. | Was actively misleading for query writing | | |
| **DEBT-016** | Docs | `FEATURE_MAP.md` covered 3 of 4 applications — Payroll Frontend was entirely absent. Full path audit (383 references vs live branches) also found: item #24 (FEAT-002) cited **six files and a route that never existed** — it described the planned design, not what shipped as `SalaryDeduction*`; wrong class names for the Keycloak config/service and the HRMS employee repository; a non-existent `authPages/setupOrganization/` folder; Payroll Backend items 23–27 filed under the HRMS Frontend heading. **All fixed 2026-09-11; 0 unresolved references.** | A whole app undiscoverable; a shipped feature documented under fictional paths | | |
| **DEBT-017** | Docs | `ARCHITECTURE.md` named `EmployeePayRunServiceImpl.saveEmployeePayRun()` as the pay-run entry point. No such method exists; it is `generateEmployeePayRuns()` (`EmployeePayRunServiceImpl.java:1003`). **Fixed 2026-09-11.** | Misdirected anyone tracing the core flow | | |

## Section 4 — Added 2026-09-11 (code audit, not previously tracked)

These were found while building the code maps and are not in the original tracker.

| ID | Component | Finding | Impact | Priority | Phase |
|---|---|---|---|---|---|
| **DEBT-018** | Payroll Backend | **Zero `@Index` declarations across all 99 entities.** Only 7 `@UniqueConstraint`. With `ddl-auto=update`, the database has almost no secondary indexes beyond PKs and InnoDB's automatic FK indexes. | 17 entities hold `organizationId` as a plain `String` (not a FK) and are therefore unindexed — including `organizationUserRoleMapping`, hit on **every request** by `OrganizationRoleInterceptor`. `findByPayrunId` on `employee_payruns` is a full table scan on the hottest path | | |
| **DEBT-019** | Payroll Backend | Only **1** repository of 78 uses `JOIN FETCH`, with 61 entities on `FetchType.LAZY` | Systemic N+1 in pay-run generation and employee listing | | |
| **DEBT-020** | Payroll Backend | `AuthzServiceImpl` caches permissions in an in-process `ConcurrentHashMap` (`AuthzServiceImpl.java:28`) | **Blocks horizontal scaling.** Two replicas diverge after any role change | | |
| **DEBT-021** | Payroll Backend | Two `@Scheduled` cron jobs with no distributed lock (`ITDeclarationAutoLockScheduler`, `POIReminderScheduler`) | **Blocks horizontal scaling.** Duplicate lock emails and POI reminders per replica | | |
| **DEBT-022** | Payroll Backend | 57 of 144 repository query methods are not org-scoped (`findByEarningId`, `findByPayrunId`, `findByBenefitId`, …) | Cross-tenant read possible by guessing a 10-digit business ID | | |
| **DEBT-023** | Payroll Backend | `/api/test/**` — three controllers exposing scheduler triggers and bulk email sends, with no separate guard | Anyone authenticated can fire production emails | | |
| **DEBT-024** | Payroll Backend | `ProfessionalTaxServiceImpl` defines `resetToDefaultSlabs` and `getAllProfessionalTaxes` **twice each** (L624/L677, L668/L788) | Dead code; unclear which body is live | | |
| **DEBT-025** | Payroll Backend | `/api/proof-of-investment` is mapped by **two** controllers with different semantics — `claimsanddeclarations/ProofOfInvestmentController` (org settings) and `taxCalculator/POIController` (documents) | Ambiguous routing; three parallel POI implementations exist | | |
| **DEBT-026** | Payroll FE | ~12,000 duplicated lines across six admin/employee file pairs (`adminProofEdit.js` ↔ `userProofEdit.js`, both 2,282 lines; `adminInvestmentDeclaration.js` ↔ `userInvestmentDeclaration.js`; others) | Every declaration fix must be applied twice | | |
| **DEBT-027** | Payroll FE | CTC calculation engine (`buildCalcTypePatch`, `calculateSalaryDetails`, `getCtcReconciliation`, `calculateStatutoryBenefits`) copy-pasted across `salaryDetails.js`, `editSalaryDetails.js`, `editReviseSalary.js` | Salary split rules can silently diverge between create and edit | | |
| **DEBT-028** | Payroll FE | Dashboard tiles (`stats`, `recentPayRuns`, `upcomingPayments` in `dashboardPage/index.js:15-36`) are hard-coded demo values. `/api/dashboard/summary` exists but is never called | Users see fabricated EPF/ESI/TDS figures | | |
| **DEBT-029** | Payroll FE | `/salary-components/add/earning` renders `addNewEarning.js`, whose `handleSubmit` posts to `/organisation-profile/save` — **an endpoint that does not exist** in the backend | That screen cannot work. The functional path is `addNewCustomEarning.js` | | |
| **DEBT-030** | Payroll FE | Route aliases pointing at the same component: `/mark-leaves-taken` = `/mark-leave-taken`; `/markleaveaddemploy` = `/mark-leave-add-employee`; `tax-details` = `taxes` | Ambiguous navigation and analytics | | |
| **DEBT-031** | Payroll FE | Dead files in the tree: `dashboardcopy.js` (2,738), `addEmpOld.js` (2,441), `header-ols.js` (441), `editEmployee.js` (0 bytes), `mockReimbursements.js`. `mockAdminReimbursements.js` is still **imported** by a live screen | Confusion; mock data on a production screen | | |
| **DEBT-032** | Both backends | `HRMS_Backend` has `mvnw` but no `.mvn/wrapper/` payload, so `./mvnw` cannot run | Build is not self-contained; requires system Maven | | |
| **DEBT-033** | Payroll Backend | HRMS↔Payroll integration authenticated by `X-API-KEY: md5("12345AB")`, a shared secret committed in plaintext | Trivially forgeable | | |

---

## Appendix — IDs removed from the original tracker

| ID | Reason |
|---|---|
| BUG-008 | Placeholder — "Reserved … during **AuditBot** UAT". AuditBot does not exist in any repo |
| BUG-009 | Placeholder — reserved for a future `org_id` migration conflict |
| BUG-010 | Placeholder — "Reserved … during **K6** load test". No load-test tooling exists |
| BUG-011 | Placeholder — "Reserved … during **ICICI/HDFC/Axis** UAT". No bank integration exists; payouts are a CSV export |
| BUG-012 | Placeholder — "Reserved … during **VAPT**". Not performed |
| BUG-013 | Literally `BUG-013+ \| Reserved \| TBD` |

If any of these become real findings, raise them with evidence under a new ID.
