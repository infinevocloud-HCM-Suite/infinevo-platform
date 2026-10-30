# Feature: Salary preview and balancing component

| Field | Value |
|---|---|
| **Feature ID** | `W-75.1` · `PAY-SALARY-SETUP` (part 1 of 2; part 2 is `W-75.2`, the screen) |
| **Owner** | claude |
| **Apps touched** | `code/backend/payroll` |
| **Related gaps** | `DEBT-027` (`legacy/docs/GAP_INVENTORY.md:77`); absorbs `D-132` (statutory profile length checks), founder 2026-10-30 |
| **Status** | **Ready to merge** — `dev-claude-w75` |
| **Blocked by** | nothing — `W-26.2`, `W-31.3` are on `main` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | A salary request with one earning marked *balancing* is previewed or saved with that earning set so the in-CTC total equals the CTC exactly. **Plus** the `D-132` length checks on the statutory profile save — a second, small behaviour folded in by the founder (2026-10-30) | 1 + `D-132` |
| Frontend area | none (`W-75.2`) | 1 |

---

## 1. Problem

Creating an employee's salary today makes the officer do the arithmetic the server already knows:

| Today | Evidence |
|---|---|
| Every in-CTC amount must add up to the CTC to the paisa, or the save is refused with "Difference: X" | `code/backend/payroll/src/main/java/com/infinevo/payroll/salary/SalarySplitCalculator.java:147-179` |
| Employer EPF, EPS, EDLI, admin and ESI join that sum when the tenant ticks "include in CTC", so the officer must also work those out by hand | `EmployeeSalaryServiceImpl.java:394-405`; `statutory/lines/StatutoryLineDeriver.java:90,109,123,137,170` |
| There is no way to see the split, the statutory lines or the take-home before saving | `EmployeeSalaryController.java:46-92` — create, revise, read, update, cancel; no preview |

Legacy computed the remainder for the officer: Fixed Allowance = CTC − Basic − HRA − Conveyance − employer EPF − employer ESI (`legacy/Payroll-Fend-react/src/pages/mainPages/employee/salaryDetails.js:392-396`), and showed EPF and ESI live (`salaryDetails.js:241-290`). It did both in the browser, copied across three screens (`DEBT-027`). This ticket keeps the rule on the server, once.

## 2. Scope

**In scope**

- An optional `balancing` flag on an earning in `SalaryVersionRequest`; the server computes that earning's amount
- `POST …/salary/preview`: the same calculation as create, nothing written, with the statutory lines and a take-home estimate
- Preview accepts an unsaved statutory profile, so the screen can preview ticks before they are saved
- **Statutory profile length checks (was `D-132`).** Today an over-long UAN, PF or ESI number reaches the database and fails as a 500: the columns are `VARCHAR(16)` / `VARCHAR(32)` (`code/backend/migration/src/main/resources/db/migration/payroll/V050__employee_statutory_profile.sql:15-17`), `upsert` copies the strings unchecked (`EmployeeStatutoryProfileServiceImpl.java:46-71`), and the controller has no handler for a constraint failure (`EmployeeStatutoryProfileController.java:52-76` — not-found, validation, conflict, unreadable body only)

**Out of scope**

- The screen (`W-75.2`)
- Digit-format rules (UAN 12 digits, ESI 10 digits) on the server — `W-75.2` checks them in the form; the server checks length only, so a stored legacy value in another format still saves
- TDS in the take-home estimate (needs the tax computation, `W-33`)
- Persisting which earning was the balancing one — it is stored as a plain `FLAT` amount

## 3. Flow

