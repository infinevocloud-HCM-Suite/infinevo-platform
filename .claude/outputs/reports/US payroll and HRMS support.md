# Ship US HRMS first, embed payroll

| Question | Short answer |
|---|---|
| How to enter the US | HRMS-only first, with the customer keeping its own payroll provider. |
| How to do US payroll | Through an embedded payroll provider that calculates, files and moves the money. Do not build a US tax engine. |
| Biggest code blocker | Country is stored on the tenant but no calculation reads it. Pay period is one calendar month, tax year is April-March, money has no currency. |
| Biggest unknown | Whether any embedded provider will sign an India-incorporated company. No source answers this. |
| What cannot be trusted yet | Every vendor price and launch time found is the vendor's own claim. |

US payroll means thousands of tax jurisdictions whose rates change all year, plus deposits, filings and bank transfers that carry penalties. The US employer stays liable for the tax even when a provider files for it, so customers will judge Infinevo on the provider behind it. An embedded provider takes over calculation, filing and money movement; a licensed tax engine only calculates and leaves the rest with Infinevo. US HRMS carries no tax or money liability, so it can ship first. It still needs real new capability: leave in hours, overtime rules by state, US identity fields and US formats. The India tax and statutory code is large but sits in its own packages, so the main work is making the platform country-aware, not rewriting it. Many facts below come from vendor or law-firm summaries; where the notes mark something unsourced or conflicting, this report says so.

Path short forms: `BE/` = `code/backend/`, `MIG/` = `code/backend/migration/src/main/resources/db/migration/`, `FE/` = `code/frontend/src/`. Inside a module, `…/` = `src/main/java/com/infinevo/<module>/`.

## 1. US rules change by state and city, all year

| Area | What the product must do | How it differs from India today | Confidence |
|---|---|---|---|
| Federal income tax | Withhold from Form W-4 data and the yearly IRS tables; support old and new W-4 forms. | Replaces regime, declarations, proofs, Form 16. | High (IRS) |
| Social Security, Medicare | 6.2% each side up to $184,500 (2026), $192,300 (2027); 1.45% each side; 0.9% extra above $200,000. | Replaces EPF and ESI. | High |
| Unemployment tax | Federal 6.0% on first $7,000; state rate is set per employer by notice. | No India equivalent; needs a per-state, dated setting. | High / medium |
| State and local income tax | Own form and formula in most states; local tax depends on exact street address. | PT is the nearest thing, and far simpler. | Medium (secondary sources) |
| State disability and paid leave | About 15 programmes, each with its own rate, cap and employer-size split. | New. | Medium |
| Deposits and filings | Deposit by pay date on a monthly or semi-weekly schedule; Form 941 quarterly; Forms 940, W-2, 1099 yearly. | Tax year is January-December. | High |
| Pay frequency | Biweekly is most common (43% of establishments); monthly is least common. | Platform allows monthly only. | High (BLS) |
| Overtime | 1.5x over 40 hours a week; daily rules in California, Alaska, Nevada, Colorado. | Platform stores overtime hours with no rate rule. | High / medium |
| Leave | No federal paid leave; state and city sick leave accrues per hour worked. | Platform holds leave in days. | Medium |
| Onboarding | Form I-9 within three business days; E-Verify compulsory in roughly 10-11 states. | New. | Medium (state lists conflict) |
| Health cover (ACA) | Track hours of service monthly; Form 1095-C yearly. | New. | High on figures, definitions unsourced |
| Privacy and hosting | No law forces US hosting; California covers employee data; Illinois restricts biometric clock-in. | Platform is locked to Central India. | Medium |

Three facts drive the design.

