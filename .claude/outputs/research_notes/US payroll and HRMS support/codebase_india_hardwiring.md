# Where the new platform is hard-wired to India (codebase inventory)

Read on 2026-11-24 from the working tree on `main` at `59b0b075`. The tree was clean apart from three untracked files in `.claude/outputs/`; no staged payroll changes were present, so working tree = committed state. `legacy/` ignored.

Path short forms used below:
- `BE/` = `code/backend/`, and inside a module `…/` = `src/main/java/com/infinevo/<module>/`
- `MIG/` = `code/backend/migration/src/main/resources/db/migration/`
- `FE/` = `code/frontend/src/`

"Inferred" marks a conclusion I drew; everything else was seen at the cited line.

## Inventory grouped by how hard it is to change

### Takeaway
Country is stored on the tenant but nothing in the compute reads it; the only code keyed off country is the tenant template. The deep assumptions are the calendar-month pay period, the April–March tax year, and an INR-only money model; the India statutory and tax code is large but sits in its own packages behind one plug-in interface.

### Cited Findings

| Difficulty | Area | What is fixed to India | Evidence | Rough size |
|---|---|---|---|---|
| **Hard (structural)** | Pay period = calendar month | `period CHAR(7)` `YYYY-MM` with a CHECK, one non-cancelled run per tenant and period; schedule frequency CHECK allows only `MONTHLY`; `YearMonth` in the contributor context | `MIG/payroll/V055__payrun.sql:7,33-35`; `MIG/payroll/V054__pay_schedule.sql:7`; `BE/payroll/…/payrun/PayRunEmployeeContext.java:33` | 12 migrations carry a `YYYY-MM` period column; `YearMonth` in 37 payroll, 25 core, 1 hrms, 1 worker main files |
| **Hard** | Tax year April–March | `FinancialYear` record hard-codes `Month.APRIL` / `Month.MARCH`; `"%04d-04"` strings in TDS, prior payroll, Form 16 | `BE/payroll/…/taxdeclaration/FinancialYear.java:66,80,91,98`; `BE/payroll/…/tds/TaxLineContributor.java:89` | 60 payroll main files import `FinancialYear`; 5 tables have a `financial_year` column |
| **Hard** | Currency | `Money` has no currency; no currency column on tenant or any amount table; frontend formats `en-IN` with `₹` | `BE/shared/…/money/Money.java:26-46`; `FE/shared/ui/format.js:14,69` | `₹` in 17 non-test frontend files (46 hits) |
| **Hard** | Data residency | Bicep `location` allows only `centralindia`; one `DB_URL` | `infra/azure/main.bicep:11-19`; `BE/app/src/main/resources/application.yml:34` | 1 deployment |
| **Medium (large but isolated)** | India statutory lines (EPF, EPS, EDLI, ESI, PT) | One unconditional `@Component @Order(400)` contributor | `BE/payroll/…/statutory/payrun/StatutoryLineContributor.java:54-56` | `statutory/` 55 main files, 22 test files |
| **Medium** | India income tax (old/new regime, declarations, proofs, Form 16, TDS, deductor) | Packages `taxcalc`, `taxdeclaration`, `proof`, `form16`, `tds`, `taxdeductor`, `fbp`, `priorpayroll` | package listing; `BE/payroll/…/tds/TaxLineContributor.java:36-38` | 70+92+59+24+21+10+20+21 = 317 main files of payroll's 580 |
| **Medium** | Reference tax data | 11 India tax rule tables, PT state and slab tables, none with a country column | `MIG/reference/V004__reference_tax_masters.sql:19-203`; `MIG/reference/V064__pt_state_and_slab.sql:5,14` | 17 reference migrations |
| **Medium** | Employee identity fields | `aadhaar_number`, `pan_number` columns and regex validators; UAN, PF number, ESI number, five India eligibility flags | `MIG/core/V017__employee_identification.sql:9-10`; `MIG/payroll/V050__employee_statutory_profile.sql:8-17` | see "Employee data" |
| **Medium** | Settings screens, setup checklist | Static lists that include EPF, ESI, PT, TDS, Form 16 with no country filter | `BE/core/…/setup/SetupStepCatalogue.java:32-39`; `FE/payroll/settings/SettingsLayout.jsx:20-57` | 8 settings menu items, 8 checklist steps |
| **Easy (already parameterised or small)** | Timezone | Per tenant, read by `TenantClock`; `Asia/Kolkata` only as fallback, plus one hard-coded use | `BE/core/…/tenant/TenantClock.java:21,40-55`; `BE/payroll/…/deduction/EmployeeDeductionRules.java:28-29` | 4 Java constants |
| **Easy** | Leave year start | Per tenant `leave_year_start_month`, default 4 | `MIG/core/V033__tenant_locale_columns.sql:8`; `BE/core/…/leave/LeaveDateUtils.java:20-40` | — |
| **Easy** | Country template | Table keyed by `country_code`; only `IN` rows exist | `MIG/reference/V162__country_template_in.sql:25-36,44` | 5 sections, 5 contributor classes |
| **Easy** | Working week | Stored per tenant on the pay schedule (`working_days SMALLINT[]`) | `MIG/payroll/V054__pay_schedule.sql:8` | 3 places still hard-code Sat–Sun |
| **Easy** | Address shape | Free-text `state`, `state_code`, `zip_code VARCHAR(16)`; no India-only constraint in the table | `MIG/core/V016__employee_contact.sql:9-15`; `MIG/core/V013__work_location.sql:9-15` | — |

### Inferences
- With no EPF/ESI settings row, no TDS record and no PT slab for the work location's state, the two India contributors would emit no lines for a US tenant without any code change (inferred from `StatutoryLineContributor.java:167` and `TaxLineContributor.java:74-77`); they would still run and query for every employee.

### Gaps
- I did not read every file in the tax packages; isolation is judged from package layout, interfaces and imports found by search.

## Country on the tenant: where country, timezone, currency and leave-year start are stored and read

### Takeaway
`core.tenant` has `country_code`, `timezone` and `leave_year_start_month` (defaults `IN`, `Asia/Kolkata`, 4). There is no currency on the tenant. `reference.country` and `reference.currency` exist and already hold US and USD. The only thing keyed off country today is which template is applied at tenant creation.