```
[Payroll officer] --> [W-75.2 drawer] --> POST /salary/preview  --> EmployeeSalaryServiceImpl.preview --> (no write)
                                       --> POST /salary | /revisions (balancing flag) --> buildAndSaveVersion --> payroll.ctc_structure …
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| DTO | `salary/SalaryComponentItemRequest.java` | add `Boolean balancing` (null = false) |
| DTO | `salary/SalaryPreviewRequest.java` (new) | `SalaryVersionRequest` fields plus optional `StatutoryProfileRequest statutoryProfile` |
| DTO | `salary/SalaryPreviewResponse.java` (new) | the `SalaryVersionResponse` shape (earnings, benefits, reimbursements, `statutory`) plus `grossMonthly`, `employeeDeductionsMonthly` (`EPF_EMPLOYEE`, `ESI_EMPLOYEE`, `PT`), `employerContributionsMonthly`, `takeHomeMonthlyEstimate`, `balancingComponentId` |
| Service | `salary/SalarySplitCalculator.java` | `resolveWithBalancing(...)` — see rules below |
| ServiceImpl | `salary/EmployeeSalaryServiceImpl.java` | `buildAndSaveVersion` (`:346`) resolves the balancing earning before `validateCtcBalance`; new `preview(employeeId, request)` runs the same path without saving |
| Controller | `salary/EmployeeSalaryController.java` | `POST /preview` |
| ServiceImpl | `salary/EmployeeStatutoryProfileServiceImpl.java` | `upsert` (`:46`): trim each number; blank → null; UAN > 16, PF account number > 32, ESI number > 32 → `SalaryValidationException` on that field ("UAN must be at most 16 characters", …) before `save` |
| Controller | `salary/EmployeeStatutoryProfileController.java` | `SalaryValidationException` already maps to `400` (`:64`); add `DataIntegrityViolationException` → `400 VALIDATION_FAILED` so no constraint failure surfaces as 500 |

**Balancing rules**

1. At most one earning has `balancing = true`; more than one → `400` on `earnings[i].balancing`.
2. It must be an earning that is `FLAT`, included in CTC, not Basic (`SalarySplitCalculator.java:359` `isBasic`), not FBP, not variable. Otherwise `400`.
3. Its sent `value` is ignored. Every other component resolves as today (`SalarySplitCalculator.java:307-333`); `HRA % of Basic` and `% of CTC` are unaffected by the balancing amount.
4. Monthly balancing amount `B` = monthly CTC − Σ other in-CTC earnings, benefits, reimbursements − Σ in-CTC employer statutory lines. Employer EPF/EPS/EDLI/admin depend on Basic only (`StatutoryLineDeriver.java:38-137`), so they are fixed before `B`.
5. **ESI depends on gross, which contains `B`.** When ESI applies and employer ESI is in CTC: solve `B = (M − others − PFer − r·E₀) / (1 + r)` with `r` = employer ESI rate, `E₀` = gross without `B`. If the resulting gross exceeds the ESI ceiling, ESI does not apply: recompute `B` without it. Use `BigDecimal`, scale 4, `HALF_UP` as the calculator does.
6. After rounding, put any paisa left over into `B` so `validateCtcBalance` passes exactly.
7. `B < 0` → `400` on `annualCtc`: "Components and employer contributions exceed the CTC by X".
8. Requests without a balancing earning behave exactly as today.

**Preview**

- Runs the same resolve and `StatutoryLineDeriver.derive` (`EmployeeSalaryServiceImpl.java:394-395`) using `statutoryProfile` from the request when present, otherwise the stored profile.
- Takes no lock and writes no row and no audit entry. It refuses what create refuses, except the "structure already exists" and duplicate-date conflicts (`:186-188`, `:205-207`), which only a save can hit.
- PT estimate: the employee's work-location state and gender through `ProfessionalTaxService.resolve` (`statutory/pt/ProfessionalTaxService.java:48`), as the pay run does (`statutory/payrun/StatutoryLineContributor.java:354-361`); omitted, with `ptEstimated=false`, when the employee has no work location.
- `takeHomeMonthlyEstimate` = gross − employee EPF − employee ESI − PT. Labelled an estimate: no TDS, no loss of pay.

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| POST | `/api/v1/payroll/employees/{employeeId}/salary/preview` | `SalaryPreviewRequest` | `200 SalaryPreviewResponse`; `400` field errors | `payroll.salary.manage` |
| POST | `…/salary`, `…/salary/revisions`; PUT `…/salary/versions/{id}` | existing, earnings may carry `balancing` | unchanged | unchanged |

## 5. Frontend changes

None — `W-75.2`.

## 6. Database changes

None. Creates no table, no column, no Flyway script. The balancing amount is stored in the existing employee earning row as a `FLAT` value. Money stays `BigDecimal`/`Money`; no floating point.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `salary/SalarySplitCalculatorBalancingTest.java` | ₹6,00,000 CTC, Basic 50% CTC, HRA 50% Basic, employer EPF in CTC, no ceiling → Special 9,500.00; ceiling on → 10,700.00; not in CTC → 12,500.00; ESI in CTC at gross under and just over ₹21,000; leftover paisa lands in `B`; two balancing rows → error; Basic as balancing → error; negative `B` → error |
| Unit | `salary/EmployeeSalaryPreviewTest.java` | unsaved statutory profile overrides the stored one; no repository `save` called; PT omitted without a work location |
| Unit | `salary/EmployeeStatutoryProfileServiceTest.java` | UAN 16 chars saves, 17 → field error; PF / ESI 32 vs 33; whitespace trimmed; blank → null |
| Integration | `salary/StatutoryProfileLengthIT.java` | `PUT …/statutory-profile` with a 17-character UAN → `400` naming `uan`, never `500` |
| Integration | `salary/SalaryPreviewIT.java` | `POST /preview` 200 for payroll officer, 403 for finance; a create with `balancing` saves a version whose in-CTC total equals CTC; a request without `balancing` is refused as before |

## 8. Verification

```bash
cd code/backend
./mvnw -pl payroll -am test -Dtest='SalarySplitCalculatorBalancingTest,EmployeeSalaryPreviewTest,EmployeeStatutoryProfileServiceTest'
./mvnw -pl payroll -am verify -Dit.test='SalaryPreviewIT,StatutoryProfileLengthIT'
```

| Check | Expected |
|---|---|
| Unit tests | `Tests run: …, Failures: 0, Errors: 0` |
| `SalaryPreviewIT` | passes (needs Docker) |
| `node .claude/scripts/check-done.mjs W-75.1` | gates green |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| ESI circularity gives a gross that flips across the ₹21,000 ceiling | medium | rule 5: solve with ESI, check the ceiling, recompute without; tests at both sides of the ceiling |
| Preview and save drift apart | low | one code path; preview is `buildAndSaveVersion` without the writes |

## 10. Rollback

Revert the commit. No schema change; saved versions are ordinary `FLAT` amounts and stay valid.