Rates change at any point in the year, not only in January. Every rate, wage base and threshold must be dated data, not code.
([IRS Publication 15](https://www.irs.gov/publications/p15); [EY state rate chart, reissued three times in 2026](https://taxnews.ey.com/news/2026-1857-2026-state-supplemental-flat-tax-and-highest-income-tax-withholding-rates-with-hyperlinks-to-the-latest-withholding-tables-instructions-updated-august-28-2026))

Most labour rules follow the place the work is done, down to city level. Each employee needs a home address and a separate work location.
([DOL minimum wage table](https://dol.gov/agencies/whd/state/minimum-wage/tipped); [Patriot Software on city sick leave](https://www.patriotsoftware.com/blog/payroll/state-mandated-paid-sick-leave-laws/))

The 2025 federal tax law added new W-2 boxes for tips and overtime from tax year 2026. Qualified overtime must be tracked as its own year-to-date amount.
([SSA 2026 W-2 file layout](https://www.ssa.gov/employer/efw/26efw2.pdf); [IRS Publication 15-T](https://www.irs.gov/publications/p15t))

What the notes could not confirm:

- The count of local tax jurisdictions is "thousands"; the common "over 10,000" figure could not be sourced.
- Meal and rest breaks, new-hire reporting, the workweek definition, core ACA definitions and SOC 2 expectations were not sourced at all.
- State lists conflict for E-Verify, paid sick leave (19 or 21 states), pay transparency (14 or 16 states) and predictive scheduling cities.
- EEO-1 reporting was proposed for removal in July 2026; no final rule was found. State pay-data reports in California, Illinois and Massachusetts continue. ([EEOC proposal](https://www.eeoc.gov/sites/default/files/2026-07/2026_NL01498_NPRM_Rescission.pdf))
- A final federal contractor-classification rule was reported for November 2026 by one source only.

## 2. An embedded provider is the only route that removes filing and money risk

| Route | What Infinevo buys | What Infinevo still owns | Fit |
|---|---|---|---|
| Build everything | Nothing | Tax engine, address lookup, forms, deposits, filings, W-2, bank transfers, licences | No |
| License a tax engine (Symmetry, Vertex, BSI) | Tax calculation and withholding forms | Deposits, filings, W-2, money movement, registrations, liability | Later, if ever |
| Engine plus filing vendor (Greenshades, TaxBandits, MasterTax) | Calculation plus returns | Money movement, bank relationship, operations | Later, if ever |
| Embedded payroll (Check, Gusto Embedded, Salsa, Zeal, ADP Embedded) | Calculation, deposits, filings, W-2/1099, money movement | Screens, data sync, pricing, first-line support | **Recommended** |
| HRMS only, customer keeps its payroll | Optional read link through Finch or Merge | HRMS, time, leave, export | **Ship first** |

**Recommendation: embed.** The reasons, in order of weight:

A tax engine only calculates. None of the engines found files returns, deposits tax or moves money.
([Symmetry docs](https://docs.symmetry.com/ste/overview/hosted-vs-on-premise))

Moving payroll money needs a US bank relationship. A payroll company acts as a third-party sender through a US bank under Nacha rules. The notes infer, but do not source, that this needs a US subsidiary.
([Venable payments law guide](https://books.venable.com/Payments-Law-Tool-Kit/2021/28-29))

On the build or engine route Infinevo becomes the reporting agent for each client. It would need a signed authorisation per client and state registrations. Maine, for example, licenses payroll processors and requires a bond.
([IRS third-party arrangements](https://www.irs.gov/zh-hant/businesses/small-businesses-self-employed/third-party-arrangements); [Maine Bureau](https://www.maine.gov/pfr/consumercredit/industry/licensing/payroll_processor.htm))

Embedded providers state that they hold the licences and bank partners themselves.
([Check Core](https://www.checkhq.com/check-core))

Peers made the same choice. HiBob launched US payroll on Gusto in 2025 and Xero also uses Gusto Embedded. Zoho built its own; no source says how long it took.
([HiBob](https://www.hibob.com/news/hibob-launches-native-us-payroll-to-cut-complexity-and-drive-confidence-for-smbs/); [Zoho launch](https://www.businesswire.com/news/home/20241212017805/en))

**Trade-offs to accept.**

- The provider's engine becomes the record for US pay. Infinevo's own pay run would not calculate US pay. (Inference in the notes, not a vendor statement.)
- The employer stays liable for the tax whoever files. Whether a provider pays for its own mistakes is a contract term, not law. ([IRS outsourcing payroll](https://www.irs.gov/Businesses/Small-Businesses-&-Self-Employed/Outsourcing-Payroll-and-Third-Party-Payers))
- Symmetry is owned by Gusto, which also sells Gusto Embedded. ([Gusto](https://gusto.com/company-news/welcoming-symmetry-to-gusto))

**Prices and timelines are not verified.**

- No provider publishes its wholesale price, minimum commitment or set-up fee.
- Check suggests partners charge customers at least $40 a month plus $6 per employee. That is a resale guide, not Check's price to Infinevo. ([Check help](https://help.checkhq.com/go-to-market-playbook/6vJ1Gx6JntHnUJetZzQj3E/how-to-price-your-payroll-product--use-discounts-effectively/6vJ1Gx6JnvbdxYWEn1MJ9X))
- Launch times of "a few weeks" to "a couple of months" are vendor claims. No independent figure exists for any route.
- Finch pricing sources conflict ($50 or $65 per connection per month).
- Unified APIs like Finch mostly read payroll data. Pushing hours and earnings into another provider's pay run is not confirmed as generally available.

## 3. Country is stored but nothing reads it

| Difficulty | What is fixed to India | Needed for US HRMS | Needed for embedded payroll |
|---|---|---|---|
| Hard | Pay period is one calendar month | Partly (leave and overtime post to a month) | Yes, for the hand-over of hours and inputs |
| Hard | Tax year April-March | No | Only if US pay data is shown in Infinevo screens |
| Hard | Money has no currency; display is rupee | Yes (display) | Yes |
| Hard | Hosting locked to Central India | Founder decision | Founder decision |
| Medium | India statutory and tax lines always run | No (payroll off) | Must be skipped for US tenants |
| Medium | Aadhaar, PAN, UAN, IFSC fields; India state list | Yes | Yes |
| Medium | Setup checklist and settings menus list EPF, ESI, PT, TDS | Yes | Yes |
| Medium | Leave in days; overtime unpriced; weekly timesheet starts Monday | Yes | Yes |
| Easy | Timezone, leave-year start, working week, country template | Small fixes | Small fixes |

The last two columns are this report's reading of the inventory, not something the inventory states.

**Country and currency**

The tenant has a country code, but no pay run, tax, leave or formatting class reads it.
- `BE/core/…/tenant/TenantServiceImpl.java:47-48,128-136,218`
- `MIG/core/V033__tenant_locale_columns.sql:6-8`

US and USD already exist in the reference tables, but no Java class reads the country's default currency.
- `MIG/reference/V003__reference_lookups.sql:14-23,48-56,80-93`

`Money` holds one number and no currency; the frontend formats with Indian grouping and the rupee sign.
- `BE/shared/…/money/Money.java:26-46`
- `FE/shared/ui/format.js:14,69,197-228`
- Rupee sign appears in 17 non-test frontend files (46 hits).

The state table needs a GST code on every row, and the bank table needs an IFSC prefix, so US rows cannot be added as it stands.
- `MIG/reference/V003__reference_lookups.sql:28-39,62-71`

**Pay period and tax year**

Only `MONTHLY` is allowed, one run per tenant per month, and the period is a `YYYY-MM` text column.
- `MIG/payroll/V054__pay_schedule.sql:7,9-11`
- `MIG/payroll/V055__payrun.sql:7-13,33-35`
- `BE/payroll/…/payrun/PayRunEmployeeContext.java:33`

The same month key is used in `core` for pay inputs, period locks, overtime and leave, so it reaches outside payroll.
- `MIG/core/V031__pay_input.sql:10-11`
- `MIG/core/V041__overtime_request.sql:20`
- `MIG/core/V132__leave_consumption.sql:12`

April-March is fixed in one record that 60 payroll files use.
- `BE/payroll/…/taxdeclaration/FinancialYear.java:66,80,91,98`
- `BE/payroll/…/tds/TaxLineContributor.java:89`

**India tax and statutory code**

The pay run loops over plug-in contributors, and a new source of lines is a new bean. This is the place to switch India lines off for a US tenant.
- `BE/payroll/…/payrun/PayLineContributor.java:14-25`
- `BE/payroll/…/payrun/PayRunComputationServiceImpl.java:337-344`

The two India contributors have no country condition, and the context they receive carries no country or currency.
- `BE/payroll/…/statutory/payrun/StatutoryLineContributor.java:54-56`
- `BE/payroll/…/tds/TaxLineContributor.java:36-38`
- `BE/payroll/…/payrun/PayRunEmployeeContext.java:29-45`

About 317 of payroll's 580 backend files are India tax or statutory, in their own packages. They can stay as they are.

**Employee data and HR**

Aadhaar and PAN are named columns with format checks; two generic ID columns already sit beside them.
- `MIG/core/V017__employee_identification.sql:8-12`
- `BE/core/…/employee/detail/EmployeeIdentificationServiceImpl.java:55,58`

Bank details have an IFSC field and no routing number.
- `MIG/core/V019__employee_bank.sql:8-13`

The state picker is an India list, and the employee import reads day-first dates only.
- `FE/core/employee/sectionFields.js:79-80`
- `BE/core/…/employeeimport/EmployeeImportParser.java:48-51`

Leave is held in days.
- `MIG/core/V129__leave_allocation.sql:12-14`
- `MIG/core/V130__leave_request.sql:13`

Overtime stores hours and an optional hand-entered amount; there is no rate or threshold rule, so US overtime is new capability.
- `MIG/core/V041__overtime_request.sql:4,12-13,26`
- `BE/payroll/…/payrun/PayInputLineContributor.java:21,104`

Timesheets must start on a Monday, and three places hard-code a Saturday-Sunday weekend.
- `MIG/hrms/V141__timesheet.sql:13-14`
- `BE/core/…/leave/WorkingDayCalculator.java:95-96`
- `BE/core/…/approval/EscalationServiceImpl.java:197`

**Screens and template**

The setup checklist and payroll settings menu are static lists with no country filter.
- `BE/core/…/setup/SetupStepCatalogue.java:32-39`
- `FE/payroll/settings/SettingsLayout.jsx:20-57`

The tenant template is already keyed by country; only India rows exist, and the statutory section understands only EPF and ESI.
- `MIG/reference/V162__country_template_in.sql:25-36`
- `BE/payroll/…/template/StatutoryTemplateContributor.java:30-35,51-57`

**Hosting**

The deployment allows one region and the app has one database address, so nothing can place a tenant in another region.
- `infra/azure/main.bicep:11-19`
- `BE/app/src/main/resources/application.yml:34`
- `docs/target-state/07-decisions.md:30`

**Already planned**

W-116 (edit country, timezone and leave year on the company profile) is approved and not built; it leaves out everything that makes another country work. W-118 "US market support" is logged with no spec.
- `docs/target-state/features/W-116-company-locale-on-company-profile.md:9,62`
- `docs/trackers/DEV-TRACKER.md:929,956`

Not checked in the inventory: every file in the tax packages, the leave accrual logic, and whether the pay-line source has a database constraint.

## 4. Four phases, each one unlocking the next

| Phase | What ships | Size | Depends on |
|---|---|---|---|
| 0. Decisions | Vendor answers, US entity question, hosting choice, target customer size | S | Nothing |
| 1. Country-aware platform | Country reaches the code; currency and US formats; India screens hidden for US tenants; US template | M | W-116 |
| 2. US HRMS | Hours-based leave, US employee fields, work locations, overtime rules, I-9 tracking, export to the customer's payroll | L | Phase 1 |
| 3. US payroll, embedded | Provider onboarding, data sync, pay screens, payslip view, pricing and billing | L | Phases 0 and 2, signed provider |
| 4. Deeper compliance | Health-cover (ACA) tracking, pay-data reports, injury log, scheduling rules, benefits enrolment | L, ongoing | Phase 2; customer demand |

**Phase 1, in order**

1. Build W-116, then pass country to the pay run, checklist, navigation and settings menus.
2. Add currency to the tenant and to money display; remove the hard-coded rupee signs.
3. Add US date order, 12-hour time and month-first import dates.
4. Make the state and bank reference tables accept non-India rows.
5. Add a US template: holidays, leave types, pay schedule.
6. Replace the roughly 40 uses of server-default "today" with the tenant's timezone.

**Phase 2, in order**

1. US employee record: SSN (masked and encrypted), home address with county, separate work location, overtime-exempt status, worker type, pay basis.
2. Leave in hours, with accrual per hours worked and rules by work location.
3. Employer-set workweek; raw punch times; no rounding by default.
4. Overtime rule set by work state: weekly 40 everywhere, daily rules for the four states.
5. I-9 record with its dates and a re-check reminder.
6. Export of hours, leave and employee changes for the customer's payroll provider.

**Phase 3 choice that shapes the rest.** With an embedded provider, the monthly pay period and April-March tax year inside payroll may not need rewriting for the US, because the provider calculates. The month key in `core` still needs a way to hand weekly and biweekly periods to the provider. This is an inference; confirm it against the chosen provider's data model before sizing.

**What not to do first.** Do not start on a US tax contributor, multi-frequency pay runs or a US deployment until Phase 0 answers are in. Each is large and each may prove unnecessary.

## 5. Questions only a vendor or a US expert can answer

| # | Question | Who answers | Blocks |
|---|---|---|---|
| 1 | Will you sign an India-incorporated software company, or is a US entity required? | Each embedded provider | Phase 3 |
| 2 | What are the wholesale price, minimum commitment and set-up fee? | Each embedded provider | Phase 3 pricing |
| 3 | Who pays penalties caused by the provider's error? | Provider contract; US lawyer | Phase 3 |
| 4 | Can hours, earnings and leave be pushed in from our HRMS, at what pay frequencies? | Each embedded provider | Phase 3 design |
| 5 | Do you need US hosting, US-only support access or SOC 2 from us? | Provider; first US prospects | Hosting decision |
| 6 | Does keeping US employee data in India raise any issue under the DOJ bulk-data rule? | US privacy lawyer | Hosting decision |
| 7 | Which states and worker types make monthly pay unlawful? | US payroll expert | Phase 2 pay schedule |
| 8 | What are the current meal-break, rest-break and daily overtime rules for the launch states? | US employment lawyer | Phase 2 overtime |
| 9 | Which states should launch first? | Founder, with the provider's coverage list | Phases 2 and 3 |
| 10 | Can a unified API (Finch, Merge) write hours into a pay run, or is file export the only path? | Finch, Merge | Phase 2 export |

On question 6: the notes say no US law requires US hosting and the DOJ rule governs access by named "countries of concern", not storage location. The list of those countries was not retrieved, so India's position is unverified.
([DOJ data security programme](https://www.justice.gov/nsd/data-security))

On question 5: decision D-18 calls any region other than Central India a compliance breach. It was written for Indian data. Holding US tenant data in India, or adding a US deployment, is a new decision the founder must record.
- `docs/target-state/07-decisions.md:30`
- `infra/azure/main.bicep:11-19`

## Conclusion

The US entry is two different products with very different risk. HRMS is a build job inside the existing design, and its hardest parts are hours-based leave and state overtime rules. Payroll is a partnership job, and its hardest part is one unanswered commercial question: whether a provider will take Infinevo as it is incorporated today.

Question 1 should be asked before any US code is written. If the answer is "US entity required", that becomes the first item of the plan. Phase 1 is worth doing either way, because it makes the platform ready for any second country, not only the US.
