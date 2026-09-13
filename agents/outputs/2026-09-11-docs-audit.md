# Docs Audit — 2026-09-11

Scope: all 9 markdown files + 1 directory in `docs/`, audited against the four
repos at their current HEADs (`d984c64`, `c72116c`, `39b37d6`, `053ca62`).

No document has been changed, moved, or deleted. This is the report for task 3.

---

## 1. Inventory

| Doc | Lines | Dated | Disposition (agreed) |
|---|---|---|---|
| `ARCHITECTURE.md` | 306 | 2026-08-12 | **Keep** + merge PROJECT_OVERVIEW in |
| `DB_SCHEMA.md` | 799 | 2026-08-12 | **Keep** |
| `FEATURE_MAP.md` | 355 | 2026-08-12 | **Keep** |
| `PROJECT_OVERVIEW.md` | 217 | 2026-08-12 | Merge → archive |
| `MASTER_BUG_AND_PROGRESS_TRACKER.md` | 242 | — | → `GAP_INVENTORY.md`, archive original |
| `FEATURE_DEVELOPMENT_TRACKER.md` | 169 | — | → `features/TEMPLATE.md`, archive |
| `PRODUCTION_READINESS_SPEC.md` | 672 | — | Archive whole (D3) |
| `CODE_MAP_PAYROLL_BACKEND.md` | 821 | 2026-09-10 | Archive |
| `CODE_MAP_PAYROLL_FRONTEND.md` | 734 | 2026-09-11 | Archive |
| `schema/` | empty dir | — | Delete (D5) |

---

## 2. Overlap

Vocabulary overlap — distinct 4+ character tokens shared, as a percentage of the
smaller document. An approximation of redundancy, not a diff.

| Pair | Shared tokens | Overlap |
|---|---|---|
| `PROJECT_OVERVIEW` ↔ `ARCHITECTURE` | 225 | **58%** |
| `MASTER_BUG_TRACKER` ↔ `PRODUCTION_READINESS_SPEC` | 291 | **45%** |
| `PROJECT_OVERVIEW` ↔ `FEATURE_MAP` | 208 | 40% |
| `ARCHITECTURE` ↔ `FEATURE_MAP` | 152 | 39% |
| `FEATURE_DEVELOPMENT_TRACKER` ↔ `FEATURE_MAP` | 97 | 35% |
| `CODE_MAP_PAYROLL_BACKEND` ↔ `FEATURE_MAP` | 253 | 33% |

The 58% and 45% pairs justify the two merges already agreed. Nothing else is
redundant enough to warrant further consolidation.

---

## 3. Claims contradicting code

Each verified against the repos. `docs` line numbers are cited; code evidence follows.

| # | Doc claim | Evidence it is wrong | Severity |
|---|---|---|---|
| **X1** | `ARCHITECTURE.md:201` — cross-service flow is driven by `EmployeePayRunServiceImpl.saveEmployeePayRun()` | **Method does not exist.** `grep -rn "saveEmployeePayRun" Payroll-Bend-SBoot/src/main/java` → 0 hits. The real entry point is `generateEmployeePayRuns()` (`EmployeePayRunServiceImpl.java:1003`) | **High** — names a non-existent method in the platform's most important flow |
| **X2** | `ARCHITECTURE.md:280` — `dashboardPage/index.js` is a "Massive 271KB file — most UI lives here" | **Wrong file.** `index.js` = 42,277 bytes / 884 lines. `dashboardcopy.js` = 270,905 bytes / 2,738 lines — and it is **not imported anywhere** (dead code) | **High** — misdirects anyone acting on it |
| **X3** | `PROJECT_OVERVIEW.md` "Code Health" — same 271KB claim for `index.js` | Same as X2 | **High** |
| **X4** | `MASTER_BUG:30` (BUG-006) and `:51` (DEBT-001) — `dashboardPage/index.js` is ">270KB monolithic file" causing dashboard lag | Same as X2. The 271KB file is dead and never loaded, so it cannot cause runtime lag | **High** — a P2 bug and a High debt item both rest on a false premise |
| **X5** | `MASTER_BUG:27` (BUG-003) — LOP half-days truncated because Payroll maps `totalLeaves` to `Integer`; status `🔴 Open` | **Already fixed.** `IntegrateWithHrmsServiceImpl.java:33` → `public Map<String, Double> fetchLeaves(...)`. Also superseded: payrun LOP now reads `employee_leave_balance_consumption`, not HRMS | **Medium** — open P0 that is no longer true |
| **X6** | `ARCHITECTURE.md:212` — HRMS returns `[{email, totalLeaves}]` | Return type is `Map<String, Double>` (`IntegrateWithHrmsServiceImpl.java:33`) | Low |
| **X7** | `ARCHITECTURE.md:97-158` — Payroll controller tree lists 11 packages | **6 missing:** `employeeTDS/`, `employeeitdeclaration/`, `employeereimbursement/`, `leave/`, `publicapi/`, `test/` | Medium |
| **X8** | `ARCHITECTURE.md:110-125` — Payroll entity tree | **4 missing:** `EmployeeITDeclaration/`, `employeeTDS/`, `employeereimbursement/`, `leave/` | Medium |
| **X9** | `ARCHITECTURE.md:297` — Payroll FE `shared/services/` = "Auth service" | Four services exist: `authService.js`, `invitationService.js`, `leaveStore.js`, `reimbursementService.js` | Low |
| **X10** | `ARCHITECTURE.md:306` — docs/ contains "ARCHITECTURE, DB_SCHEMA, FEATURE_MAP, PROGRESS_TRACKER" | No file named `PROGRESS_TRACKER` exists; it is `MASTER_BUG_AND_PROGRESS_TRACKER.md` | Low |
| **X11** | `FEATURE_DEVELOPMENT_TRACKER.md:27` — receipts upload to **S3** | Code uses Cloudinary: `CloudinaryServiceImpl.uploadReimbursementAttachment()` (`CloudinaryServiceImpl.java:134`). Also repeated in `PRODUCTION_READINESS_SPEC.md` | Medium — **self-resolving**, both files are being archived |