### Cited Findings
- The three columns were added with India defaults — `MIG/core/V033__tenant_locale_columns.sql:6-8`.
- The provisioning function also falls back to `'IN'`, `'Asia/Kolkata'`, `4` — `MIG/core/V116__lop_policy.sql:93-95`.
- Java defaults: `DEFAULT_COUNTRY_CODE = "IN"`, `DEFAULT_TIMEZONE = "Asia/Kolkata"` — `BE/core/…/tenant/TenantServiceImpl.java:47-48`.
- Country is validated only as two characters, not against `reference.country` — `BE/core/…/tenant/TenantServiceImpl.java:128-136`.
- Country is read to apply the template at creation and on re-apply — `BE/core/…/tenant/TenantServiceImpl.java:218,298-305`.
- A search of all main Java for `countryCode|country_code|"IN"` found country used only in: tenant create/overview, the template package, `WorkLocation` (a stored field), and `ReferenceState` (PT). No pay-run, tax, leave or formatting class reads it.
- `reference.country` (code, alpha3, numeric_code, name, dial_code, default_currency) — `MIG/reference/V003__reference_lookups.sql:14-23`; seeded with IN, US, GB, AE, SG — `:88-93`.
- `reference.currency` (code, numeric_code, symbol, name, decimal_places) — `MIG/reference/V003__reference_lookups.sql:48-56`; seeded INR, USD, EUR, GBP, AED, SGD — `:80-86`.
- `reference.state` is commented "Indian states and union territories, with GST state codes"; `gst_state_code CHAR(2) NOT NULL UNIQUE` — `MIG/reference/V003__reference_lookups.sql:28-39`. It does have a `country_code` column — `:35`.
- `reference.bank` has `ifsc_prefix VARCHAR(10) NOT NULL` and `rbi_code` — `MIG/reference/V003__reference_lookups.sql:62-71`.
- Timezone is read per tenant by `TenantClock.zone()`, falling back to `Asia/Kolkata` — `BE/core/…/tenant/TenantClock.java:21,40-55`. 8 main files use `TenantClock`.
- Leave year start month is read per tenant, fallback 4 — `BE/core/…/leave/LeaveDateUtils.java:20-40`; the year label is `YYYY` for January start, `YYYY-YY` otherwise — `:68-90`.
- W-116 (edit these three fields on Company profile; countries endpoint; lock country after first pay run) is approved, not built — `docs/target-state/features/W-116-company-locale-on-company-profile.md:9`. Its scope explicitly excludes anything that makes a non-India country work — `:62`.
- W-118 "US market support" is logged 2026-11-24, not specced, not assigned, needs W-116 — `docs/trackers/DEV-TRACKER.md:956`.
- The W-116 tracker row names the real work for another country: EPF, ESI, PT, TDS, LWF hard-coded in compute; April–March tax year; INR throughout — `docs/trackers/DEV-TRACKER.md:929`.
- Frontend tenant create defaults `country_code: 'IN'`, `timezone: 'Asia/Kolkata'`, `leave_year_start_month: 4` — `FE/core/admin/TenantCreate.jsx:120-122`.

### Inferences
- No `core/reference` package and no `CountryController` exist yet (the core package listing has none), consistent with W-116 being unbuilt.
- `reference.country.default_currency` is not read by any Java main class (the currency search returned only comments about rupees).

### Gaps
- Not checked: whether any frontend screen reads `reference.currency` or the tenant's country at runtime (search for `countryCode` in frontend was limited to `TenantCreate.jsx` and `WorkLocationForm.jsx`).

## Pay run compute: how lines are assembled, and whether India statutory is conditional

### Takeaway
The pay run loops over every `PayLineContributor` bean in `@Order`; a new source of lines is a new bean and the loop is not edited. The two India contributors (statutory at 400, TDS at 500) are plain `@Component`s with no country condition. This interface is the extension point a US tax contributor could implement.

### Cited Findings
- The port: `List<PayLine> contribute(PayRunEmployeeContext ctx)` and optional `String note(ctx)` — `BE/payroll/…/payrun/PayLineContributor.java:14-25`. Its Javadoc: "a later ticket adds a bean and never edits the loop" — `:6-8`.
- The loop: every contributor runs in order, each seeing the lines before it — `BE/payroll/…/payrun/PayRunComputationServiceImpl.java:337-344`; contributors are injected as a list sorted by `@Order` — `:120-121`.
- Five implementations: Structure `@Order(100)`, LOP 200, PayInput 300, Statutory 400, Tax 500 — `BE/payroll/…/payrun/StructureLineContributor.java:37`, `LopLineContributor.java:42`, `PayInputLineContributor.java:36`, `BE/payroll/…/statutory/payrun/StatutoryLineContributor.java:55`, `BE/payroll/…/tds/TaxLineContributor.java:37`.
- The context handed to a contributor: tenantId, payrunId, `YearMonth period`, periodStart, periodEnd, employee, salary version, working-day basis, LOP rounding, pay inputs, days, pro-rata ids, prior lines, run type, `StatutoryProfileResponse` — `BE/payroll/…/payrun/PayRunEmployeeContext.java:29-45`. It carries no country and no currency.
- `LineSource` is a closed enum: `STRUCTURE, LOP, PAY_INPUT, STATUTORY, TAX` — `BE/payroll/…/payrun/LineSource.java:7-18`.
- `StatutoryLineContributor` is unconditional as a bean; it skips only off-cycle runs — `…/statutory/payrun/StatutoryLineContributor.java:54-56,132-134`.
- PF applies only when the company EPF setting is enabled and the employee is ticked eligible — `…/StatutoryLineContributor.java:167`.
- India constants inside it: wage ceiling default `15000.0000` — `:157`; default employer PF rate `12.0000` — `:83`; line names "Provident fund - employee", "Employee state insurance - employee", "Professional tax" — `:58-80`.
- Whole-rupee rounding rules for PF and ESI — `BE/payroll/…/statutory/payrun/StatutoryLineRounder.java:8-31`.
- `TaxLineContributor` derives the year with `FinancialYear.of(...)`, returns nothing when there is no active TDS record, and sums year-to-date from `"%04d-04"` — `BE/payroll/…/tds/TaxLineContributor.java:73-77,89-94`. Its line code is `"TDS"`, name "Tax deducted at source" — `:40-41`.
- LWF is not computed anywhere: it appears only as an eligibility flag (`is_eligible_for_lwf`) and in comments — `MIG/payroll/V050__employee_statutory_profile.sql:10`; file search for `lwf|labour welfare` returned only profile classes, two dashboard tests and comments. (The tracker row at `DEV-TRACKER.md:929` lists LWF among hard-coded items; the code shows a flag only.)
- The payroll dashboard buckets lines by India codes: `EPF_CODES`, `ESI_CODES`, `PT_CODE` — `BE/payroll/…/dashboard/PayrollDashboardServiceImpl.java:55-59`.
- `StatutoryProfileResponse` (PF/ESI/PT/LWF/EPS flags) is part of the shared contributor context — `PayRunEmployeeContext.java:45`.

