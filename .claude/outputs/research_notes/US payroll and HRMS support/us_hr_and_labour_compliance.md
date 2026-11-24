# US HR, time and labour compliance for an HRMS (as of 24 Nov 2026)

| Area | Headline fact for the software team | Confidence |
|---|---|---|
| FLSA exempt threshold | $684/week ($35,568/yr); HCE $107,432; formally restored by DOL 14 May 2026 | High (DOL) |
| Overtime | 1.5x over 40 h per workweek federally; daily rules in CA, AK, NV, CO | High / medium on state detail |
| Minimum wage | Federal $7.25, tipped cash $2.13; states and cities set their own, several change mid-year | High |
| Onboarding | I-9 edition 01/20/25, expires 05/31/2027; E-Verify mandatory in roughly 10-11 states for private employers | Medium (sources conflict on states) |
| Leave | No federal paid leave; FMLA unpaid 12 weeks; about 19-21 states + DC with paid sick leave; 14 states + DC with PFML | Medium |
| ACA | 2026 affordability 9.96%, 2027 10.22%; 1095-C due 2 Mar / e-file 31 Mar 2026 | High |
| EEO-1 | EEOC proposed rescission 23 Jul 2026; not final; 2025 cycle never opened | High on status, outcome pending |
| Privacy | No federal law; CCPA covers employee data since 1 Jan 2023; BIPA damages now per person | High |
| Offshore hosting | No general US data-localisation law; DOJ bulk-data rule restricts access by "countries of concern" only | Medium |

