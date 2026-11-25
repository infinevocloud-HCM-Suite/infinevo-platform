# Embedded payroll API providers for the United States (as of 24 Nov 2026)

Source marks used on every finding:

- **[V]** read on the vendor's own page (docs, site, press release) in this round
- **[S]** read on a secondary page (press, analyst, comparison article)
- **[SR]** seen only in a search-result summary; the page itself was not opened — treat as a lead, not a fact
- **[NC]** not confirmed anywhere

Pages that blocked fetching: `embedded.gusto.com` (HTTP 403), `flows.gusto.com/terms` (HTTP 403), `checkhq.com/legal/payroll-terms` (404, guessed URL). Next-best sources were used and are marked.

## Cross-vendor summary

### Takeaway
No vendor publishes a wholesale price or a rule on foreign-incorporated partners; every one of them gates production behind a sales and review conversation. The vendors differ most on three things a founder can check from public pages: whether a sandbox is self-serve (Gusto yes; Everee partly; Check, Salsa, Zeal, Paychex no), whether a Java client exists (only Gusto lists one), and a stated minimum partner size (Salsa: 25,000+ workers paid by the platform's customers).

### Cited Findings

| Vendor | Product type | Sandbox without a contract | Java SDK | Prebuilt UI | Auth | Public price | Stated partner floor | Status 2026 |
|---|---|---|---|---|---|---|---|---|
| Check | Embedded payroll, API-first | Not stated; features need account-team setup [V] | None mentioned [V] | Components + Check Onboard [V] | Bearer API key [V] | None [V] | None stated [V] | Independent; last confirmed round 2022 Series C [SR] |
| Gusto Embedded | Embedded payroll on Gusto's engine | Yes, self-serve Developer Portal gives demo credentials [V] | Yes, `gusto-java-client` [V] | Flows + React SDK [V] | OAuth2, 2-hour tokens [V] | None [S] | "not able to support all partner use cases" [V] | Part of Gusto; active |
| Salsa | Embedded payroll, US + Canada | Tokens "provisioned by Salsa" [V] | None listed; OpenAPI spec download [V] | Salsa Express, Salsa.js, hosted flows [V] | OAuth2 bearer [V] | None [V] | 25,000+ workers; no pre-launch [V] | $20M Series A Apr 2025 [SR] |
| Zeal | Embedded payroll, staffing/gig tilt | "contact sales" for sandbox or production [V] | None mentioned [V] | White-label dashboards and onboarding [V] | Bearer API key + partnerID [V] | None on site [V] | None stated [V] | Independent; $13M Series A 2021 [SR] |
| ADP Embedded Payroll | Embedded payroll on ADP's SMB engine | Not stated [V] | "Software Development Kit" claimed, language not stated [V] | "pre-built UIs" [V] | Not stated | "Revenue share", no figures [V] | None stated [V] | Active, marketed 2026 |
| ADP API Central | Data-access API for an existing ADP client — **not** embedded payroll | n/a | n/a | n/a | n/a | USD 2.00–2.50 per employee/month [SR] | Must be an ADP Workforce Now client [SR] | Active |
| Paychex developer APIs | Integration into a client's Paychex Flex account — **not** white-label | Sandbox given after partnership approval [V] | Not stated | None | OAuth 2.0 [V] | None [V] | Questionnaire + team evaluation [V] | Active |
| Rollfi (Priority) | Embedded payroll + benefits | Not stated [V] | Not stated [V] | White label, embedded, API [V] | Not stated [V]; "Basic auth" [SR] | None [V] | None stated [V] | Acquired by Priority (PRTH) Jan 2025 [V/S] |
| Everee | "Payroll as a service", staffing/gig tilt | Simulators before access; sandbox on request [V/SR] | Not covered in docs index [V] | Embed via iframe/WebView [V] | Partner API key, self-claimed [V] | "pricing calculator", no figure on page [V] | None stated [V] | Independent, active |
| Nmbr | Embedded payroll — **Canada**, not US | — | — | — | — | — | — | Out of scope for US [SR] |

- A 2026 directory groups the API-first providers as Check, Gusto Embedded, Salsa, Nmbr, ADP Embedded and Zeal. [SR] — [Open Banking Tracker](https://openbankingtracker.com/embedded-finance/category/payroll)
- One secondary source describes the typical retail structure as a base fee of $35–70 per customer per month plus $6–10 per employee per month, with the platform keeping roughly two-thirds. [SR] — [search summary of Zeal guide / Rollfi blog](https://www.rollfi.xyz/blog/what-embedded-payroll-does-to-arpu-clv-and-payback); [Zeal guide](https://www.zeal.com/guides/why-more-software-platforms-are-offering-payroll)
- Check's own blog gives "standard pricing of $6 to $10 per employee per month" as the retail price platforms charge; it gives no wholesale figure. [V] — [Check blog](https://www.checkhq.com/resources/blog/how-to-make-a-real-world-business-case-for-embedded-payroll)
- A comparison article dated 17 Aug 2026 says Check, Gusto Embedded, Salsa and Rippling Embedded pricing is not public and is negotiated; its "$3 wholesale / $8 retail" figures are illustrative, not vendor quotes. [S] — [FintechSpecs](https://fintechspecs.com/blog/best-embedded-payroll-api-vertical-saas/)
- The same article says liability and tax-penalty commitments sit in each partner agreement and that a vendor's direct-product guarantees do not automatically carry into the embedded contract. [S] — [FintechSpecs](https://fintechspecs.com/blog/best-embedded-payroll-api-vertical-saas/)

### Inferences
- "Paychex Flex Embedded", "Rippling Embedded" and "Fingercheck" appear as embedded options only in the FintechSpecs article; Paychex's own developer pages describe client-account integration, not white-label payroll, so the article's label is not supported by the vendor's pages read here.
- Because every vendor's wholesale price, minimums and contract length are absent from public pages, those items can only be obtained by a sales conversation; nothing found suggests a published rate card exists anywhere.
- Symmetry (tax engine), Payroll Integrations (401(k)/benefits connector) and isolved/white-label resellers were named in the brief but no evidence was gathered on them this round; they are not embedded payroll-of-record APIs in the sense of the vendors above (inference from product category, not from pages read).

### Gaps
- **Foreign-incorporated partners:** no vendor page read states whether a non-US software company can sign as a partner. An extended search found no published rule for Check or Gusto. [NC]
- **Wholesale price, minimums, implementation fee, contract length:** not public for any vendor.
- **SOC 2 or security questionnaire required of the partner; data-residency or US-hosting requirement:** not stated on any page read. Gusto says only that "security ... reviews" precede production.
- Branch, PayPro, Symmetry Payments, isolved, Payroll Integrations: not researched (tool budget).

## Check (checkhq.com)

### Takeaway
Check is the most API-complete of the set (dozens of resources, components, CLI, MCP server) and names the most platform partners, but its docs show that production features are switched on by a Partner Engineer rather than self-serve, and no SDK or price is published.

### Cited Findings

**Scope of service**
- Site lists: money movement, tax calculation, tax filing, tax notices, voids, deductions, garnishments, workers' comp, 401(k), health benefits, fraud protection, onboarding. [V] — [checkhq.com](https://www.checkhq.com/)
- Check performs KYB on each company and files Form 8655 with the IRS as part of company implementation. [V] — [Implementation Status](https://docs.checkhq.com/docs/implementation-status.md)
- The partner sets a company up through Console when status is `needs_attention`; the employer completes Onboard tasks (bank account, tax setup). [V] — [Implementation Status](https://docs.checkhq.com/docs/implementation-status.md)
- Footer names "Check Payments LLC (NMLS #2103307)". [V] — [checkhq.com](https://www.checkhq.com/)
- ADP's comparison (vendor-authored, dated 29 May 2026) says partners on Check "carry more compliance and operational responsibility" and that Check's accounting integration is QuickBooks Online only. [S, competitor claim] — [ADP article](https://www.adp.com/resources/articles-and-insights/articles/c/choosing-the-right-embedded-payroll-partner.md)

**Integration model**
- Objects: Companies, Workplaces, Employees, Contractors, Bank Accounts, Payrolls, Payroll Items, Contractor Payments, External Payrolls, Corrections, Pay Schedules, Benefits, Post-Tax Deductions, Earning Rates, Earning Codes, Time Off Policies/Balances/Entries, Documents, Taxes, Agencies, Tax Elections, Filings, Webhook Configs, Usage, Integrations. [V] — [docs index](https://docs.checkhq.com/)
- Auth is `Authorization: Bearer <key>`, with separate sandbox and live keys; IP restriction is set up by the Check account team. [V] — [Authentication](https://docs.checkhq.com/reference/authentication.md)
- Idempotency header `X-Idempotency-Key`; keys expire after 24 hours; external-payroll keys do not expire. [V] — [Idempotent requests](https://docs.checkhq.com/docs/idempotent-requests.md)
- Base URLs `sandbox.checkhq.com` and `api.checkhq.com`. [V] — [Sandbox and Production](https://docs.checkhq.com/docs/understanding-sandbox-and-production-environments.md)
- Prebuilt UI: Check Onboard ("Automated onboarding for companies, employees, and contractors") and Components including Run Payroll, Company Team Setup and Employee Withholdings Setup. [V] — [docs index](https://docs.checkhq.com/)
- Webhooks: production retries up to 18 times over a 7-day window; sandbox retries once. [V] — [Sandbox and Production](https://docs.checkhq.com/docs/understanding-sandbox-and-production-environments.md)
- Rate limit: sandbox 10 requests/second per partner; production scales with payees paid the prior month. [V] — [Sandbox and Production](https://docs.checkhq.com/docs/understanding-sandbox-and-production-environments.md)
- No SDK or language is mentioned in the docs pages read; a CLI and an MCP server are listed. [V] — [docs index](https://docs.checkhq.com/)

**Pay-run inputs**
- Earnings are sent as payroll items; bulk create/update of up to 500 payroll items; earning rates and custom earning codes; piece rate, reimbursements and gross-ups have guides. [V] — [docs index](https://docs.checkhq.com/)
- Pay frequencies named in the guide: weekly, biweekly, semi-monthly, monthly, plus quarterly/annual bonus schedules; a pay schedule cannot be used for off-cycle payrolls. [V] — [Pay Schedules](https://docs.checkhq.com/docs/pay-frequency.md)
- Off-cycle payrolls have their own guide and options object. [V] — [docs index](https://docs.checkhq.com/)
- W-4 and state forms: the Employee Withholdings Setup component "allows an employee to complete and sign state and federal withholding forms (i.e. W-4)". [V] — [docs index](https://docs.checkhq.com/)
- Coverage: "Sandbox supports all US states. Production supports about 50" (as summarised from the page; go-live checklist says to confirm each state is supported in production). [V] — [Sandbox and Production](https://docs.checkhq.com/docs/understanding-sandbox-and-production-environments.md)

**Commercial**
- No pricing on the site. [V] — [checkhq.com](https://www.checkhq.com/)
- A `Usage` resource exists in the API. [V] — [docs index](https://docs.checkhq.com/)

**Eligibility and go-live**
- "All partner integrations require a contract amendment and Check-side configuration before testing in sandbox or deploying to production." [V] — [Sandbox and Production](https://docs.checkhq.com/docs/understanding-sandbox-and-production-environments.md)
- Enabling processing periods, payment methods and tax jurisdictions requires the Partner Engineer or Growth Manager; "No self-serve API exists for this." [V] — [Sandbox and Production](https://docs.checkhq.com/docs/understanding-sandbox-and-production-environments.md)
- Per employer in production: real IRS EIN check, bank verification about 3 banking days, KYB up to 2 business days; `in_review` to `completed` within two business days. [V] — [Sandbox and Production](https://docs.checkhq.com/docs/understanding-sandbox-and-production-environments.md); [Implementation Status](https://docs.checkhq.com/docs/implementation-status.md)
- KYB outcomes include `four_day_approved` and `two_day_approved` (processing speed is a risk decision per employer). [V] — [Implementation Status](https://docs.checkhq.com/docs/implementation-status.md)
- A comparison article estimates two to four months for a production-ready custom integration (unsourced estimate). [S] — [FintechSpecs](https://fintechspecs.com/blog/best-embedded-payroll-api-vertical-saas/)

**Company status**
- Site stats: "1 Million +" employees paid, "50,000 +" businesses, "$15 Billion +" processed annually. [V] — [checkhq.com](https://www.checkhq.com/)
- Named partners (case studies and logo wall): Hourly, Eddy, Miter, 7shifts, Trayd, Wave, Playground, Housecall Pro, Homebase, Warp, Dripos, Mercury, Zoho, Moxie, Lasso, BuddyPunch, Tekion, Arcoro. [V] — [checkhq.com](https://www.checkhq.com/)
- $75M Series C led by Stripe in Feb 2022 at a reported $725M valuation; total raised about $119M; no 2025–2026 round found; aggregators conflict. [SR] — [Fortune 2021 round](https://fortune.com/crypto/2021/01/27/fintech-startup-check-raises-35-million-to-improve-payroll-automation-led-by-stripe/); [Caplight](https://www.caplight.com/company/check-software-development-applications)

### Inferences
- The "contract amendment" sentence may refer to ecosystem/third-party integrations rather than the base sandbox key; either way the docs do not describe any self-serve route to a key.
- "Production supports about 50" is the fetch tool's paraphrase and reads oddly; the reliable point is that state coverage is enabled per partner and must be confirmed.

### Gaps
- Whether a sandbox key is issued before a signed contract. [NC]
- Whether Check signs non-US partners; partner SOC 2 requirement; data residency. [NC]
- Penalty liability terms and who is the reporting agent in the employer terms (terms page not found at the URL tried).
- New-hire reporting and state tax registration help: not on pages read.
- An aggregator claim about Stripe launching competing payroll and partner terminations had no primary source and is not relied on. [NC]

## Gusto Embedded (embedded.gusto.com, docs.gusto.com/embedded-payroll)

### Takeaway
Gusto Embedded is the only vendor whose docs show a self-serve demo environment and an official Java client, but it says outright that it cannot support every partner use case and requires commercial, security and implementation reviews before production. The marketing site and the employer terms could not be fetched, so liability and support terms are unconfirmed.

### Cited Findings

**Scope of service**
- Platform overview: "Calculate and file taxes automatically"; "Execute payroll calculations and disbursements"; tax configuration, bank account and payment setup. [V] — [Platform overview](https://docs.gusto.com/embedded-payroll/docs/platform-overview)
- Garnishment endpoints exist, including a child-support garnishment guide with agency lookup. [V] — [docs index](https://docs.gusto.com/embedded-payroll/llms.txt)
- Contractor management, contractor payments and 1099 generation are covered. [V] — [docs index](https://docs.gusto.com/embedded-payroll/llms.txt)
- Benefits partnerships in docs: Guideline for 401(k) ("More 401(k) integration options to come soon!"), SimplyInsured for health, NEXT for workers' comp. [V/SR] — [Platform overview](https://docs.gusto.com/embedded-payroll/docs/platform-overview); [changelog](https://docs.gusto.com/embedded-payroll/v2026-02-01/changelog/added-product-partnership-guides)
- Partner is told to "Prepare support procedures"; Gusto's own support tier is not described. [V] — [Platform overview](https://docs.gusto.com/embedded-payroll/docs/platform-overview)

**Integration model**
- Three build options: full API; Flows (prebuilt white-label UI for company onboarding, employee/contractor onboarding, payroll); React SDK ("React component libraries with built-in business logic"); they can be mixed. [V] — [Platform overview](https://docs.gusto.com/embedded-payroll/docs/platform-overview)
- OAuth2: `POST /oauth/token`; system access tokens (`grant_type=system_access`) for app-level actions; one company-level token per partner-managed company; both expire after two hours; refresh tokens rotate. [V] — [Authentication](https://docs.gusto.com/embedded-payroll/docs/authentication-and-authorization.md)
- Official client libraries: TypeScript, **Java** (`github.com/Gusto/gusto-java-client`), Python, C#/.NET, Ruby; libraries target API version v2025-06-15; some may be beta. [V] — [API clients](https://docs.gusto.com/embedded-payroll/docs/api-clients)
- Libraries are auto-generated from the OpenAPI spec with Speakeasy. [SR] — [Gusto Embedded blog](https://embedded.gusto.com/blog/api-client-libraries/)
- Webhooks, webhook events and partner-notification testing in demo are documented. [V] — [docs index](https://docs.gusto.com/embedded-payroll/llms.txt)
- Demo base URL `https://api.gusto-demo.com`; a Postman collection is published. [V] — [Getting started](https://docs.gusto.com/embedded-payroll/docs/getting-started.md)
- API versions are date-named (for example `v2024-04-01`); Gusto "may require developers to use the most current version". [V] — [Authentication](https://docs.gusto.com/embedded-payroll/docs/authentication-and-authorization.md); [API Policy](https://docs.gusto.com/embedded-payroll/page/api-policy.md)

**Pay-run inputs**
- Pay schedules: weekly, biweekly, twice-monthly, monthly. [V] — [Pay Schedules](https://docs.gusto.com/embedded-payroll/docs/pay-schedule-info.md)
- Standard processing is four business days before check date; expedited is 2-day or next-day; daily cutoff 4 pm PST. [V] — [Pay Schedules](https://docs.gusto.com/embedded-payroll/docs/pay-schedule-info.md)
- Off-cycle: "Creates a new, unprocessed, off-cycle payroll." [V] — [docs index](https://docs.gusto.com/embedded-payroll/llms.txt)
- Time off requests can be submitted and applied automatically to payroll; no dedicated time-entry guide appeared in the index read. [V] — [docs index](https://docs.gusto.com/embedded-payroll/llms.txt)
- Employee federal tax (W-4) data and employee forms have endpoints; state tax requirements are retrievable per company and per state. [V] — [docs index](https://docs.gusto.com/embedded-payroll/llms.txt)

**Commercial**
- An invoicing endpoint returns "active companies used to calculate Gusto Embedded Payroll invoices" — billing to the partner is driven by active partner-managed companies. [V] — [docs index](https://docs.gusto.com/embedded-payroll/llms.txt)
- Partner pricing is not public; Gusto's direct product is $40/month plus $6 per person, which is separate. [S] — [FintechSpecs](https://fintechspecs.com/blog/best-embedded-payroll-api-vertical-saas/)

**Eligibility and go-live**
- Sign-up at the Developer Portal creates an Organization and Application and "will create the credentials necessary to access our Demo environment". [V] — [Getting started](https://docs.gusto.com/embedded-payroll/docs/getting-started.md)
- "Gusto Embedded is not able to support all partner use cases at this time." [V] — [Introduction](https://docs.gusto.com/embedded-payroll/docs/introduction)
- "There are commercial, security, and implementation conversations/ reviews required before partners can move to production"; developers should seek pre-approval "before getting too deep into your build". [V] — [Introduction](https://docs.gusto.com/embedded-payroll/docs/introduction)
- API Policy requires security controls "in no case less than reasonable care"; it names no SOC 2, no entity-location rule and no data-location rule. [V] — [API Policy](https://docs.gusto.com/embedded-payroll/page/api-policy.md)
- The Embedded Payroll Service Agreement lets Gusto require verification of the employer's identity, including ownership documentation. [SR — page returned 403] — [flows.gusto.com/terms](https://flows.gusto.com/terms)

**Company status**
- Named partners: Chase Payment Solutions ("two of the top five U.S. banks"), Xero (US payroll powered by Gusto), Lattice, Vagaro, Heard, Collective. [SR] — [Gusto news](https://gusto.com/company-news/gusto-embedded-one-year-in-fueling-smb-tech-success-at-scale-with-critical-compliance); [Xero announcement](https://gusto.com/company-news/xero-to-launch-all-in-one-simple-payroll-for-us-small-business-customers-powered-by-gusto)
- ADP's comparison says Gusto's APIs "follow the workflows of Gusto's own app for small businesses". [S, competitor claim] — [ADP article](https://www.adp.com/resources/articles-and-insights/articles/c/choosing-the-right-embedded-payroll-partner.md)

### Inferences
- Gusto is the one vendor where a Java/Spring team could start building against a live demo API before any commercial conversation.
- The "not able to support all partner use cases" line, combined with the pre-approval advice, means a build can be finished in demo and still be refused production.

### Gaps
- Reporting-agent role, penalty liability, funds holding, support tiers: on `embedded.gusto.com` and `flows.gusto.com/terms`, both blocked (403). [NC]
- Whether non-US partners are accepted. [NC]
- State coverage statement (all 50?) not on docs pages read. [NC]
- New-hire reporting and state registration help: not found in the docs index.

## Salsa (salsa.dev)

### Takeaway
Salsa has the clearest documented path for pushing external time and pay data (the Paystream API) and covers the US and Canada in one API, but it publicly declines platforms whose customers pay fewer than 25,000 workers in total, including pre-launch platforms.

### Cited Findings

**Scope of service**
- FAQ: Salsa handles "tax calculations, filings, payments, compliance, dashboards, and employer/employee workflows". [V] — [salsa.dev/product](https://www.salsa.dev/product)
- Worker types: W-2 employee and 1099-NEC contractor. [V] — [docs index](https://docs.salsa.dev/)
- Optional add-ons: "AI agents, benefits, daily pay and auto payroll". [V] — [salsa.dev](https://www.salsa.dev/)
- Support: hands-on onboarding, Slack access to product and engineering; post-launch "Slack or ticket-based engineering help, or a dedicated account team, depending on the package". [V] — [salsa.dev/product](https://www.salsa.dev/product)

**Integration model**
- Objects: Employer, EmployerBankAccount, Signatory, WorkLocation, WorkerPayGroup, Worker, WorkerContract, WorkerTermination, PayrollRun, WorkerPayment, pay/deduction/benefit/time-off types, Document, WebhookEndpoint, Usage. [V] — [docs index](https://docs.salsa.dev/)
- REST and GraphQL APIs. [V] — [salsa.dev/product](https://www.salsa.dev/product)
- Auth: "OAuth 2.0 Bearer Token in the `Authorization` header"; API Token (static, provisioned by Salsa, one per environment), OAuth client credentials (1-hour tokens), short-lived User Tokens with role tiers. [V] — [Authorization](https://docs.salsa.dev/docs/authorization.md)
- Environments: `api.sandbox.salsa.dev` and `api.salsa.dev`; mock-onboarding endpoints exist for employers and workers. [V] — [Authorization](https://docs.salsa.dev/docs/authorization.md); [docs index](https://docs.salsa.dev/)
- Prebuilt UI: Salsa Express, Salsa.js browser library, hosted employer and worker onboarding, employer dashboard/run-payroll/taxes-setup screens, theming to partner brand; "Always your branding, no co-branding". [V] — [docs index](https://docs.salsa.dev/); [salsa.dev/product](https://www.salsa.dev/product)
- Webhooks: lifecycle events (`Employer.created`, `PayrollRun.statusChanged`, `Worker.onboardingStatus`) and notification events. [V] — [docs index](https://docs.salsa.dev/)
- No server SDK listed; OpenAPI spec downloads are offered; an MCP server exists. [V] — [docs index](https://docs.salsa.dev/); [salsa.dev](https://www.salsa.dev/)
- No idempotency header documented; idempotent creation is done through `externalId`. [V] — [Paystream](https://docs.salsa.dev/docs/paystream-api.md)

**Pay-run inputs**
- Paystream: "Stream the pay data you already collect into Salsa, and let payroll runs pick it up." JSON endpoints `/v1/paystream/employers`, `/workers`, `/payroll-elements`. [V] — [Paystream](https://docs.salsa.dev/docs/paystream-api.md)
- Entry types: `pay`, `timeWorked`, `benefits`, `deductions`, `timeOff` (`taken`, `accrued`, `balanceAdjusted`); "Send hours day by day or shift by shift." [V] — [Paystream](https://docs.salsa.dev/docs/paystream-api.md)
- Period replacement: the caller declares a period and workers and Salsa makes its records match; "A period replacement means 'this is everything I have for these dates.'" [V] — [Paystream](https://docs.salsa.dev/docs/paystream-api.md)
- A draft payroll run picks up pending entries when calculated; `PayrollRun.awaitingInput` webhook asks the partner for data for an exact scope. [V] — [Paystream](https://docs.salsa.dev/docs/paystream-api.md)
- Changes after confirmation: `REJECT` (default), `ACCEPT`, or `COMPUTE_DIFFERENCE`. [V] — [Paystream](https://docs.salsa.dev/docs/paystream-api.md)
- Partial failures return `201` with per-worker errors: "Always read `errors`, not only the status code." [V] — [Paystream](https://docs.salsa.dev/docs/paystream-api.md)
- Overtime, premium pay and work-week guides exist; off-cycle runs exist (mentioned as able to exclude element types). [V] — [docs index](https://docs.salsa.dev/); [Paystream](https://docs.salsa.dev/docs/paystream-api.md)

**Commercial**
- No pricing page. [V] — [salsa.dev](https://www.salsa.dev/)
- A "Usage billing" guide (calculating employer usage) exists. [V] — [docs index](https://docs.salsa.dev/)
- Described as negotiated per employee per month. [S] — [FintechSpecs](https://fintechspecs.com/blog/best-embedded-payroll-api-vertical-saas/)

**Eligibility and go-live**
- "Salsa works with software platforms whose customers pay 25,000+ workers in total."; it does not take on platforms below that size, "including pre-launch". [V] — [salsa.dev](https://www.salsa.dev/)
- "Launch in weeks, not quarters"; four steps Partner, Integrate, Launch, Grow. [V] — [salsa.dev](https://www.salsa.dev/); [salsa.dev/product](https://www.salsa.dev/product)
- SOC 2 Type 2 badge (Salsa's own). [V] — [salsa.dev](https://www.salsa.dev/)

**Company status**
- Named partners: Franpos, HHAeXchange, Alpaca Health, Innergy, Aaniie, FarmRaise, jebra, Trez, Everyset, Maple, GlossGenius, DaySmart, Honeybook, EasyTeam, Jane, Mangomint, Band of Hands. [V] — [salsa.dev](https://www.salsa.dev/)
- $20M Series A, April 2025, led by Altos Ventures; total about $30M. [SR] — [Axios](https://www.axios.com/pro/fintech-deals/2025/04/22/exclusive-embedded-payroll-startup-salsa-20m); [PYMNTS](https://www.pymnts.com/payroll/2025/salsa-raises-20m-to-expand-embedded-payroll-services-across-us-and-canada/)

### Inferences
- The 25,000-worker floor is the only published eligibility rule found for any vendor and would exclude a platform with no US customers yet.
- Paystream's period-replacement model fits an HRMS that already owns attendance and leave: it sends the whole period and Salsa reconciles.

### Gaps
- Pay frequencies, state list, W-4 flow, new-hire reporting, garnishments, reporting-agent role and liability: not on pages read (the Payroll Fundamentals page returned only its intro).
- Foreign partner acceptance; whether a sandbox token is issued before contract. [NC]

## Zeal (zeal.com)

### Takeaway
Zeal now positions itself around staffing platforms, labour marketplaces and EOR/PEO, with payroll, billing and wallets, and publicly states that partners are not liable for missed payments or wrong tax calculations. Sandbox access requires contacting sales.

### Cited Findings

**Scope of service**
- "Zeal uses this information to manage compliance, run payroll, and remit and file taxes on behalf of the employer." [V] — [Building a Payroll Product](https://docs.zeal.com/docs/building-a-payroll-product.md)
- W-2 and 1099 year-end forms are provided through dashboards or APIs. [V] — [FAQs](https://docs.zeal.com/docs/faqs.md)
- Garnishments have a guide, object, endpoints and a webhook event. [V] — [docs index](https://docs.zeal.com/llms.txt)
- Remote Form I-9 with E-Verify, wallets, paycards, invoicing/receivables are marketed. [V] — [zeal.com](https://www.zeal.com/)
- Zeal maintains an in-house tax engine. [V] — [FAQs](https://docs.zeal.com/docs/faqs.md)
- Native earned wage access is noted as a Zeal differentiator. [SR] — [Open Banking Tracker](https://openbankingtracker.com/embedded-finance/category/payroll)

**Integration model**
- Objects: Companies, Employees, Contractors, Work Locations, Employee Checks, Shifts, Contractor Payments, Reporting Periods, Garnishments, Reports, Funding Source, Reserve balance. [V] — [docs.zeal.com](https://docs.zeal.com/); [docs index](https://docs.zeal.com/llms.txt)
- Auth `Authorization: Bearer {apiKey}`; test and production keys fully isolated; `partnerID` passed in body or query; keys are found in the Partner Dashboard. [V] — [Authentication and Keys](https://docs.zeal.com/reference/introduction/authentication-and-keys.md)
- Idempotency Key header is documented. [V] — [docs index](https://docs.zeal.com/llms.txt)
- White-label Employer, Employee and Contractor dashboards and onboarding components, shown on the partner's domain and brand. [V] — [Building a Payroll Product](https://docs.zeal.com/docs/building-a-payroll-product.md); [FAQs](https://docs.zeal.com/docs/faqs.md)
- Webhooks: company onboarding, employee onboarding, employee check, garnishment, job queue. [V] — [docs index](https://docs.zeal.com/llms.txt)
- No SDK documented. [V] — [docs index](https://docs.zeal.com/llms.txt)

**Pay-run inputs**
- Time data is sent as Shifts on an Employee Check scoped to a Reporting Period; the docs' example maps a Clockify time entry to a Shift. [V] — [Time & Attendance Integration](https://docs.zeal.com/docs/time-attendance-integration.md)
- Employees are matched by `external_id`. [V] — [Time & Attendance Integration](https://docs.zeal.com/docs/time-attendance-integration.md)
- "Zeal processes payroll in all 50 states and all 13,000+ localities"; no payroll outside the US. [V] — [FAQs](https://docs.zeal.com/docs/faqs.md)
- Default processing starts at 2 PM PT two bank days before pay date. [V] — [FAQs](https://docs.zeal.com/docs/faqs.md)
- W-4: a "Create W4 Link" endpoint and per-state/locality tax requirement endpoints. [V] — [docs index](https://docs.zeal.com/llms.txt)

**Commercial**
- No pricing on the site. [V] — [zeal.com](https://www.zeal.com/)
- A review says partners can embed at no upfront cost and Zeal shares monetised transaction revenue; another article says Zeal lists per-run and per-employee fees publicly but gives no figures. Sources conflict with the vendor site, which shows none. [SR/S] — [hr.software review](https://www.hr.software/reviews/zeal); [FintechSpecs](https://fintechspecs.com/blog/best-embedded-payroll-api-vertical-saas/)

**Eligibility and go-live**
- "If you haven't received sandbox or production access please contact sales". [V] — [docs.zeal.com](https://docs.zeal.com/)
- White-label partners "go live in 1-3 weeks"; custom API builds vary. [V] — [FAQs](https://docs.zeal.com/docs/faqs.md)
- Employers sign authorization documents that include Reporting Agent Terms. [V] — [docs index](https://docs.zeal.com/llms.txt)

**Liability**
- Partners are not liable for missed payments or improper tax calculations; employers must keep tax documents current; Zeal accepts liability for its own errors. [V] — [FAQs](https://docs.zeal.com/docs/faqs.md)
- "Zeal is a financial technology company, not an FDIC insured depository institution"; banking services by Bangor Savings Bank. [V] — [zeal.com](https://www.zeal.com/)

**Company status**
- Named customers: Wonolo, Lawtrades, WorkGenius, Eats2Seats, NurseIO, CinemaHive, Backlit, Band of Hands, Qwick, Traba, Jyve, Veryable, Humla Health, Acrobat Staff, ACE FMS. [V] — [zeal.com](https://www.zeal.com/)
- $13M Series A led by Spark Capital, 2021; a $15M raise dated Dec 2023 on CB Insights not confirmed elsewhere; Sacra valuation about $65M. [SR] — [TechCrunch 2021](https://techcrunch.com/2021/08/27/zeal-banks-13m-to-offer-employers-a-build-your-own-payroll-product-infrastructure); [CB Insights](https://www.cbinsights.com/compare/salsa-vs-zeal-5)

### Inferences
- The customer list is almost entirely staffing and gig platforms; a general HR/payroll SaaS for SMB employers is not the profile Zeal's site addresses.
- Zeal's per-check, shift-level model suits high-frequency or daily pay more than a monthly salaried run.

### Gaps
- Supported pay frequencies and off-cycle rules (reference pages exist but were not opened).
- Pricing; foreign partner acceptance; partner security requirements; new-hire reporting. [NC]

## ADP Embedded Payroll and ADP API Central

### Takeaway
These are two different things: ADP Embedded Payroll is a partner programme with APIs, prebuilt UI and revenue share, entered through a sales form; ADP API Central is a paid add-on that lets an existing ADP client reach its own ADP data and is not a way to resell payroll.

### Cited Findings

**ADP Embedded Payroll**
- Tagline "Your platform. Our payroll."; "combines best-in-class payroll with decades of expertise to help you sell, support and scale". [V] — [ADP Embedded Payroll](https://www.adp.com/what-we-offer/products/adp-embedded-payroll.md)
- "ADP automates payroll calculations and filings across federal, state and local levels." [V] — [ADP article](https://www.adp.com/resources/articles-and-insights/articles/c/choosing-the-right-embedded-payroll-partner.md)
- Integration: "flexible APIs", "intuitive, pre-built UIs", "Software Development Kit, including hands-on guidance", "Change-notification webhooks aligned to system of record", "Robust APIs for onboarding, payroll processing and reporting". [V] — [ADP Embedded Payroll](https://www.adp.com/what-we-offer/products/adp-embedded-payroll.md); [ADP article](https://www.adp.com/resources/articles-and-insights/articles/c/choosing-the-right-embedded-payroll-partner.md)
- Commercial: "Revenue share, made for integrated partners"; no figures. [V] — [ADP Embedded Payroll](https://www.adp.com/what-we-offer/products/adp-embedded-payroll.md)
- Support: expert-led implementation, go-to-market help from ADP sales, access to ADP payroll specialists for "day-to-day needs". [V] — [ADP Embedded Payroll](https://www.adp.com/what-we-offer/products/adp-embedded-payroll.md)
- Named partners: FieldClock, Clover (a Fiserv solution). [V] — [ADP Embedded Payroll](https://www.adp.com/what-we-offer/products/adp-embedded-payroll.md)
- Entry is "Get Started" and a form; no sandbox, eligibility or timeline is stated. [V] — [ADP Embedded Payroll](https://www.adp.com/what-we-offer/products/adp-embedded-payroll.md)
- Scale claims: "over 900,000 small businesses"; "99.999% historical uptime" (internal ADP data). [V] — [ADP Embedded Payroll](https://www.adp.com/what-we-offer/products/adp-embedded-payroll.md)

**ADP API Central**
- Priced per employee per month on the ADP invoice: one listing shows USD 2.50 (first 999 employees), 1.75 (1,000–2,999), 1.50 (3,000–4,999); another shows a flat USD 2.00. Listings conflict. [SR] — [ADP Marketplace](https://apps.adp.com/en-US/apps/410612/adp-api-central-for-adp-workforce-now/editions); [configure page](https://apps.adp.com/en/apps/410612/adp-api-central-for-adp-workforce-now/configure)
- Available to clients using ADP Workforce Now / Next Generation; other editions for Vantage, Enterprise HR, Lyric. [SR] — [ADP Marketplace](https://apps.adp.com/en-US/apps/410612/adp-api-central-for-adp-workforce-now/editions)

### Inferences
- The "900,000 small businesses" figure and SMB framing point to RUN Powered by ADP as the underlying engine; ADP's pages read do not name it. [NC]
- API Central is bought by the employer, per employee, so it does not fit a model where the SaaS company is the reseller of payroll.

### Gaps
- Developer docs for Embedded Payroll (objects, auth, sandbox, SDK language) not opened; `developers.adp.com` is the pointer.
- Eligibility of foreign partners; minimums; who is support tier 1; liability. [NC]

## Paychex (developer.paychex.com)

### Takeaway
Paychex's developer programme connects a partner application to an employer's existing Paychex Flex account, one client at a time; no white-label or embedded payroll programme appears on Paychex's own pages.

### Cited Findings
- Partner process: questionnaire, evaluation by the Corporate Partnerships Team, "If approved, we will create a Partner application", build and test in a Paychex-provided sandbox, demo to Paychex for final approval, then production key/secret with the requested endpoints. [V] — [Paychex partner process](https://developer.paychex.com/partner)
- Client linking needs the client's 8-digit `displayId`; the client approves the integration inside Paychex Flex; requests are one client at a time. [V] — [Paychex partner process](https://developer.paychex.com/partner)
- Security: HTTPS, OAuth 2.0, and an app must be granted access to each client's company. [V] — [Paychex FAQ](https://developer.paychex.com/developer-support/faq)
- Sandbox is "a Flex sandbox account" provided by Paychex. [V] — [Paychex FAQ](https://developer.paychex.com/developer-support/faq)
- Payroll API access appears only after online payroll is added to the client's account by a Paychex representative. [V] — [Paychex FAQ](https://developer.paychex.com/developer-support/faq)
- Time punch data is not in the main API; Time & Attendance clients use a separate Stratustime API. [V] — [Paychex FAQ](https://developer.paychex.com/developer-support/faq)
- A webhook (`CLT_ACCESS`) notifies when a client accepts an integration. [V] — [Paychex partner process](https://developer.paychex.com/partner)
- Published APIs include Companies, Workers and Time Entries. [SR] — [API Evangelist listing](https://providers.apievangelist.com/providers/paychex-developer/)
- One article lists "Paychex Flex Embedded" with negotiated pricing and slow onboarding. [S, not supported by Paychex pages read] — [FintechSpecs](https://fintechspecs.com/blog/best-embedded-payroll-api-vertical-saas/)

### Inferences
- With Paychex the employer is Paychex's customer and is billed by Paychex; the software company is an integrated app, not the payroll brand.

### Gaps
- Fees, timeline, security review, rate limits, foreign partner rule: not on pages read. [NC]
- Whether a separate white-label programme exists off the developer site. [NC]

## Rollfi (a division of Priority, Nasdaq: PRTH)

### Takeaway
Rollfi offers payroll and benefits in three depths (white label with no developer work, embedded, full API) and has been part of Priority Technology Holdings since January 2025; its public developer page is thin and gave no auth, sandbox or object detail.

### Cited Findings
- "Rollfi is a division of Priority (Nasdaq: PRTH)"; bundled with wallets and banking on "Priority's Commerce Engine". [V] — [rollfi.xyz](https://www.rollfi.xyz/)
- Acquisition announced 24 Jan 2025, price undisclosed; TechCrunch reports the buyer as Priority Tech Ventures, team retained. [S] — [Priority press release](https://prioritycommerce.com/priority-acquires-rollfi-payroll-and-benefits-software); [TechCrunch](https://techcrunch.com/2025/01/23/after-pivoting-from-crypto-to-payroll-rollfi-gets-acquired/)
- Rollfi pivoted from crypto to embedded payroll before the acquisition. [S] — [TechCrunch](https://techcrunch.com/2025/01/23/after-pivoting-from-crypto-to-payroll-rollfi-gets-acquired/)
- W-2 and 1099 payroll in all 50 states; federal, state and local tax obligations handled; in-house benefits advisors for health, dental, vision, life. [V] — [rollfi.xyz](https://www.rollfi.xyz/)
- Integration options: White Label (no developer resources), embedded, API. "Go live in days"; a case study cites one developer launching in 3 weeks. [V] — [rollfi.xyz](https://www.rollfi.xyz/)
- Money movement claims: connected to 20+ banks, nationwide Money Transmitter Licenses, "$130B+ processed annually" (Priority-level figures). [V] — [rollfi.xyz](https://www.rollfi.xyz/)
- Named customers: FiveTax, Fiat Finance, Ease Practice. [V] — [rollfi.xyz](https://www.rollfi.xyz/)
- Developer docs sections: Company Onboarding, Employee Onboarding, Employer Portal, Employee Portal, Payroll Processing, Webhooks. [V] — [developer.rollfi.xyz](https://developer.rollfi.xyz/)
- Pricing not stated; one directory says "pricing on request". [V/SR] — [rollfi.xyz](https://www.rollfi.xyz/)
- A third-party listing says the API uses Basic authentication. [SR, unverified] — [Open Banking Tracker](https://openbankingtracker.com/embedded-finance/rollfi)

### Inferences
- Named customers are small accounting and fintech firms rather than large platforms, which suggests a lower entry bar than Salsa's stated floor — but no eligibility rule is published.

### Gaps
- Auth, sandbox, SDKs, pay frequencies, time input, reporting-agent role, liability, foreign partner rule. [NC]

## Everee and other providers found

### Takeaway
Everee is a credible additional US option with public developer docs, simulators usable before access is granted and a stated 2–12 week launch range; Nmbr is Canada-only and out of scope for US payroll.

### Cited Findings

**Everee**
- Everee handles "Tax calculation and filing", payment rails and settlement, "long-term compliance liability", worker support for pay issues, year-end documents, "garnishments and mandatory deductions". [V] — [everee.com/paas](https://www.everee.com/paas/)
- The platform keeps product experience, workflow design, pricing and packaging, and the customer relationship. [V] — [everee.com/paas](https://www.everee.com/paas/)
- "50-state compliance" and "Multi-state filings and multiple EINs, with Everee as your agent". [V] — [everee.com/paas](https://www.everee.com/paas/)
- Launch timeline: Crawl 2–4 weeks, Walk 4–8 weeks, Run 8–12 weeks. [V] — [everee.com/paas](https://www.everee.com/paas/)
- API areas: Workers, Company Onboarding, Payroll Run, Payroll Calendar, Pay Codes, Deductions, Timesheets, Payables, Leave Accrual, Embedded Experience, Webhook Endpoints. [V] — [developer.everee.com](https://developer.everee.com/)
- Partner API key: "You claim it yourself, so nobody at Everee ever sees it."; each client company has its own keys. [V] — [developer.everee.com](https://developer.everee.com/)
- An "Evaluate Payroll Infrastructure before you have access" guide offers company-signup and payroll-run simulators; a separate Sandbox company instance follows. [V] — [developer.everee.com](https://developer.everee.com/)
- Time input: Timesheets API (shifts, breaks, classified hours, corrections after payroll) and bulk Payables API. [V] — [developer.everee.com](https://developer.everee.com/)
- Embed is by iframe or WebView (web/React, React Native, iOS, Android, Flutter) via a magic link. [V] — [developer.everee.com](https://developer.everee.com/)
- Only two Everee screens appear in an API-first build: company contract signing and the funding bank account. [SR] — [Everee Developer Center](https://developer.everee.com/)
- Pricing: page links a "Pricing calculator"; marketing says "only pay for what you use. No setup fees"; a SourceForge listing of $50 per connection/month is third-party. [V/SR] — [everee.com/paas](https://www.everee.com/paas/); [everee.com/partner](https://everee.com/partner)
- Pay card issued by The Bancorp Bank, N.A. [V] — [everee.com/paas](https://www.everee.com/paas/)
- A Run Payroll API was announced for a November release in a 2025 expansion announcement. [SR] — [TechRSeries](https://techrseries.com/?p=186619)

**Nmbr**
- Toronto company; embedded payroll for Canada; CAD 7.6M seed Sept 2024; about 25 partners; considering international expansion (May 2026). [SR] — [Business Wire](https://www.businesswire.com/news/home/20240924021989/en/Nmbr-Secures-CAD-7.6-Million-to-Bring-Embedded-Payroll-to-Canada); [BetaKit](https://betakit.com/nmbr-says-its-reshaping-payroll-competition-in-canada-now-it-wants-to-go-worldwide/)

### Inferences
- Everee's target list (staffing, delivery, home care, PEO/EOR, software platforms) overlaps Zeal's more than Check's or Gusto's.

### Gaps
- Everee: SDKs, idempotency, pay frequencies, named platform partners, funding status, foreign partner rule. [NC]
- Branch, PayPro, Symmetry Payments, isolved white-label, Payroll Integrations, Rippling Embedded, Fingercheck: not researched this round.

## Partner eligibility for a foreign-incorporated software company

### Takeaway
Nothing public answers this for any vendor. The only published eligibility rules found are Salsa's size floor and Gusto's statement that it cannot support every use case.

### Cited Findings
- Salsa: platforms whose customers pay fewer than 25,000 workers, "including pre-launch", are not taken on. [V] — [salsa.dev](https://www.salsa.dev/)
- Gusto: "Gusto Embedded is not able to support all partner use cases at this time"; commercial, security and implementation reviews precede production. [V] — [Gusto docs](https://docs.gusto.com/embedded-payroll/docs/introduction)
- Check: features and production configuration are enabled by a Partner Engineer or Growth Manager; each employer passes EIN verification and KYB. [V] — [Check docs](https://docs.checkhq.com/docs/understanding-sandbox-and-production-environments.md)
- Paychex: questionnaire, evaluation and demo approval. [V] — [Paychex](https://developer.paychex.com/partner)
- Zeal and ADP: access through sales contact or form only. [V] — [Zeal docs](https://docs.zeal.com/); [ADP](https://www.adp.com/what-we-offer/products/adp-embedded-payroll.md)
- An extended search for a US-entity requirement on embedded payroll partners returned only general guidance on foreign companies running their own US payroll (EIN, state accounts), not partner rules. [SR] — [Forvis Mazars](https://www.forvismazars.us/forsights/2025/03/key-considerations-for-foreign-businesses-establishing-us-payroll)

### Inferences
- Every end employer must be a US business with an EIN and a US bank account regardless of where the platform is incorporated (follows from Check's EIN and bank verification steps and Zeal's US-only statement); the open question is only the platform's own entity.
- Since revenue share is paid to the partner and the partner signs a US-law agreement, a US subsidiary may be asked for — this is a guess, not a finding.

### Gaps
- Each vendor's answer on: India-incorporated partner; India-hosted (Azure India) storage of US employee data; SOC 2 required of the partner; partner support obligations. All require a direct question to the vendor. [NC]