### Inferences
- Switching India contributors off by country would need a country (or a per-tenant "statutory scheme") reaching the contributor, since the context has none today; alternatively a condition inside each contributor. This is a description of what is missing, not a design.
- A US contributor would need new `LineSource` handling only if it wants a tag other than `STATUTORY`/`TAX`; the enum is closed (seen), whether a DB CHECK also lists the values was not checked.

### Gaps
- Not read: `PayRunServiceImpl` in full (840+ lines), `StatutoryLineContributor` beyond line 175 (ESI and PT branches), `StatutoryLineDeriver` (salary-setup-time statutory rows).
- Not checked: a CHECK constraint on `employee_payrun_line.source` or `kind`.

## Pay frequency and periods

### Takeaway
Monthly only, by database constraint and by type. The period is a calendar month (`YearMonth` in Java, `CHAR(7)` `YYYY-MM` in SQL) in the pay run, the pay input ledger, period locks, leave consumption, LOP, overtime posting, deductions, reimbursements, TDS and prior payroll.

### Cited Findings
- `frequency VARCHAR(16) NOT NULL DEFAULT 'MONTHLY' CHECK (frequency IN ('MONTHLY'))` — `MIG/payroll/V054__pay_schedule.sql:7`. Entity default `"MONTHLY"` — `BE/payroll/…/schedule/PaySchedule.java:44,56-57`.
- Schedule fields are month-shaped: `pay_day_of_month` (1–28), `input_cutoff_day` (1–28), `pay_day_rule IN ('LAST_DAY_OF_PERIOD','LAST_WORKING_DAY','SPECIFIC_DAY')` — `MIG/payroll/V054__pay_schedule.sql:9-11`. One schedule per tenant — `:20`.
- Period dates are derived as first and last day of the month — `BE/payroll/…/schedule/PayPeriodServiceImpl.java:47-48`; the service takes a `YearMonth` — `BE/payroll/…/schedule/PayPeriodService.java` (`periodFor(YearMonth period)`).
- Pay run: `period CHAR(7)` with month CHECK, plus `period_start`, `period_end`, `cutoff_date`, `pay_date` dates; unique on `(tenant_id, period)` for non-cancelled runs — `MIG/payroll/V055__payrun.sql:7-13,33-35`. `PayRun.getPeriod()` returns `YearMonth.parse(period)` — `BE/payroll/…/payrun/PayRun.java:339-340`. `PayRunServiceImpl.create(YearMonth period)` — `BE/payroll/…/payrun/PayRunServiceImpl.java:273`.
- Run types: `REGULAR`, `OFF_CYCLE` — `BE/payroll/…/payrun/PayRunType.java` (enum).
- Pay input ledger: `period CHAR(7)` with month CHECK, "A YearMonth in Java end to end" — `MIG/core/V031__pay_input.sql:10-11`; period lock likewise — `MIG/core/V032__pay_input_period_lock.sql:9`. `core/payinput` has 12 main files using `YearMonth`, including `YearMonthPeriodConverter.java`.
- Other month-keyed tables: overtime `posted_period CHAR(7)` — `MIG/core/V041__overtime_request.sql:20`; leave consumption — `MIG/core/V132__leave_consumption.sql:12`; leave monthly LOP — `MIG/core/V133__leave_monthly_lop.sql:8`; WFH consumption — `MIG/core/V182__wfh_consumption.sql:12`; migration ledger `pay_period` — `MIG/core/V178__migration_ledger.sql:52`; reimbursement `posted_period` — `MIG/payroll/V098__employee_reimbursement_request.sql:18`; employee deduction `period`, `posted_period` — `MIG/payroll/V101__employee_deduction.sql:13,26`; TDS `effective_from_period` — `MIG/payroll/V102__employee_tds.sql:19`; prior payroll `period` — `MIG/payroll/V140__prior_payroll.sql:47`; scheduled earning `source_ref '…:<yyyy-MM>'` — `MIG/payroll/V161__scheduled_earning.sql:7`.
- LOP policy is per month: `configured_days_per_month`, basis `ACTUAL_DAYS | ORG_DAYS | FIXED_30` — `MIG/core/V116__lop_policy.sql:8-9`.
- Salary is annual CTC divided by 12: `MONTHS_IN_YEAR = 12` — `BE/payroll/…/salary/SalarySplitCalculator.java:25,168`; FBP divides annual by 12 — `BE/payroll/…/fbp/FbpDeclarationServiceImpl.java:152,202,384`; statutory annual = monthly × 12 — `BE/payroll/…/statutory/lines/StatutoryLineDeriver.java:23,59`.
- Variable earning frequency enum: `MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY` — `BE/payroll/…/payrun/EarningFrequency.java` (enum).
- Frontend formats months with `format('YYYY-MM')` in 24 places (count across `FE/`, tests included).
- Timesheets are the one weekly structure: `week_start_date` must be a Monday (`ISODOW = 1`) — `MIG/hrms/V141__timesheet.sql:13-14`.

### Inferences
- A weekly, bi-weekly or semi-monthly run cannot be stored today: the period column's CHECK and the unique index both assume one run per calendar month (seen); every consumer that parses `period` as `YearMonth` would need a different period identity (inferred).

### Gaps
- Not counted precisely: how many service methods take `YearMonth` as a parameter. File counts are given instead.

## Tax year (April–March)

### Takeaway
April–March is fixed in one Java record (`payroll.taxdeclaration.FinancialYear`), mirrored in two frontend helpers, and re-stated as `"-04"` string literals in five places. It is separate from the leave year, which is already per tenant.