Claims **verified correct** (no action): HRMS/Payroll ports (1010, 3032/3029), auth
split (JWT vs Keycloak), DB names, the `timeshhet/` and `leaveAndAttedance/` typos,
`exception/` + `exceptions/` both existing, `IntegrateWithPayroll` controller name.

---

## 4. Invented scope — not present in code

| Item | Where | Status in code |
|---|---|---|
| **AuditBot** (AI anomaly detection, HITL gates) | `MASTER_BUG:39,208,220`, `PRODUCTION_READINESS_SPEC` | Does not exist. No agent, service, or table |
| **K6 load testing** (1,000 concurrent users) | `MASTER_BUG:41,210,222`, `PRS` | No load-test tooling in any repo |
| **VAPT / third-party pen test** | `MASTER_BUG:43,222`, `PRS` | Not performed |
| **Bank payout API** (ICICI/HDFC/Axis) | `MASTER_BUG:42`, `PRS` | No bank integration. Payouts are a CSV export only |
| **PostgreSQL** | `PRS` | Both backends are MySQL today |

---

## 5. ⚠️ Correction to decision D1 — I recommended wrongly

Your original spec said **keep BUG-001–007**. I recommended keeping all 13, and you
accepted that. **Your original instinct was right and my recommendation was wrong.**

I based it on an ID count without reading the rows. Having now read them:

`MASTER_BUG_AND_PROGRESS_TRACKER.md:35` is a heading that reads:

> ### Reserved — Future Discovery Placeholders

Everything under it is a placeholder, not a finding:

| ID | Description | Root cause |
|---|---|---|
| BUG-008 | "Reserved — potential … discovered during **AuditBot** UAT" | `TBD` |
| BUG-009 | "Reserved — potential data migration conflict …" | `TBD` |
| BUG-010 | "Reserved — bottleneck … during **K6** load test" | `TBD` |
| BUG-011 | "Reserved — integration defect … during **ICICI/HDFC/Axis** UAT" | `TBD` |
| BUG-012 | "Reserved — vulnerability … during **VAPT**" | `TBD` |
| BUG-013 | Literally `BUG-013+ \| ⚪ Reserved \| TBD` (`:242`) | `TBD` |

These are precisely the "reserved/invented rows" your spec told me to strip — they
describe defects in work that has not happened, for tooling that does not exist.

**Recommendation: revert to your original spec — keep BUG-001–007 only.**
All 14 DEBT rows are real, verified findings and should all carry over.

That gives `GAP_INVENTORY.md` **21 findings**: BUG-001–007 + DEBT-001–014.

---

## 6. Staleness risk

| Doc | Risk | Evidence |
|---|---|---|
| `DB_SCHEMA.md` | **High** | Documents **76** tables; actual is **117** (86 Payroll + 31 HRMS). The three newest tables — `employee_leave_allocation`, `employee_leave_balance_consumption`, `employee_deduction` — have **zero** mentions |
| `FEATURE_MAP.md` | **High** | Covers only **3 of 4 apps** — there is no Payroll Frontend section (headings at `:8`, `:166`, `:293`). Zero mentions of Leave Allocation or Salary Deduction |
| `ARCHITECTURE.md` | **Medium** | Package trees predate 6 controller and 4 entity packages (X7, X8) |
| `MASTER_BUG_TRACKER` | **Medium** | BUG-003 closed in code but marked Open (X5); BUG-006/DEBT-001 rest on a false premise (X4) |
| `CODE_MAP_*` | **Low** | Generated 2026-09-10/11 against current HEADs |

