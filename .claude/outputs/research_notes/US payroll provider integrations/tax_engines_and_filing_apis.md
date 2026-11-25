# US payroll tax engines and filing APIs — vendor fact sheet (as read 2026-11-24)

Source marks used on every fact: **[V]** = read on the vendor's (or IRS/Nacha) own page in this round; **[S]** = secondary source, or a search-result summary of a vendor page that was not opened directly; **[U]** = unconfirmed.

| Vendor | Role | Delivery | Calculates / files | Moves tax money? | Public price | Mark |
|---|---|---|---|---|---|---|
| Symmetry Tax Engine (Gusto) | Calculation | Hosted REST/JSON; on-prem SDK (Java 11+, .NET, C/C++) | Federal, 50 states + DC + 5 territories, local, SUI, PFML/SDI, gross-up, benefits | No | Quote only; metric = paychecks, forms, calls | V |
| Symmetry Payroll Point / Forms / MWF | Tax-data, geocoding, W-4 forms | REST + portal; SPF also hosted UI and on-prem | Address to taxes; 160+ withholding forms; minimum wage | No | Quote only | V |
| Symmetry Tax Filing (STF) | Filing (new) | gRPC or REST | "Sends payroll data, reviews prepared filings, collects files" | Not known | Not public | V (one line only; docs behind login) |
| Vertex Payroll Tax | Calculation | SaaS REST; on-prem in Docker | US + Canada gross-to-net, gross-up, vacation pay, reciprocity | No | Not public | V |
| BSI TaxFactory | Calculation | SaaS / integrates with payroll system; no public API docs | Gross-to-net, garnishments, reciprocity, pension | No | Not public | V |
| BSI ComplianceFactory | Filing + remittance | SaaS | Schedule, pay, file payroll taxes; garnishment remittance; W-2 | Says "pay" — method not stated | Not public | V |
| MasterTax (ADP) | Filing + deposit software/service | Software, plus outsourced service | 11,000+ US jurisdictions; returns, payments (check, ACH debit) | Yes (creates payments) | Not public | V |
| ADP SmartCompliance Employment Tax | Managed filing service | Service, integrates with Workday/Oracle/SAP/Infor | Registrations, filings, deposits, notices, amendments | Yes | Not public | V |
| Greenshades | Filing (DIY + managed) | Cloud + "open API"; ERP integrations | 941, 940, W-2, SUI, some local, new hire to 50 states | Managed tier: state filing and payment execution | Not public | V |
| TaxBandits API | Form e-filing | REST, OAuth2, SDKs incl. Java, sandbox | 94x, W-2, 1099, ACA, state withholding/SUI/new hire | Not stated on pages read | From $5.95 per 94x form (retail) | V / S |
| Aatrix (Sovos) | OEM forms + payments | Partner integration (60+ payroll products) | W-2, 941, 940, state withholding and SUI reports and payments | Yes (federal deposits, state payments listed) | Per-filing prices public but inconsistent | S |
| Tax1099, Avalara 1099 (Track1099) | Form e-filing | API | 1099, W-2, 94x | No evidence | Tax1099 from $2.99/form | S |

## 1. Symmetry Tax Engine (STE) — calculation engine

### Takeaway
STE is the most completely documented engine: a stateless calculator with a hosted JSON API and an on-premise Java SDK that accept the same payload, covering federal, all states, territories and local taxes plus Canada. Its docs describe no filing or deposit function inside STE, no garnishment calculation, and no public price.