### Cited Findings
- `FinancialYear(startYear, endYear)`, label `YYYY-YYYY`; `months()` starts at `Month.APRIL`; `of(LocalDate)` uses `>= APRIL`; `start()` = 1 April; `end()` = 31 March — `BE/payroll/…/taxdeclaration/FinancialYear.java:16-25,63-71,77-99`.
- 60 payroll main files reference `FinancialYear`: taxcalc 21, taxdeclaration 12, proof 10, tds 7, priorpayroll 5, form16 4, dashboard 1 (search count).
- Literal April: `BE/payroll/…/form16/Form16Assembler.java:133` (Q1 = `-04,-05,-06`); `Form16ServiceImpl.java:127`; `BE/payroll/…/priorpayroll/PriorPayrollServiceImpl.java:62,107`; `PriorPayrollTaxQuery.java:73`; `BE/payroll/…/tds/EmployeeTdsServiceImpl.java:165`; `TaxLineContributor.java:89`.
- Columns `financial_year VARCHAR(9)` with `^[0-9]{4}-[0-9]{4}$` CHECK: `MIG/payroll/V102__employee_tds.sql:12`, `V104__tax_computation.sql:7`, `V109__form16_part_a.sql:14`, `V140__prior_payroll.sql:10`, `V222__tax_recalc_failure.sql:12`.
- Setup checklist: prior payroll counts as settled when the first regular run is for April — `BE/payroll/…/setup/PayrollSetupStepConfiguration.java:60-66` (Javadoc).
- Frontend: `month >= 3 ? year : year - 1` — `FE/payroll/tax/financialYear.js:32`; `FE/payroll/dashboard/financialYear.js:10`. Imported by declaration, Form 16, prior payroll, TDS card and dashboard screens (12 import sites listed by search).
- Dashboard resolves its year with `FinancialYear.containing(today)` — `BE/payroll/…/dashboard/PayrollDashboardServiceImpl.java:82,117-125`.
- Leave year is not tied to this: per-tenant start month — `BE/core/…/leave/LeaveDateUtils.java:20-59`; `leave_year VARCHAR(9)` — `MIG/core/V129__leave_allocation.sql:9`.
- Reference tax rules are published per financial year by migration (FY 2026-27) — `MIG/reference/V105__fy_2026_27_reference_rules.sql`, `V106…`, `V147__fy_2026_27_tax_rules.sql` (file names).

### Inferences
- `FinancialYear` lives in the `taxdeclaration` package but is used by the pay run contributor and the dashboard, so it is the single shared notion of "tax year" in payroll (inferred from import counts).

### Gaps
- None material.

## Currency and money

### Takeaway
There is no currency anywhere in the amount model. `Money` is a scale-4 `BigDecimal` wrapper with no currency; display is `₹` with Indian grouping; the payslip's amount in words uses Lakh and Crore.

### Cited Findings
- `Money`: `MONETARY_SCALE = 2`, `CALCULATION_SCALE = 4`, `HALF_UP`, one private `BigDecimal value` field, no currency — `BE/shared/…/money/Money.java:26-46`.
- A search of main Java for `INR|₹|Currency|en_IN|lakh|crore` found only comments about rupee rounding (statutory rounder, `TaxableIncomeRounding.toRupee`) — `BE/payroll/…/taxcalc/TaxableIncomeRounding.java:19`; `BE/payroll/…/taxcalc/NewRegimeCalculator.java:92`.
- A search of migrations for `currency` found only `reference.currency` and `reference.country.default_currency` — `MIG/reference/V003__reference_lookups.sql:20,48`. No tenant or amount table has a currency column.
- Frontend: `new Intl.NumberFormat('en-IN', …)` — `FE/shared/ui/format.js:14-17`; `formatMoney(value, symbol = '₹')` — `:69`; doc comment "₹ 1,25,000.00, grouped the Indian way" — `:61`.
- `MoneyText({ value, symbol = '₹' … })` — `FE/shared/ui/MoneyText.jsx:10`.
- Amount in words, "Indian numbering system", Lakh and Crore — `FE/shared/ui/format.js:197-228` (Lakh `:221`, Crore `:222`); used on the payslip — `FE/payroll/payslip/PayslipDocument.jsx:3,42`.
- Hard-coded `₹` in labels and inputs outside the helper: 17 non-test files, 46 hits, e.g. `FE/payroll/settings/EpfScreen.jsx:260`, `FE/payroll/salary/SalarySetupDrawer.jsx:1022`, `FE/payroll/payrun/OffCycleCreate.jsx:291`, `FE/payroll/tax/HousingSection.jsx:38-39`, `FE/payroll/form16/Form16StatementView.jsx:74-278`.
- "Approved Amount (INR)" — `FE/core/approvals/DecideModal.jsx:78`.
- Second `en-IN` formatter — `FE/payroll/salary/statutoryRules.js:49`; `toLocaleString('en-IN')` — `FE/payroll/tax/HousingSection.jsx:527-528`.
- Payslip fallback company name is a hard-coded Indian company string — `FE/payroll/payslip/PayslipDocument.jsx:29`.

### Inferences
- `formatMoney` and `MoneyText` already take a `symbol` argument, so the symbol is a parameter at the helper; the grouping locale is not (seen: module-level constant at `format.js:14`).

### Gaps
- Not checked: whether a backend PDF or email template formats amounts (search for `pdf` in payroll main found only Form 16 Part A upload classes; payslip rendering appears to be frontend `PayslipDocument.jsx` — inferred).

## Employee data: India-only fields and validations

### Takeaway
India identifiers are named columns (Aadhaar, PAN, UAN, PF account, ESI number, IFSC) with regex checks for PAN and Aadhaar on both server and client, and a 10-digit mobile check on the client only. Two generic columns (`personal_tax_id`, `social_insurance_number`) already exist beside them. Address tables are generic; the state picker is an India list.

