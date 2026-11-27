# Feature: AI helper tells the payroll officer one employee's TDS (read-only)

| Field | Value |
|---|---|
| **Feature ID** | `W-114.27` · `PAYROLL-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/payroll` (`ai`) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.7` and `W-114.14` on `main` |

Founder, 2026-11-27: "yes, same as the screen". A payroll officer asks "what is Asha's TDS so far
this year?" and gets the figures the employee's TDS card shows, for callers who may open that card.

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `payroll` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | An officer gets one employee's TDS figures; a caller without the screen's action gets nothing | 1 |
| Frontend area | none | 1 |

Size **S**.

## The tool

| Tool | Input | Calls | `requiredAction` |
|---|---|---|---|
| `payroll.employee_tds` | `employee` (employee number, or a full name), `financialYear` (`YYYY-YYYY`; the current one when left out) | `EmployeeQueryService.search` to find the person; `EmployeeTdsService.active`, `yearToDate`, `spread` — what `GET /api/v1/payroll/employees/{id}/tds/{fy}` calls (`tds/EmployeeTdsController.java:85-97`) | `payroll.tax_declaration.read`, the card's action |

**Finding the person.** An exact employee number first; otherwise an exact full name. Several
matches answer with up to 10 names and numbers and no figures, so the helper asks which. None, or a
soft-deleted employee, answers "No employee with that number or name." — a hidden person answers
like an unknown one.

**Returns** — the card's figures, amounts as two-decimal text, `"currency": "INR"` once: name,
employee number, financial year, regime, annual gross, annual taxable income, annual tax, deducted
so far, remaining, months left, monthly share, the month it is next deducted in, effective from, the
record's source, and whether a suggested change waits (with its annual tax). **Never sent:** any id,
PAN, the record's note or the suggestion's note (free text), the declaration's lines.

No record for that year: "There is no TDS record for that employee and year." with the screen.

`deductedSoFar` is the card's own figure: TDS on computed, approved and paid regular runs plus prior
payroll (`tds/TdsSpread.java`), so the helper and the card agree. (`W-114.14`'s employee tool counts
paid runs only; that is the employee's own payslip view, a different question.)

## What is sent to the model

Another employee's name, number and tax figures, for a caller who can already open that employee's
TDS card. Nothing else about the person. Stored masked like any TOOL row (`W-114.4`).

## Tests

| Type | File | Covers |
|---|---|---|
| Unit | `payroll/src/test/.../ai/EmployeeTdsAiToolTest.java` | The tool needs `payroll.tax_declaration.read`; a number match, a name match, two name matches list both with no figures; unknown and soft-deleted read the same; no UUID, no note text in the result; no record for the year says so; amounts are two-decimal text |

## Verification

```bash
cd code/backend && ./mvnw -B -pl payroll,app -am verify
```

## AI helper

This part is itself an AI helper read tool.

**As built (2026-11-27, Claude).** `payroll/ai/EmployeeTdsAiToolProvider.java`; `EmployeeTdsAiToolTest` 5 pass.

**Review fix (2026-11-27).** The lookup reads every page of the search (100 a page, up to 50 pages, in employee number order), so an exact match past the first 100 rows is found.

## Rollback

Revert the commit. The tool is no longer offered.