### Cited Findings
- [V] STE is "a payroll tax calculation engine for the U.S. and Canada"; caller supplies addresses, wages and tax form elections; it returns employee and employer withholding — [docs.symmetry.com what-is-the-ste](https://docs.symmetry.com/ste/overview/what-is-the-ste.md)
- [V] It calculates on the rules in force for the pay date supplied, which covers current, historical, retroactive and off-cycle payrolls — [what-is-the-ste](https://docs.symmetry.com/ste/overview/what-is-the-ste.md)
- [V] Stateless: each call is self-contained, no employee data or session is kept; the caller must send prior totals such as year-to-date wages — [what-is-the-ste](https://docs.symmetry.com/ste/overview/what-is-the-ste.md)
- [V] Federal coverage: FIT, FICA incl. Additional Medicare, FUTA, pension/annuity withholding (W-4P/W-4R), railroad retirement (RRTA, RUIA) — [STE coverage](https://docs.symmetry.com/ste/overview/coverage.md)
- [V] State coverage: income tax for 50 states, DC and five territories (American Samoa, Guam, CNMI, Puerto Rico, USVI); SUI/SUTA and surcharges, PFML/FLI, SDI, workers' compensation and other assessments, transit taxes, child-care contributions, long-term care — [STE coverage](https://docs.symmetry.com/ste/overview/coverage.md)
- [V] Local coverage: county, city and school-district income taxes, OLF, EIT, LST; deepest in Ohio (city, school district, JEDD/JEDZ) and Pennsylvania (EIT, LST, Philadelphia); employer taxes such as Seattle payroll expense tax, NY MCTMT, Nevada MBT — [STE coverage](https://docs.symmetry.com/ste/overview/coverage.md)
- [V] Special cases covered: pre-tax and post-tax benefits (401(k), Section 125, HSA), imputed income, supplemental wages, gross-up, resident/nonresident taxation, reciprocity, credits for tax paid to other states, courtesy withholding, nonresident certificates — [what-is-the-ste](https://docs.symmetry.com/ste/overview/what-is-the-ste.md)
- [V] Canada: all 10 provinces and 3 territories, CPP/CPP2, QPP/QPP2, EI, QPIP, EHT — [STE coverage](https://docs.symmetry.com/ste/overview/coverage.md)
- [V] Three-step workflow: (1) turn addresses into location codes with the Symmetry Location Service, (2) find the applicable taxes and their unique tax IDs, (3) calculate per pay run; steps 1–2 are per employee and repeat on address change — [what-is-the-ste](https://docs.symmetry.com/ste/overview/what-is-the-ste.md)
- [V] Hosted endpoint: `POST https://ste.symmetry.com/ste-hosted/v1/payCalc`; staging `https://ste-staging.symmetry.com/ste-hosted`; API key sent as the raw `Authorization` header value (no `Bearer`); separate staging and production keys — [hosted API quickstart](https://docs.symmetry.com/ste/quickstarts/hosted-api-quickstart)
- [V] Request `PayCalcRequest.payCalc[]` requires `employeeID`, `payrollRunParameters` (`payDate`, `payPeriodNumber`, `payPeriodsPerYear`), `wages[]` (`locationCode`, `wageType`, `grossWages`, optional `mtdWages`/`qtdWages`/`ytdWages`, `hours`, `hasNexus`, regular/supplemental calc method) and `taxJurisdictionParms[]` (`uniqueTaxID`, `locationCode`, `isExempt`, `isResident`, `hasNonResCertificate`, `additionalWH`, `ytdWH`, override rate/wage base, `miscParameters[]` for W-4 style elections such as `FILINGSTATUS`); optional `benefits[]`, `imputedIncome[]`, custom wage/benefit types, about 50 `options` flags — [compute gross-to-net](https://docs.symmetry.com/ste/api-reference/payroll-calculations/compute-gross-to-net-tax-withholding)
- [V] Response `PayCalcResponse` has `transactionStamp` (`steVersion`, `schemaVersion`, `calcDateTime`) and per employee `taxCalculation[]` (`uniqueTaxID`, `taxAmount`, `subjectWages`, `rate`, `wageBase`, `isResident`, `isUnemploymentTax`, CTD/QTD/YTD subject and excess wages) and `benefitCalculation[]` — [compute gross-to-net](https://docs.symmetry.com/ste/api-reference/payroll-calculations/compute-gross-to-net-tax-withholding)
- [V] The request carries no name, SSN or street address: the only employee identifier is `employeeID`, and location is a `locationCode` — [compute gross-to-net](https://docs.symmetry.com/ste/api-reference/payroll-calculations/compute-gross-to-net-tax-withholding)
- [V] A `version` query parameter or header selects the STE database version; an endpoint lists installed STE database versions — [compute gross-to-net](https://docs.symmetry.com/ste/api-reference/payroll-calculations/compute-gross-to-net-tax-withholding); [docs index](https://docs.symmetry.com/llms.txt)
- [V] Quickstart example: $4,000 biweekly, single, pay date 2026-06-15 returns FIT `taxAmount` 613.15, `steVersion` "2026.7.2" — [hosted API quickstart](https://docs.symmetry.com/ste/quickstarts/hosted-api-quickstart)
- [V] Other hosted endpoints: gross-up, benefit taxability check, address-to-location-code, taxes for a location, unique tax IDs, OpenAPI YAML, Postman collection — [docs index](https://docs.symmetry.com/llms.txt)
- [V] Hosted and on-premise use "the same calculation engine — same tax tables, same logic, same results" and the same JSON; they are licensed separately — [hosted vs on-premise](https://docs.symmetry.com/ste/overview/hosted-vs-on-premise.md)
- [V] Hosted: Symmetry manages servers, tax-table updates and scaling. On-premise: engine and license file are downloaded from a Download Center; "you decide when to apply new releases" — [hosted vs on-premise](https://docs.symmetry.com/ste/overview/hosted-vs-on-premise.md)
- [V] On-premise SDK languages: C/C++, Java, .NET; Java needs JDK 11 or later plus the JNA dispatch native library; platforms Windows x64/x86, Linux x64/ARM64, macOS; install is under 40 MB on Win64 — [platform and language support](https://docs.symmetry.com/ste/overview/platform-and-language-support.md)
- [V] On-premise needs a license file (`ste-license.dat` or `STE_LICENSE_KEY`); "will not function without a valid license file"; on-premise still calls the hosted Symmetry Location Service with an `SLS_API_KEY` for geocoding — [licensing](https://docs.symmetry.com/ste/overview/licensing.md)
- [V] Staging key is issued when a trial begins; production key after licensing — [hosted vs on-premise](https://docs.symmetry.com/ste/overview/hosted-vs-on-premise.md)
- [V] Symmetry's marketing guide states the engine "averages 3.32 milliseconds per calculation" — [payroll-tax-api-guide](https://www.symmetry.com/payroll-tax-api-guide)
- [V] Pricing is quote-based and "tracks what your platform produces: paychecks, forms, calls"; implementation, support and tax updates are included; on-premise deployment and Canada are sold separately — [symmetry.com pricing](https://www.symmetry.com/solutions/pricing)
- [S] Vendr lists a median of about $75,000 per year for Symmetry Software, from 3 purchases — [Vendr](https://www.vendr.com/marketplace/symmetry-software)
- [V] Customer logos on the pricing page include ADP, Paychex, Paycor, Paylocity, UKG, OnPay, Namely, Deel, Gusto, Zenefits, Acumatica; testimonial from Salsa — [symmetry.com pricing](https://www.symmetry.com/solutions/pricing)
- [S] Symmetry was acquired by Gusto on 8 July 2021 and continues to run as its own business — [Gusto news](https://gusto.com/company-news/welcoming-symmetry-to-gusto)
- [V] 2026 additions: an STE MCP server, Tax Logic Chat/API (explains a calculation), Developer Copilot — [docs.symmetry.com](https://docs.symmetry.com/)

### Inferences
- The engine fits the "send gross, get tax lines" pattern, but the caller owns all state: YTD wages, YTD withholding, W-4 elections and location codes must be stored in the payroll engine and re-sent on every call.
- A Spring Boot engine has two routes: HTTPS JSON to the hosted API (no native code), or the Java SDK, which wraps a native library through JNA and so adds an OS-specific binary to the deployment.
- Retroactive recalculation is driven by pay date; a `version` selector also exists, which suggests a past result can be reproduced against a named table release.

### Gaps
- Garnishment calculation is not mentioned anywhere in the STE pages read; treat as not offered until Symmetry confirms.
- No published update cadence, rate limits, hosting region, data-retention statement or security certification on the pages read.
- No statement on whether Symmetry licenses to non-US companies or needs a US entity. Deel (a non-payroll-processor global firm) appears as a logo, which proves nothing about entity rules.
- No exact count of taxes or jurisdictions on the docs pages; "7,400+ jurisdictions" appears in a Symmetry guide but not as a product claim.

## 2. Symmetry companion products — Payroll Point, Payroll Forms, Minimum Wage Finder, Guides, Tax Filing

### Takeaway
Symmetry sells the surrounding pieces as separate APIs: address-to-tax determination, W-4 and state form capture that emits engine parameters, minimum-wage lookup, and a rates-and-filing-rules reference. A product called Symmetry Tax Filing now appears in the docs index, but its pages sit behind a login, so its scope is unconfirmed.

### Cited Findings
- [V] Payroll Point (PRP): given a home address, a work address and a date, returns every applicable tax and rate; validates addresses against postal data, geocodes to latitude/longitude "aiming for rooftop accuracy", and matches against 35,000 tax boundary shapefiles — [PRP introduction](https://docs.symmetry.com/prp/overview/introduction.md)
- [V] PRP returns federal, employee and employer taxes, rates or rate types, a unique tax ID per tax, FIPS/GNIS codes and Pennsylvania PSD codes; it evaluates nexus, reciprocity, nonresident certificates and courtesy withholding — [PRP introduction](https://docs.symmetry.com/prp/overview/introduction.md)
- [V] PRP does not calculate withholding amounts; that is STE's job. It is offered as a browser portal and a REST API — [PRP introduction](https://docs.symmetry.com/prp/overview/introduction.md)
- [V] Payroll Forms (SPF): more than 160 withholding forms — federal, state, territory, local, nonresident, exemption, military spouse — plus Canadian TD1 forms — [what-is-spf](https://docs.symmetry.com/spf/overview/what-is-spf.md)
- [V] SPF returns a filled, signed government PDF and the elections as structured tax parameters; a published mapping converts them to STE parameters — [what-is-spf](https://docs.symmetry.com/spf/overview/what-is-spf.md)
- [V] SPF comes as an API, a hosted UI, or on-premise; "SPF stores nothing" — only completed form IDs are counted for usage; the caller must store the PDF and parameters — [what-is-spf](https://docs.symmetry.com/spf/overview/what-is-spf.md)
- [V] Minimum Wage Finder returns rates by address or coordinates and effective date, with endpoints for recent and upcoming changes; available standalone or as a PRP add-on — [docs index](https://docs.symmetry.com/llms.txt)
- [V] Symmetry Guides: "payroll tax rates, rules, and filing requirements across 7,000+ jurisdictions", with webhooks; it replaces the older Tax Notification Service — [docs.symmetry.com](https://docs.symmetry.com/)
- [V] Symmetry Tax Filing (STF) is listed as a product: "Sends payroll data, reviews prepared filings, and collects files over gRPC or REST" — [docs.symmetry.com](https://docs.symmetry.com/)
- [V] The STF overview page returned only "Log in" when opened — [STF index](https://docs.symmetry.com/stf/index.md)
- [V] Symmetry's public pricing page lists twelve products and does not name a tax filing product — [symmetry.com pricing](https://www.symmetry.com/solutions/pricing)
- [V] Calculators by Symmetry: paycheck, gross-up, bonus, final-pay and 401(k) calculators as widgets and an API — [docs index](https://docs.symmetry.com/llms.txt)

### Inferences
- The one-line STF description ("reviews prepared filings, collects files") reads as return preparation and file output, not as a deposit or agency-transmission service; this is a reading of one sentence, not a confirmed fact.
- SPF plus PRP plus STE form a closed chain: forms produce parameters, addresses produce tax IDs, and both feed the calculation call.

### Gaps
- STF: forms covered, jurisdictions, who transmits, whether money moves, and general availability are all unknown (docs gated, no press release found).
- PRP batch limits and pricing per lookup not stated.

## 3. Vertex Payroll Tax — calculation engine

### Takeaway
Vertex offers the same calculate-only role as a SaaS REST API with an on-premise Docker option, covering the US and Canada with geocode-based jurisdiction assignment. Its public developer page lists endpoints but not schemas, and it publishes no price.

### Cited Findings
- [V] "SaaS-based payroll tax software for HCM and staffing applications"; calculates "gross-to-net payroll tax deductions for the U.S. and Canada" — [Vertex Payroll Tax](https://www.vertexinc.com/solutions/products/vertex-payroll-tax)
- [V] Calculation types: Gross to Net, Gross Up, vacation pay; handles multiple work locations in a pay period, reciprocity, and employees who move mid-period — [Vertex Payroll Tax](https://www.vertexinc.com/solutions/products/vertex-payroll-tax)
- [V] Federal: withholding, FUTA, FICA, railroad retirement. State: withholding, SUI, SDI, state miscellaneous taxes. Local: county, city, borough, township, school district, occupational privilege and head taxes; US territories included — [Vertex Payroll Tax](https://www.vertexinc.com/solutions/products/vertex-payroll-tax)
- [V] Jurisdictions are assigned through a "unique Geocode" for residence and work location rather than ZIP code — [Vertex Payroll Tax](https://www.vertexinc.com/solutions/products/vertex-payroll-tax)
- [V] Address cleansing to "side-of-street" level is a related product that "requires an additional license and fee"; GeoAlign is a further add-on — [Vertex Payroll Tax](https://www.vertexinc.com/solutions/products/vertex-payroll-tax)
- [V] Delivered as cloud SaaS, with an on-premise option that runs "within a docker container"; integrates through REST APIs "in real-time"; SOC 2 Type 2 — [Vertex Payroll Tax](https://www.vertexinc.com/solutions/products/vertex-payroll-tax)
- [V] Developer endpoints: Paycheck (one employee), PaycheckBatch (array), GrossUp, VacationPay, Queries, FormsDownload (tax form links by work and residence location), Reports (XML), Logs (logs of calculation requests); page last updated 2026-06-29 — [developer.vertexinc.com](https://developer.vertexinc.com/payroll/docs/payroll-cloud)
- [V] Named technology partners: Heartland Ovation Payroll, isolved, SurePayroll (Paychex) — [Vertex Payroll Tax](https://www.vertexinc.com/solutions/products/vertex-payroll-tax)
- [S] Vertex data sheets cite more than 19,000 jurisdictions and monthly data updates; a CMiC page says the cloud version removes the monthly install — [Vertex 2022 data sheet](https://www.vertexinc.com/sites/default/files/2022-03/vertex-payroll-tax-data-sheet.pdf); [CMiC](https://docs.cmicglobal.com/portal/Content/E_Reference_Material/Vertex/Vertex_Project/Vertex_Transition_To_Vertex_Cloud.htm)
- [S] Oracle Cloud HCM uses Vertex data for US payroll — [Oracle docs](https://docs.oracle.com/en/cloud/saas/human-resources/24d/fapus/vertex-data-for-the-us.html)

### Inferences
- The Logs and Reports endpoints imply the Vertex cloud service retains request data, unlike Symmetry's stated stateless design; this needs confirming with Vertex.
- The 19,000 figure is from 2021–2022 data sheets and may count jurisdictions differently from Symmetry's 7,000+.

### Gaps
- Request/response schema, authentication, SDK languages, sandbox access and versioning were not on the public page read.
- Garnishment calculation: not mentioned.
- Price, minimums and foreign-entity eligibility: not public.

## 4. BSI TaxFactory and ComplianceFactory

### Takeaway
BSI is the one vendor in this set that states both sides — a calculation engine that includes garnishments, and a SaaS suite that schedules, pays and files — but it publishes no API reference, counts or prices, and sells mainly into SAP, Infor and similar enterprise payroll systems.

### Cited Findings
- [V] TaxFactory is "a single, integrated U.S. payroll tax calculation engine" with a built-in TaxLocator for jurisdiction assignment — [bsi.com TaxFactory](https://bsi.com/products-and-services/bsi-taxfactory)
- [V] It covers tax location, wage and tax calculation, wage attachments (garnishments), reciprocal taxes, gross-to-net, qualified benefit plan calculations and pension payouts — [bsi.com TaxFactory](https://bsi.com/products-and-services/bsi-taxfactory)
- [V] Canada is a separate product, TaxFactory Canada — [bsi.com TaxFactory](https://bsi.com/products-and-services/bsi-taxfactory)
- [V] Target markets include "payroll vendors and service bureaus"; no price is listed — [bsi.com TaxFactory](https://bsi.com/products-and-services/bsi-taxfactory)
- [V] ComplianceFactory is a cloud SaaS suite to "schedule, pay, assess, and file payroll taxes", plus remittances to garnishment authorities, employee W-2 access and "digital power of attorney letters", across "Federal, State, Local and Territory" — [bsi.com ComplianceFactory](https://bsi.com/products-and-services/bsi-compliancefactory/)
- [V] It "integrates with your payroll system" and is aimed at "payroll vendors, service bureaus, and growing enterprises" — [bsi.com ComplianceFactory](https://bsi.com/products-and-services/bsi-compliancefactory/)
- [S] Infor documents TaxFactory as a third-party SaaS product that interacts with its payroll module; SAP Payroll passes wage and employee data and receives calculated taxes — [Infor docs](https://docs.infor.com/csstlwsn/10.1.1-hybrid/en-us/csstlwsnolh/cscsov/nic1471528985718.html); [SAPinsider](https://sapinsider.org/blogs/bsi-taxfactory-delivers-a-single-engine-for-u-s-payroll-tax-calculation/)

### Inferences
- "Digital power of attorney letters" suggests ComplianceFactory supports the agent-authorisation paperwork, but the page does not say whether BSI or the customer is the filer.

### Gaps
- No public API documentation, language list, jurisdiction count, update cadence or price for either product.
- Whether ComplianceFactory itself originates the tax payments, and from whose bank account, is not stated.

## 5. Wolters Kluwer / CCH and newer API-first calculators, open source

### Takeaway
No evidence was found this round of a Wolters Kluwer/CCH payroll tax calculation engine with an API, nor of a maintained open-source library covering federal plus all states. The newer "API-first" names found were form-filing or embedded-payroll products, not standalone calculators.

### Cited Findings
- [S] `python-taxes` (PyPI) calculates Social Security, Medicare and federal income tax withholding from IRS Pub 15-T; support listed only through 2025 — [PyPI](https://pypi.org/project/python-taxes/)
- [S] `CertiPay.Taxes.State` / `.Federal` (NuGet, .NET) hold withholding tables; the state package is at version 2018.0.22 — [NuGet](https://www.nuget.org/packages/CertiPay.Taxes.State)
- [S] `tenforty` computes income tax returns, not payroll withholding, and supports only three states — [GitHub](https://github.com/mmacpherson/tenforty/blob/main/README.md)
- [V] Calculators by Symmetry exposes paycheck and gross-up calculators as an API, separate from STE — [docs index](https://docs.symmetry.com/llms.txt)
- [S] Symmetry's own estimate for building the equivalent in-house is about 336 weeks and $2.76M; the source sells the alternative — [Symmetry](https://www.symmetry.com/payroll-tax-insights/payroll-solution-build-cost)

### Inferences
- Open-source options cover federal withholding at best and are stale for 2026; none handles state, local, SUI or PFML.

### Gaps
- Wolters Kluwer / CCH: not researched to a vendor page; no API product surfaced in any search. Unconfirmed either way.
- Abound, Keeper, OnPay and Gusto standalone calculation APIs: none surfaced. Gusto's calculation capability is sold through Symmetry or through Gusto Embedded (a full embedded payroll, outside this note's scope).
- No independent engineering write-up by a payroll builder on integrating a tax engine was found; the search returned vendor marketing only.

## 6. TaxBandits API — form e-filing

### Takeaway
TaxBandits is a self-serve REST API with a free sandbox and a Java SDK that e-files 94x, W-2, 1099 and state payroll forms. The pages read describe returns only — nothing on deposits — and the retail price is a few dollars per form.

### Cited Findings
- [V] Forms: 941, 940, 943, 944, 941/940 Schedule R, W-2, 1099, 1042-S, ACA 1095-B/C, W-9/W-8, extension forms; W-2 and 1099 can be e-filed with states — [developer.taxbandits.com](https://developer.taxbandits.com/)
- [V] 94x set: 941, 941 (SP), 940, 940 (SP), 943, 944, 945, 941-X; Schedule R for CPEOs, PEOs and Section 3504 agents — [94x overview](https://developer.taxbandits.com/docs/94xForms/Overview)
- [V] Workflow: Create (up to 250 returns per request), sign, Transmit to the IRS, poll Status; webhook "E-file Status Change (Federal)"; OAuth 2.0 bearer token — [94x overview](https://developer.taxbandits.com/docs/94xForms/Overview)
- [V] Signing methods: Online Signature PIN, Form 8453-EMP, Form 8879-EMP, or RA PIN, depending on filer type; the API can submit a 94x Online Signature PIN request for a client business — [94x overview](https://developer.taxbandits.com/docs/94xForms/Overview)
- [S] Reporting agents authorised by Form 8655 sign with their 5-digit IRS RA PIN — [TaxBandits](https://taxbandits.com/94x-software/efile-form-941-online)
- [V] State payroll filings offered to payroll software: state withholding returns, unemployment (SUI) wage reports, new hire reporting; number of states not stated — [API for payroll software](https://developer.taxbandits.com/api-for-payroll-software/)
- [V] SDKs: Java, Node.js, .NET/C#, Python, Ruby; Postman collection; free developer account with sandbox that simulates e-filing; SOC 2 — [developer.taxbandits.com](https://developer.taxbandits.com/); [API for payroll software](https://developer.taxbandits.com/api-for-payroll-software/)
- [V] API versions 1.7.1, 1.7.3 and 2.0.0 are documented — [developer.taxbandits.com](https://developer.taxbandits.com/)
- [V] Corrections to filed W-2/1099 supported; recipient copies by post or online portal; TIN matching — [developer.taxbandits.com](https://developer.taxbandits.com/)
- [S] Retail price: Form 941 from $5.95, falling with volume to about $3.00; Schedule R $9.95 plus $1.00 per line; 941-X $2.99; the vendor's own tables disagree on tier boundaries — [TaxBandits 941 page](https://taxbandits.com/94x-software/efile-form-941-online); [support article](https://support.taxbandits.com/art/15260/how-much-does-it-cost-to-e-file-94x-forms-in-taxbandits)
- [V] E-filing charges are deducted from prepaid credits (stated for the public API for individuals) — [developer.taxbandits.com](https://developer.taxbandits.com/)
- [V] Customers are anonymised: "a popular payroll software provider", "one of the largest CPEOs" — [API for payroll software](https://developer.taxbandits.com/api-for-payroll-software/)

### Inferences
- With an Online Signature PIN or 8453-EMP the employer signs its own return and TaxBandits acts as transmitter, so the software company need not be an IRS reporting agent to file a 941 this way; if the software company signs with an RA PIN it must itself hold Form 8655 authorisations.
- This service does not replace a deposit service: a 941 reports liability, the deposits must already have been made.

### Gaps
- Whether a balance-due payment (EFW) can ride with the return, and any deposit capability: not stated on the pages read.
- API-specific pricing, minimums and partner terms: not public.
- Eligibility of a non-US software company: not stated.

## 7. Greenshades — filing centre and managed tax service

### Takeaway
Greenshades offers a do-it-yourself filing centre and a managed tier where a named specialist executes state filings and payments after the customer approves. It states plainly that the customer stays responsible, and local coverage is partial.

### Cited Findings
- [V] Tax Filing Center covers 941s, W-2s, SUTA/SUI, 1042-S and local returns including PA Act 32, plus new hire reporting to all 50 states — [Greenshades managed payroll taxes](https://go.greenshades.com/platform/tax-and-compliance/managed-payroll-taxes)
- [V] Managed tier: a named specialist owns the compliance calendar, handles state filing and payment execution, rejections, amendments and quarter-end true-ups — [Greenshades managed payroll taxes](https://go.greenshades.com/platform/tax-and-compliance/managed-payroll-taxes)
- [V] "Your team still reviews and approves submissions before Greenshades files them." — [Greenshades managed payroll taxes](https://go.greenshades.com/platform/tax-and-compliance/managed-payroll-taxes)
- [V] "You are responsible for timely filing and payment of employment taxes." — [Greenshades managed payroll taxes](https://go.greenshades.com/platform/tax-and-compliance/managed-payroll-taxes)
- [V] For most county, city and municipal taxes Greenshades prepares the form and instructions and the customer files on the jurisdiction's website — [Greenshades managed payroll taxes](https://go.greenshades.com/platform/tax-and-compliance/managed-payroll-taxes)
- [V] Integrations: Microsoft Dynamics, Sage Intacct, Acumatica, Bullhorn, Avionté, Journyx, JazzHR; "an open API lets teams connect other systems" — [Greenshades managed payroll taxes](https://go.greenshades.com/platform/tax-and-compliance/managed-payroll-taxes)
- [S] Also transmits W-2, selected 1098/1099, 1042-S, W-2G and Canadian T4/T4A — [Greenshades compliance corner Q3 2026](https://go.greenshades.com/compliance-corner/the-greenshades-compliance-corner-q3-2026)
- [S] A G2 reviewer reports local taxes could not be processed through the tool — [G2](https://g2.com/products/greenshades/reviews)

### Inferences
- Greenshades' buyers are employers using an ERP payroll; partners such as Avionté and Bullhorn embed it, which is the closest analogue to this buyer's position.

### Gaps
- Who funds payments and how the employer's account is debited: not stated.
- Form 8655 / power of attorney handling, penalty guarantee, API documentation and prices: not on the pages read.
- Two older greenshades.com URLs returned 404; current pages live on go.greenshades.com.

## 8. MasterTax and ADP SmartCompliance Employment Tax

### Takeaway
MasterTax is the back-office tax software that payroll service providers and PEOs run to schedule deposits, create payments and produce returns across 11,000+ jurisdictions; ADP SmartCompliance is the fully managed service built around it for large employers. Both are sales-led with no public API reference or price.

### Cited Findings
- [V] MasterTax claims built-in compliance for more than 11,000 US jurisdictions and "over 25 years of leadership in payroll tax processing" — [mastertax.com](https://www.mastertax.com/)
- [V] Core functions: scheduling, payment creation, balance checks, return production; variance detection between deposits and liabilities — [mastertax.com](https://www.mastertax.com/)
- [V] It generates the authorisation forms (e-file, EFTPS, POA) for client signature, supports check and ACH debit payment methods, and files on paper or by e-file as each agency requires — [mastertax.com](https://www.mastertax.com/)
- [V] Includes a Tax Locator tool (local codes and rates) and a Wage Attachment tool (garnishment calculation) — [mastertax.com](https://www.mastertax.com/)
- [V] Target customers: small employers, large corporations, PEOs and "payroll service providers of all sizes"; an "Implementation, APIs & Integration" page exists — [mastertax.com](https://www.mastertax.com/)
- [V] The site carries ADP legal links and an adp.com support address — [mastertax.com](https://www.mastertax.com/)
- [S] ADP acquired MasterTax LLC and MasterTax Services; services range from in-house software to full outsourcing — [Accounting Today](https://www.accountingtoday.com/news/adp-acquires-mastertax)
- [V] ADP SmartCompliance Employment Tax: "manage payroll tax registrations, filings, remittances, payments & reporting"; amended returns; coordinated responses to agency notices; link to Registration Services for state and local tax IDs — [ADP SmartCompliance](https://www.adp.com/what-we-offer/products/adp-smartcompliance/employment-tax.aspx)
- [V] "Provider-agnostic integration with most payroll, HR or third-party systems"; named: Workday, Oracle, SAP, Infor — [ADP SmartCompliance](https://www.adp.com/what-we-offer/products/adp-smartcompliance/employment-tax.aspx)
- [S] Oracle Cloud HCM documents a third-party tax filing interface that feeds ADP — [Oracle docs](https://docs.oracle.com/en/cloud/saas/human-resources/24a/falzi/run-the-third-party-tax-filing-interface-for-the-us.html)

### Inferences
- MasterTax is software the payroll provider operates: the provider (or the employer) still holds the agent authorisations, the bank relationship and the liability; the tool automates the work.
- SmartCompliance is sold to employers, not as a white-label API to other payroll software vendors.

### Gaps
- MasterTax deployment model (installed versus hosted), file formats, price and minimum size: not stated.
- Whether ADP sells MasterTax to a non-US software company, or to a direct competitor: unconfirmed.
- SmartCompliance funding/impound mechanics and penalty guarantee: not on the page read.

## 9. Aatrix (Sovos), Sovos, Nelco, Tax1099, Avalara 1099, Asure

### Takeaway
Aatrix is the long-standing OEM route — embedded in 60+ payroll products, covering federal and state reports and payments — and has been a Sovos company since January 2024. The 1099-centred filers (Tax1099, Avalara/Track1099) list W-2 and 94x but showed no deposit or state-payment capability.

### Cited Findings
- [S] Aatrix partners with more than 60 US payroll products and describes "OEM Payroll Reporting & Payments through our partner integrations" — [aatrix.com partners](https://www.aatrix.com/partners)
- [S] Aatrix federal forms: W-2, 941, 940, 943, 944, 945, 1095-B/C, federal tax deposits; state: wage withholding reports, withholding payments, SUI reports, SUI payments — [Aatrix eFile Center pricing](https://efile.aatrix.com/pages/public/PackagePricing.aspx)
- [S] Aatrix prices seen: W-2 complete eFile $3.10 per employee on one page ($2.65 on an older page); quarterly/annual/new-hire/payment filings $9.99–$10.79 plus $0.55–$0.59 per employee; annual packages from $109 (1–5 employees) to $4,599 (11,000 employees); pages disagree by date — [Aatrix eFile Center pricing](https://efile.aatrix.com/pages/public/PackagePricing.aspx); [partner.aatrix.com](https://partner.aatrix.com/)
- [S] Sovos acquired Aatrix in January 2024; Aatrix served over 300,000 businesses, mainly through ERP and payroll provider integrations; Sovos is owned by Hg and TA Associates — [Sovos press release](https://sovos.com/press-releases/sovos-acquires-aatrix-software-expanding-solution-and-service-capabilities-for-smbs/)
- [S] Tax1099 states API availability and prices "starting at $2.99 per form" for 1099, W-2, 1095, 1042-S and 94x — [Tax1099](https://www.tax1099.com/avalara-alternative)
- [S] Avalara 1099 & W-9 (formerly Track1099) prices by number and type of forms with no minimum fee, and has an API — [Avalara pricing](https://www.avalara.com/us/en/products/1099/pricing.html); [Track1099 API docs](https://www.track1099.com/api_info/docs)

### Inferences
- Aatrix's classic integration is a desktop/forms component fed by the host payroll product, not a modern REST API; whether a REST option exists was not confirmed.

### Gaps
- Aatrix pages were not opened directly this round; every Aatrix fact is from search summaries of its pages.
- Nelco, Sovos' own payroll filing API, and Asure Payroll Tax Management: no usable facts surfaced. Unconfirmed.
- Partner terms, revenue share and foreign-entity eligibility for all vendors in this section: not public.

## 10. New-hire reporting and garnishment services

### Takeaway
New-hire reporting is available as an API line item from the form filers (TaxBandits, Greenshades, Aatrix). No standalone garnishment calculation-and-remittance API was found; garnishments come bundled inside engines (BSI, MasterTax) or managed services (ADP), or inside embedded payroll.

### Cited Findings
- [V] TaxBandits offers new hire reporting to state directories through its API — [API for payroll software](https://developer.taxbandits.com/api-for-payroll-software/)
- [V] Greenshades includes new hire reporting to all 50 states — [Greenshades managed payroll taxes](https://go.greenshades.com/platform/tax-and-compliance/managed-payroll-taxes)
- [V] BSI TaxFactory calculates wage attachments and ComplianceFactory remits to garnishment authorities — [bsi.com TaxFactory](https://bsi.com/products-and-services/bsi-taxfactory); [bsi.com ComplianceFactory](https://bsi.com/products-and-services/bsi-compliancefactory/)
- [V] MasterTax has a Wage Attachment tool — [mastertax.com](https://www.mastertax.com/)
- [S] ADP SmartCompliance Wage Garnishments is a managed service integrating with payroll systems and all 50 state child support agencies — [ADP](https://www.adp.com/what-we-offer/products/adp-smartcompliance/wage-garnishment.aspx)
- [S] Gusto Embedded added child support garnishment endpoints and remits to all child support agencies except South Carolina — [Gusto Embedded changelog](https://docs.gusto.com/embedded-payroll/changelog/added-support-for-child-support-garnishments-to-apis-flows)
- [S] Employers must report new and rehired employees within 20 days of hire — [ACF infographic](https://acf.gov/sites/default/files/documents/ocse/fy2025_employer_services_infographic.pdf)

### Gaps
- No API-first garnishment vendor confirmed; one search only.

## 11. IRS and banking rules that sit above every vendor

### Takeaway
Under IRS rules a payroll service provider or reporting agent never takes over the employer's liability, and becoming a reporting agent or e-file provider has personal eligibility conditions. Moving the tax money makes the software company an ACH third-party sender with its own audit duties.

### Cited Findings
- [V] A payroll service provider (PSP) files using the employer's EIN, and the forms it files are signed by the employer — [IRS third party payer arrangements](https://www.irs.gov/node/54906)
- [V] A reporting agent is a PSP authorised on Form 8655; it may sign and electronically file certain returns and make deposits; paper returns need a separate Form 2848 — [IRS third party payer arrangements](https://www.irs.gov/node/54906)
- [V] These third parties "do not assume any of the employer's employment tax liability" — [IRS third party payer arrangements](https://www.irs.gov/node/54906)
- [V] Rev. Proc. 2012-32 requires a reporting agent to send clients a quarterly written statement that the client remains responsible for timely filing and payment, and recommends the client enrol in EFTPS to monitor deposits — [IRS third party payer arrangements](https://www.irs.gov/node/54906)
- [S] A reporting agent cannot represent the employer in an examination or request penalty abatement — [Taxpayer Advocate](https://taxpayeradvocate.irs.gov/get-help/small-business/third-party-arrangements-form-employment-taxes)
- [S] A Section 3504 agent (Form 2678) assumes liability along with the employer; the employer remains liable too — [IRS section 3504 agents](https://irs.gov/zh-hans/government-entities/federal-state-local-governments/third-party-payer-arrangements-section-3504-agents)
- [S] E-file application: responsible officials must be a US citizen or an alien lawfully admitted for permanent residence, at least 18, and pass a suitability check (tax compliance, credit, criminal background, fingerprints); wording is from a May 2026 TIGTA report, not IRS.gov — [TIGTA report](https://www.oversight.gov/sites/default/files/documents/reports/2026-05/2026ier006fr.pdf); [IRS become an e-file provider](https://irs.gov/e-file-providers/become-an-authorized-e-file-provider)
- [S] EFTPS Batch Provider software lets a professional enrol and pay for many clients (older guidance: up to 1,000 clients or 5,000 payments per transmission); a Form 8655 must be held before enrolling a client; Bulk Providers use EDI files and can send one bulk ACH debit — [EFTPS](https://www.eftps.gov/eftps/ext/hds.html); [Treasury TFM ch. 2000](https://tfx.treasury.gov/tfm-classic/volume4/chapter-2000-electronic-federal-tax-collections)
- [S] A 2022 IRS notice flags a planned transition away from EFTPS that affects Batch Provider users; current status not confirmed — [IRS notice](https://www.irs.gov/pub/irs-npl/2022ntf-10.pdf)
- [V] Nacha: every third-party sender must run its own risk assessment and an annual ACH rules compliance audit, and cannot rely on its bank's; the originating bank must register its third-party senders with Nacha — [Nacha TPS reminder](https://www.nacha.org/news/reminder-each-third-party-sender-must-conduct-risk-assessment-march-31-2023); [Nacha TPS roles](https://www.nacha.org/rules/third-party-sender-roles-and-responsibilities)

### Inferences
- The citizenship/permanent-resident condition on e-file responsible officials is a direct obstacle for an India-incorporated company with no US officer; routing returns through a transmitter where the employer signs (PIN or 8453-EMP) avoids that application, at the cost of an employer step every quarter or a one-time PIN enrolment.
- The EFTPS source documents are 2003–2012; requirements should be re-read on eftps.gov before design.

### Gaps
- Whether a foreign entity can be listed as reporting agent on Form 8655 was not found on a primary source.
- State-level agent rules (third-party administrator authorisations, state power-of-attorney forms) were not researched.

## 12. What remains unsolved after combining an engine with a filing service

### Takeaway
An engine plus a form filer covers calculation and return transmission. Tax deposits, the bank relationship, employer state registrations, notices, amendments and penalty exposure stay with the software company or the employer unless a managed service (ADP SmartCompliance, Greenshades managed tier, BSI ComplianceFactory, MasterTax Services) is added.

### Cited Findings
- [V] Engines calculate only: Symmetry's products list has no deposit product, and its API guide describes the engine as calculating withholding — [symmetry.com pricing](https://www.symmetry.com/solutions/pricing); [payroll-tax-api-guide](https://www.symmetry.com/payroll-tax-api-guide)
- [V] TaxBandits' API pages describe creating, signing and transmitting returns; no deposit function appears — [94x overview](https://developer.taxbandits.com/docs/94xForms/Overview)
- [V] Greenshades: the customer "is responsible for timely filing and payment of employment taxes", and most local returns are filed by the customer — [Greenshades managed payroll taxes](https://go.greenshades.com/platform/tax-and-compliance/managed-payroll-taxes)
- [V] Registrations, agency notices and amended returns appear as named services only at ADP SmartCompliance — [ADP SmartCompliance](https://www.adp.com/what-we-offer/products/adp-smartcompliance/employment-tax.aspx)
- [S] CorpNet offers a payroll tax registration API for white-label resellers — [CorpNet](https://www.corpnet.com/corpnet-api/payroll/)
- [V] The employer keeps the liability under PSP and reporting-agent arrangements — [IRS third party payer arrangements](https://www.irs.gov/node/54906)
- [V] Originating ACH debits for many employers makes the originator a third-party sender with audit and risk-assessment duties — [Nacha TPS roles](https://www.nacha.org/rules/third-party-sender-roles-and-responsibilities)

### Inferences
- Open items by layer, after engine + form filer:

| Layer | Covered by engine + form filer? | Who can cover it |
|---|---|---|
| Withholding and employer tax amounts | Yes | Symmetry, Vertex, BSI |
| Address to jurisdiction, W-4 capture | Yes, as add-ons | Symmetry PRP/SPF, Vertex geocode/cleansing, BSI TaxLocator |
| Deposit schedule (semi-weekly, monthly, next-day; state frequencies) | No | MasterTax, ComplianceFactory, managed services, or own build |
| Federal and state tax deposits | No | Employer's own EFTPS/state logins; or MasterTax/Aatrix/managed services |
| Net pay and tax ACH origination, bank partner | No | A US bank or payment partner; third-party sender duties attach |
| Quarterly and annual returns, W-2 | Yes | TaxBandits, Greenshades, Aatrix, Tax1099 |
| Local returns | Partly | Greenshades prepares, customer files; MasterTax claims 11,000+ |
| Employer state registrations (withholding, SUI accounts) | No | ADP Registration Services, CorpNet API |
| Notices, amendments, penalty abatement | No | Managed services; reporting agents cannot represent the employer |
| Penalty and interest liability | No | Stays with the employer by IRS rule; contract decides what the vendor absorbs |

- The lighter the combination (hosted engine + pay-per-form filer), the more the employer does by hand; the heavier combination (engine + MasterTax-class software or a managed service) needs a US bank relationship, agent authorisations and, in practice, US-based staff.

### Gaps
- No vendor page read states whether it contracts with foreign-incorporated software companies or requires US hosting; this must be asked of each vendor.
- No vendor publishes a penalty-guarantee term on the pages read.
- State money-transmitter licensing for holding client tax funds was not researched.