Root cause: all three surviving docs are dated **2026-08-12**, which predates the
Leave Allocation and Salary Deduction modules (merged 2026-08-28 → 2026-09-09).

---

## 7. Proposed task-4 edits (per D4: factual errors only)

| Doc | Edit |
|---|---|
| `ARCHITECTURE.md` | Fix X1 (method name), X2 (dashboard file), X6 (return type), X7/X8 (add missing packages), X9 (services list), X10 (docs list). Merge PROJECT_OVERVIEW content. Update the Code Health table to correct X3 |
| `DB_SCHEMA.md` | **No factual errors found.** Gap flagged as a finding, not fixed (documents 76 of 117 tables) |
| `FEATURE_MAP.md` | **No factual errors found.** Missing Payroll Frontend section flagged as a finding, not authored |
| `GAP_INVENTORY.md` (new) | BUG-001–007 + DEBT-001–014, with X4 and X5 corrections applied to the affected rows |

Two new findings to add to `GAP_INVENTORY.md`, both discovered here:

- **GAP-NEW-1** — `DB_SCHEMA.md` documents 76 of 117 tables (41 undocumented).
- **GAP-NEW-2** — `FEATURE_MAP.md` has no Payroll Frontend coverage (1 of 4 apps missing).

---

## 8. Decisions taken

- §5 approved — reverted to BUG-001–007. All 14 DEBT rows kept.
- §7 approved, **widened by founder**: `DB_SCHEMA.md` and `FEATURE_MAP.md` gaps were to be
  filled, not just flagged.

---

## 9. Post-apply verification (added after task 4)

### 9.1 `DB_SCHEMA.md` — correction to my own claim

I first reported "133/133 tables, zero gaps". **That was wrong.** A founder spot-check showed
`employee_leave_allocation` with zero mentions. Two extraction bugs on my side:

| Bug | Effect |
|---|---|
| Only the `@Table(` line itself was scanned, so **multi-line** `@Table(\n name = "...", uniqueConstraints = ...)` returned no name | 6 tables documented under their **class name** instead of the real table name |
| Commented-out `//@Entity` / `//@Table` were not stripped when counting | 2 disabled entities counted as live tables |

Fixed: `EmployeeLeaveAllocation→employee_leave_allocation`,
`EmployeeLeaveBalanceConsumption→employee_leave_balance_consumption`,
`EmployeeProofOfInvestment→employee_proof_of_investment`, `NewTaxCalculation→new_tax_calculation`,
`OldTaxCalculation→old_tax_calculation`, `OldTaxCalculationRevision→old_tax_calculation_revision`.
`EmployeeTaxCalculationResult` and `EmployeeTaxRecalculation` relabelled *DISABLED, not a live table*.

**Final state (multi-line-aware, comment-stripped scan):** 131 real tables (95 Payroll + 39 HRMS,
deduped) · 131 documented · 0 missing · 3 deliberately-labelled non-table entries
(`ResidentialAddress` @Embeddable + the 2 disabled). Derived counts corrected in
`ARCHITECTURE.md`, `GAP_INVENTORY.md` DEBT-015, root `CLAUDE.md`, `agents/active-work.md`.

**Caveat:** derived from JPA source, not from the live databases. `ddl-auto=update` never drops,
so the real DBs may hold orphaned tables/columns from deleted entities. Confirm with
`information_schema.columns` against each DB when access is available.

### 9.2 `FEATURE_MAP.md` — full path verification against live branches

Every backtick path reference resolved against `main`/`main`/`taxation`/`employee` (all at
remote tip). **383 references checked, 0 missing.** Genuine errors found and fixed:

| Line | Was | Now |
|---|---|---|
| §2 HRMS | `repository/EmployeeRepo.java` | `repository/EmployeeRepository.java` |
| §12 Payroll | `config/KeycloakAdminClientConfig.java` | `config/KeycloakAdminConfig.java`, `config/KeycloakClientProvider.java` |
| §12 Payroll | `service/keycloak/KeycloakService(.Impl).java` | `KeycloakUserService(.Impl).java` |
| §13 Payroll | `pages/authPages/setupOrganization/` (does not exist) | `pages/mainPages/organizationRegister/` |
| §13 Payroll | route `/settings` | `/all-settings` |
| **#24 FEAT-002** | 5 backend paths + 1 frontend path + route `/admin/ad-hoc-deductions` — **none exist** (planned design, implemented under different names) | Rewritten as *Superseded — see #27*, naming the real `SalaryDeduction*` files |
| Structure | Payroll Backend items 23–27 sat under the `## HRMS Frontend` heading | Moved under `## Payroll Backend` |

Verified correct (no change): Keycloak client `hrms-payroll-backend`; every `.jsx` cited in the
HRMS sections; all `leaveAndAttendance/` sub-paths.