**How to read these notes.** Almost every cited finding below comes from search-result summaries of the linked pages, not a full read of each page. Only the DOL FMLA fact sheet (#28) was fetched and read in full. Items the researcher knows from background but could not source in this session are listed under Gaps and labelled "background, unverified".

## FLSA: classification, salary threshold, overtime, regular rate, workweek, records, state overtime

### Takeaway
The federal exempt salary floor in force in 2026 is $684 per week, formally restored by a DOL technical amendment on 14 May 2026, and overtime is weekly at federal level but daily in four states. The HRMS needs a per-employee FLSA status, a per-employer fixed workweek, and state-specific overtime rule sets rather than one global rule.

### Cited Findings
- On 14 May 2026 DOL issued a technical amendment requiring most exempt executive, administrative and professional employees to be paid at least $684 per week, after rescinding the 2024 rule that would have raised it to $844 and then $1,128 per week — [DOL news release](https://www.dol.gov/newsroom/releases/whd/whd20260514); [Federal Register 2026-09839](https://www.federalregister.gov/documents/2026/05/15/2026-09839/defining-and-delimiting-the-exemptions-for-executive-administrative-professional-outside-sales-and)
- The highly compensated employee threshold is $107,432 per year, of which at least $684 per week must be paid on a salary or fee basis — [Littler](https://www.littler.com/news-analysis/asap/department-labor-restores-salary-levels-flsa-white-collar-exemptions)
- The federal figure does not override higher state exempt thresholds; Alaska, California and Washington are named as having higher ones — [SHRM](https://www.shrm.org/topics-tools/employment-law-compliance/dol-amendment-formally-restores-2019-overtime-exemption)
- Some vendor guides still show the vacated $844/week ($43,888) figure; these are out of date — [Homebase (stale example)](https://joinhomebase.com/blog/federal-overtime-salary-threshold-2020-whats-changing/)
- California exempt salary minimum: $70,304/yr from 1 Jan 2026; $72,384/yr ($1,392/week) from 1 Jan 2027. It is tied to the state minimum wage ($16.90 in 2026, $17.40 in 2027). Meeting the salary alone does not make an employee exempt; duties tests still apply — [Thompson Coburn](https://www.thompsoncoburn.com/insights/new-california-2026-minimum-wage-will-also-increase-salary-threshold-for-many-exempt-employees/); [SHRM on 2027](https://www.shrm.org/in/topics-tools/employment-law-compliance/californias-statewide-minimum-wage-increasing-again-2027); [California DIR](https://www.dir.ca.gov/DIRNews/2025/2025-118.html)
- California sets a separate, annually adjusted compensation rate for the computer professional exemption — [Morgan Lewis](https://www.morganlewis.com/pubs/2026/10/california-announces-increased-compensation-rates-for-computer-professional-exemption-in-2027)
- Daily overtime: Alaska, California and Nevada require overtime premiums for hours beyond 8 in a day; Colorado's daily trigger is 12 hours — [SHRM](https://shrm.org/topics-tools/tools/hr-answers/states-daily-overtime-pay-laws)
- California pays double time for hours over 12 in a day and for hours over 8 on a seventh consecutive day of work. Colorado's premium after 12 hours is 1.5x, not double — [Paylocity](https://www.paylocity.com/resources/learn/articles/overtime-pay-laws-by-state/); [Bloomberg Law](https://pro.bloomberglaw.com/insights/labor-employment/overtime-pay-laws-by-state/)
- One guide states Alaska also has a double-time tier after 12 hours; this is a single secondary source and conflicts with the usual description of Alaska as 1.5x only — [Harvest](https://www.getharvest.com/calculators/which-states-have-daily-overtime) (treat as unverified)
- Nevada's daily overtime applies only to workers below an hourly-rate ceiling; one source gives "less than $18 an hour" and that figure may be outdated — [Paycor](https://www.paycor.com/resource-center/articles/overtime-pay-laws-by-state/) (verify against statute)
- Nondiscretionary bonuses must be included in the regular rate of pay unless another statutory exclusion applies; a bonus employees know about and expect (for example a safety bonus) is nondiscretionary; the label on the bonus does not decide it — [DOL Fact Sheet #56C](https://www.dol.gov/agencies/whd/fact-sheets/56c-bonuses); [DOL Fact Sheet #56A](https://www.dol.gov/agencies/whd/fact-sheets/56a-regular-rate)
- Regular-rate exclusions are listed at 29 CFR 778.200-.225 and bonus rules at 778.208-.211; DOL updated the regular-rate rules in 2019 — [eCFR Part 778](https://www.ecfr.gov/current/title-29/subtitle-B/chapter-V/subchapter-B/part-778); [DOL regular rate page](https://www.dol.gov/agencies/whd/fact-sheets/regular-rate)
- Record retention: payroll records, collective bargaining agreements and sales/purchase records at least 3 years; records on which wage computations are based (time cards, piece-work tickets, wage rate tables, work and time schedules, records of additions to or deductions from wages) 2 years. Basis is 29 CFR Part 516 — [DOL Fact Sheet #21](https://www.dol.gov/agencies/whd/fact-sheets/21-flsa-recordkeeping)
- Records must be available for inspection within 72 hours of a Wage and Hour Division notice; employers must also keep records for exempt employees; state law may require longer retention — [SHRM](https://shrm.org/topics-tools/employment-law-compliance/how-to-comply-payroll-record-keeping-requirements); [DOL Fact Sheet #21](https://www.dol.gov/agencies/whd/fact-sheets/21-flsa-recordkeeping)
- DOL's Fact Sheet #21 page is labelled "Revised July 2008"; no newer revision was found — [DOL Fact Sheet #21](https://www.dol.gov/agencies/whd/fact-sheets/21-flsa-recordkeeping)

### Inferences
- FLSA status (exempt / non-exempt) must be a field on the employee record, with the exemption type and salary basis, and a validation against both the federal floor and the work-state floor, which changes every 1 January in California.
- Overtime cannot be a single "hours over N per day" setting copied from Indian practice. It needs a rule engine keyed by work state: weekly 40 everywhere, plus daily 8 (CA, AK, NV with conditions), daily 12 (CO), CA double time and seventh-day rules.
- Overtime pay is not "1.5 x base hourly rate". It is 1.5 x a regular rate recomputed each workweek to include nondiscretionary bonuses, so the HRMS must pass bonus type (discretionary or not) and the period it was earned over to payroll.
- An overtime *request and approval* workflow, as in the India-built product, does not control liability: the summaries above describe overtime as owed on hours worked. Whether unapproved overtime must still be paid was not sourced here (see Gaps).
- Time records should be kept at least 3 years as a safe product default, longer where a state requires.

### Gaps
- Fixed workweek definition. Background, unverified in this session: the FLSA workweek is a fixed, regularly recurring period of 168 hours (seven consecutive 24-hour periods), may start on any day and hour, is set per employer or per employee group, and hours cannot be averaged across weeks (29 CFR 778.105; DOL Fact Sheet #23). No source was retrieved.
- The specific Part 516 data items to hold per employee (full name, SSN, address with ZIP, birth date if under 19, sex, occupation, workweek start day and time, hours each day, total each week, regular rate, straight-time and overtime earnings, deductions, wages paid, pay date and period) are background, unverified; the fact sheet text itself was not read.
- "Unauthorised overtime must still be paid" (29 CFR 785.11-.13) is background, unverified.
- Exact 2026 exempt thresholds for Washington, Alaska, New York, Colorado and Maine were not retrieved.
- California alternative workweek schedules, and the makeup-time rule, were not researched.
- Alaska double time and the current Nevada hourly-rate ceiling for daily overtime need checking against the state statutes; the sources found conflict or are dated.

## Minimum wage: federal, state, city, tipped

### Takeaway
The federal minimum wage is $7.25 with a $2.13 tipped cash wage, but most employees are governed by a higher state or city rate, and several rates change on dates other than 1 January. The HRMS needs a dated rate table keyed by work location down to city level.

### Cited Findings
- Federal: $7.25 minimum wage, maximum tip credit $5.12, minimum cash wage $2.13 (DOL table dated 1 July 2026) — [DOL tipped wage table](https://dol.gov/agencies/whd/state/minimum-wage/tipped)
- Alabama, Louisiana, Mississippi, South Carolina and Tennessee have no state minimum wage law; Georgia's law does not apply to tipped employees; the federal rate generally applies — [DOL tipped wage table](https://dol.gov/agencies/whd/state/minimum-wage/tipped)
- California: $16.90 from 1 Jan 2026; $17.40 from 1 Jan 2027 — [California DIR](https://www.dir.ca.gov/DIRNews/2025/2025-118.html); [SHRM](https://www.shrm.org/in/topics-tools/employment-law-compliance/californias-statewide-minimum-wage-increasing-again-2027)
- California cities set their own higher rates, with one batch effective 1 January and another 1 July — [DWT January 2026](https://www.dwt.com/blogs/employment-labor-and-benefits/2025/12/california-minimum-wage-state-and-local-increases); [DWT July 2026](https://www.dwt.com/blogs/employment-labor-and-benefits/2026/06/california-local-minimum-wage-increases-july-2026)
- 2026 examples (general / tipped cash wage): Colorado $15.16 / $12.14; Illinois $15.00 / $9.00; Massachusetts $15.00 / $6.75; Maryland $15.00 / $3.63; Delaware $15.00 / $2.23; Nebraska $15.00 / $2.13; Nevada $12.00 with no tip credit; Connecticut $16.94 with $6.38 for hotel and restaurant servers and $8.23 for bartenders — [Toast 2026 guide](https://support.toasttab.com/en/article/Toast-Payroll-Get-Started-With-2026-Minimum-Wages?lang=en_US); [Ogletree 2026 chart](https://ogletree.com/app/uploads/blog-assets/2026-Minimum-Wage-and-Tip-Credit-Update-Chart.pdf)
- Mid-year changes in 2026: Alaska to $14.00 on 1 July (tips cannot be counted toward it); DC to $18.40 on 1 July; Florida from $14.00 to $15.00 on 30 September, tipped cash wage from $10.98 to $11.98 — [Toast 2026 guide](https://support.toasttab.com/en/article/Toast-Payroll-Get-Started-With-2026-Minimum-Wages?lang=en_US)
- Michigan's tipped cash wage rises each year until it reaches 50% of the state minimum in 2031 — [Toast 2026 guide](https://support.toasttab.com/en/article/Toast-Payroll-Get-Started-With-2026-Minimum-Wages?lang=en_US)
- Some cities set their own tipped minimums (Flagstaff is an example), so the state rate is not always the binding one — [NerdWallet](https://www.nerdwallet.com/finance/learn/tipped-minimum-wage)
- A full state table of 2025 and 2026 rates and the type of increase is published by EPI — [EPI](https://www.epi.org/chart/2026-jan-mw-table-2-2025-and-2026-minimum-wages-size-of-increase-and-type-of-increase-by-state/)

### Inferences
- Minimum wage must be stored as effective-dated rows by jurisdiction (federal, state, county, city), not as a constant. Effective dates include 1 January, 1 July and 30 September.
- The applicable rate depends on where the work is performed, so each employee needs a work location resolvable to city and county, separate from the home address.
- Tipped employees need a flag, a cash wage, and tips received per pay period so payroll can test the tip-credit make-up to full minimum wage.

### Gaps
- The complete 50-state and city table was not extracted; the Ogletree PDF and EPI table hold it.
- One secondary source still listed Alaska at $13.00; the $14.00 from 1 July 2026 figure should be confirmed with Alaska DOLWD.
- Sub-minimum rates (youth, training) and employer-size tiers were not researched.

## Time tracking: rounding, meal and rest breaks, predictive scheduling

### Takeaway
Rounding clock punches is a legal risk in California when the system captures exact minutes, so the safe product default is to pay to the minute. Predictive scheduling is statewide only in Oregon and otherwise a city-level rule in about ten to eleven places.

### Cited Findings
- California, *Camp v. Home Depot* (Sixth District Court of Appeal, October 2022): where the timekeeping system captures each minute worked, the employer must pay for all time worked rather than rounding, even if the rounding policy is neutral. Home Depot rounded to the nearest quarter hour; the plaintiff lost 470 minutes in aggregate — [Proskauer](https://calemploymentlawupdate.proskauer.com/2022/10/california-court-of-appeal-dismantles-rounding-where-accurate-timekeeping-records-exist/); [Morgan Lewis](https://www.morganlewis.com/pubs/2022/11/rounding-employee-time-in-california-even-via-neutral-policy-creates-risk-under-new-appellate-decision)
- The court said there is "no provision in California law that privileges arithmetic simplicity over paying employees for all time worked", and left open whether neutral rounding is allowed where actual minutes cannot be captured — [Proskauer](https://calemploymentlawupdate.proskauer.com/2022/10/california-court-of-appeal-dismantles-rounding-where-accurate-timekeeping-records-exist/)
- The California Supreme Court was reported to have granted review in March 2023; the outcome was not confirmed in this research — [Husch Blackwell](https://www.huschblackwell.com/newsandinsights/no-well-rounded-timekeeping-why-california-employers-need-to-stop-rounding-now) (status pending / unverified)
- Predictive scheduling: Oregon is the only state with a statewide law — [Homebase 2026 guide](https://www.joinhomebase.com/blog/predictive-scheduling-laws)
- Cities named across sources: New York City, Chicago, Seattle, San Francisco, Los Angeles, Philadelphia, Emeryville, Berkeley, Evanston — [Netchex](https://netchex.com/?p=22770); [Rippling](https://www.rippling.com/blog/predictive-scheduling-laws)
- Sources disagree on the local count: "one state and eleven municipalities" versus "ten cities and counties plus Oregon" — [Homebase 2026 guide](https://www.joinhomebase.com/blog/predictive-scheduling-laws); contradicted by [Paycor](https://www.paycor.com/resource-center/articles/predictive-work-schedule-laws-a-city-by-city-guide/)
- States that block local scheduling laws include Alabama, Arkansas, Florida, Georgia, Indiana, Iowa, Kansas, Michigan, Ohio and Tennessee; another source counts 11 — [Paycom](https://www.paycom.com/resources/blog/predictive-scheduling-laws/)
- Michigan, Connecticut and Rhode Island were considering statewide bills in 2026 (pending, not law) — [WorkAxle](https://www.workaxle.com/blog/predictive-scheduling-laws-2026)
- HR Dive keeps a running tracker with effective dates and links to each law — [HR Dive tracker](https://www.hrdive.com/news/a-running-list-of-states-and-localities-with-predictive-scheduling-mandates/540835/)

### Inferences
- The attendance clock should store raw punch times to the second, and any rounding should be a configurable, per-state display or pay rule that is off for California.
- Predictive scheduling laws need features the India product probably lacks: a published schedule with a publish timestamp, a change log for each shift edit, and an automatic premium-pay line when a shift is changed inside the notice window.

### Gaps
- Federal rounding rule (29 CFR 785.48(b)): background, unverified — rounding to the nearest 5 minutes, one-tenth or one-quarter hour is tolerated if it averages out and does not, over time, underpay employees. No source was retrieved.
- Meal and rest breaks. No source was retrieved. Background, unverified: federal law requires neither; short breaks (about 5 to 20 minutes) count as paid time and a bona fide meal period of 30 minutes or more can be unpaid; California requires a 30-minute unpaid meal break before the end of the fifth hour (second one before the end of the tenth), a paid 10-minute rest break per 4 hours or major fraction, and one extra hour of pay at the regular rate for each workday a meal break is missed and one for each workday a rest break is missed; Colorado, Washington, Oregon, New York, Illinois and others have their own meal-break rules. This whole topic needs a dedicated source pass (DOL state meal-period table and California DIR).
- Notice periods (commonly described as 14 days) and premium amounts for each predictive scheduling city were not extracted.
- Rules on travel time, on-call time and "off the clock" work were not researched.

## Onboarding: I-9, E-Verify, W-4, new-hire reporting, SSN and ITIN, worker classification

### Takeaway
Every US hire needs a Form I-9 on the current edition (01/20/25, expiring 05/31/2027) with document examination within three business days of the start date; E-Verify is voluntary federally but compulsory for most private employers in roughly ten to eleven states. The federal contractor-classification test was being rewritten in 2026 and state tests such as California's ABC test stay stricter.

### Cited Findings
- The current Form I-9 edition is dated 01/20/25 and expires 05/31/2027 — [E-Verify.gov](https://e-verify.gov/about-e-verify/whats-new/uscis-extends-form-i-9-expiration-date); [SHRM](https://shrm.org/topics-tools/news/talent-acquisition/uscis-extends-form-i9-expiration-date-2027)
- Prior editions may be used until their own expiration date; the 08/01/23 edition exists with both a 07/31/2026 and a 05/31/2027 expiration, and employers with electronic systems using the 07/31/2026 version had to update by that date — [E-Verify.gov](https://e-verify.gov/about-e-verify/whats-new/uscis-extends-form-i-9-expiration-date); [Paychex](https://www.paychex.com/articles/human-resources/what-is-form-i9-latest-updates)
- Employers must examine the new hire's documents within three business days after the first day of employment — [DHS](https://www.dhs.gov/news/2023/07/21/dhs-provides-employers-certainty-and-new-flexible-option-employment-eligibility)
- A remote (video) document examination alternative has existed since 1 Aug 2023, only for employers in good standing with E-Verify and enrolled for every hiring site using it; the employee may still insist on physical examination — [Federal Register, 25 Jul 2023](https://www.govinfo.gov/content/pkg/FR-2023-07-25/html/2023-15533.htm); [Fisher Phillips](https://www.fisherphillips.com/print/v2/content/33525/new-options-remote-i9-completion.pdf)
- E-Verify employers must open a case by the end of the third business day after hire — [Foley](https://www.foley.com/en/insights/publications/2023/07/dhs-new-version-form-i-9-remote-e-verify-employers)
- E-Verify is voluntary for most private employers under federal law, but mandatory for federal contracts containing the E-Verify clause and for employers of F-1 STEM OPT students — [Paylocity](https://www.paylocity.com/resources/learn/articles/e-verify-states/)
- States requiring E-Verify for all private employers, no size threshold: Alabama, Arizona, Mississippi, South Carolina — [Paylocity](https://www.paylocity.com/resources/learn/articles/e-verify-states/); [Experian](https://www.experian.com/blogs/employer-services/e-verify-state-by-state-requirements/)
- States with a size threshold: Georgia (10+), Florida (25+), North Carolina (25+), Utah (150+), Tennessee (35+), Virginia (50+) — [Ellis](https://www.ellis.com/resources/e-verify); [OnPay](https://onpay.com/?p=21901)
- Tennessee's threshold is given as 35+ by two sources and "six or more" by another — [Ellis](https://www.ellis.com/resources/e-verify); contradicted by [Paylocity](https://www.paylocity.com/resources/learn/articles/e-verify-states/)
- Indiana is reported to require it of all employers from 1 July 2026, and a June 2026 guide says new 2026 laws in Indiana and Ohio extend the map; another listing still shows Indiana and Louisiana as public-employer-only — [Ellis](https://www.ellis.com/resources/e-verify); contradicted by [Equifax](https://workforce.equifax.com/e-verify-state-requirements)
- Total count of private-employer states is given as 10 by one source and 11 by another — [Ellis](https://www.ellis.com/resources/e-verify); [SHRM vendor directory guide, June 2026](https://vendordirectory.shrm.org/company/844241/whitepapers/13901/e-verify-requirements-by-state-the-2026-employer-map-updated-june-2026)
- Public employers and contractors only: Idaho, Missouri, Nebraska, Oklahoma, Texas, Kentucky, Minnesota, Pennsylvania — [Equifax](https://workforce.equifax.com/e-verify-state-requirements)
- Pending: Florida HB 197, passed by the Florida House in January 2026, would remove the 25-employee threshold; no evidence it became law — [Jimerson Birr](https://www.jimersonfirm.com/blog/2026/06/e-verify-update-changing-state-rules-you-should-watch-in-2026/amp/)
- Worker classification: in February 2026 DOL proposed rescinding the 2024 independent-contractor rule and returning to an economic reality test with two "core" factors, control over the work and opportunity for profit or loss — [DOL news release 26 Feb 2026](https://www.dol.gov/newsroom/releases/WHD/whd20260226); [DOL 2026 rulemaking page](https://www.dol.gov/agencies/whd/flsa/misclassification/2026rulemaking)
- A Littler alert describes a final rule on independent contractor status taking effect 60 days after Federal Register publication; the search summary dated it 19 November 2026 but other sources retrieved only describe the proposal, so the final-rule status is unconfirmed — [Littler](https://www.littler.com/news-analysis/asap/dol-releases-final-rule-regarding-independent-contractor-status)
- The federal rule covers the FLSA and laws that borrow its definition of employment; DOL rejected the ABC test as too restrictive, and a worker who is a contractor under the federal rule may still be an employee under state law such as California's ABC test — [Littler](https://www.littler.com/news-analysis/asap/dol-releases-final-rule-regarding-independent-contractor-status); [DLA Piper](https://www.dlapiper.com/en-us/insights/publications/2026/03/dol-proposes-independent-contractor-rule)

### Inferences
- I-9 is a tracked document with its own dates: Section 1 completion, document examination date, examination method (physical or remote), form edition used, work-authorisation expiry and a reverification reminder.
- E-Verify applicability is a function of hiring-site state, employer headcount and federal-contractor status, so it should be a derived flag, not a tenant-wide switch.
- "Worker type" must distinguish W-2 employee from 1099 contractor at the record level, because contractors are outside overtime, leave accrual, I-9 and ACA counting. The product should not let a contractor silently flow into those calculations.

### Gaps
- New-hire reporting. The HHS/ACF page returned HTTP 403 and no other source was retrieved. Background, unverified: federal law (PRWORA) requires every employer to report each new hire and rehire to the State Directory of New Hires within 20 days of hire (many states set shorter deadlines); required elements are employee name, address, SSN and date of hire (first day of work for pay), and employer name, address and FEIN; a rehire after 60 or more consecutive days of separation is a new hire; multistate employers may pick one state and report all hires there electronically twice a month; some states also require contractor reporting (California is the usual example) and extra fields such as date of birth and health-insurance availability. Verify at [ACF new hire reporting](https://www.acf.hhs.gov/css/employers/employer-responsibilities/new-hire-reporting).
- I-9 details not sourced. Background, unverified: employee completes Section 1 no later than the first day of employment; retention is the later of 3 years after hire or 1 year after termination; I-9s should be stored apart from the personnel file; reverification goes in Supplement B.
- Form W-4 and state withholding forms were not researched. Background, unverified: the federal W-4 (2020 redesign onward) holds filing status, a multiple-jobs checkbox, dependent credit amount, other income, deductions and extra withholding rather than "allowances"; most income-tax states have their own certificate and some accept the federal form; nine states have no wage income tax.
- SSN validation and ITIN were not researched. Background, unverified: SSA's Social Security Number Verification Service (SSNVS) lets employers verify name/SSN matches for wage reporting only; an ITIN (starts with 9) is a tax-processing number and is not work authorisation, so it should not be accepted in the SSN field of an employee record; a new hire may start work with an SSN applied for.
- Whether the DOL independent-contractor final rule has in fact been published, and its effective date, must be checked on the Federal Register.
- IRS common-law test and Form SS-8, and the list of states using the ABC test, were not researched.

## Leave: FMLA, paid sick leave, paid family and medical leave, PTO payout, other leave

### Takeaway
There is no federal paid leave mandate; FMLA gives 12 unpaid, job-protected workweeks to eligible employees of 50+ employers, and paid leave is entirely a state and city patchwork that is measured in hours. The HRMS needs hour-based accrual policies keyed by work location, with per-jurisdiction caps and carry-over.

### Cited Findings
- FMLA covered employer: private employers with 50 or more employees in 20 or more workweeks of the current or preceding calendar year; public agencies and schools regardless of size — [DOL Fact Sheet #28](https://www.dol.gov/agencies/whd/fact-sheets/28-fmla)
- FMLA employee eligibility: 12 months of service, at least 1,250 hours worked in the 12 months before leave starts, and a worksite with 50 or more employees within 75 miles — [DOL Fact Sheet #28](https://www.dol.gov/agencies/whd/fact-sheets/28-fmla)
- Entitlement: up to 12 workweeks in a 12-month period; up to 26 workweeks in a single 12-month period for military caregiver leave — [DOL Fact Sheet #28](https://www.dol.gov/agencies/whd/fact-sheets/28-fmla)
- Leave can be intermittent or a reduced schedule when medically necessary (separate blocks, or fewer hours per day or week); for bonding it needs the employer's agreement — [DOL Fact Sheet #28](https://www.dol.gov/agencies/whd/fact-sheets/28-fmla)
- Employer may request medical certification; the employee has 15 calendar days to provide it — [DOL Fact Sheet #28](https://www.dol.gov/agencies/whd/fact-sheets/28-fmla)
- Group health coverage continues on the same terms during leave, and the employee returns to the same or an equivalent position — [DOL Fact Sheet #28](https://www.dol.gov/agencies/whd/fact-sheets/28-fmla)
- There is no federal sick leave law — [Paycor](https://www.paycor.com/resource-center/articles/paid-sick-leave-laws-by-state/)
- Count of states with mandatory paid sick leave: "DC and 19 states" as of 1 Jan 2026 per one source, "21 states and DC" (including paid-leave-for-any-reason laws) per another — [Paylocity](https://www.paylocity.com/resources/learn/articles/paid-sick-leave-laws/); contradicted by [Rippling](https://www-j.rippling.com/blog/paid-sick-leave-laws-by-state)
- Missouri's voter-approved paid sick leave law was repealed effective 28 August 2025 — [Inova Payroll](https://inovapayroll.com/hr-news/2026-paid-sick-leave-laws-by-state/)
- Connecticut: applies to employers with 11 or more employees in 2026 and to all employers from 1 January 2027 — [Inova Payroll](https://inovapayroll.com/hr-news/2026-paid-sick-leave-laws-by-state/)
- Alaska: accrual of 1 hour per 30 hours worked, all employers with one or more employees, per one source; a second source gives a different Alaska figure — [Eddy](https://eddy.com/blog/us-state-sick-leave-requirements/); contradicted by [Gusto](https://support.gusto.com/article/106622152100000/state-and-city-sick-leave-laws-for-admins)
- Cities and counties legislate where the state does not: Philadelphia, Pittsburgh and Allegheny County have sick leave laws though Pennsylvania has none — [Patriot Software](https://www.patriotsoftware.com/blog/payroll/state-mandated-paid-sick-leave-laws/)
- Paid family and medical leave: as of May 2026, 14 states plus DC have or will soon have a statewide program — [HCA Magazine](https://www.hcamag.com/us/specialization/benefits/paid-family-leave-mandates-now-cover-14-states-and-washington-dc/591280)
- New in 2026: Delaware benefits from 1 Jan 2026; Minnesota benefits from 1 Jan 2026, covering nearly all employers regardless of size; Maine benefit payments from May 2026, covering private employers with at least one employee — [HR Dive](https://www.hrdive.com/news/state-paid-family-leave-benefit-changes-in-2026/809625/); [Epstein Becker Green](https://www.ebglaw.com/insights/publications/2026-family-and-medical-leave-law-updates-what-employers-in-seven-states-need-to-know)
- Delaware employer coverage is described inconsistently (employers with 10 or more employees versus a small-business exemption) — [Troutman](https://www.troutman.com/insights/the-expanding-patchwork-of-state-paid-family-and-medical-leave-laws-what-employers-should-know/)
- Maryland is delayed: benefits from January 2028, contributions reported from 1 January 2027 — [Bloomberg Law](https://news.bloomberglaw.com/daily-labor-report/state-paid-leave-laws-to-grow-next-year-in-number-variation) (dates conflict between sources; confirm with the state)
- PTO payout at termination is required only where state law or company policy requires it; some states mandate payout of accrued vacation, others leave it to the employer — [Paylocity](https://www.paylocity.com/resources/learn/articles/pto-payout-laws/); [Paycor](https://www.paycor.com/resource-center/articles/pto-payout-laws-by-state/)

### Inferences
- Leave balances must be held in hours (accrual of the form "1 hour per N hours worked" cannot be expressed in days). This is a schema-level difference from an India-style days-per-year leave model.
- Sick-leave accrual needs hours actually worked as an input, so leave depends on the timesheet or attendance data for non-exempt staff and on an assumed 40-hour week for exempt staff.
- FMLA eligibility is computable from HRMS data: hire date, hours worked in the trailing 12 months, and headcount within 75 miles of the worksite. That last test needs worksite geolocation or at least a worksite grouping.
- Intermittent FMLA must be tracked in the smallest increment the payroll system uses, against a 12-week entitlement converted to hours for the employee's normal schedule.
- State PFML is usually a payroll contribution plus a state claim, so the HRMS mainly needs leave-of-absence type, dates, and a job-protection flag; contribution rates belong to payroll.

### Gaps
- A full per-jurisdiction sick-leave table (accrual rate, annual accrual cap, usage cap, carry-over, front-loading, waiting period) was not extracted. Background, unverified: the common pattern is 1 hour per 30 worked, capped at 40 to 72 hours a year, with carry-over or front-loading; California is 1 per 30 with at least 40 hours or 5 days usable per year. Sources to pull: [Mercer roundup](https://www.mercer.com/our-thinking/law-and-policy-group/roundup-state-accrued-paid-leave-mandates.html), [Brightmine chart](https://www.brightmine.com/us/?p=7719).
- The named list of PFML states was not confirmed line by line. Background, unverified: California, New Jersey, Rhode Island, New York, Washington, DC, Massachusetts, Connecticut, Oregon, Colorado, plus Delaware, Minnesota and Maine in 2026 and Maryland later.
- Which states mandate PTO payout was not extracted. Background, unverified: California treats accrued vacation as wages and bans use-it-or-lose-it; Colorado, Illinois, Massachusetts, Montana and Nebraska are commonly listed as payout states.
- FMLA 12-month period methods (calendar year, fixed leave year, forward from first use, rolling backward) and the 5-business-day eligibility-notice deadline are background, unverified; the fact sheet does not state them.
- Jury duty, voting leave and bereavement leave laws were not researched. Background, unverified: federal law protects jury service from retaliation but does not require pay; many states require time off to vote, some paid; a small number of states (Oregon, Illinois, California, and others) mandate bereavement leave.
- Federal holidays. Background, unverified: private employers are not required to give federal holidays as paid days off, and the FLSA does not require premium pay for holiday work; Massachusetts and Rhode Island have limited holiday-pay rules. No source was retrieved.
- "Paid leave for any reason" laws (Nevada, Maine, Illinois) were not confirmed.

## Final pay and termination: timing, COBRA, at-will

### Takeaway
Final-pay deadlines are set by the state where the employee worked and are often shorter for dismissal than for resignation, sometimes immediate. COBRA applies to employers with 20 or more employees and brings fixed notice clocks the HRMS should start on termination.

### Cited Findings
- The FLSA sets no specific final-paycheck deadline; earned wages are due by the next regular payday — [Rippling](https://www.rippling.com/blog/pto-payout-laws-by-state)
- Alabama, Florida, Georgia and Mississippi have no state-specific final paycheck law — [Rippling](https://www.rippling.com/en-IE/blog/final-paycheck-for-terminated-employee)
- State deadlines for a dismissed employee are often shorter, sometimes immediate, and may force an off-cycle payroll run — [Paylocity](https://www.paylocity.com/resources/library/articles/final-paycheck-laws)
- 2026 changes: Colorado clarified by statute the involuntary-termination situations in which pay is due immediately; in Washington, an employee who ends employment must be paid at the end of the established pay period — [Paycom](https://www.paycom.com/resources/blog/final-paycheck-laws-by-state/)
- The governing law is that of the employee's work location, not the company's base — [Rippling](https://www.rippling.com/en-IE/blog/final-paycheck-for-terminated-employee)
- Per-state tables exist at Gusto and ADP — [Gusto](https://support.gusto.com/article/178508731100000/Dismissal-paycheck-requirements-by-state); [ADP, Aug 2026](https://www.adp.com/spark/articles/2026/08/final-pay-and-pto-payout-when-are-they-required.aspx)
- Federal COBRA applies to group health plans of employers with 20 or more employees on more than half of typical business days in the previous year — [DOL EBSA](https://www.dol.gov/agencies/ebsa/about-ebsa/our-activities/resource-center/publications/workers-guide-health-benefits-cobra)
- Notice clocks: the employer notifies the plan administrator within 30 days of the qualifying event, and the administrator sends the election notice within 14 days (44 days in total when the employer is also the administrator) — [Gallagher employer guide](https://ajg.com/-/media/files/gallagher/us/news-and-insights/an-employers-guide-to-cobra.pdf); [Paychex](https://www.paychex.com/articles/hcm/breaking-down-cobra)
- Beneficiaries have at least 60 days to elect, counted from the later of the date coverage would end and the date of the notice — [DOL EBSA](https://www.dol.gov/agencies/ebsa/about-ebsa/our-activities/resource-center/publications/workers-guide-health-benefits-cobra)
- Federal COBRA coverage lasts 18 to 36 months depending on the qualifying event — [DOL EBSA](https://www.dol.gov/agencies/ebsa/about-ebsa/our-activities/resource-center/publications/workers-guide-health-benefits-cobra)
- Many states have "mini-COBRA" laws for employers under 20 employees, with different counting methods, notice periods and durations — [TriNet](https://trinet.com/insights/what-are-state-mini-cobra-laws); [SHRM](https://www.shrm.org/topics-tools/employment-law-compliance/what-exactly-are-mini-cobra-laws)

### Inferences
- The termination record needs a separation type (voluntary, involuntary, layoff) and a last-day-worked date, because both drive the final-pay deadline, and the HRMS should surface a computed "final pay due by" date to payroll.
- Termination should trigger a COBRA qualifying-event record with its own 30/14/60-day clocks, usually passed to a COBRA administrator.
- A notice-period field of the Indian kind (30/60/90 days served) is not a US default; see the at-will gap below.

### Gaps
- Per-state final-pay deadlines were not extracted. Background, unverified: California requires wages immediately on discharge and within 72 hours of a resignation without notice, with "waiting time" penalties of up to 30 days' wages.
- At-will employment: no source was retrieved. Background, unverified: employment is at-will in every state except Montana (which requires good cause after a probationary period), meaning either side may end it without notice or cause, subject to anti-discrimination, retaliation and contract exceptions.
- WARN Act (60 days' notice for mass layoffs at 100+ employee employers) and state mini-WARN laws were not researched.
- State separation notices (unemployment insurance notices handed over at termination) were not researched.

## ACA and benefits enrolment data

### Takeaway
The 2026 affordability percentage is 9.96% (10.22% for 2027), and Forms 1095-C for 2025 were due to employees by 2 March 2026 and to the IRS electronically by 31 March 2026. ACA status is driven by hours of service, so the HRMS is the source system for it.

### Cited Findings
- 2026 affordability percentage: 9.96% (Rev. Proc. 2025-25), up from 9.02% in 2025; non-calendar-year plans switch when the plan year starting in 2026 begins — [EY](https://taxnews.ey.com/news/2025-1542-aca-affordability-percentage-increases-again-for-2026-employer-health-plans); [WTW](https://www.wtwco.com/en-us/insights/2025/07/irs-announces-2026-aca-affordability-percentage)
- 2027 affordability percentage: 10.22% (Rev. Proc. 2026-26) — [EY, 23 Jul 2026](https://taxnews.ey.com/news/2026-1601-aca-affordability-percentage-rises-again-for-2027-employer-health-plans)
- 2026 employer penalties: $3,340 per full-time employee under 4980H(a), $5,010 under 4980H(b); the (a) penalty applies when coverage is not offered to at least 95% of full-time employees — [CBIZ](https://www.cbiz.com/insights/article/aca-affordability-percentage-and-employer-penalties-set-to-rise-in-2026); [Equifax](https://workforce.equifax.com/all-blogs/-/post/2026-aca-affordability-percentage-and-penalties-important-information-for-employers)
- The 4980H(a) amount is reported to rise to $3,780 for 2027 — [HCA Magazine](https://www.hcamag.com/us/specialization/benefits/affordability-threshold-climbs-to-1022-percent-for-2027-plans/597385) (single source)
- 2025-tax-year deadlines: furnish 1095-C to employees by 2 March 2026; paper filing 2 March 2026; electronic filing of 1094-C and 1095-C by 31 March 2026 — [Baldwin](https://baldwin.com/compliance/2025-aca-reporting-and-filing-deadlines/); [Newfront](https://www.newfront.com/blog/aca-reporting-requirements-in-2026)
- Employers with 10 or more information returns in aggregate (1094-C and 1095-C counted together with others) must file electronically — [Newfront](https://www.newfront.com/blog/aca-reporting-requirements-in-2026)
- Under the Paperwork Burden Reduction Act, employers need not send every employee a 1095-C automatically; they may instead post a notice of availability and furnish on request, with the notice staying up through 15 October 2026 for the 2025 forms — [Baldwin](https://baldwin.com/compliance/2025-aca-reporting-and-filing-deadlines/); [Newfront](https://www.newfront.com/blog/aca-reporting-requirements-in-2026)
- Form 8809 gives an automatic 30-day filing extension if submitted by the due date; one source cites a $340 per-return penalty for failing to file or furnish — [Tax1099](https://www.tax1099.com/blog/aca-form-1095-b-c-reporting-deadline-penalties-and-extension)
- Some states have their own ACA-style reporting dates — [Mitratech](https://mitratech.com/?p=115192)

### Inferences
- The HRMS should hold, per employee per month: hours of service, ACA full-time status, offer-of-coverage code inputs, employee share of the lowest-cost self-only premium, and coverage start and end dates. These feed 1095-C lines 14 to 16.
- Variable-hour employees need measurement, administrative and stability period definitions at tenant level and a per-employee look-back result.
- An "ALE member" flag and aggregated-group identifier are needed at employer level, since related companies are counted together.

### Gaps
- Core ACA definitions were not sourced in this session. Background, unverified: an applicable large employer has an average of 50 or more full-time employees including full-time equivalents in the prior calendar year; full-time means an average of 30 hours of service a week or 130 a month; the look-back method uses a standard measurement period of 3 to 12 months, an administrative period of up to 90 days, and a stability period of at least 6 months and no shorter than the measurement period; the waiting period for coverage may not exceed 90 days. Verify at IRS.gov (employer shared responsibility Q&A).
- Benefits enrolment basics were not researched. Background, unverified: annual open enrolment sets elections for the plan year; mid-year changes need a qualifying life event (marriage, divorce, birth or adoption, loss of other coverage, change in employment status) under Section 125 rules, with HIPAA special-enrolment windows of 30 days (60 days for Medicaid/CHIP events); the HRMS typically holds plan, coverage tier, dependents with SSN and date of birth, effective dates, employee and employer contribution, and the event that justified the change.
- Deadlines for 2026-tax-year forms (due early 2027) were not retrieved.
- The list of states with individual-mandate reporting (commonly California, New Jersey, Massachusetts, Rhode Island, DC) is background, unverified.

## Reporting: EEO-1, VETS-4212, OSHA, state pay data, pay transparency

### Takeaway
Federal demographic reporting is in flux: the EEOC proposed in July 2026 to rescind the EEO-1 and never opened the 2025 cycle, but the rule is not final and state pay-data reports in California, Illinois and Massachusetts continue. Pay transparency in job postings now applies in about 14 to 16 states plus DC.

### Cited Findings
- On 21 July 2026 the EEOC voted 2-1 to propose rescinding the EEO-1 and the EEO-2 to EEO-5 reports; the proposal was published in the Federal Register on 23 July 2026; comments closed 24 August 2026 with more than 2,800 received — [EEOC NPRM](https://www.eeoc.gov/sites/default/files/2026-07/2026_NL01498_NPRM_Rescission.pdf); [HSF Kramer](https://www.hsfkramer.com/insights/2026-08/eeoc-proposes-to-eliminate-eeo-1-reporting-what-employers-need-to-know)
- No final rule was found as of the latest sources (about September 2026); EEO-1 obligations formally remain in effect — [Foley, Sept 2026](https://www.foley.com/insights/publications/2026/09/risk-considerations-as-eeoc-moves-to-ax-data-reports-2/); [Seyfarth](https://www.seyfarth.com/news-insights/eeoc-proposes-rescission-of-annual-reporting-requirement-what-employers-need-to-know-now.html)
- The EEOC had not opened the 2025-data filing window; the workforce snapshot for that cycle is a pay period between 1 October and 31 December 2025; the current OMB clearance covers 2022-2024 and expires 30 November 2026 — [Jackson Lewis](https://www.jacksonlewis.com/insights/eeoc-signals-end-key-federal-eeo-reporting-mandates-timing-uncertain-state-obligations-remain); [Paycor](https://www.paycor.com/resource-center/articles/eeo-1-reporting-deadline)
- For comparison, the 2024-data window ran 20 May to 24 June 2025 — [Paycor](https://www.paycor.com/resource-center/articles/eeo-1-reporting-deadline)
- Law firms advise keeping Q4 2025 snapshot data and not purging workforce demographic records, and note that state demographic reporting continues even if the federal form ends — [Jackson Lewis](https://www.jacksonlewis.com/insights/2026-employee-data-reporting-requirements-are-employers-ready); [Cozen O'Connor](https://www.cozen.com/news-resources/publications/2026/is-this-the-end-your-eeo-1-reporting-obligations-may-be-over)
- VETS-4212: the 2026 filing platform opened 1 August 2026 and filings were due 30 September 2026 — [Ogletree](https://ogletree.com/insights-resources/blog-posts/dol-opens-2026-vets-4212-filing-platform-covered-contractors-and-data-requirements/)
- VETS-4212 contract threshold: DOL's page states $150,000, while 2026 law-firm guidance uses $200,000 — [DOL VETS](https://dol.gov/vets/vets4212.htm); contradicted by [Ogletree](https://ogletree.com/insights-resources/blog-posts/dol-opens-2026-vets-4212-filing-platform-covered-contractors-and-data-requirements/)
- VEVRAA obligations for protected veterans, including the annual VETS-4212, remain in effect, while the future of disability-related affirmative action and self-identification requirements is described as uncertain — [Lathrop GPM](https://www.lathropgpm.com/insights/attention-federal-contractors-compliance-update/)
- One blog reports an OFCCP Section 503 final rule effective 21 September (2026) removing disability utilisation-goal analyses from affirmative action plans — [Beancount blog](https://beancount.io/blog/2026/09/25/vets-4212-september-30-federal-contractor-filing-guide) (single secondary source; unverified)
- OSHA: covered employers post a signed Form 300A summary from 1 February to 30 April each year, and certain establishments submit data electronically through the Injury Tracking Application by 2 March — [SHRM](https://www.shrm.org/in/topics-tools/employment-law-compliance/reminder-covered-employers-must-post-osha-injury-illness-data); [Fisher Phillips](https://www.fisherphillips.com/en/news-insights/employers-must-electronically-submit-workplace-injury-form-march-2.html)
- OSHA electronic thresholds: establishments with 100+ employees in designated industries (Appendix B to Subpart E, 29 CFR 1904) submit Forms 300, 300A and 301; establishments with 250+ employees in covered industries, and 20-249 in listed industries, submit the 300A — [EHS](https://www.ehs.com/blogs/preparing-for-oshas-electronic-recordkeeping-deadline/); [Maryland MOSH](https://labor.md.gov/labor/mosh/moshregrecordcfr.shtml)
- The signed 300A must be retained for five years — [Fisher Phillips](https://www.fisherphillips.com/print/v2/content/19741/employers-its-time-to-post-and-electronically-file-your-workplace-safety-records.pdf)
- California pay data report: private employers with 100 or more employees (at least one in California) file annually with the Civil Rights Department; 2026 deadline was 13 May 2026 for 2025 data; a separate labour-contractor report applies to employers with 100+ workers hired through labour contractors; from 2026 penalties are mandatory on the Department's request — [California Civil Rights Department](https://calcivilrights.ca.gov/?p=520675); [Thomson Reuters](https://tax.thomsonreuters.com/news/californias-2026-pay-data-reporting-rules-add-new-compliance-pressures-legal-experts-say/)
- Illinois Equal Pay Registration Certificate: private employers with 100 or more employees in Illinois that must file an EEO-1; renewed every two years; requires an employee-by-employee data list, not aggregates, plus a compliance statement; amended rules took effect 18 June 2026 — [Jackson Lewis](https://www.jacksonlewis.com/insights/2026-employee-data-reporting-requirements-are-employers-ready); [Fisher Phillips](https://www.fisherphillips.com/en/insights/insights/an-employers-guide-to-pay-equity-compliance-as-state-rules-evolve)
- Massachusetts requires employers with 100 or more employees in the state that file federal EEO reports to submit data to the state — [ADP](https://sbshrs.adpinfo.com/newsletter/winter-2026-pay-data-reporting-requirements-a-growing-trend)
- Pay transparency: no federal law for private employers in 2026; counts of states requiring pay ranges in postings run from "14 plus DC" to "16 plus DC as of 1 August 2026", with Delaware joining in 2027 — [Paylocity](https://www.paylocity.com/resources/learn/articles/state-pay-transparency-laws/); contradicted by [National Law Review](https://natlawreview.com/article/midyear-pay-transparency-update-job-postings-lead-employer-responsibilities)
- 2026 additions: Virginia from 1 July 2026 (public and internal postings, including promotions and transfers); Maine from 29 July 2026; Connecticut moves from on-request to proactive disclosure on 1 October 2026 — [Cooley](https://www.cooley.com/news/insight/2026/2026-07-06-show-me-the-money-or-the-wage-range-new-state-pay-transparency-laws); [Jackson Lewis](https://www.jacksonlewis.com/publication/navigating-2026-pay-transparency-laws-and-employer-obligations)
- Including localities, 27 US jurisdictions had pay transparency laws as of 29 July 2026; the laws are likely to reach remote roles that could be filled from a covered state — [Brightmine](https://www.brightmine.com/us/resources/charts/u-s-pay-transparency-laws-by-state-and-locality/)

### Inferences
- Race/ethnicity, sex, job category and veteran status fields should stay in the US data model whatever happens to the EEO-1, because Illinois, California and Massachusetts reports are built on the same categories and Illinois explicitly keys off EEO-1 filer status.
- A pay band (minimum and maximum) per job or position is now a compliance field, not just a compensation-planning one, for any tenant that posts jobs.
- OSHA recordkeeping is an incident-log feature (case number, date, location, description, days away or restricted, classification) attached to an establishment, with establishment-level headcount and NAICS industry code deciding the filing duty.

### Gaps
- EEO-1 basics were not sourced. Background, unverified: private employers with 100 or more employees (and, historically, federal contractors with 50 or more) file; data is headcount by establishment, by 10 job categories, by sex and seven race/ethnicity categories; the EEOC removed the non-binary reporting option for the 2024 collection.
- Whether the EEOC issued a final rescission rule between September and 24 November 2026 was not found.
- Executive Order 11246 (affirmative action for women and minorities at federal contractors) was revoked in January 2025 by EO 14173; this is background, unverified, and its effect on applicant-flow data fields was not researched.
- Which OSHA employers are partially exempt (10 or fewer employees; low-hazard industries) and the five-year retention of the 300 log itself are background, unverified.
- The correct 2026 VETS-4212 contract threshold ($150,000 or $200,000) needs confirming on the DOL VETS site.
- Named list of pay-transparency states was not extracted line by line. Background, unverified: Colorado, California, Washington, New York, Nevada, Connecticut, Maryland, Rhode Island, Hawaii, Illinois, Minnesota, Vermont, Massachusetts, New Jersey, plus DC.
- California's pay-data fields (pay bands, hours worked, mean and median hourly rate by group) were not extracted.

## Employee data fields that differ from India

### Takeaway
No single source lists these, so this section is mostly inference from the rules above: a US employee record needs SSN, separate home and work locations resolved to state, county and city, FLSA status, voluntary self-identification fields, and time handling that is zone-aware.

### Cited Findings
- Minimum wage, sick leave, scheduling and final-pay rules all attach to the place the work is performed, down to city or county level (Flagstaff, Philadelphia, Pittsburgh and Allegheny County are examples) — [NerdWallet](https://www.nerdwallet.com/finance/learn/tipped-minimum-wage); [Patriot Software](https://www.patriotsoftware.com/blog/payroll/state-mandated-paid-sick-leave-laws/); [Rippling](https://www.rippling.com/en-IE/blog/final-paycheck-for-terminated-employee)
- State pay-data and EEO reports use demographic categories collected per employee, and Illinois requires an employee-level list — [Jackson Lewis](https://www.jacksonlewis.com/insights/2026-employee-data-reporting-requirements-are-employers-ready)
- Veteran reporting (VETS-4212) continues for federal contractors, which requires a protected-veteran status field — [Lathrop GPM](https://www.lathropgpm.com/insights/attention-federal-contractors-compliance-update/)
- FMLA eligibility depends on headcount within 75 miles of the employee's worksite — [DOL Fact Sheet #28](https://www.dol.gov/agencies/whd/fact-sheets/28-fmla)

### Inferences
- Identity: legal first, middle and last name exactly as on the Social Security card (used for W-2 and new-hire reporting), plus an optional preferred name; SSN as a 9-digit masked, encrypted field replacing PAN/Aadhaar/UAN; date of birth.
- Address: street, city, state (2-letter), ZIP or ZIP+4, and county. County and city matter because local taxes and local labour rules attach to them.
- Work location: a separate worksite record with its own address and state, a remote-worker flag, and the state used for unemployment insurance and withholding. An employee can live in one state and work in another.
- Employment: FLSA status, worker type (W-2 / 1099), full-time or part-time and standard weekly hours, pay basis (hourly or salary), pay frequency, tipped flag, EEO job category, I-9 and work-authorisation dates.
- Voluntary self-identification: race/ethnicity, sex, veteran status and disability status must each allow "decline to answer", be stored apart from the main personnel file with restricted access, and never be mandatory in onboarding.
- Formats: dates displayed MM/DD/YYYY (store ISO), 12-hour clock option, US dollar with two decimals, US phone format, imperial units where they appear (miles for mileage and the FMLA 75-mile test).
- Time: store punches in UTC with the worksite's IANA time zone; the US mainland spans four zones plus Alaska and Hawaii, and most of the country observes daylight saving time while Hawaii and most of Arizona do not. Shifts crossing the clock change are 23 or 25 hours long and must be paid on actual elapsed hours.
- The workweek start day and time is an employer setting that determines which week a midnight-crossing shift's hours fall into.

### Gaps
- No authoritative source for a "US employee master field list" was found; the list above is the researcher's inference and should be checked against payroll-provider import templates (ADP, Gusto, Paychex).
- The exact EEO-1 race/ethnicity categories and the OFCCP veteran and disability self-identification form (CC-305) wording were not retrieved.
- Daylight saving time facts are background, unverified in this session.

## Privacy and data handling

### Takeaway
There is no general federal privacy law, but California's CCPA has applied in full to employee and applicant data since 1 January 2023, and Illinois BIPA makes fingerprint or face clock-in a consent-first feature. No US law requires employee data to stay in the US; the one federal cross-border rule targets access by named "countries of concern".

### Cited Findings
- California has covered employee and job-applicant data under the CCPA/CPRA since 1 January 2023, when the HR exemption ended; it is described as the first US jurisdiction to bring workforce data under a comprehensive privacy law — [Littler](https://www.littler.com/practices-industries/privacy-and-data-security/ccpa); [SHRM](https://www.shrm.org/topics-tools/employment-law-compliance/heres-california-employers-know-ccpa-compliance)
- CCPA applies to for-profit businesses doing business in California that meet at least one of three revenue or data-volume tests (Civil Code 1798.140(d)); many small employers fall under all three and are not covered — [Beancount guide, Jul 2026](https://beancount.io/blog/2026/07/16/california-privacy-law-employee-data-payroll-biometric-health-information-small-employer-guide)
- Regulations effective 1 January 2026 require covered businesses to complete documented risk assessments before many routine data-processing activities; cybersecurity audits phase in from 2028 — [Littler](https://www.littler.com/news-analysis/asap/california-risk-assessments-seven-steps-employers); [WGA](https://www.wga.com/news/updated-cppa-regulations-impacting-hr-compliance/)
- Pending: on 20 April 2026 the California Privacy Protection Agency began preliminary rulemaking on employee data, notices and disclosures; not final — [Data Privacy + Security Insider](https://www.dataprivacyandsecurityinsider.com/2026/04/ccpa-employee-data-rulemaking-could-reshape-employer-privacy-compliance-in-california/); [BakerHostetler](https://www.bakerlaw.com/insights/california-privacy-protection-agency-solicits-public-comments-on-employee-data-requirements/)
- Illinois BIPA: SB 2979, effective 2 August 2024, limits recovery to one per person rather than per scan, and confirms the required written release may be electronic; damages remain $1,000 per negligent and $5,000 per intentional or reckless violation — [Faegre Drinker](https://www.faegredrinker.com/en/insights/publications/2024/8/illinois-governor-signs-law-that-limits-damages-recoverable-under-the-biometric-information-privacy-act); [Fisher Phillips](https://www.fisherphillips.com/en/news-insights/breaking-bipa-news-illinois-curtails-massive-damage-awards-in-biometric-cases.html)
- The Seventh Circuit held the BIPA damages amendment applies retroactively — [ABA Business Law Today, May 2026](https://www.americanbar.org/groups/business_law/resources/business-law-today/2026-may/7th-circuit-holds-bipa-damages-remedy-applies-retroactively); [DWT](https://www.dwt.com/blogs/privacy--security-law-blog/2024/08/illinois-bipa-biometrics-law-amended-for-damages)
- Biometric clock-in and clock-out is the typical BIPA employment claim — [Fisher Phillips](https://www.fisherphillips.com/en/news-insights/breaking-bipa-news-illinois-curtails-massive-damage-awards-in-biometric-cases.html)
- DOJ Data Security Program (under Executive Order 14117): in effect from 8 April 2025, with due-diligence and compliance-programme duties from 6 October 2025; it restricts transactions giving "countries of concern" or covered persons access to bulk US sensitive personal data, with civil and criminal penalties — [DOJ National Security Division](https://www.justice.gov/nsd/data-security)
- Six data categories are covered: covered personal identifiers, precise geolocation, biometric identifiers, human 'omic data, personal health data and personal financial data; the bulk definition applies even to anonymised or encrypted data — [University of Washington glossary](https://www.washington.edu/research/glossary/bulk-sensitive-personal-data-doj); [FTI Consulting](https://www.fticonsulting.com/en/germany/insights/articles/dojs-data-security-program-new-era-cross-border-data-transfer-compliance)
- DOJ stated the rule does not impose generalised data-localisation requirements; it governs access, not storage location — [DOJ proposed-rule release](https://www.justice.gov/archives/opa/pr/justice-department-issues-comprehensive-proposed-rule-addressing-national-security-risks)
- There are six "countries of concern", including China — [Cloud Security Alliance](https://cloudsecurityalliance.org/articles/why-data-protection-is-now-a-national-security-matter-understanding-the-bulk-data-rule)

### Inferences
- Biometric attendance (fingerprint or face) should be off by default for US tenants and gated behind a per-employee electronic consent record, a published retention and destruction schedule, and a non-biometric alternative. Templates should be deletable on termination.
- CCPA rights (access, deletion, correction, notice at collection) apply to California employees of covered tenants, so the product needs a per-employee data export and a documented retention schedule that reconciles deletion requests with the 2 to 3 year FLSA and I-9 retention duties.
- The DOJ rule matters for an India-built vendor mainly in one way: employee SSNs, bank details, biometrics and health-plan data fall into its categories, so US customers will ask who can access production data and from where. India is not described in the sources as a country of concern (see Gaps), so the rule does not itself bar Indian engineers from access, but it will show up in security questionnaires.
- Because no statute forces US-only hosting, the hosting region is a commercial and contractual question rather than a legal one for private-sector customers.

### Gaps
- The named six countries of concern (background, unverified: China including Hong Kong and Macau, Russia, Iran, North Korea, Cuba, Venezuela) and the bulk thresholds per data category were not retrieved; nor was any employment-data exemption in the rule.
- What US customers commonly demand on data residency: no reliable source was found. Background, unverified: US-region hosting, US-based or contractually restricted support access, a signed data processing addendum, and no offshore access to unmasked SSNs are common requests in enterprise security questionnaires; public-sector and regulated buyers may impose US-only access by contract.
- SOC 2: no source was retrieved. Background, unverified: SOC 2 is an AICPA attestation, not a law; mid-market and enterprise US buyers of HR and payroll software routinely ask for a SOC 2 Type II report (security criterion at minimum, often availability and confidentiality), and payroll processors are also asked for SOC 1 Type II because they affect customers' financial reporting.
- State SSN protection laws were not researched. Background, unverified: most states restrict printing or transmitting full SSNs (for example on pay stubs, ID cards or unencrypted email), and several require a written SSN protection policy.
- State data breach notification laws were not researched. Background, unverified: all 50 states, DC and territories have one; triggers are generally name plus SSN, driver's licence or financial account number; deadlines range from "without unreasonable delay" to fixed 30, 45 or 60 days; several require notice to the state attorney general.
- Whether other state comprehensive privacy laws exempt employee data (background: most do, California is the exception) was not confirmed.
- Texas (CUBI) and Washington biometric laws were not retrieved.
- CCPA numeric thresholds were cut off in the source; they need reading from the statute.

## Calendar and conventions: workweek, pay periods, tax year

### Takeaway
Biweekly pay is the most common US pay frequency (43% of private establishments), followed by weekly, semimonthly and monthly; monthly pay, the Indian default, is the least common.

### Cited Findings
- February 2023 BLS data: 43.0% of US private establishments pay every two weeks and 27.0% weekly; semimonthly and monthly are less common; no single frequency is used by a majority — [BLS Current Employment Statistics](https://www.bls.gov/ces/publications/length-pay-period.htm)
- Semimonthly 19.8% and monthly 10.3% are figures quoted by secondary sites citing the same BLS survey — [TimeTrex](https://www.timetrex.com/?p=14832) (secondary)
- Older BLS figures (March 2013): biweekly 36.5%, weekly 32.4%, semimonthly 19.8%, monthly 11.3% — [BLS, The Economics Daily](https://www.bls.gov/opub/ted/2014/ted_20140605.htm)
- A third set of figures attributed to BLS (42 / 34 / 19 / 5) could not be reconciled with BLS pages — [Motley Fool](https://www.fool.com/the-ascent/small-business/payroll/how-many-pay-periods-in-a-year/) (do not rely on)

### Inferences
- Pay periods per year are 52 (weekly), 26 (biweekly), 24 (semimonthly) and 12 (monthly). Biweekly calendars produce a 27th pay date in some years, which the pay-calendar generator must handle.
- Semimonthly periods do not line up with workweeks, so overtime must still be computed per workweek and then allocated to the pay period; the timesheet period and the pay period are different objects.
- The India-style monthly attendance cycle and April-March leave or financial year should not be assumed; US leave years are usually the calendar year or the employee's anniversary year, set by employer policy.

### Gaps
- State pay-frequency laws were not researched. Background, unverified: many states set a minimum pay frequency by worker type (for example weekly for manual workers in New York, at least twice a month in California), so monthly pay is unlawful for many non-exempt workers.
- Calendar tax year: no source retrieved. Background, unverified: individual and payroll tax reporting runs 1 January to 31 December; Form W-2 is due to employees by 31 January; Form 941 is quarterly.
- The FLSA workweek definition is covered under the FLSA section Gaps.
- Pay stub (wage statement) content rules by state were not researched.
