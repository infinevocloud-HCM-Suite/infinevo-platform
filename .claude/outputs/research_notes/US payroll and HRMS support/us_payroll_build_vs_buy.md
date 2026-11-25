# US payroll for an existing HCM SaaS: build, license or embed (as of November 2026)

| Route | What you buy | What you still build | Public price signal | Launch time reported |
|---|---|---|---|---|
| Build everything | Nothing | Tax engine, geocoding, forms, deposits, filings, W-2, ACH, licences, support | None | No independent figure found |
| License a tax engine (Symmetry, Vertex, BSI) | Gross-to-net tax calculation, jurisdiction lookup, withholding forms | Deposits, filings, W-2, money movement, registrations, liability | Not published by any vendor | No figure found |
| Engine + filing vendor (MasterTax, Greenshades, TaxBandits) | Calculation plus returns/e-file | Money movement, bank relationship, liability, operations | Not published | No figure found |
| Embedded payroll (Check, Gusto Embedded, Salsa, Zeal, ADP Embedded) | Calculation, deposits, filings, W-2/1099, money movement | UI or component wiring, data sync, pricing, first-line support, sales | Check suggests partners charge at least $40 base + $6 per employee per month | "A couple of months" (Check), "a few weeks, one engineer" (Salsa) - vendor claims |
| HR only + connector (Finch, Merge) | Read access to the customer's existing payroll system | HRMS, time, leave, export | Finch Starter about $50-65 per connection per month (sources conflict) | Not researched in depth |

Reading note: most web evidence here is vendor marketing or search-result summaries. Items marked "unconfirmed" were not verified on a primary page.

## Tax calculation engines that are licensed and embedded

### Takeaway
A tax engine only calculates; none of the engines found files returns, deposits tax or moves money. Symmetry is the one with a documented Java on-premise SDK and a hosted REST API, and it has been owned by Gusto since 2021; no vendor publishes prices.

