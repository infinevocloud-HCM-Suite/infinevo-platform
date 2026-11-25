# US payroll taxes and filings for employers (tax years 2026 and 2027)

Researched 2026-11-24. Figures are for 2026 unless a different year is written next to them. "Secondary" marks a figure taken from a payroll vendor or advisory firm, not from the agency itself.

## Federal income tax withholding (Form W-4, Publication 15-T, supplemental wages, 2026 changes)

### Takeaway
Withholding is a formula driven by the employee's Form W-4 and the annual tables in IRS Publication 15-T; a payroll engine must support both the 2020-and-later W-4 and the older allowance-based W-4. The 2025 "One Big Beautiful Bill" law (P.L. 119-21) did not change how withholding is calculated on tips and overtime, but it added new W-2 reporting from tax year 2026.

### Cited Findings

| Item | 2026 value |
|---|---|
| Supplemental wage flat rate | 22% |
| Supplemental wages above $1 million in the year | 37% |
| Backup withholding | 24% |
| Worksheet 1A line 1g amount (Step 2 box not ticked) | $12,900 married filing jointly, $8,600 others |
| Value of one pre-2020 allowance | $4,300 |
| Qualified tips deduction (employee's own return) | up to $25,000, tax years 2025 to 2028 |
| Qualified overtime deduction (employee's own return) | up to $12,500, or $25,000 joint, tax years 2025 to 2028 |

- Supplemental wages are withheld at 22%, and at 37% once supplemental wages to one employee pass $1 million in the calendar year; backup withholding is 24% (2026) — [IRS Publication 15 (2026)](https://www.irs.gov/publications/p15)
- Publication 15-T (2026) has seven sections: (1) percentage method for automated payroll systems, Worksheet 1A; (2) wage-bracket tables for manual payroll with 2020+ W-4; (3) wage-bracket tables for 2019-or-earlier W-4; (4) percentage tables for manual payroll, 2020+ W-4; (5) percentage tables for manual payroll, 2019-or-earlier W-4; (6) alternative methods (annualized, cumulative, part-year and others); (7) Indian gaming distributions — [IRS Publication 15-T (2026)](https://www.irs.gov/publications/p15t)
- How a 2020+ W-4 feeds Worksheet 1A: Step 4(a) other income is added to annualized wages; Step 4(b) deductions are subtracted; if the Step 2 box is not ticked, $12,900 (joint) or $8,600 (others) is subtracted, and if it is ticked nothing is subtracted and a separate "Step 2 checkbox" rate schedule is used; the Step 3 credit is divided by pay periods and subtracted from the tentative tax; Step 4(c) extra withholding is added per period — [IRS Publication 15-T (2026)](https://www.irs.gov/publications/p15t)
- Pre-2020 W-4 forms stay valid: each allowance is multiplied by $4,300 and subtracted from annual wages, and the publication gives an optional "computational bridge" to convert an old form into the new data shape — [IRS Publication 15-T (2026)](https://www.irs.gov/publications/p15t)
- A new employee who gives no W-4 is treated as Single / Married filing separately with nothing in Steps 2 to 4; an employee first paid before 2020 with no form is treated as Single with zero allowances — [IRS Publication 15-T (2026)](https://www.irs.gov/publications/p15t)
- 2026 annual percentage-method bracket starts, standard schedule (rate: joint / single or married-separate / head of household): 10%: $19,300 / $7,500 / $15,550; 12%: $44,100 / $19,900 / $33,250; 22%: $120,100 / $57,900 / $83,000; 24%: $230,700 / $113,200 / $121,250; 32%: $422,850 / $209,275 / $217,300; 35%: $531,750 / $263,725 / $271,750; 37%: $788,000 / $648,100 / $656,150 — [IRS Publication 15-T (2026)](https://www.irs.gov/publications/p15t)
- 2026 "Step 2 checkbox" schedule bracket starts (joint / single / head of household): 10%: $16,100 / $8,050 / $12,075; 12%: $28,500 / $14,250 / $20,925; 22%: $66,500 / $33,250 / $45,800; 24%: $121,800 / $60,900 / $64,925; 32%: $217,875 / $108,938 / $112,950; 35%: $272,325 / $136,163 / $140,175; 37%: $400,450 / $328,350 / $332,375 — [IRS Publication 15-T (2026)](https://www.irs.gov/publications/p15t)
- Nonresident alien employees need an add-back to wages for withholding only: $16,100 a year ($619.20 biweekly, $1,341.70 monthly) with a 2020+ W-4, or $11,800 a year for those first paid before 2020 with no new form; the add-back does not apply to students and business apprentices from India — [IRS Publication 15-T (2026)](https://www.irs.gov/publications/p15t)
- What changed for 2026: tables updated for P.L. 119-21, which made the individual rates, the larger standard deduction and the end of personal exemptions permanent; the 2026 W-4 has a new "exempt from withholding" checkbox below Step 4(c) — [IRS Publication 15-T (2026)](https://www.irs.gov/publications/p15t)
- The 2026 W-4 Step 4(b) deductions worksheet gained lines for qualified tips and qualified overtime, so the employee lowers their own withholding through the form — [Patriot Software](https://www.patriotsoftware.com/blog/payroll/new-w-4-form-irs-changes/) (secondary)
- Employers keep calculating withholding on gross wages; the tips and overtime deductions are taken by the employee on their return — [KPMG Payroll Insights, Jan 2026](https://kpmg.com/kpmg-us/content/dam/kpmg/pdf/2026/payroll-insights-jan-2026.pdf) (secondary)
- Tips and overtime remain fully subject to Social Security and Medicare tax — [IRS Publication 15 (2026)](https://www.irs.gov/publications/p15)
- W-2 reporting for the new deductions starts with tax year 2026 forms, due 1 February 2027; 2025 W-2s were unchanged — [EY Tax News](https://taxnews.ey.com/news/2025-1745-irs-releases-draft-2026-forms-w-2-and-w-4-with-provisions-for-reporting-tips-and-overtime) (secondary)
- 2026 W-2 new Box 12 codes: TP (cash tips reported to the employer), TT (qualified overtime compensation), TA (employer contributions under a Section 128 Trump account programme); Box 14 is split into 14a "Other" and 14b "Treasury Tipped Occupation Code", which takes up to two codes and is required when TP is used — [Experian Employer Services](https://www.experian.com/blogs/employer-services/irs-finalizes-2026-form-w-2/) (secondary); [SSA EFW2 specification for 2026](https://www.ssa.gov/employer/efw/26efw2.pdf)
- From 2026 an employee can generally claim the overtime deduction only for overtime separately reported on the W-2, and cannot use a substitute Form 4852 to add more — [EY Tax News, 13 Aug 2026](https://taxnews.ey.com/news/2026-1738-irs-updates-2026-guidance-on-overtime-deduction-with-information-for-employers-and-employees) (secondary)
- Trump accounts: $5,000 annual contribution limit, employer may contribute up to $2,500 a year, starting 4 July 2026 — [IRS Publication 15 (2026)](https://www.irs.gov/publications/p15)

### Inferences
- The engine must store, per employee: W-4 version (pre-2020 or 2020+), filing status, Step 2 flag, Step 3 amount, Steps 4(a), 4(b), 4(c), exempt flag, allowances (old forms), and nonresident alien flag.
- "Qualified overtime" (the premium portion required by federal law) has to be tracked as its own year-to-date amount per employee to fill Box 12 code TT; this is a new data requirement, not a new tax calculation.
- Tipped employers need an occupation-code field on the employee or job record for Box 14b.

### Gaps
- The cumulative tax amount at each bracket start (column C of the tables) came back garbled from the page extract; read it from Publication 15-T directly or derive it from the bracket starts above.
- A draft W-2 code "TS" (non-qualified tips) was reported in 2025; I could not confirm whether it is in the final 2026 form. The final General Instructions for Forms W-2 and W-3 were not read directly.
- 2027 Publication 15-T and 2027 income tax brackets were not found; the IRS normally issues the final 15-T in December.

## FICA: Social Security, Medicare, Additional Medicare Tax

### Takeaway
Rates are fixed by statute and only the Social Security wage base moves each year; the 2027 base is already announced.

### Cited Findings

| Item | 2026 | 2027 |
|---|---|---|
| Social Security rate, employee and employer each | 6.2% | 6.2% |
| Social Security wage base | $184,500 | $192,300 |
| Maximum Social Security tax per side | $11,439.00 | $11,922.60 |
| Medicare rate, employee and employer each, no cap | 1.45% | 1.45% |
| Additional Medicare Tax, employee only | 0.9% on wages over $200,000 | not indexed |

- 2026 rates and the $184,500 wage base — [IRS Publication 15 (2026)](https://www.irs.gov/publications/p15)
- 2027 wage base of $192,300, announced 14 October 2026, with a maximum of $11,922.60 per side — [PayrollOrg](https://www.payroll.org/news-resources/news/news-detail/2026/10/14/social-security-wage-base-increases-to-$192-300-for-2027); [Journal of Accountancy](https://www.journalofaccountancy.com/news/2026/oct/social-security-wage-base-and-cola-announced-for-2027/)
- Additional Medicare Tax is 0.9% withheld on wages above $200,000 in a calendar year, with no employer share — [IRS Publication 15 (2026)](https://www.irs.gov/publications/p15)
- Household worker threshold $3,000 and election worker threshold $2,500 (2026) — [IRS Publication 15 (2026)](https://www.irs.gov/publications/p15)

### Inferences
- The employer withholds Additional Medicare Tax from the $200,000 mark regardless of the employee's filing status; the employee's true threshold is settled on their own return.
- The wage base is per employer, so year-to-date wages must be tracked per employee per legal employer (EIN).

### Gaps
- None material.

## FUTA and state unemployment (SUTA/SUI)

### Takeaway
Federal unemployment tax is small and uniform except in "credit reduction" states (California only for 2026); state unemployment tax is where the variation sits, with an employer-specific rate and a state-specific wage base that both change yearly.

### Cited Findings
- FUTA is 6.0% on the first $7,000 of each employee's wages, with a credit of up to 5.4% for state unemployment tax paid, leaving 0.6% net — [IRS Publication 15 (2026)](https://www.irs.gov/publications/p15)
- FUTA must be deposited once the undeposited amount is over $500; it is reported yearly on Form 940 — [IRS Publication 15 (2026)](https://www.irs.gov/publications/p15)
- For 2026, California is the only credit reduction state, at 1.5%; the US Virgin Islands repaid its loans by the 10 November 2026 deadline and is at 0.0%; the extra tax is due with the 2026 Form 940 on 1 February 2027 — [PayrollOrg, 13 Nov 2026](https://payroll.org/news-resources/news/news-detail/2026/11/13/california-faces-futa-credit-reduction-for-2026) (secondary, citing the Department of Labor's final notice)
- Every state's taxable wage base must be at least $7,000; 28 jurisdictions have a "flexible" base that can rise each year by formula — [PayrollOrg](https://payroll.org/news-resources/news/news-detail/2025/10/31/state-unemployment-insurance-taxable-wage-bases-for-2026) (secondary)
- Sample 2026 state wage bases: Washington $78,200 (2025: $72,800); New Jersey $44,800 (2025: $43,300); Nevada $43,700; Rhode Island $30,800, or $32,300 for employers at the highest rate; Massachusetts $15,000; Arizona $8,000; Alaska $54,200; Pennsylvania $10,000 — [EY Tax News](https://taxnews.ey.com/news/2026-0124-2026-state-unemployment-insurance-taxable-wage-bases) (secondary); [Rhode Island DLT](https://dlt.ri.gov/press-releases/2026-tax-rates-unemployment-insurance-and-temporary-disability-insurance); [Bloomberg Government](https://news.bgov.com/payroll/new-jersey-issues-2026-unemployment-family-leave-wage-bases)
- Wage bases rose 1.9% on average from 2025 to 2026, and Iowa, Louisiana, Missouri and Oklahoma lowered theirs — [Equifax Workforce Solutions](https://workforce.equifax.com/all-blogs/-/post/outlook-for-potential-federal-and-state-unemployment-insurance-tax-rates-in-2026-and-beyond) (secondary)
- Employees pay unemployment contributions in three states only: Alaska 0.50% up to $54,200; New Jersey; Pennsylvania 0.07% on all wages with no cap — [Patriot Software](https://www.patriotsoftware.com/payroll/training/blog/what-is-my-state-unemployment-tax-rate/) (secondary)
- New Jersey employee rate: EY lists 0.3825% up to $44,800 — [EY Tax News](https://taxnews.ey.com/news/2026-0124-2026-state-unemployment-insurance-taxable-wage-bases); contradicted by [Deel](https://deel.com/blog/state-unemployment-insurance-rates), which lists 0.425% including a 0.0425% workforce surcharge

### Inferences
- California employers pay an effective FUTA rate of 2.1% for 2026 (0.6% + 1.5%), about $147 per employee instead of $42.
- The state unemployment rate is a per-employer, per-state, per-year setting that the employer receives by notice from the state; it cannot be looked up from a public table, so the product needs a configuration field with an effective date.
- Some states change the rate or base mid-year (New Jersey's fiscal year runs July to June), so effective dates must be finer than a calendar year.

### Gaps
- A full 50-state table of 2026 wage bases and rate ranges was not extracted; the EY chart updated 29 July 2026 ([link](https://taxnews.ey.com/news/2026-1640-state-unemployment-insurance-wage-bases-and-tax-rates-for-2026-updated-as-of-july-29-2026)) and the Equifax 2026 SUI guide hold it.
- Whether California's "benefit cost rate" add-on (reported earlier in 2026 as a possible extra 3.8%) was applied or waived is not confirmed; the November PayrollOrg article gives only the 1.5%.
- 2027 state wage bases are only partly announced as of this date and were not collected.

## State income tax: no-tax states, state forms, reciprocity, multi-state and remote workers

### Takeaway
Nine states have no wage income tax; most of the rest need their own withholding certificate and their own formula, and where the employee lives and works in different states the rules are set state-pair by state-pair.

### Cited Findings
- No state wage income tax and no state W-4: Alaska, Florida, Nevada, New Hampshire, South Dakota, Tennessee, Texas, Washington, Wyoming — [Paylocity, 2026 state withholding forms](https://www.paylocity.com/resources/tax-compliance/state-tax-withholding-forms/) (secondary)
- A few states use the federal W-4 for state withholding: Colorado, New Mexico, North Dakota and Utah per Gusto — [Gusto](https://support.gusto.com/article/201398639100000/State-and-local-withholding-certificates) (secondary); other 2026 charts omit Colorado, so sources disagree
- Most other income-tax states require their own certificate, for example Ohio IT-4, Georgia G-4, Nebraska W-4N, Illinois IL-W-4, and Minnesota W-4MN in addition to the federal form — [Paylocity](https://www.paylocity.com/resources/tax-compliance/state-tax-withholding-forms/) (secondary)
- Reciprocity: about 30 agreements across 16 states plus DC in 2026 (Arizona, Illinois, Indiana, Iowa, Kentucky, Maryland, Michigan, Minnesota, Montana, New Jersey, North Dakota, Ohio, Pennsylvania, Virginia, West Virginia, Wisconsin); Kentucky has the most partners, seven — [OnPay](https://onpay.com/insights/employers-guide-to-state-tax-reciprocity-agreements/) (secondary)
- "Convenience of the employer" rule: New York taxes wages of an employee assigned to a New York office even for days worked remotely elsewhere unless the remote work is an employer necessity; a July 2026 appellate decision upheld it — [Bloomberg Tax](https://news.bloomberglaw.com/payroll/ny-convenience-of-the-employer-rule-is-an-audit-risk) (secondary)
- Pennsylvania and Delaware keep similar necessity-versus-convenience tests; Connecticut and New Jersey apply a "mirror" rule only against residents of states that have such a rule; Nebraska changed its rule from 1 January 2025 to include a 7-day threshold — [Thomson Reuters](https://tax.thomsonreuters.com/news/capital-summit-session-warns-multistate-payroll-compliance-risks-are-growing-with-remote-work/) (secondary); [KPMG Payroll Insights, Oct 2026](https://kpmg.com/kpmg-us/content/dam/kpmg/pdf/2026/payroll-insights-october.pdf) (secondary)
- The count of states with a convenience rule is given as six by an older source and eight by a 2026 source — [NCSL](https://www.ncsl.org/fiscal/state-and-local-tax-considerations-of-remote-work-arrangements/quot); contradicted by [Taxes for Expats](https://www.taxesforexpats.com/articles/expat-tax-rules/convenience-of-employer-rule.html)
- Alabama announced in 2026 that it will not require withholding on nonresident wages for work done outside the state — [EY Tax News, 9 Sep 2026](https://taxnews.ey.com/news/2026-1919-alabama-will-not-require-employers-to-withhold-income-tax-on-nonresident-wages-earned-for-work-performed-outside-the-state) (secondary)
- As many as 10 states lowered income tax withholding rates for 2026, and EY reissued its state withholding rate chart in January, June and August 2026 — [EY Tax News](https://taxnews.ey.com/news/2025-2591-many-states-have-lowered-their-personal-income-tax-rates-in-2026) (secondary)

### Inferences
- Each employee needs both a residence address and one or more work locations; the engine decides per pay run which state gets withholding (work state first, resident state for any difference, or resident state only under reciprocity when the employee files the exemption certificate).
- State withholding formulas change at any point in the year, not only on 1 January, so tables need effective dates.

### Gaps
- No primary-source list was read for reciprocity pairs or for which states accept the federal W-4; treat both lists as needing a check against each state's revenue department.
- Day-count thresholds for nonresident withholding (for example states that wait 30 days) were not researched.

## Local taxes and address-level geocoding

### Takeaway
Local wage taxes are concentrated in a handful of states but number in the thousands of jurisdictions, and whether a tax applies depends on the exact home or work address, not the postal code.

### Cited Findings
- Pennsylvania levies an Earned Income Tax (EIT) and a Local Services Tax (LST) through municipalities and school districts, across roughly 2,500 to 3,000 taxing jurisdictions (sources differ) — [Symmetry local payroll tax guide](https://www.symmetry.com/local-payroll-tax-compliance/) (secondary)
- Ohio has over 600 municipalities with an income tax plus separate school district income taxes; municipal tax generally follows the work location — [Symmetry](https://www.symmetry.com/local-payroll-tax-compliance/) (secondary)
- Kentucky cities and counties levy occupational taxes with no central collection, so the employer files with each jurisdiction separately — [Thomson Reuters](https://tax.thomsonreuters.com/news/capital-summit-session-highlights-growing-risks-and-tools-for-local-payroll-tax-compliance/) (secondary)
- All 92 Indiana counties levy an income tax, based on where the employee lives on 1 January — [Gusto](https://gusto.com/resources/articles/taxes/local-taxes) (secondary)
- New York has a New York City resident tax and Yonkers resident and nonresident taxes — [Zoho Payroll](https://www.zoho.com/us/payroll/help/employer/taxes-and-forms/unsupported-taxes.html) (secondary)
- One vendor dataset counts 4,246 local jurisdictions in the US that levy a payroll-related tax — [payroll tax API vendor blog](https://payroll-tax-api-9f4b18020da9.herokuapp.com/blog/state-of-local-payroll-taxes-2024-2026) (secondary, a vendor's own count, unverified)
- Ohio school district levies can change after elections, outside the 1 January cycle — [same vendor blog](https://payroll-tax-api-9f4b18020da9.herokuapp.com/blog/how-local-payroll-taxes-work) (secondary)

### Inferences
- Jurisdiction boundaries (municipality, school district, county) do not follow postal codes, so a street address must be resolved to latitude and longitude and matched to boundary data; this is why payroll products buy a geocoding tax-location service rather than keep a postal-code table.
- Pennsylvania needs both the home and work address to pick the higher of the resident and nonresident EIT rates, plus a flat LST by work location.

### Gaps
- No government or independent count of local payroll tax jurisdictions was found; the common "over 10,000 jurisdictions" figure could not be sourced.
- Local taxes in Michigan, Missouri, Alabama, Colorado (head taxes), Oregon (transit taxes), Maryland counties and others were not researched.

## State disability and paid family and medical leave contributions

### Takeaway
About fifteen jurisdictions now run a mandatory disability or paid-leave payroll contribution, each with its own rate, wage cap, employee/employer split and employer-size rule; Minnesota started in 2026 and Maryland starts in 2027.

### Cited Findings

2026 figures, all from the EY summary chart of 7 January 2026 (secondary) — [EY Tax News 2026-0131](https://taxnews.ey.com/news/2026-0131-2026-state-disability-paid-family-and-medical-leave-and-long-term-care-insurance-wage-base-and-rates)

| Programme | Total rate | Employee share | Employer share | Wage cap |
|---|---|---|---|---|
| California SDI (includes paid family leave) | 1.3% | all | none | none |
| Colorado FAMLI | 0.88% | half; all of own half only if under 10 staff | half if 10+ staff, else none | $184,500 |
| Connecticut | 0.5% | all | none | $184,500 |
| Delaware | 0.8% (0.4 medical, 0.08 family, 0.32 parental) | employer may deduct up to half | rest; 10 to 24 staff parental only; under 10 exempt | $184,500 |
| Hawaii disability | plan cost | half, capped at 0.5% of weekly wages, max $7.50 a week | balance | $1,500.21 weekly |
| Maine | 1.0% | half | half if 15+ staff, else none | $184,500 |
| Maryland | set by state, cap 1.2% | half | half | $184,500; contributions start 1 Jan 2027 |
| Massachusetts | 0.88% | 0.46% (0.28 medical, 0.18 family) | 0.42% if 25+ staff, else none | $184,500 |
| Minnesota (new 1 Jan 2026) | 0.88% | up to half | at least half | $185,000 as listed by EY |
| New Jersey disability | 0.19% employee | 0.19% | 0.1% to 0.75% experience-rated, 0.5% new | $171,100 employee, $44,800 employer |
| New Jersey family leave | 0.23% | all | none | $171,100 |
| New York disability | 0.5% | capped at $0.60 a week | balance of cost | none |
| New York paid family leave | 0.432% | all, max $411.91 a year | none | $95,347.22 |
| Oregon | 1.0% | 60% | 40% if 25+ staff, else none | $184,500 |
| Rhode Island TDI (includes family leave) | 1.1% | all | none | $100,000 |
| Washington PFML | 1.13% | 71.43% | 28.57% if 50+ staff, else none | $184,500 |
| Washington WA Cares (long-term care) | 0.58% | all | none | none |
| Puerto Rico disability | 0.6% | 0.3% | 0.3% | $9,000 |

- DC funds paid leave by an employer-only payroll tax on total wages; EY lists 0.75% through 31 December 2025 and no 2026 rate — [EY Tax News 2026-0131](https://taxnews.ey.com/news/2026-0131-2026-state-disability-paid-family-and-medical-leave-and-long-term-care-insurance-wage-base-and-rates) (secondary)
- Vermont and New Hampshire run voluntary programmes, not mandatory contributions — [EY Tax News 2026-0131](https://taxnews.ey.com/news/2026-0131-2026-state-disability-paid-family-and-medical-leave-and-long-term-care-insurance-wage-base-and-rates) (secondary)
- California's rate rose from 1.2% (2025) to 1.3% (2026); Colorado's fell from 0.90% to 0.88% — [Mercer 2026 PFML chart](https://marketingpreview.mercer.com/content/dam/mercer-dotcom/migrated-assets/blogs/law-and-policy/2026/02/pdf-2026-state-paid-family-and-medical-leave-contributions-and-benefits.pdf) (secondary)
- Massachusetts 2027: total stays 0.88%, but a 2026 law moves the employer share from the medical leave part to the family leave part — [Holland & Knight, 15 Oct 2026](https://www.hklaw.com/en/insights/publications/2026/10/massachusetts-announces-paid-family-and-medical-leave-updates-for-2027) (secondary)

### Inferences
- Programmes capped at the Social Security wage base (CO, CT, DE, ME, MD, MA, OR, WA) will move to $192,300 for 2027 if they keep that link; each state confirms its own figure.
- The split depends on employer headcount, and each state counts headcount its own way, so the product needs a per-state employer-size setting.
- Several states allow an approved private plan instead of the state fund, which switches the contribution off in payroll; this needs a per-state employer flag.

### Gaps
- Minnesota's $185,000 cap in the EY chart differs from the $184,500 Social Security base; another snippet gave a different Minnesota rate. Confirm with Minnesota Paid Leave.
- Maryland's actual 2027 rate, and 2027 rates for all other states, were not collected.
- Maximum annual contribution amounts for most states were not extracted.

## Deposits, returns and year-end filings

### Takeaway
Federal tax is deposited electronically on a monthly or semi-weekly schedule set by a lookback rule, reported quarterly on Form 941 and yearly on Forms 940 and W-2; almost every employer must now file W-2s and 1099s electronically.

### Cited Findings

| Rule | Value |
|---|---|
| Monthly depositor | $50,000 or less in the lookback period; deposit by the 15th of next month |
| Semi-weekly depositor | over $50,000; Wed-Fri paydays due next Wednesday, Sat-Tue paydays due next Friday |
| Lookback period for 2026 | 1 July 2024 to 30 June 2025 |
| Next-day rule | $100,000 or more accumulated on any day; due next business day |
| Pay with return instead | quarter's liability under $2,500 |
| Form 941 due | 30 April, 31 July, 31 October, 31 January; 10 extra days if all deposits were on time and in full |
| Form 944 (annual) | only on IRS written notice, expected yearly tax $1,000 or less |
| FUTA deposit trigger | over $500 undeposited |
| E-file threshold | 10 or more information returns in total, all types counted together |
| 1099-NEC threshold | $2,000 for payments made in 2026 (was $600) |

- Deposit schedule, lookback, next-day and $2,500 rules — [IRS Publication 15 (2026)](https://www.irs.gov/publications/p15)
- A monthly depositor that hits the $100,000 rule becomes semi-weekly for the rest of that year and all of the next — [IRS Publication 15 (2026)](https://www.irs.gov/publications/p15)
- Deposits must be made by electronic funds transfer through EFTPS, IRS Direct Pay or the IRS business tax account — [IRS Publication 15 (2026)](https://www.irs.gov/publications/p15)
- Late deposit penalties: 2% for 1 to 5 days, 5% for 6 to 15 days, 10% beyond that, 15% if unpaid more than 10 days after an IRS notice; no penalty if the shortfall is within the greater of $100 or 2% and is made up in time — [IRS Publication 15 (2026)](https://www.irs.gov/publications/p15)
- Form 941 due dates, Form 944 conditions, annual Form 940 and Form 945 — [IRS Publication 15 (2026)](https://www.irs.gov/publications/p15)
- 2026 Form 940 is due 1 February 2027 because 31 January 2027 is a Sunday — [PayrollOrg](https://payroll.org/news-resources/news/news-detail/2026/11/13/california-faces-futa-credit-reduction-for-2026) (secondary)
- 2026 Forms W-2 are due 1 February 2027 — [EY Tax News](https://taxnews.ey.com/news/2025-1745-irs-releases-draft-2026-forms-w-2-and-w-4-with-provisions-for-reporting-tips-and-overtime) (secondary)
- Filers of 10 or more information returns in a year, counting W-2s and 1099s together, must file electronically — [IRS](https://www.irs.gov/businesses/small-businesses-self-employed/Am-I-Required-to-File-a-Form-1099-or-Other-Information-Return)
- The 1099-NEC and 1099-MISC threshold rises from $600 to $2,000 for payments made in 2026, and the W-2 threshold where no tax was withheld is also $2,000 — [IRS Publication 15 (2026)](https://www.irs.gov/publications/p15)
- The $2,000 threshold is indexed for inflation from tax year 2027 — [Patriot Software](https://www.patriotsoftware.com/blog/accounting/1099-reporting-threshold/) (secondary)
- 2026 Forms 1099-NEC are due to the IRS on 1 February 2027 — [TaxBandits](https://www.taxbandits.com/1099-forms/1099-reporting-requirements/) (secondary); [Xero](https://www.xero.com/us/accountant-bookkeeper-guides/1099-filing-requirements/) states 31 January 2027, which is the unadjusted date
- SSA publishes the electronic W-2 file layout (EFW2) each year; the 2026 layout carries the new Box 12 codes — [SSA EFW2 2026](https://www.ssa.gov/employer/efw/26efw2.pdf)
- Employment tax records must be kept at least 4 years — [IRS Publication 15 (2026)](https://www.irs.gov/publications/p15)

### Inferences
- The engine needs a liability ledger by pay date (not pay period end), because the deposit due date and the next-day rule are driven by the day wages are paid.
- Fourth-quarter 2026 Form 941 is also due 1 February 2027 by the same weekend rule.
- Each state with income tax or unemployment tax adds its own deposit schedule and a quarterly wage report listing every employee's wages, so the filing count grows with the number of states the employer has staff in.

### Gaps
- State quarterly wage report formats and due dates were not researched state by state; most fall on the last day of the month after quarter end, but this was not sourced.
- State W-2 filing deadlines and state e-file thresholds were not researched.
- 2027 lookback period dates were not read from a source (by the rule they would be 1 July 2025 to 30 June 2026).

## Employer registrations

### Takeaway
An employer needs one federal number plus two accounts in most states where it has an employee, and sometimes local accounts as well.

### Cited Findings
- Kentucky has no central local-tax collection, so employers register and file separately with each city or county — [Thomson Reuters](https://tax.thomsonreuters.com/news/capital-summit-session-highlights-growing-risks-and-tools-for-local-payroll-tax-compliance/) (secondary)
- State unemployment tax is paid at an employer-specific rate on a state-specific wage base, which requires a state unemployment account — [Paylocity](https://www.paylocity.com/resources/tax-compliance/suta-tax) (secondary)

### Inferences
- Minimum set per employer: federal EIN; per state with staff, a withholding account (unless the state has no income tax) and an unemployment insurance account; per paid-leave state, a paid-leave account where it is separate from unemployment; local accounts in Pennsylvania, Ohio, Kentucky and similar.
- One remote hire in a new state triggers new registrations before the first pay run there, so the product should block or warn on an unregistered work state.

### Gaps
- No primary source was read on EIN application or on each state's registration steps and lead times; this key question is answered mostly by inference.

## Pre-tax and post-tax deductions and 2026/2027 limits

### Takeaway
Each deduction type reduces a different set of taxes, so the engine needs a per-deduction, per-tax "taxability" matrix rather than a single pre-tax flag.

### Cited Findings

| Limit | 2026 | 2027 |
|---|---|---|
| 401(k), 403(b), 457 employee deferral | $24,500 | $25,500 |
| Catch-up, age 50 and over | $8,000 | $8,500 |
| Catch-up, age 60 to 63 | $11,250 | not found |
| Catch-up must be Roth if prior-year FICA wages exceed | $150,000 (2025 wages) | not found |
| HSA, self-only / family | $4,400 / $8,750 | $4,500 / not found |
| HSA catch-up, age 55 and over | $1,000 | not found |
| Health FSA salary reduction / carryover | $3,400 / $680 | not found |
| Commuter transit and parking, per month | $340 | not found |

- 2026 retirement and benefit limits — [Mayer Brown](https://www.mayerbrown.com/en/insights/publications/2025/12/irs-annual-limits-for-benefit-plans-2026-cost-of-living-adjustments) (secondary); [WTW](https://www.wtwco.com/en-us/insights/2025/11/2026-inflation-adjusted-limits-for-a-wide-range-of-employee-benefit-plans) (secondary)
- 2026 is the first year employers must apply the Roth catch-up rule for employees whose prior-year FICA wages exceeded $150,000 — [DLA Piper](https://www.dlapiper.com/en-pt/insights/publications/2025/11/irs-announces-2026-retirement-and-benefit-plan-limits-ssa-announces-cola-adjustment) (secondary)
- 2027 deferral limit $25,500 and catch-up $8,500, released 12 November 2026; IRA limit unchanged at $7,500 — [InvestmentNews](https://www.investmentnews.com/practice-management/irs-lifts-2027-401k-limit-to-25500-holds-ira-cap-at-7500/269012) (secondary); [NAPA](https://www.napa-net.org/news/2026/11/breaking-irs-announces-2027-401k-contribution-limits/) (secondary)
- 2027 self-only HSA limit $4,500 — [401(k) Specialist](https://401kspecialistmag.com/irs-makes-it-official-2027-401k-contribution-limit-rises-to-25500/) (secondary)
- 2026 commuter exclusion $340 a month — [Patriot Software](https://www.patriotsoftware.com/blog/payroll/a-look-at-qualified-pre-tax-deductions/) (secondary)
- Traditional 401(k) deferrals reduce federal income tax wages but stay subject to Social Security, Medicare and FUTA — [Asure Software](https://www.asuresoftware.com/how-pre-tax-benefits-like-401k-and-health-insurance-affect-your-payroll-tax-calculations/) (secondary)
- Section 125 cafeteria plan health premiums are exempt from federal income tax withholding, FICA and FUTA, and need a written plan document — [Patriot Software](https://www.patriotsoftware.com/blog/payroll/a-look-at-qualified-pre-tax-deductions/) (secondary)
- Roth 401(k) contributions are after-tax and reduce no tax base — [Gusto](https://gusto.com/resources/articles/taxes/pre-tax-deductions-contributions) (secondary)

### Inferences
- The matrix has at least these columns per deduction code: federal income tax, Social Security, Medicare, FUTA, state income tax (per state), state unemployment (per state), local tax (per jurisdiction); states and Pennsylvania localities do not always follow the federal treatment.
- Annual limits need year-to-date tracking per employee with age-based catch-up tiers, and the Roth catch-up rule needs the prior year's Social Security wages from the same employer.

### Gaps
- The treatment table was sourced from vendor articles only; IRS Publication 15 and 15-B were not read for it, and sources were noted as disagreeing on FUTA treatment of some items (HSA).
- 2027 health FSA, commuter, family HSA and age 60 to 63 catch-up figures were not found in this run.
- State-by-state departures from federal pre-tax treatment were not researched.

## Wage garnishments

### Takeaway
Federal law caps how much of "disposable earnings" can be taken and the cap differs by order type; the order of priority between a support order and a tax levy is not a single fixed rule.

### Cited Findings
- Ordinary creditor garnishment is capped at the lesser of 25% of disposable earnings or the amount by which weekly disposable earnings exceed 30 times the federal minimum wage ($7.25, so $217.50 a week); the cap applies in total however many orders arrive — [US Department of Labor, Fact Sheet 30](https://www.dol.gov/agencies/whd/fact-sheets/30-cppa)
- Child support and alimony orders may take up to 50% if the employee supports another spouse or child and 60% if not — [US Department of Labor, Fact Sheet 30](https://www.dol.gov/agencies/whd/fact-sheets/30-cppa)
- Those limits rise to 55% and 65% when support is 12 or more weeks in arrears — [SSA POMS GN 02410.215](https://secure.ssa.gov/poms.nsf/LNX/0202410215)
- Where state law is stricter, the rule that takes less from the employee applies; California, for example, is listed at a lower percentage tied to the state minimum wage — [Thomson Reuters](https://www.thomsonreuters.com/en-us/help/accounting-cs/set-up-employees-and-payroll-items/garnishment-limits) (secondary)
- The percentage caps do not apply to bankruptcy orders or to federal or state tax levies — [US Department of Labor, Fact Sheet 30](https://www.dol.gov/agencies/whd/fact-sheets/30-cppa)
- An employer may not dismiss an employee because of garnishment for any single debt — [US Department of Labor, Fact Sheet 30](https://www.dol.gov/agencies/whd/fact-sheets/30-cppa)
- Priority: vendor guidance puts child support first, then federal levy, bankruptcy, creditor garnishment, student loans — [Thomson Reuters](https://www.thomsonreuters.com/en-us/help/accounting-cs/set-up-employees-and-payroll-items/garnishment-limits) (secondary); contradicted in part by [Paychex](https://www.paychex.com/articles/payroll-taxes/calculating-wage-garnishments), which says that between a support order and a federal levy the one received first controls

### Inferences
- The engine needs a defined "disposable earnings" figure (gross less legally required deductions such as taxes, not voluntary deductions), an order type, a received date, and per-state limit rules.
- A federal tax levy is calculated differently from the rest: the IRS exempt amount comes from a table by filing status and dependants, and everything above it is taken.

### Gaps
- IRS Publication 1494 exempt amounts for 2026 were not found.
- State-by-state garnishment limits and state rules on employer administrative fees were not researched.

## Pay stubs, pay frequency and final pay

### Takeaway
Federal law requires none of these in detail; each state sets its own rules, so the product needs a per-state rule set for stub content, minimum pay frequency and final-pay deadline.

### Cited Findings
- There is no federal pay stub mandate; nine states do not require a stub — [Paycor](https://www.paycor.com/resource-center/articles/pay-stub-requirements-by-state/) (secondary); the list of the nine differs between Paycor and [Zippia](https://www.zippia.com/advice/?p=63791), so it is unresolved
- Typical required stub items are rate of pay, hours worked, gross and net wages, and each deduction itemised — [Economic Policy Institute](https://files.epi.org/uploads/Wage-payment-State-solutions-to-the-workers-rights-crisis-Economic-Policy-Institute.pdf)
- Most states set a minimum pay frequency, sometimes by industry or worker class; New York requires weekly pay for manual workers; some states have no frequency law — [ADP](https://www.adp.com/spark/articles/2024/11/review-pay-frequency-laws-in-states-where-you-have-employees.aspx) (secondary, dated 2024)
- Federal law only requires final wages by the next regular payday; many states require faster payment on dismissal, sometimes immediately, and treat resignation differently — [Paycom](https://www.paycom.com/resources/blog/final-paycheck-laws-by-state/) (secondary)
- Some states require unused paid time off to be paid out with final wages — [Paylocity](https://www.paylocity.com/resources/library/articles/final-paycheck-laws) (secondary)
- Alabama, Florida, Georgia and Mississippi are listed as having no state final-pay law — [Rippling](https://www.rippling.com/en-IE/blog/final-paycheck-for-terminated-employee) (secondary)

### Inferences
- Immediate final pay in some states means the product must support an off-cycle pay run with full tax calculation.
- Stub layout should be driven by the work state, with a superset of fields available.

### Gaps
- No state-by-state table was compiled for stub items, pay frequency or final-pay deadlines; vendor tables disagree with each other and state labour department text was not read.

## How often rules change and how many jurisdictions exist

### Takeaway
Rules change at federal, state and local level throughout the year, not once; jurisdictions number in the thousands, which is why most payroll products license a tax engine rather than maintain rates in-house.

### Cited Findings
- Fixed annual federal cycle: Social Security wage base announced mid-October (14 October 2026 for 2027) — [PayrollOrg](https://www.payroll.org/news-resources/news/news-detail/2026/10/14/social-security-wage-base-increases-to-$192-300-for-2027); retirement limits in November (12 November 2026 for 2027) — [NAPA](https://www.napa-net.org/news/2026/11/breaking-irs-announces-2027-401k-contribution-limits/); FUTA credit reduction fixed after 10 November — [PayrollOrg](https://payroll.org/news-resources/news/news-detail/2026/11/13/california-faces-futa-credit-reduction-for-2026); Publication 15-T final in December (2026 edition released December 2025) — [PayrollOrg](https://payroll.org/news-resources/news/news-detail/2025/12/12/irs-releases-2026-publication-15-t-includes-obbba-information)
- State withholding rates changed often enough in 2026 that EY reissued its all-state chart in January, June and August — [EY Tax News, 28 Aug 2026](https://taxnews.ey.com/news/2026-1857-2026-state-supplemental-flat-tax-and-highest-income-tax-withholding-rates-with-hyperlinks-to-the-latest-withholding-tables-instructions-updated-august-28-2026) (secondary)
- State unemployment wage bases and rates were reissued by EY in January and again in July 2026 — [EY Tax News, 29 Jul 2026](https://taxnews.ey.com/news/2026-1640-state-unemployment-insurance-wage-bases-and-tax-rates-for-2026-updated-as-of-july-29-2026) (secondary)
- Local rates mostly change on 1 January but also mid-year, often with little notice — [Gusto](https://gusto.com/resources/articles/taxes/local-taxes) (secondary)
- One vendor counts 4,246 local jurisdictions with a payroll-related tax — [vendor blog](https://payroll-tax-api-9f4b18020da9.herokuapp.com/blog/state-of-local-payroll-taxes-2024-2026) (secondary, unverified)
- The number of local income taxes has declined over recent decades — [Tax Foundation](https://taxfoundation.org/research/all/state/local-income-taxes-city-and-county-level-income-and-wage-taxes-continue-wane/)
- Symmetry's marketing puts the cost of building payroll tax compliance in-house at over $4 million — [vendor blog quoting Symmetry](https://payroll-tax-api-9f4b18020da9.herokuapp.com/blog/multi-state-payroll-local-tax-problem) (secondary, a competitor's report of a marketing claim)

### Inferences
- Order of magnitude: 1 federal, 51 state-level (50 states plus DC), about 15 disability or paid-leave programmes, and several thousand local jurisdictions; the supportable figure from this research is "thousands", not "tens of thousands".
- Every rate, wage base and threshold in the product should be data with an effective-from date, loaded without a code release; a law such as P.L. 119-21 (signed July 2025, W-2 changes for 2026) shows that reporting fields can also change with about six months' notice.

### Gaps
- No authoritative count of rate changes per year or of total taxing jurisdictions was found; commonly quoted figures ("over 10,000 jurisdictions", "hundreds of changes a year") could not be traced to a source in this run.