### Cited Findings
- `core.employee_identification`: `aadhaar_number VARCHAR(12)`, `pan_number VARCHAR(10)`, and generic `personal_tax_id VARCHAR(32)`, `social_insurance_number VARCHAR(64)`, `immigration_status` — `MIG/core/V017__employee_identification.sql:8-12`.
- Server regexes: `AADHAAR_PATTERN = "\\d{12}"`, `PAN_PATTERN = "[A-Z]{5}[0-9]{4}[A-Z]"` — `BE/core/…/employee/detail/EmployeeIdentificationServiceImpl.java:55,58`.
- PAN is a lookup key for Form 16 Part A matching — `BE/core/…/employee/detail/PanLookup.java:8-18`; `BE/payroll/…/form16/PartAEntryParser.java:22`.
- Other PAN regexes: landlord PAN — `BE/payroll/…/taxdeclaration/housing/HousingRules.java:27`; deductor PAN — `BE/payroll/…/taxdeductor/TaxDeductorServiceImpl.java:23`.
- `payroll.tax_deductor`: `tan CHAR(10)` and `pan CHAR(10)` NOT NULL with regex CHECKs, `tds_circle` — `MIG/payroll/V107__tax_deductor.sql:7-9`.
- `payroll.employee_statutory_profile`: `is_eligible_for_pf/pt/lwf/esi/eps`, `contributes_eps_on_higher_wages`, `pf_account_number`, `uan`, `esi_number` — `MIG/payroll/V050__employee_statutory_profile.sql:8-17`. Server checks length only — `BE/payroll/…/salary/EmployeeStatutoryProfileServiceImpl.java:30,136-146`.
- `core.employee_bank`: `payment_mode`, `account_holder_name`, `bank_name`, `ifsc_code VARCHAR(20)`, `bank_account_number`, `bank_account_type` — `MIG/core/V019__employee_bank.sql:8-13`. IFSC is length-checked only, not validated — `BE/core/…/employee/detail/EmployeeBankServiceImpl.java:20-21,32,66`.
- `core.employee_personal`: `father_name`, `differently_abled_type`, `is_eligible_for_full_tax_exemption` — `MIG/core/V015__employee_personal.sql:12-14`.
- Mobile: column `VARCHAR(32)`, server length check only — `BE/core/…/employee/Employee.java:145`; `BE/core/…/employee/EmployeeServiceImpl.java:71,554`. Client requires exactly 10 digits — `FE/core/employee/tabs/OverviewTab.jsx:74`.
- Client regexes: Aadhaar 12 digits, PAN — `FE/core/employee/tabs/SectionTab.jsx:96,103`; UAN 12 digits, ESI number 10 digits — `FE/payroll/salary/statutoryRules.js:33,41`.
- Address: `address_line1/2`, `city`, `state`, `state_code VARCHAR(10)`, `zip_code VARCHAR(16)`, current and permanent — `MIG/core/V016__employee_contact.sql:9-21`. Work location adds `country_code CHAR(2)` — `MIG/core/V013__work_location.sql:9-15`.
- Work location form defaults `countryCode: 'IN'` — `FE/core/org/WorkLocationForm.jsx:68,94,133`.
- State picker: `INDIAN_STATE_OPTIONS` — `FE/core/employee/sectionFields.js:79-80`. ID proof options include "Aadhaar Card", "PAN Card", "Voter ID" — `:42-47`. Nationality list is multi-country — `:64-76`.
- CSV employee import: columns `employee_number, first_name, last_name, work_email, mobile, date_of_joining, department, designation, location, give_access, roles` (no India identifiers) — `BE/core/…/employeeimport/EmployeeImportParser.java:28-39`. Accepted date formats `yyyy-MM-dd`, `dd/MM/yyyy`, `dd-MM-yyyy` (day-first; no `MM/dd/yyyy`) — `:48-51`.
- AI masking lists PAN, Aadhaar, IFSC, UAN, ESI patterns — `BE/core/…/ai/AiMaskingFilter.java:16,35,57`.
- Payslip carries PF number and UAN — `BE/payroll/…/payslip/PayslipServiceImpl.java:217-237`; `FE/payroll/payslip/PayslipDocument.jsx:40,237-241`.
- Salary components carry India flags `epf_inclusion_type`, `included_in_esi` — `BE/payroll/…/component/Earning.java` (and `EarningRequest`, `EarningResponse`, `EarningServiceImpl`); template values e.g. `"epf_inclusion_type": "WHEN_PF_WAGE_BELOW_15000"` — `MIG/reference/V162__country_template_in.sql` (salary_components payload).

Files with India identifier references (search hits, main only): backend 38 Java files (top: `EmployeeIdentificationServiceImpl` 29 hits, `EmployeeIdentification` 20, `TaxDeductorServiceImpl` 14); 8 migrations; frontend 15 non-test files (top: `statutoryRules.js` 18, `HousingSection.jsx` 10, `Form16StatementView.jsx` 10, `statutoryProfileForm.js` 9, `SalarySetupDrawer.jsx` 8).

### Inferences
- The generic `personal_tax_id` / `social_insurance_number` columns look like placeholders for non-India identifiers; whether any screen writes them was not checked.

### Gaps
- Not read: the full employee detail forms; whether Aadhaar/PAN are required or optional at each step.

## Tax: calculator, declarations, proofs, Form 16, TDS — isolation and interfaces

### Takeaway
India income tax is well isolated by package inside `payroll` (about 317 of 580 main files) but is not behind a country-neutral interface. The interfaces that exist are India-shaped (`RegimeCalculator` for old/new regime). The only neutral seam into the pay run is `PayLineContributor`.

### Cited Findings
- Package sizes (main Java files): `taxdeclaration` 92, `taxcalc` 70, `proof` 59, `statutory` 55, `form16` 24, `tds` 21, `priorpayroll` 21, `fbp` 20, `taxdeductor` 10 (directory counts). Test files: taxcalc 32, proof 32, statutory 22, taxdeclaration 22, fbp 10, priorpayroll 10, tds 8, form16 7, taxdeductor 3.
- `taxcalc` contents: `OldRegimeCalculator`, `NewRegimeCalculator`, `RegimeCalculator` (interface, `…/taxcalc/RegimeCalculator.java:13`), `RegimeCalculators`, `TaxRegime`, `AgeCategory`, `PfApplicability`; engine classes `ChapterViaDeductions`, `HraExemption`, `Rebate87A`, `SurchargeAndCess`, `SlabTax`, `HousePropertyIncome`, `SalaryDeductions`, `SalaryProjection`, `OtherIncomeAndInterest` (directory listing of `…/taxcalc` and `…/taxcalc/engine`).
- Service interfaces: `TaxCalculationService` (`…/taxcalc/TaxCalculationService.java:11`), `EmployeeTdsService` (`…/tds/EmployeeTdsService.java:13`), `ProfessionalTaxService` (`…/statutory/pt/ProfessionalTaxService.java:11`), `StatutorySettingsService` (`…/statutory/settings/StatutorySettingsService.java:8`). These are ordinary service interfaces with one implementation each (inferred from naming; not a plug-in pattern).
- Reference tables, all India rules with no country column: `tax_slab_master`, `hra_rule_master`, `home_loan_rule_master`, `let_out_property_rule_master`, `other_income_rule_master`, `standard_deduction_rule_master`, `section6a_item_master`, `section87a_rebate_rule_master`, `cess_surcharge_rule_master` (+2 history tables) — `MIG/reference/V004__reference_tax_masters.sql:19-203`; `pt_state`, `pt_slab` — `MIG/reference/V064__pt_state_and_slab.sql:5,14`. The migration search for `country` matched none of these files.
- Statutory settings are three per-tenant entities: `EpfSetting`, `EsiSetting`, `TdsSetting` — `BE/payroll/…/statutory/settings/` (listing).
- Form 16: `Form16Assembler`, `Form16PartA*`, `QuarterTax`, `DeductorDetails` — `BE/payroll/…/form16/` (24 files).
- Scheduled jobs tied to India tax: IT-declaration auto-lock and proof-of-investment reminder — `BE/worker/…/scheduler/WorkerAutoLockScheduler.java:19`, `WorkerPOIReminderScheduler.java:19`.
- The payroll schema has 48 `CREATE TABLE` statements across 56 payroll migrations (count).

### Inferences
- Because these packages are all inside the single `payroll` Maven module, nothing prevents non-tax payroll code from importing them; `FinancialYear` (in `taxdeclaration`) being imported by `tds`, `dashboard`, `priorpayroll` shows cross-package coupling exists.