### Cited Findings
- Gusto acquired Symmetry Software in July 2021; terms undisclosed; Symmetry said it would keep operating independently with its 43 staff — [Gusto](https://gusto.com/company-news/welcoming-symmetry-to-gusto); [Symmetry](https://www.symmetry.com/payroll-tax-insights/symmetry-software-joining-gusto)
- Gusto licensed the Symmetry engine as the base of its payroll product when it was a startup, before buying the company — [Symmetry customer story](https://www.symmetry.com/customer-stories/gusto-symmetry-payroll-compliance)
- Symmetry's current product list: Symmetry Tax Engine (gross-to-net, US and Canada), Payroll Forms (federal, state, local withholding forms), Payroll Point (local withholding from home and work address coordinates, with nexus and reciprocity logic), Minimum Wage Finder, I-9, calculators, plus AI tooling (STE MCP, Developer Copilot) — [Symmetry](https://www.symmetry.com/)
- Symmetry's stated numbers: 7,400+ tax jurisdictions, 130+ withholding forms, 40+ years in payroll compliance — [Symmetry](https://www.symmetry.com/)
- Symmetry Tax Engine ships two ways with the same engine and tax tables: a hosted REST API (JSON over HTTPS, API key) and an on-premise SDK for C/C++, Java and .NET that runs as a native library with a local `ste.db` and a licence file — [Symmetry docs](https://docs.symmetry.com/ste/overview/hosted-vs-on-premise)
- Updates: Symmetry applies tax-table changes itself on the hosted API; on-premise customers apply them by downloading a new release. The two forms are licensed separately. The docs give no update frequency — [Symmetry docs](https://docs.symmetry.com/ste/overview/hosted-vs-on-premise)
- The Symmetry docs page describes calculation only and does not mention filing, deposits or money movement — [Symmetry docs](https://docs.symmetry.com/ste/overview/hosted-vs-on-premise)
- Logos shown on Symmetry's site include ADP, Paychex, Paylocity, Paycor, UKG, Deel, Gusto, OnPay, Namely and Zenefits (logos shown, not confirmed contracts) — [Symmetry](https://www.symmetry.com/)
- Vertex Payroll Tax: SaaS engine with REST APIs; US federal, state and local plus Canada; gross-to-net, gross-up, multiple work locations, reciprocity; can also run on-premise in a Docker container; assigns a proprietary geocode per address; address cleansing is a separately licensed add-on; pricing not published; named partners are Heartland, isolved and SurePayroll — [Vertex](https://www.vertexinc.com/solutions/products/vertex-payroll-tax)
- BSI TaxFactory is described as a single engine for US federal, state and local payroll tax with a built-in TaxLocator component that identifies the jurisdiction (secondhand source, an SAP Insider profile) — [SAP Insider](https://sapinsider.org/?p=106255)
- Wolters Kluwer's W-2/1099 product is a filing tool, not a real-time calculation engine — [TrustRadius](https://www.trustradius.com/compare-products/vertex-vs-wolters-kluwer-w2-1099)

### Inferences
- For a Java/Spring Boot engine hosted in India, Symmetry on-premise (Java SDK) and Vertex in Docker are the two documented ways to keep calculation inside the platform's own runtime; the hosted APIs put a US network call into each pay calculation.
- Symmetry being owned by Gusto, which also sells Gusto Embedded, means a licensee buys from a company that competes in the adjacent embedded route.

### Gaps
- No vendor publishes pricing. Typical licence model (annual fee, per-employee or per-calculation) could not be confirmed from any source.
- Update frequency could not be confirmed for any engine.
- One search summary stated that Wolters Kluwer acquired Vertex in September 2020. This is unconfirmed and likely wrong: the Vertex product page makes no mention of Wolters Kluwer. Treat Vertex and Wolters Kluwer as separate until checked.
- BSI TaxFactory delivery form, languages and customer base were not found on a primary page (unverified background: it is commonly paired with SAP and other large ERP payrolls).
- No evidence found of a Wolters Kluwer / CCH embeddable calculation engine.
- W-4 handling specifics per engine were not confirmed beyond Symmetry Payroll Forms covering withholding forms.

## Embedded payroll APIs / payroll-as-a-service

### Takeaway
Embedded providers take over calculation, tax deposits, filings, year-end forms and money movement, and leave the platform with the product surface, pricing and customer relationship. Check, Gusto Embedded, Salsa, Zeal and (since September 2025) ADP Embedded Payroll are active; Rollfi was acquired in January 2025.

### Cited Findings
- Check: API and white-label components; calculates, pays and files 6,000+ taxes across 1,000+ jurisdictions; API covers tax deposits, money movement, paystubs, garnishments, post-tax deductions, net pay splits, benefits, W-2 and 1099 — [Check Core](https://www.checkhq.com/check-core)
- Check describes its money movement as fully licensed with redundant bank partners — [Check Core](https://www.checkhq.com/check-core)
- Check reports 65+ partners and nearly $1B a month in volume; Wave launched payroll on Check for 300,000+ businesses; a Filings API exposes filing status, deadlines, blockers and amendments — [Check 2025 review](https://www.checkhq.com/2025-year-in-review); [Check blog](https://checkhq.com/resources/blog/lessons-learned-from-65-embedded-payroll-partners)
- Check funding: $75M Series C led by Stripe at a reported $725M valuation, dated February 2022 by most sources — [Coverager](https://coverager.com/check-raises-75-million/)
- Check's partner playbook recommends partners charge customers a $40 monthly base fee plus $6 per employee per month as a minimum starting point. This is the resale price guidance, not Check's own wholesale price — [Check help](https://help.checkhq.com/go-to-market-playbook/6vJ1Gx6JntHnUJetZzQj3E/how-to-price-your-payroll-product--use-discounts-effectively/6vJ1Gx6JnvbdxYWEn1MJ9X)
- Check billing model for partners: flat base fee per company plus per-unit fee on actual usage, billed monthly in arrears; each unique employee or contractor in an approved payroll counts; some fees (failed account validation, failed payment) pass through — [Check help](https://help.checkhq.com/1d14274a2e7580b1bdedcc519517e94c)
- Gusto Embedded: pre-built UI flows plus APIs on Gusto's own payroll stack; price to the end customer is set by the platform provider — [Gusto Embedded terms](https://flows.gusto.com/terms); [Gusto Embedded](https://embedded.gusto.com/)
- Gusto's revenue-share language belongs to its referral channel programme, which is a different track from Embedded — [Gusto channel](https://gusto.com/partners/channel)
- HiBob and Xero are public Gusto Embedded partners — [HiBob](https://www.hibob.com/news/hibob-launches-native-us-payroll-to-cut-complexity-and-drive-confidence-for-smbs/); [Gusto](https://gusto.com/company-news/xero-to-launch-all-in-one-simple-payroll-for-us-small-business-customers-powered-by-gusto?idgm=m5ef98q4)
- Salsa: $20M Series A on 22 April 2025 led by Altos Ventures, $30M total; covers US and Canada — [Axios](https://www.axios.com/pro/fintech-deals/2025/04/22/exclusive-embedded-payroll-startup-salsa-20m); [PYMNTS](https://www.pymnts.com/payroll/2025/salsa-raises-20m-to-expand-embedded-payroll-services-across-us-and-canada/)
- Zeal: tax filing in all 50 states, W-2 and 1099 workers, and native earned wage access, which Check, Gusto Embedded and Salsa are said not to offer; last funding found was $15M in December 2023 — [FintechSpecs](https://fintechspecs.com/?p=1327); [CB Insights](https://www.cbinsights.com/compare/salsa-vs-zeal-5)
- Zeal has not published pricing — [G2](https://www.g2.com/products/puzzl-group-inc-zeal/pricing)
- Rollfi was acquired by Priority Payment Solutions in January 2025; total prior funding about $2M — [CB Insights](https://www.cbinsights.com/company/rollfi/financials)
- ADP Embedded Payroll launched September 2025 for SMB software providers, with enterprise APIs and white-labelled components of RUN Powered by ADP; PNC Bank announced as a partner on 28 October 2026 — [ADP newsroom](https://mediacenter.adp.com/2025-09-29-ADP-R-Embedded-Payroll-Gives-Partners-a-Competitive-Edge-with-Integrated-HCM-Solution); [CPA Practice Advisor](https://www.cpapracticeadvisor.com/2026/10/28/pnc-bank-and-adp-announce-embedded-payroll-solution-for-small-businesses/192683/)
- Paychex exposes a payroll API (pay components, pay periods, checks) but the client or Paychex still has to review and submit the payroll; partner access needs a formal review said to take weeks to months (third-party assessment) — [Paychex developer](https://developer.paychex.com/SimpleCheck); [Supergood](https://supergood.ai/api-report-card/paychex-flex)
- Paycor also runs an embedded software partner programme and gives no fixed timeline — [Paycor](https://www.paycor.com/partners/embedded-software/)

### Inferences
- The platform's remaining work on this route is the product layer: mapping its own employee, earnings and time data to the provider's objects, embedding or rebuilding the screens, pricing and billing, and first-line support.
- With an embedded provider the platform's own Java payroll engine is not used for US gross-to-net; the provider's engine is the system of record for US pay.
- Wholesale cost must sit below the $40 + $6 resale floor Check suggests, but the actual figure is not public.

### Gaps
- Whether any provider accepts a non-US (India-incorporated) software company as a partner, or requires a US entity, was not answered by any source found. This needs a direct question to each vendor.
- Wholesale prices, minimum commitments and implementation fees are not public for Check, Gusto Embedded, Salsa, Zeal or ADP.
- How tax liability is split by contract between provider and platform was not found for any vendor.
- No 2026 funding or status news was found for Zeal; its current standing is unconfirmed.
- Salsa's detailed feature scope was not confirmed on a primary page.

## Tax filing and deposit services alone

### Takeaway
Filing-only options exist for a platform that calculates itself, but the evidence found is thin: MasterTax and Greenshades are established in the service-bureau world, and TaxBandits offers a public e-file API aimed at payroll software.

### Cited Findings
- MasterTax is payroll tax software covering over 11,000 jurisdictions, sold to employers, PEOs and payroll service providers; it integrates with PrismHR — [Built In](https://builtin.com/company/mastertax); [PrismHR](https://www.prismhr.com/?p=221)
- Greenshades Tax Filing Center creates and e-files federal, state and local returns including Forms 940 and 941; a managed "Payroll Tax Service" adds state filing and payment execution, a compliance calendar and rejection follow-up, with the customer approving before filing — [Greenshades](https://go.greenshades.com/platform/tax-and-compliance/managed-payroll-taxes); [Greenshades](https://greenshades.com/tax-and-compliance/)
- TaxBandits offers an API for payroll and HCM software covering federal and state filings, state withholding, unemployment insurance and new hire reporting, with a negotiable revenue-share model for platforms — [TaxBandits](https://developer.taxbandits.com/api-for-payroll-software/)
- Symmetry itself frames the choice as build, buy or "blend" (keep core logic in-house, outsource local rules and forms) — [Symmetry](https://www.symmetry.com/payroll-software-for-payroll-service-providers)

### Inferences
- Engine plus filing vendor still leaves the platform holding client tax money and making deposits unless the filing vendor also executes payments, which only the Greenshades managed tier was seen to state.

### Gaps
- Whether MasterTax (reported elsewhere as ADP-owned; not confirmed in this research) is sold to new third-party software vendors in 2026 was not confirmed.
- Nothing was found on Sovos or Asure as filing-only services for software platforms.
- No pricing found for any filing vendor.

## Money movement

### Takeaway
A payroll company that moves funds acts as a third-party sender through an ODFI bank under Nacha rules, and money transmitter treatment varies by state and is unsettled in most. Embedded providers carry the bank relationships and licences themselves.

### Cited Findings
- In the ACH model the employer is the Originator and the payroll company is a Third-Party Sender working through its ODFI; the third-party sender needs an origination agreement with the ODFI and an agreement with each employer; ODFIs must register third-party senders with Nacha — [Venable Payments Law Tool Kit](https://books.venable.com/Payments-Law-Tool-Kit/2021/28-29)
- Nacha rules make explicit that a third-party sender must conduct its own risk assessment — [Nacha new rules](https://www.nacha.org/newrules?page=1)
- Same Day ACH per-payment limit has been $1 million since 18 March 2022; Nacha reported in April 2026 an approved increase to $10 million, with 17 September 2027 as the better-supported effective date — [Nacha](https://www.nacha.org/index%2ephp/news/same-day-ach-1-million-transaction-limit-guidance); [Nacha new rules](https://www.nacha.org/newrules?page=1)
- Nacha fraud-monitoring rule deadlines fell on 20 March 2026 (large senders) and 20 June 2026 (all others), per search snippets that should be checked against the rule text — [Nacha new rules](https://www.nacha.org/newrules?page=1)
- Most states have not said expressly whether money transmitter laws cover payroll processing; California, Ohio, North Carolina and Washington are reported to exclude it — [Morrison Foerster](https://www.mofo.com/resources/insights/191017-payroll-money-transmission); [Troutman transcript](https://www.troutman.com/wp-content/uploads/2024/07/Transcript_CFP_Navigating_New_Compliance_Challenges.pdf)
- Hawaii's regulator says payroll processors can fall under money transmitter law when they offer payroll advances — [Hawaii DFI](https://cca.hawaii.gov/dfi24/files/2023/06/2023-0607-Payroll-Processors.pdf)
- Massachusetts has issued a formal opinion on licensing for payroll and employee benefit services — [Mass.gov Opinion 024-003](https://www.mass.gov/opinion/opinion-024-003-licensing-requirements-for-payroll-and-employee-benefit-services)
- Check states its money movement is fully licensed with redundant bank partnerships, so the partner does not hold these itself — [Check Core](https://www.checkhq.com/check-core)

### Inferences
- A company without a US entity and US bank relationship cannot originate ACH on its own; the build route therefore implies a US subsidiary, a sponsoring ODFI and that bank's underwriting.
- Earned wage access and payroll advances are the features most likely to pull a payroll processor into money transmitter licensing.

### Gaps
- Nacha file format details, prefunding practice, ACH return risk and typical ODFI underwriting requirements were not sourced in this research.
- Paper check and paycard options per provider were not confirmed.
- State money transmitter positions date from 2019-2024 sources and have been changing; a current state-by-state chart was not found.

## Liability and regulation

### Takeaway
Under IRS rules the employer stays liable for employment taxes even when a reporting agent or payroll service provider deposits and files for it. A provider's exposure is contractual and regulatory rather than a transfer of the tax liability.

### Cited Findings
- Form 8655 lets a reporting agent sign and e-file Forms 940 and 941 and make deposits for the employer; the reporting agent assumes no liability for the employer's withholding, deposit or filing duties — [IRS third party arrangements](https://www.irs.gov/zh-hant/businesses/small-businesses-self-employed/third-party-arrangements)
- If a third party fails to deposit, the employer remains responsible for the tax, penalties and interest — [IRS outsourcing payroll](https://www.irs.gov/Businesses/Small-Businesses-&-Self-Employed/Outsourcing-Payroll-and-Third-Party-Payers); [Wolters Kluwer](https://www.wolterskluwer.com/en-my/expert-insights/protect-yourself-when-outsourcing-payroll)
- A Form 2678 (section 3504) agent is jointly liable with the employer, unlike a reporting agent — [Taxpayer Advocate](https://taxpayeradvocate.irs.gov/get-help/small-business/third-party-arrangements-form-employment-taxes)
- IRS failure-to-deposit penalty: 2% for 1-5 days late, 5% for 6-15 days, 10% beyond 15 days, 15% if unpaid more than 10 days after an IRS notice; tiers do not stack — [IRS](https://www.irs.gov/payments/failure-to-deposit-penalty)
- Maine requires every payroll processor to be licensed before serving Maine employers, with fees by client count and a fidelity bond of twice the highest weekly payroll processed or $5,000,000, whichever is less — [Maine Bureau](https://www.maine.gov/pfr/consumercredit/industry/licensing/payroll_processor.htm); [Maine statute 1495-E](https://legislature.maine.gov/statutes/10/title10sec1495-E.html)

### Inferences
- Because the tax liability stays with the employer, a provider's commercial promise to pay penalties for its own errors is a contract term to check in each vendor agreement, not something the law gives.
- On the build or engine route the platform itself becomes the reporting agent and needs Form 8655 authorisations per client plus state-level registrations.

### Gaps
- A full list of states that license or register payroll service providers was not found; only Maine was confirmed.
- SOC 1 Type II and SOC 2 expectations for payroll providers were not sourced.
- Trust Fund Recovery Penalty rules were only mentioned in passing by sources.
- Which embedded providers contractually cover penalties caused by their errors was not found.

## What well-known HR platforms did

### Takeaway
Among the platforms checked, the only clearly documented recent choice is HiBob, which embedded Gusto for its US payroll in September 2025. Zoho shipped its own US payroll in December 2024, and Gusto itself began on a licensed Symmetry engine.

### Cited Findings
- HiBob announced native US payroll and benefits administration in September 2025, powered by Gusto's payroll engine and PlanSource for benefits, in early access at launch; dozens of customers were reported live later — [HiBob](https://www.hibob.com/news/hibob-launches-native-us-payroll-to-cut-complexity-and-drive-confidence-for-smbs/); [OutSail](https://www.outsail.co/post/hibobs-us-payroll-play)
- Zoho launched Zoho Payroll in the US on 12 December 2024, stated as available in all 50 states, from $39 a month, after India (2019) and the UAE (2023); multi-state payroll came in a later plan — [Business Wire](https://www.businesswire.com/news/home/20241212017805/en); [CPA Practice Advisor](https://www.cpapracticeadvisor.com/2024/12/13/zoho-releases-new-payroll-solution-in-the-u-s/152935/); [Zoho](https://www.zoho.com/blog/payroll/introducing-zoho-payroll-professional-plan-usa.html)
- Zoho ran a "request your state" page for US payroll, indicating a state-by-state roll-out before general availability — [Zoho](https://www.zoho.com/us/payroll/request-state.html)
- Gusto started on a licensed Symmetry engine and bought the vendor in 2021 — [Symmetry](https://www.symmetry.com/customer-stories/gusto-symmetry-payroll-compliance)
- Deel sells US payroll in all 50 states aimed at companies with fewer than 200 US employees, with self-serve and managed tiers; Deel's payroll capability came largely through acquisitions including PaySpace — [Deel](https://deel.com/us-payroll); [Sacra](https://sacra.com/chat/h/791b689d-39a5-46d9-9f71-642e78747902/)
- Deel appears among the logos on Symmetry's site — [Symmetry](https://www.symmetry.com/)
- Rippling says its suite was built in-house as one integrated system (vendor claim) — [Rippling](https://www.rippling.com/blog/rippling-vs-deel-global-workforce-management-comparison)
- Keka markets a US offering claiming 50-state payroll compliance and US data residency on AWS (vendor claim, no launch date found) — [Keka](https://www.keka.com/lp/us/hrms)
- Xero chose Gusto Embedded for US payroll — [Gusto](https://gusto.com/company-news/xero-to-launch-all-in-one-simple-payroll-for-us-small-business-customers-powered-by-gusto?idgm=m5ef98q4)

### Inferences
- Zoho's US state coverage appears to have been built up over time before the December 2024 launch, which suggests a multi-year in-house effort, but no source states the duration.
- An established HR platform of HiBob's size choosing to embed rather than build is a data point on how peers weigh the build cost.

### Gaps
- No source confirms whether Zoho, Keka or Deel use a licensed tax engine underneath; the Deel logo on Symmetry's site is suggestive only.
- Nothing found on Darwinbox, greytHR, Personio or Remote running US payroll, or on how they deliver it.
- No build duration was found for any platform.

## Effort, timeline and ongoing maintenance

### Takeaway
Only embedded providers publish launch timelines, and those are their own claims: weeks to a couple of months. No independent figure was found for building in-house or for integrating a licensed engine.

### Cited Findings
- Check says partners can launch payroll in as little as a couple of months — [Check](https://www.checkhq.com/post/launching-payroll-with-check)
- Check's standard customer migration is about 2 calendar weeks end to end; for enterprise customers it advises a start date no earlier than 6 weeks after the setup request — [Check help](https://checkhq.helpkit.so/1d74274a2e75801f9c4df331b0b005d1?pvs=25)
- Salsa says most customers get first users running real payroll within a few weeks with one engineer — [Salsa](https://www.salsa.dev/blog-post/we-answer-your-biggest-payroll-product-question-launch-now-or-wait)
- Gusto ranks embedded as fastest to market and in-house as longest, with full control but more work (qualitative only) — [Gusto Embedded](https://embedded.gusto.com/blog/payroll-api-what-it-is-how-embedded-payroll-is-different/)
- Scale of the rule set: Symmetry cites 7,400+ tax jurisdictions, Check 6,000+ taxes across 1,000+ jurisdictions, MasterTax over 11,000 jurisdictions (vendors count differently) — [Symmetry](https://www.symmetry.com/); [Check Core](https://www.checkhq.com/check-core); [Built In](https://builtin.com/company/mastertax)
- Changes arrive all year, not only in January: one payroll vendor's 2022 log shows state table, county and ZIP-rule changes through the year, and Sage's 2026 list includes new wage bases and paid-leave rates effective 1 January 2026 — [Aptora](https://support.aptora.com/tom-release-notes/payroll-tax-updates-2022/); [Sage](https://help-sage100.na.sage.com/PayrollTaxUpdates/Content/TaxUpdate/TaxUpdates2022.htm)

### Inferences
- The jurisdiction counts (thousands, mostly local) explain why even large payroll vendors appear on Symmetry's customer wall rather than maintaining tables alone.
- The maintenance burden on the build route is a standing research and release function, not a one-off project.

### Gaps
- No sourced figure for engineer-years, cost or elapsed time to build a US tax engine and filing operation.
- No sourced count of US payroll rule changes per year.
- No sourced integration time for Symmetry, Vertex or BSI.

## In-between route: HR-only US launch with a link to an existing payroll provider

### Takeaway
Unified payroll APIs let an HR product read from, and in limited cases write to, the customer's existing US payroll system. They do not run payroll, and write access is narrow.

### Cited Findings
- Finch claims 250+ integrations with HRIS and payroll providers, counting both API-based and "assisted" ones — [Finch](https://tryfinch.com/finch-vs-merge)
- Finch says it is the only unified API officially approved by Gusto and the only one that can create and manage payroll deductions programmatically (vendor claims) — [Finch](https://tryfinch.com/finch-vs-merge)
- Finch pricing: three plans; Starter reported at $65 a month by one tracker and $50 per connection per month by others; higher tiers undisclosed — [PricingSaaS](https://pricingsaas.com/companies/tryfinch); [SourceForge](https://sourceforge.net/software/product/Finch-API)
- Merge (a competitor) says about 90% of Finch's integrations are "assisted" and may carry rising set-up fees — [Merge](https://www.merge.dev/blog/finch-api-pricing)
- API aggregators usually give read-only access to payroll data, which is different from processing payroll (Gusto's framing) — [Gusto Embedded](https://embedded.gusto.com/blog/payroll-api-what-it-is-how-embedded-payroll-is-different/)
- Paychex production API access goes through a partner review of weeks to months and each customer must approve the link (third-party assessment) — [Supergood](https://supergood.ai/api-report-card/paychex-flex)
- Gusto runs a partner programme with referral revenue share separate from Embedded — [Gusto](https://gusto.com/partners/channel)

### Inferences
- On this route time, leave and employee data flow out to the payroll provider mainly by file export or deduction-level writes; pushing hours and earnings into a third-party pay run is not shown to be generally available through unified APIs.
- This route carries no tax or money-movement liability because the customer's existing provider keeps running payroll.

### Gaps
- Direct partner programmes of ADP (Marketplace), QuickBooks Payroll and Paychex were not researched in detail; terms and fees not confirmed.
- Merge's own payroll provider list and write coverage were not confirmed from Merge's documentation.
- Whether any unified API supports writing time or earnings into a pay run was not confirmed.
