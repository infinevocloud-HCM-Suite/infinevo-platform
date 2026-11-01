# Feature: Statutory lines follow the "in salary structure" switches

| Field | Value |
|---|---|
| **Feature ID** | `W-76.1` · `PAY-SALARY-SETUP` |
| **Owner** | claude |
| **Apps touched** | none |
| **Status** | **Dropped** 2026-11-01. The rule is now applied on the screen, in `W-76.2` |

## Why dropped

The first build removed employer lines from the saved salary in `StatutoryLineDeriver`. The merge review found that the pay run pays **only the employer lines it finds on the saved salary**:

- `code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/payrun/StatutoryLineContributor.java:139-229`

Every structure switch defaults to off:

- `code/backend/migration/src/main/resources/db/migration/payroll/V062__epf_setting.sql:22,24`
- `V063__esi_setting.sql:14-15`
- `V153__epf_edli_admin_split.sql:11-14`

So hiding a line from the structure would also have stopped employer PF, EPS, EDLI, admin and ESI being paid. The backend change was reverted before merge. Every line stays on the saved salary, and the screen hides what the company keeps out of the structure (`W-76.2`).

## Found, not fixed here

An employee not in EPS (or over the EPS age) still gets a ₹0 `EPS_EMPLOYER` line that carries the EPS rate:

- `code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/lines/StatutoryLineDeriver.java:76-86`

The pay run recomputes EPS from that rate without checking eligibility, so it splits employer PF into EPS anyway. The employer total is right; the EPS / EPF split is wrong. Fixed in `W-76.3`:

- `StatutoryLineContributor.java:168-189`