### Gaps
- Not measured: how many non-tax packages (payrun, payslip, salary) import tax or statutory classes. `PayRunEmployeeContext` importing `StatutoryProfileResponse` is one seen case.

## Leave, holidays, attendance, overtime, timesheets

### Takeaway
Leave is in days (half-day supported), timesheets and overtime in hours. The working week is configurable on the pay schedule, but three places hard-code Saturday–Sunday. Overtime has no rate rule engine: hours are stored and the amount is entered by hand. The only holiday and leave-type seed is India's.

### Cited Findings
- Leave units are days: `working_days NUMERIC(10,2)` — `MIG/core/V130__leave_request.sql:13`; `entitlement_days`, `accrued_days`, `carried_forward_days` — `MIG/core/V129__leave_allocation.sql:12-14`; `half_day_period` — `MIG/core/V130__leave_request.sql:12`.
- Working week is tenant data: `working_days SMALLINT[]` (ISO 1–7) — `MIG/payroll/V054__pay_schedule.sql:8`; validated — `BE/payroll/…/schedule/PayScheduleServiceImpl.java:91-101`; exposed to core through the port `WorkingWeekSource` — `BE/core/…/lop/WorkingWeekSource.java:13`, implemented by `BE/payroll/…/schedule/PayScheduleWorkingWeekSource.java:19`.
- Hard-coded Saturday–Sunday: leave working-day count — `BE/core/…/leave/WorkingDayCalculator.java:95-96`; approval escalation — `BE/core/…/approval/EscalationServiceImpl.java:197`; WFH — `BE/core/…/wfh/WfhApprovalOutcomeHandler.java:156`.
- LOP policy: `working_day_basis IN ('ACTUAL_DAYS','ORG_DAYS','FIXED_30')`, `configured_days_per_month`, `holidays_payable`, `lop_rounding` — `MIG/core/V116__lop_policy.sql:8-12`. "Loss of pay" is a pay input kind (`LOP_DAYS`) — `BE/core/…/payinput/PayInputKind.java:11`.
- Pay input kinds: `LOP_DAYS, OVERTIME, REIMBURSEMENT, AD_HOC_DEDUCTION, ONE_TIME_PAYOUT` — `BE/core/…/payinput/PayInputKind.java:11-15`.
- Overtime: `hours NUMERIC(10,2)` (0–24], optional `amount NUMERIC(19,4)`, "No pricing here: hours and an optional amount are stored" — `MIG/core/V041__overtime_request.sql:4,12-13,26`. An employee request may not carry an amount — `BE/core/…/overtime/OvertimeServiceImpl.java:107-108`. In the pay run, overtime rows with no amount "pay nothing and are counted as unpriced" — `BE/payroll/…/payrun/PayInputLineContributor.java:21,104`.
- A search of `core/overtime`, `hrms/overtime` and `core/payinput` for `multiplier|1.5|40 hours|weekly` found nothing: no rate or threshold rule exists.
- Attendance: status `PRESENT | HALF_DAY | ABSENT` — `MIG/core/V030__attendance.sql:16`; preference defaults `full_day_minimum_hours 9.00`, `half_day_minimum_hours 4.50` — `MIG/hrms/V121__attendance_preference.sql:8-9`.
- Timesheets: weekly, Monday start enforced in SQL and Java — `MIG/hrms/V141__timesheet.sql:13-14`; `BE/hrms/…/timesheet/TimesheetRules.java:34,39`; `hours NUMERIC(4,2)` per day entry — `MIG/hrms/V144__timesheet_day_entry.sql:13`.
- Holiday seed: "India national holidays", 17 dates for 2026 and 5 for 2027 — `MIG/reference/V162__country_template_in.sql:37-65` (as numbered from the template section); the tracker notes the 2027 list must ship before 1 Jan 2027 — `docs/trackers/DEV-TRACKER.md:891`.
- Leave type seed: EL, CL, SL, ML, PL, LOP — `MIG/reference/V162__country_template_in.sql` (leave_types payload).
- Holiday calendars are tenant tables — `MIG/core/V036__holiday_calendar.sql` (file).

### Inferences
- Because overtime is unpriced by design, US overtime rules would be new capability rather than a change to existing rules.

### Gaps
- Not read: leave accrual and reset logic, attendance clock session zone handling, whether leave can be requested in hours (columns suggest days only).

## Tenant creation template (W-73.9)

### Takeaway
Yes, it is structured per country: `reference.country_template` is keyed by `(country_code, section)` and applied by section contributors. Only `IN` rows exist. Two limits: the set of sections is fixed by a CHECK, and the `statutory` section's contributor only understands EPF and ESI.

### Cited Findings
- Table: `country_code CHAR(2) REFERENCES reference.country`, `section`, `version`, `payload JSONB`, PK `(country_code, section)` — `MIG/reference/V162__country_template_in.sql:25-36`. Header: "India (IN) first; another country is one more migration" — `:4`.
- Sections are fixed by CHECK: `holidays, leave_types, salary_components, statutory, pay_schedule` — `MIG/reference/V162__country_template_in.sql:33-34`.
- Extension interface: `TenantTemplateContributor { String section(); PlatformModule module(); boolean apply(UUID tenantId, JsonNode payload, LocalDate today); }` — `BE/core/…/template/TenantTemplateContributor.java:19-42`.
- One contributor per section is enforced (two for the same section throws) — `BE/core/…/template/TenantTemplateService.java:79-86`. Apply order — `:42-43`. A country with no template applies nothing — `:113-116`. Each applied section is recorded with its version — `:131-139`; table `core.tenant_template_applied` — `MIG/core/V169__tenant_template_applied.sql:14`.
- Contributors: `HolidayTemplateContributor` (`BE/core/…/holiday/HolidayTemplateContributor.java:19`), `LeaveTypeTemplateContributor` (`BE/core/…/leave/LeaveTypeTemplateContributor.java:18`), `PayScheduleTemplateContributor`, `SalaryComponentTemplateContributor`, `StatutoryTemplateContributor` (`BE/payroll/…/template/*.java`).
- `StatutoryTemplateContributor` reads `payload.epf` and `payload.esi` into `EpfSetting`/`EsiSetting` — `BE/payroll/…/template/StatutoryTemplateContributor.java:30-35,51-57`.
- Reader selects by country code — `BE/core/…/template/CountryTemplateReader.java:29-40`. Endpoint `/api/v1/reference/country-templates` — cited in `W-116` spec `:48`.
- A later migration patches the IN `salary_components` row — `MIG/payroll/V175__default_reimbursement_components.sql:64-80`.
- Create Tenant screen shows "Starts with: …" or "No template" for the chosen country — `FE/core/admin/TenantCreate.jsx:36-37,137`.

### Inferences
- A US template for holidays, leave types, salary components and pay schedule would be new rows read by the existing contributors, as long as the payload shapes fit (e.g. the salary component payload has India fields such as `epf_inclusion_type`). A US `statutory` payload would not be understood by the existing contributor (seen: it reads only `epf` and `esi`).

### Gaps
- Not read: the `pay_schedule` payload values and the full `PayScheduleTemplateContributor`.

## Setup checklist, navigation and settings screens

### Takeaway
Three registries, none country-aware. The checklist is a static catalogue in `core` that names EPF, ESI and Professional tax. Navigation is contributed per module and filtered by module and permission only. The payroll settings menu is a static frontend array.

### Cited Findings
- Checklist catalogue: `WORK_LOCATION, EMPLOYEE, PAY_SCHEDULE, PRIOR_PAYROLL, SALARY_COMPONENTS, EPF, ESI, PROFESSIONAL_TAX` — `BE/core/…/setup/SetupStepCatalogue.java:32-39`. Boot validation requires a checker for every catalogue step — `:88`.
- Checker port: `SetupStepChecker { code(); module(); isComplete(tenantId); isPrefilled(tenantId) }` — `BE/core/…/setup/SetupStepChecker.java` (interface). Payroll's checkers are beans in `BE/payroll/…/setup/PayrollSetupStepConfiguration.java` (EPF `:118-128`, ESI `:130`, PT `:153-178`).
- The PT step is complete when every active work location has a state code — `PayrollSetupStepConfiguration.java:160-177`.
- Navigation port: `NavigationContributor { List<ItemDefinition> items(); }`, filtered "by its module, by its action" — `BE/core/…/navigation/NavigationContributor.java:14` and Javadoc. Payroll's items: dashboard, runs, prior payroll, claims, deductions, settings, components — `BE/payroll/…/navigation/PayrollNavigation.java:20-79`.
- Settings menu (frontend, static): Pay Schedule, EPF, ESI, Professional Tax, TDS, Flexible Benefit Plan, Tax declaration, Form 16 — `FE/payroll/settings/SettingsLayout.jsx:20-57`. Routes registered statically — `FE/payroll/index.js:92-147`.
- Frontend checklist: `FE/core/setup/SetupChecklist.jsx`, `stepLinks.js` (files).
- Dashboard statutory tiles — `FE/payroll/dashboard/StatutoryTiles.jsx:15`.

### Inferences
- Hiding India steps for a US tenant would need a country dimension added to the catalogue/`ItemDefinition` (which today carry module and action only) and to the frontend settings array.

### Gaps
- Not read: `NavigationCatalogue.ItemDefinition` fields in full; `stepLinks.js`.

## Dates, time zones and locale

### Takeaway
Timezone is per tenant with an India fallback; scheduled jobs run on UTC cron. There is no i18n: no translation library, all text is English literals, date display is `D MMM YYYY` via dayjs, and number formatting is `en-IN`.

### Cited Findings
- `TenantClock` reads `core.tenant.timezone`; fallback `Asia/Kolkata` — `BE/core/…/tenant/TenantClock.java:21,40-55`.
- Other `Asia/Kolkata` constants: `BE/payroll/…/payrun/PayRunServiceImpl.java:90` (fallback; reads tenant zone at `:800-811`), `BE/payroll/…/priorpayroll/PriorPayrollRowValidator.java:37` (fallback), and a non-fallback hard-code "Payroll months are Indian months" — `BE/payroll/…/deduction/EmployeeDeductionRules.java:28-29`.
- About 40 `LocalDate.now()` calls use the server default zone rather than the tenant zone, e.g. `BE/core/…/attendance/AttendanceServiceImpl.java:74`, `BE/core/…/approval/ApprovalService.java:104`, `BE/payroll/…/fbp/FbpDeclarationServiceImpl.java:87,97`, `BE/payroll/…/salary/EmployeeSalaryServiceImpl.java:289,335` (search listing).
- Cron jobs with `zone = "UTC"`: leave accrual, scheduled earning, IT-declaration auto-lock, POI reminder (9:00 UTC), approval escalation — `BE/worker/…/leave/LeaveAccrualJob.java:37`, `…/scheduler/ScheduledEarningJob.java:38`, `WorkerAutoLockScheduler.java:19`, `WorkerPOIReminderScheduler.java:19`, `BE/core/…/approval/EscalationSweep.java:48`. Reminder and report evaluators resolve the tenant's zone — `BE/worker/…/notification/ReminderEvaluator.java:94`, `…/report/ReportScheduleEvaluator.java:141`.
- Employee-level time zone field exists — `BE/core/…/employee/detail/EmployeeEmploymentServiceImpl.java:72,94`; `FE/core/employee/timeFields.js:14,44-64`.
- Frontend date formats: `'D MMM YYYY'`, `'D MMM YYYY, HH:mm'` (24-hour) — `FE/shared/ui/format.js:32,57`; one `'DD/MM/YYYY'` and one `'ddd DD/MM'` elsewhere (format-string count).
- A search of `FE/` for `i18n|react-intl|useTranslation` returned no files; a search for `en-US|en-GB` returned none.
- Server validation and error messages are English string literals, e.g. `BE/core/…/tenant/TenantServiceImpl.java:134`.

### Inferences
- English-only is not an obstacle for the US; day-month-year order and 24-hour time are the visible format differences.

### Gaps
- `code/frontend/package.json` was not read (the grep for `i18n|dayjs` printed nothing, possibly a path issue), so the absence of an i18n dependency is inferred from source imports only.
- Keycloak realm locale settings under `infra/keycloak/` were not checked.

## Bank and payments

### Takeaway
Bank details are stored per employee with an IFSC field; there is no bank file or payment export in the new platform.

### Cited Findings
- Fields: `payment_mode`, `account_holder_name`, `bank_name`, `ifsc_code`, `bank_account_number`, `bank_account_type` — `MIG/core/V019__employee_bank.sql:8-13`. No routing-number field.
- `payment_mode` has no CHECK by design — `MIG/core/V019__employee_bank.sql:31`; values in `BE/core/…/employee/detail/PaymentMode.java`.
- `reference.bank` requires `ifsc_prefix` — `MIG/reference/V003__reference_lookups.sql:62-71`.
- A search of backend main and frontend for `NEFT|bank advice|bank file|payment file|salary transfer` matched only `EmployeeBankServiceImpl.java`, `PaymentMode.java` and a test: no export exists.
- Pay run statuses include `PAID` — `MIG/payroll/V055__payrun.sql:17`.

### Inferences
- Marking a run `PAID` is a status change only, with payment made outside the system (inferred from the absence of any export).

### Gaps
- `PaymentMode` enum values not read.

## Infrastructure residency

### Takeaway
One deployment, locked to `centralindia` by a single-valued `@allowed` list, justified by decision D-18 (DPDP Act). The app has one database URL and isolates tenants by row-level security, so there is no per-tenant data region.

### Cited Findings
- `@allowed(['centralindia']) param location string = 'centralindia'`, with the comment "a deployment to any other region is a compliance breach, not a configuration choice" — `infra/azure/main.bicep:11-19`.
- The infra workflow passes `--location centralindia` — `.github/workflows/infra.yml:75`.
- Azure OpenAI location is a separate parameter allowing `southindia, centralindia, swedencentral, eastus2`, default `southindia` — `infra/azure/main.bicep:125-136`.
- D-18 "Azure India region … Primary and backups both in-jurisdiction" — `docs/target-state/07-decisions.md:30`; restated — `docs/target-state/05-azure-architecture.md:139-146`. D-61 narrows it for the AI helper only — `docs/target-state/07-decisions.md:73`.
- Single datasource: `url: ${DB_URL}` — `BE/app/src/main/resources/application.yml:34`.
- Tenancy is RLS on `tenant_id` via `app.current_tenant_id` — e.g. `MIG/payroll/V055__payrun.sql:40-49`; binding classes in `BE/shared/…/tenant/` (`TenantBindingDataSourceProxy`, `TenantContextFilter`).
- Parameter files exist for dev, uat, prod — `infra/azure/parameters/` (listing). Project memory says only one environment is live (not verified in repo).
- 15 infra files mention India regions (bicep modules, runbooks, README) — search listing.

### Inferences
- Hosting US data in the US would mean either a second full deployment (own database, own Front Door/host) or relaxing D-18 and the `@allowed` list; nothing in the app routes a tenant to a region (inferred from single `DB_URL` and no region column on `core.tenant`).

### Gaps
- Not read: `infra/azure/modules/*.bicep` for other region-specific settings (zones, SKUs, DR pairing in `DR_RUNBOOK.md`).

## Module boundaries

### Takeaway
Confirmed: `hrms` and `payroll` may each depend only on `core` and `shared`; maven-enforcer bans the cross dependency in both poms. Cross-module behaviour is done with interfaces declared in `core` and implemented in a module — the same pattern a country-specific piece would follow.

### Cited Findings
- Dependency graph and rule — `BE/pom.xml:21-33`; modules `shared, core, hrms, payroll, app, worker, migration` — `:35-41`.
- `hrms` bans `com.infinevo:payroll` (transitive) — `BE/hrms/pom.xml:85-107`; `payroll` bans `com.infinevo:hrms` — `BE/payroll/pom.xml:83-97`.
- Ports declared in `core`, implemented in `payroll`: `TenantTemplateContributor` (`BE/core/…/template/TenantTemplateContributor.java:19`), `SetupStepChecker` (`BE/core/…/setup/SetupStepChecker.java`), `NavigationContributor` (`BE/core/…/navigation/NavigationContributor.java:14`), `WorkingWeekSource` (`BE/core/…/lop/WorkingWeekSource.java:13`); W-116 plans one more, `PayRunExistenceChecker` — spec `:85-86`.
- Port declared and implemented inside `payroll`: `PayLineContributor` — `BE/payroll/…/payrun/PayLineContributor.java:14`.
- Cross-module data flows through `core.pay_input`, per the enforcer message — `BE/hrms/pom.xml:102-103`.
- Module sizes (main / test Java files): shared 64/69, core 586/306, hrms 123/64, payroll 580/260, worker 26/35, app 1/6, migration 1/15. Migrations: core 100, hrms 11, payroll 56, reference 17. Frontend files: shell 55, core 189, hrms 82, payroll 172, shared 30.

### Inferences
- Country-specific payroll code can live as packages inside `payroll` (as `statutory/` and `tds/` do today) without touching the enforcer rule. A separate Maven module per country that depends on `payroll` would also not break the two existing bans, but would need adding to `app` and `worker`; no such module exists today.
- India-specific code in `core` (PAN/Aadhaar validation in `employee/detail`, `PanLookup`) sits outside payroll, so "country code only in payroll" is not true today.

### Gaps
- `docs/target-state/03-code-structure.md` and `12-core-contracts.md` were only searched for keywords, not read in full; any stated rule about where country-specific code should live was not found.

## Size summary

### Takeaway
Roughly 320 of payroll's 580 backend classes and about 60 of 172 payroll frontend files are India tax or statutory; outside payroll the India-specific surface is small.

### Cited Findings

| Area | Backend main files | Tests | Migrations / tables | Frontend files (incl. tests) |
|---|---|---|---|---|
| Statutory (EPF, ESI, PT, settings) | `statutory/` 55 | 22 | PT reference `V064`; settings tables in payroll | `payroll/settings/` 29 (shared with pay schedule, FBP) |
| Tax calculator | `taxcalc/` 70 | 32 | 11 reference tables in `V004`; rule seeds `V005, V027, V105, V106, V147` | — |
| Tax declaration | `taxdeclaration/` 92 | 22 | e.g. `V073`, `V075` | `payroll/tax/` 23 |
| Proof of investment | `proof/` 59 | 32 | — | (within tax/claims screens; not counted) |
| Form 16 | `form16/` 24 | 7 | `V109` | `payroll/form16/` 8 |
| TDS | `tds/` 21 | 8 | `V102`, `V104`, `V222` | in `payroll/salary/` (44 files total) |
| Tax deductor (TAN/PAN) | `taxdeductor/` 10 | 3 | `V107` | — |
| FBP | `fbp/` 20 | 10 | — | in `payroll/settings/` |
| Prior payroll (FY import) | `priorpayroll/` 21 | 10 | `V140` | `payroll/priorpayroll/` 6 |
| Pay run core (month-based) | `payrun/` 61, `schedule/` 13, `scheduled/` 11 | not counted | `V054`, `V055` | `payroll/payrun/` 15 |
| Country template | core `template/` 7 + 5 contributors | not counted | `V162`, `V169`, `V175` | `core/admin` (1 helper) |
| Employee India identifiers | 38 files with hits (core + payroll) | not counted | 8 migrations | 15 non-test files |
| Currency display | 0 | — | 0 | 17 non-test files with `₹`; 3 with `en-IN` |

(Counts are directory or search counts taken in this session; see the commands' scope in each section.)

### Inferences
- The count of files is a proxy for surface, not effort; the month-period and tax-year assumptions touch fewer files by name but more behaviour.

### Gaps
- Proof-of-investment and TDS frontend files were not counted separately.
- Test counts for payrun, schedule, core were not taken.
