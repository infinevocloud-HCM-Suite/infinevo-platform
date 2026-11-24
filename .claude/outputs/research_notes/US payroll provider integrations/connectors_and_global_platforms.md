# Connecting to existing US payroll systems and global payroll platforms (as of 2026-11-24)

Evidence tags used on every finding:

| Tag | Meaning |
|---|---|
| **[V]** | Read on the vendor's own page in this session |
| **[V-s]** | Vendor's own page, seen only through a search-engine summary (page body not read in full) |
| **[S]** | Secondary source (competitor blog, aggregator, comparison site) |
| **[U]** | Unconfirmed, conflicting or dated — treat as a lead, not a fact |

Summary of the option families (detail and sources in the sections below):

| Family | Can push hours/earnings into a US pay run? | Can pull pay statements? | Contract needed with | Public price signal |
|---|---|---|---|---|
| Unified APIs (Finch, Merge, Kombo, Knit, Apideck...) | No (none found); Finch writes deductions only; Merge/Kombo/Knit write employees on some providers | Yes (Finch line-item; others vary) | Unified-API vendor (+ Gusto still reviews you) | Finch $65/connection/mo read-only; Merge $650/mo for 10 accounts |
| Direct provider APIs (Gusto, Paychex, ADP, Paylocity, Paycor, Square) | Yes — each has a time/pay-data input API | Varies; ADP payroll output is partner-only | Each provider separately | ADP API Central $2-2.50/employee/mo (customer pays); Intuit tiers $0-$4,500/mo [S] |
| File exchange (CSV/SPI/paydata) | Yes, by manual upload by the customer | No | Nobody | Free |
| Global payroll / EOR / PEO partner APIs (Deel, Remote, Oyster, Playroll) | They run payroll themselves; not a connector | Via their API | Platform partner agreement | Not public for partner tiers |
| Referral | No data exchange by itself | No | Referral terms only | Gusto up to $1,000/referral [V-s]; ADP 10% [U] |

## Section 1 — Unified payroll/HRIS APIs

### Takeaway
No unified API found offers a write of hours or earnings into a US pay run; the write surface is deductions (Finch) or employee create/update (Merge, Kombo, Knit) and varies per provider. They are strongest as a read channel (census, pay statements), with freshness of 24 hours on API-style connections and 7 days on Finch's assisted connections.

### Cited Findings

**Finch**
- [V] Starter plan: $65/month per connection, up to 15 connections, 24 providers, employers under 500 employees, read-only. Pro and Premier: "contact us", all 250+ providers, read and write (Deductions). Volume discounts need a 25-connections-per-month commitment. — [Finch pricing](https://www.tryfinch.com/pricing)
- [V] Sandbox (Finch Sandbox and Provider Sandbox) is included on all plans; SOC 2 Type II, AES-256 at rest, TLS 1.2, GDPR on all plans; RBAC/SSO on Pro and Premier; uptime SLA on Premier only. — [Finch pricing](https://www.tryfinch.com/pricing)
- [V] Automated integrations: OAuth, API token or credentials; 30+ providers for read, 5 for write; data refresh every 24 hours; deduction writes complete in 24 hours; on-demand sync available; all plans. — [Finch integration types](https://developer.tryfinch.com/integrations/integration-types.md)
- [V] Assisted integrations: employer adds a third-party administrator in its payroll system and Finch operates the sync; 175+ providers read, 20+ write; refresh every 7 days; write-backs within 2 business days; one-time setup of 2 weeks (Organization, Payroll) or 1 week (Deductions); no on-demand sync; Pro and Premier only. — [Finch integration types](https://developer.tryfinch.com/integrations/integration-types.md)
- [V] The only write product is Deductions: create/register/update company deductions, enroll and unenroll individuals; types include 401(k), HSA, FSA, Section 125; writes are asynchronous (job_id polling); some providers only allow managing existing deductions. Organization, Payroll and Benefits are read products. The page describes no employee-create or hours/earnings write. — [Finch Deductions overview](https://developer.tryfinch.com/products/deductions/Overview.md)
- [V] Finch's docs index lists pay-statement, pay-statement-item and payment read endpoints. — [Finch llms.txt](https://developer.tryfinch.com/llms.txt)
- [S] "No employee create or update"; assisted connections send no change webhooks; "mostly US-focused". — [Nango comparison](https://nango.dev/blog/best-unified-apis-for-hris-ats-payroll/) (competitor-authored)
- [S] Finch's own comparison claims write support on "25+ providers" and line-item pay-statement standardisation. — [Finch blog](https://www.tryfinch.com/blog/best-unified-apis-hris-payroll) (vendor-authored)
- [V-s] Finch states Paycom "does not offer an API to its customers or third-party developers", i.e. Paycom is reached by assisted connection. — [Finch Paycom page](https://www.tryfinch.com/integrations/paycom)
- Earlier pricing conflict resolved for now: the vendor page read on 2026-11-24 shows $65/connection/month Starter; other figures in circulation are older or secondary.

**Merge (HRIS category)**
- [V] Launch plan: 3 production linked accounts free; $650/month for up to 10 linked accounts, then $65 per additional account; daily sync; 100 requests/min. Professional and Enterprise are contract-based (custom sync frequency, sandbox 60 days / unlimited). SOC 2, ISO 27001, HIPAA/GDPR badges. No country eligibility restriction stated. — [Merge pricing](https://www.merge.dev/pricing)
- [V] Writes are per-integration: required fields must be discovered through the `/meta` endpoint per linked account. — [Merge docs index](https://docs.merge.dev/llms.txt)
- [V-s] Merge HRIS docs list `/timesheet-entries` and `/time-off` with POST meta endpoints, so the unified model has write endpoints for those objects; the ADP Workforce Now pages only describe reading TimeOff and balances. — [Merge HRIS integrations overview](https://docs.merge.dev/merge-unified/hris/integrations/overview), [Merge ADP Workforce Now](https://www.merge.dev/adp-workforce-now)
- [S] "Employees can be created, but not updated or terminated. Payroll runs, benefits, and compensation are read-only." 87 HRIS integrations. — [Nango comparison](https://nango.dev/blog/best-unified-apis-for-hris-ats-payroll/)
- [S] Finch's comparison says Merge has "no pay-statement standardization" and no payroll write. — [Finch blog](https://www.tryfinch.com/blog/best-unified-apis-hris-payroll) (competitor-authored)

**Kombo**
- [V] Paylocity connector: reads employees, employments, timesheets, pay runs, payslips, payslip line items, groups, legal entities; the only write action is "Create employee" (created employee is in pre-hire status); no update, absence or timesheet write listed; country always set to US. — [Kombo Paylocity connector](https://docs.kombo.dev/hris/connectors/paylocity.md)
- [V-s] Kombo has a separate US API server/region and a `POST /hris/employees` endpoint. — [Kombo post-employees](https://docs.kombo.dev/api-reference/hris/v1/post-employees.md)
- [S] About 100 HRIS/payroll connectors, strongest in Europe; payroll read-only, covering four HCM systems; no deduction writes; no public pricing (demo-led); new connection can take hours to sync. — [Nango comparison](https://nango.dev/blog/best-unified-apis-for-hris-ats-payroll/)
- [S] Pricing described as fixed annual platform fee plus per-customer connection fee. — [Apideck on Kombo](https://apideck.com/alternatives/kombo-dev) (competitor-authored)

**Knit**
- [S] 71 HRIS/payroll apps; creates employees on 41, updates on 37, terminates on 20; employee payroll covered for 17 of 71; no Gusto or Justworks; from $499/month (Start Up), 24-hour sync on that plan; stores no raw customer data; US, EU and India data residency. — [Nango comparison](https://nango.dev/blog/best-unified-apis-for-hris-ats-payroll/)
- [S] Finch's table credits Knit with line-item payroll detail and write on 5 providers. — [Finch blog](https://www.tryfinch.com/blog/best-unified-apis-hris-payroll)
- [U] One search summary reported "free for 10 connections, then from $50/month" for Knit; the same figures appear on Nango's own pricing, so this is probably a mis-attribution. — [Nango comparison](https://nango.dev/blog/best-unified-apis-for-hris-ats-payroll/)

**Apideck**
- [S] About 60 HRIS connectors; payroll resource covers only Gusto and Remote, read-only; pass-through (no stored copy), HR change events polled every 24 hours; from $599/month, HRIS plus ATS needs the $1,299/month Scale plan. — [Nango comparison](https://nango.dev/blog/best-unified-apis-for-hris-ats-payroll/)
- [V-s] Apideck documents a Paychex HRIS connector (company structure, pay-standards, employee webhooks). — [Apideck Paychex overview](https://developers.apideck.com/apis/hris/paychex/overview)

**Unified.to**
- [V-s] Bills by API call with unlimited connections; Grow tier from $750/month (750K calls); its own page says payroll data is limited to payslips. — [Unified.to HRIS comparison](https://unified.to/blog/best_unified_api_for_hris_integrations_in_2026) (vendor-authored)

**Bindbee**
- [S] Roughly 40-75 HRIS/payroll systems; ADP Workforce Now, Paylocity, Rippling and isolved are reached by SFTP; reported $35-50 per connection. — [Finch blog](https://www.tryfinch.com/blog/best-unified-apis-hris-payroll) (competitor-authored)

**StackOne**
- [V-s] Pricing is per action call with the connector library included on every tier. — [StackOne pricing](https://stackone.com/pricing/)
- [U] Third-party listings quote a free 1,000-credit tier and a $600/month Team plan; counts of connectors differ between sources. — [Merge on StackOne pricing](https://www.merge.dev/blog/stackone-pricing)

**Nango (an "others" entry)**
- [V] Free for 10 connections, then from $50/month at $0.29 per connection; payroll tools "aren't in the catalog yet"; Rippling, Deel, Paychex are auth-and-proxy only; no shared payroll model. — [Nango comparison](https://nango.dev/blog/best-unified-apis-for-hris-ats-payroll/)

**Truto (an "others" entry)**
- [S] 40+ HRIS/payroll, real-time pass-through, line-item payroll detail, limited write. — [Finch blog](https://www.tryfinch.com/blog/best-unified-apis-hris-payroll)

**Argyle / Pinwheel / Atomic (employee-permissioned)**
- [V-s] Argyle: the consumer (employee) selects the employer or payroll provider and logs in with own credentials to share income and employment data; product is aimed at lenders' income/employment verification; claims coverage of over 210 million US consumers. — [Argyle verification](https://argyle.com/solutions/verification-income-and-employment)
- [S] Pinwheel is described as direct-deposit switching plus income and employment verification. — [CB Insights Argyle vs Pinwheel](https://www.cbinsights.com/compare/argyle-systems-vs-pinwheel)

**Gusto's rule for unified-API users**
- [V] A company integrating with Gusto through an approved unified-API partner still goes through Gusto's own Production Pre-Approval and Security Review; Gusto then notifies the unified-API partner. — [Gusto security review](https://docs.gusto.com/app-integrations/docs/security-review)

### Inferences
- A time/leave product gets its key write (hours into the pay run) from no unified API found in this research; unified APIs solve the read half (employee census in, pay statements out) and, on Finch Pro, deduction write-back.
- Per-connection pricing ($65/connection/month at both Finch Starter and Merge Launch overage) is a per-customer cost the HR company carries for every US customer, independent of headcount.
- Finch assisted connections (7-day refresh, 1-2 weeks setup, employer must add Finch as a third-party admin) are not suitable for per-pay-period data exchange on a weekly or bi-weekly cycle without lag.
- Argyle/Pinwheel/Atomic are built around each employee logging in individually for verification; that model does not map to an employer-level HR/time integration, and no write was found.
- Rutter was not found in any HRIS/payroll comparison read; it appears to be outside this category (see Gaps).

### Gaps
- Finch's per-provider table (which of ADP WFN, RUN, Paychex, Gusto, QuickBooks, Paylocity etc. are automated vs assisted, and which 5 automated providers take writes) is a JavaScript table that did not render; the vendor offers a CSV export at [Finch providers](https://developer.tryfinch.com/integrations/providers.md).
- Merge's per-provider write matrix (supported-fields page) did not render; whether timesheet POST works on any US payroll provider is unverified.
- Knit, Apideck, Unified.to, Bindbee, StackOne vendor pricing/docs pages were not read directly; all facts above for them are secondary or search summaries.
- Rutter, Pinwheel and Atomic: no vendor page read; no reliable facts obtained on payroll coverage.
- No unified-API vendor page read states an eligibility rule for non-US companies either way.
- Pro/Premier (Finch) and Professional/Enterprise (Merge) prices are not public.

## Section 2 — Direct partner programmes of US payroll providers

### Takeaway
Gusto, Paychex, ADP, Paylocity, Paycor and Square all document an API path for a third-party time product to put hours or earnings into payroll; every one except Square gates production behind a partner application and security review, and the employer must authorise each connection. Fees and revenue share are almost never published.

### Cited Findings

**Gusto developer platform (app integrations, not Embedded)**
- [V] API reference available to app integrations includes: employees (read; create/update/delete onboarding employee), compensations (read/write), time off (requests read-only; add employees to a policy), payrolls (list/get; `PUT` update payroll; prepare payroll for update), time tracking (create/update/delete time sheets, batch, payroll sync), garnishments (create/update), company and employee benefits (create/update/delete). — [Gusto app-integrations reference](https://docs.gusto.com/app-integrations/reference)
- [V] Time tracking: batch endpoint creates or updates up to 500 time sheets per request; "A payroll sync takes approved time sheet data and syncs it to the company's payroll", processed asynchronously. — [Gusto app-integrations index](https://docs.gusto.com/app-integrations/llms.txt)
- [V] Production keys require an approved Production Pre-Approval and Security Review plus a completed Partner Checklist; API scopes are assigned by Gusto per use case and enforced in production; the API is restricted to App Integration and Embedded Payroll partners; "early-stage products without a track record serving small businesses aren't currently supported". — [Gusto introduction](https://docs.gusto.com/app-integrations/docs/introduction)
- [V] Security review asks for SOC 2 Type 2 or ISO 27001 (with Statement of Applicability) or PCI DSS, penetration test reports, and security/privacy policies; without a formal audit the applicant must describe its controls. No fee, country or minimum-customer rule is stated on the page. — [Gusto security review](https://docs.gusto.com/app-integrations/docs/security-review)
- [V-s] Gusto says its Partnerships team reviews applications within 1-2 weeks; continued access depends on passing annual reviews. — [Gusto security review](https://docs.gusto.com/app-integrations/docs/security-review), [Gusto developer terms](https://gusto.com/legal/terms/developer-terms-of-service)
- [S] Finch reports Gusto "typically takes at least 2 weeks to respond" and the process "can take several weeks", with no fast track. — [Finch Gusto guide](https://developer.tryfinch.com/integrations/guides/Gusto.md)

**Paychex Flex APIs**
- [V] Six-step partner process: questionnaire; Corporate Partnerships Team evaluation; partner application created; build and test in Paychex sandbox; demo to Paychex for final approval; production application with key and secret for the requested endpoints. — [Paychex partner process](https://developer.paychex.com/partner)
- [V] Client authorisation is one client at a time: partner calls "Request Client Access" with the client's 8-digit displayId, sends the returned deep link, and the client approves under Integrated Apps in Flex; bulk linking is by support request. — [Paychex partner process](https://developer.paychex.com/partner)
- [V] Published use cases: maintain employee data with Paychex as source of record; keep Paychex updated from an external HR system; custom fields; simple check creation; complex check creation (job costing and labour distribution); recurring deduction for benefit administration; direct deposit; Flex Time. — [Paychex use cases](https://developer.paychex.com/use_cases)
- [V-s] Simple check creation: "The most common unprocessed check use cases are adding hours worked and commission/bonus/tip amounts to checks in Flex payroll entry"; recurring earnings and deductions on the worker are applied automatically when the check is created by API. — [Paychex SimpleCheck](https://developer.paychex.com/SimpleCheck)
- [V] The main API does not expose time punch or timecard endpoints; clients on Paychex Time & Attendance use a separate Stratustime API. Payroll API settings appear only after a Paychex representative adds online payroll to the account. — [Paychex FAQ](https://developer.paychex.com/developer-support/faq)
- [V-s] REST, OAuth 2.0, JSON; rate limit listed at 10,000 requests per minute per partner. — [Paychex getting started](https://developer.paychex.com/getting-started/overview)
- [U] Endpoint names `POST /workers/{workerId}/checks` and `POST /checks/{checkId}/checkcomponents` come from a third-party catalogue. — [Jentic Paychex](https://jentic.com/apis/paychex.com/paychex.md)

**ADP Marketplace and ADP API Central**
- [V] Marketplace steps: apply for a partner account (reviewed by Sales, Security and Legal; mutual NDA accepted in the form); 30-minute initial call with a Business Development Representative; partner security review using a questionnaire built on ISO 27001 plus policy and architecture documents (fast-track or pre-approval possible for organisations already reviewed); sign a Developer Participation Agreement; onboarding and billing setup ("referral or e-commerce"); credentials issued. — [ADP Becoming a Partner (PDF)](https://marketplace.adp.com/downloads/becoming-a-partner.pdf)
- [V-s] Apps also face penetration tests and source-code analysis by ADP's Global Security Organization; unresolved issues after three review rounds require a third-party assessment. ADP evaluates strategic fit, looking for partners with a substantial number of shared clients. — [ADP GSO review](https://marketplace-cdn.adp.com/dev-portal/pdf/protected/Global_Security_Organization_Review_for_ADP_Marketplace_Apps), [ADP why marketplace](https://esi.apps.adp.com/en-US/pages/why-marketplace)
- [V-s] RUN: a Marketplace connector for Pay Data Input imports time and attendance data into each payroll and needs a Pay Data Input scope on the partner application subscription. — [ADP Payroll Data Input API Guide for RUN](https://marketplace-cdn.adp.com/dev-portal/pdf/protected/Payroll_Data_Input_API_Guide_for_RUN_Powered_by_ADP)
- [V-s] Workforce Now: "Payroll Data Input APIs import a specific Pay Data batch to ADP Workforce Now"; Payroll Output is limited to Marketplace partners only; a Time Entries / Team Time Cards API family exists. — [ADP API Central features](https://apps.adp.com/en-us/apps/410612/adp-api-central-for-adp-workforce-now/features), [ADP Time Entries guide](https://marketplace-cdn.adp.com/dev-portal/pdf/protected/Time_Entries_Modify_TLM_API_Guide_for_ADP_Workforce_Now)
- [V-s] API Central (bought by the ADP client, not the partner) is billed monthly on the client's ADP invoice by employee count; listings show $2.50/employee/month for the first 999 employees (tiered down to $1.50) on one listing and a flat $2.00/employee/month on another; optional consulting $250/hour. Listings also say "Login to see prices". — [ADP API Central editions](https://apps.adp.com/en/apps/410612/adp-api-central-for-adp-workforce-now/editions)
- [U] ADP's partner portal mentions tracking "revenue-share payments to ADP" on app sales; no percentage is published. — [ADP partner portal](https://partners.adp.com/)

**Intuit QuickBooks Online Payroll**
- [V-s] The QuickBooks Payroll API is GraphQL with a single production endpoint; the Payroll Compensation API exposes pay types (salary, hourly, overtime, holiday) "for enhanced time tracking" and is not available in sandbox; sensitive scopes need an extra legal agreement and manual onboarding; access is requested by support ticket ("New API access"). — [Intuit Payroll API get started](https://developer.intuit.com/app/developer/qbo/docs/workflows/integrate-with-payroll-api/get-started), [Intuit Premium APIs](https://developer.intuit.com/app/developer/qbo/docs/learn/premium-apis)
- [V-s] Paid tiers (Silver, Gold, Platinum) carry a flat monthly programme fee plus variable CorePlus API fees; eligibility checkpoints twice a year; premium APIs are a Silver-and-up benefit. — [Intuit App Partner Program FAQ](https://developer.intuit.com/app/developer/qbo/docs/get-started/partner-faq), [Intuit App Partners](https://intuitapppartners.com/)
- [S] Tier fees reported as Builder $0, Silver $300/month, Gold $1,700/month, Platinum $4,500/month; Gold needs 500+ active connections, Platinum 3,000+. — [Truto on QuickBooks API cost](https://truto.one/blog/how-much-does-the-quickbooks-api-cost-2026-pricing-rate-limits/), [Apideck on Intuit programme](https://www.apideck.com/blog/quickbooks-api-pricing-and-the-intuit-app-partner-program)
- [U] Conflict: an Intuit developer blog says Gold and Platinum partners can request the Time API with Payroll Compensation, while Apideck says Silver suffices for premium APIs. — [Intuit blog](https://blogs.a.intuit.com/?p=35224); contradicted by [Apideck](https://www.apideck.com/blog/quickbooks-api-pricing-and-the-intuit-app-partner-program)

**Paylocity**
- [V-s] Punch Import API pushes externally recorded punches into Paylocity Time & Labor, whose rules engine calculates hours; needs the client's Time & Labor module live and matching cost centres; 9,000 records per payload; only open pay periods; edits must be made in Paylocity. — [Paylocity Punch Import overview](https://developer.paylocity.com/integrations/reference/punch-import-overview)
- [V-s] Pay Entry API creates a new payroll batch; insert-only, no edits or updates to submitted data. — [Paylocity Pay Entry overview](https://developer.paylocity.com/integrations/reference/pay-entry-overview)
- [V-s] A Partner Onboarding API exists for the Paylocity Marketplace workflow; some APIs are restricted to "Paylocity Technology Partners". — [Paylocity changelog](https://developer.paylocity.com/integrations/changelog?page=2)

**Paycor**
- [S] Public developer portal; production access needs partner approval; a timecard import endpoint starts a backend import with no status API, and the client administrator must still approve the import in Paygrid. — [Bindbee Paycor guide](https://bindbee.dev/blog/paycor-api), [Paycor developers portal](https://developers.paycor.com/explore) (portal body did not render)

**Paycom**
- [S] "Walled garden": third-party API access is heavily restricted; a customer can ask its Paycom representative for an "internal use" API key. Finch says Paycom offers no API to customers or third parties. Sources disagree on whether an API exists. — [Knit Paycom guide](https://getknit.dev/blog/paycom-api-integration-guide-in-depth), [Finch Paycom page](https://www.tryfinch.com/integrations/paycom)

**UKG**
- [V-s] Developer Hub is public without login. UKG Pro Time Management web services: only schedule data can be imported. Workforce Management Classic has a Punch Import API for raw punches. Partners receive an `X-Partner-Id` header value from UKG Alliances. — [UKG Time Management web services](https://developer.ukg.com/hcm/docs/time-management-web-services), [UKG WFM Classic web services](https://developer.ukg.com/hcm/docs/workforce-management-classic-web-services)

**Rippling App Shop**
- [V] Three steps: apply (company, customers, integration goals), build (docs, sandbox), publish to the App Shop; every partner gets an App Shop listing; fees, revenue share and eligibility criteria are not stated. — [Rippling developers](https://www.rippling.com/developers)
- [S] Approval has a pre-vet stage before the formal App Shop application, and Rippling requires SSO support by default. — [Merge help: Rippling](https://help.merge.dev/en/articles/9265388-how-can-i-access-the-rippling-integration)

**Justworks**
- [V-s] Justworks launched a Partner API in July 2025 with Electric as the first native integration. — [Justworks press](https://www.justworks.com/press/company-news/launching-our-new-partner-api-with-electric)
- [S] OAuth app creation is gated; registration by email to the partner API team. Justworks also uses Finch for its integration strategy. — [Supergood Justworks](https://supergood.ai/api-report-card/justworks), [Finch on Justworks](https://www.tryfinch.com/blog/justworks-taps-finch-to-support-their-hr-and-fintech-integration-strategy)

**TriNet**
- [V] Marketplace Partner steps: interest form; product-team review; demos and vetting; partnership agreement (contracting); launch with joint marketing. Fees and eligibility are not stated. — [TriNet marketplace partners](https://www.trinet.com/partners/marketplace-partners)
- [S] Roughly five REST APIs (employees, companies, payroll, money, time off), mostly read-only, access gated by approval and technical review. — [Supergood TriNet](https://supergood.ai/api-report-card/trinet)

**OnPay**
- [V-s] "The OnPay API has access limited to approved partners only. To partner, please contact us." — [OnPay payroll API](https://onpay.com/payroll-api)

**Square Payroll**
- [V-s] Labor API lets an external time-clock application import timecards into a Square account, and export hours through SearchTimecards; timecard endpoints need API version 2025-05-21 or later (older Shift objects retired). — [Square Labor API](https://developer.squareup.com/docs/labor-api/how-it-works)

### Inferences
- Gusto has the most complete documented surface for a time/leave/HR product (time sheets, payroll sync, employee create, benefits and garnishments), followed by Paychex (worker sync plus check creation) and Paylocity (punch or pay-entry import).
- Pull of pay statements is weaker than push: Gusto's app-integration reference shows payrolls read but no pay-stub endpoint was listed; ADP Payroll Output is Marketplace-partner-only.
- ADP is the heaviest path: Sales, Security and Legal reviews, pen test and source-code analysis, a "strategic fit" screen on shared clients, and an undisclosed revenue share; alternatively each ADP client buys API Central at about $2-2.50 per employee per month.
- Every direct path multiplies: one application, security review and build per provider.

### Gaps
- No provider page read states that foreign-incorporated partners are accepted or excluded. Gusto's stated screen is track record with small businesses, ADP's is shared clients; neither names country.
- Time to list is published only by Gusto (1-2 weeks for the application review, security review after). ADP, Paychex, Paylocity, Paycor, Rippling, TriNet give none.
- Partner fees and revenue share: not published by Gusto, Paychex, Paylocity, Paycor, Rippling, TriNet, OnPay. ADP's share is referenced but not quantified.
- Intuit tier fees and the tier required for payroll/time APIs are from secondary sources; Intuit's pages were truncated on fetch.
- ADP RUN Pay Data Input guide is an image-only PDF and could not be read; field-level detail (earnings codes, limits) is unverified.
- Whether Intuit's Time API accepts time writes that flow into QuickBooks Payroll was not confirmed.
- Paycor's and UKG's partner terms were not read on vendor pages.

## Section 3 — File-based exchange

### Takeaway
Every major provider accepts a time or pay-data file uploaded by the employer, so a product can export hours with no contract; the formats are provider-specific and the exchange is one-way and manual.

### Cited Findings
- [V-s] ADP RUN: the Time Sheet Import guide requires the time system to output a properly formatted .csv before import into RUN. — [ADP RUN time sheet import guide](https://support.adp.com/adp_payroll/content/hybrid/@runcomplete/doc/pdf/GTS_payroll_guide.pdf)
- [V-s] Paychex: Standard Payroll Import (SPI) has a published specification sheet; setup is a one-time choice of import file format; the import remains backward compatible with a 16-field SPI file used before January 2016; Paychex-controlled file types are configured by a Paychex representative. — [Paychex SPI specification](https://myapps.paychex.com/pngHelp_static/helpHtml/SPI_Specification_Sheet.htm), [Paychex import file setup](https://myapps.paychex.com/pngHelp_static/helpHtml/Import_File_Setup.htm)
- [V-s] Gusto: "Run payroll with CSV upload" / Smart Import accepts files in their original format; unsupported report types fail. — [Gusto Smart Import](https://support.gusto.com/article/999914471000000/Run-payroll-with-Smart-Import)
- [V-s] QuickBooks: QuickBooks Online Advanced Payroll has an Import Timesheets option with defined layouts (date column, start/end time columns). The help URL is in the en-uk tree, so it may describe the UK product. — [QuickBooks importing timesheets](https://quickbooks.intuit.com/learn-support/en-uk/help-articles/importing-timesheets-in-quickbooks-online-advanced-payroll/00/545878)
- [V-s] ADP Workforce Now: an "Automated Paydata Integration" listing and third-party tools automate paydata file uploads; one such partner requires the client to hold API Central. — [ADP Automated Paydata Integration](https://apps.adp.com/en-US/apps/372723/automated-paydata-integration-for-adp-workforce-now/support)
- [S] Other software already ships preconfigured ADP Paydata export jobs (delimited files). — [Sage 100 payroll import jobs](https://help-sage100.na.sage.com/2018/Subsystems/VI/Concept/Preconfigured_Payroll_Import_Jobs.htm)

### Inferences
- A file export needs the customer's own earnings codes, employee IDs (ADP file number, Paychex worker ID) and company code in the file, so the HR product must store a per-customer mapping even in the zero-contract path.
- File exchange covers push only; pulling pay statements or deductions back requires the customer to export reports manually.

### Gaps
- Column-level layouts for ADP paydata (EPI) and the Paychex SPI sheet were not read; only their existence is confirmed.
- No confirmed US QuickBooks Online Payroll time-import CSV spec was found; US QuickBooks normally takes time through QuickBooks Time.

## Section 4 — Global payroll, EOR and PEO platforms with partner APIs

### Takeaway
Deel, Remote, Oyster and Playroll offer embedded or white-label programmes, but these make the platform the payroll operator rather than connecting to the customer's existing provider. US domestic payroll is offered by Deel and by Remote (as a US PEO); partner-tier pricing is not public.

### Cited Findings
- [V] Deel Embedded: direct API access to Deel's employment infrastructure, "Deel remains invisible to end users"; worker types EOR, independent contractor, contractor of record and direct employee; parent/child multi-tenant account structure for resale; forward-deployed Deel engineer; full deployment "typically takes about 180 days"; target partners include HRIS platforms and domestic payroll providers. — [Deel Embedded introduction](https://developer.deel.com/api/embedded/introduction)
- [V-s] Deel Embedded models: Branded (Deel-hosted platform under partner brand), Hybrid, and fully API-driven Embedded. Deel lists a US Payroll product and an API partnership programme with App Marketplace listing. — [Deel Embedded](https://www.deel.com/solutions/embedded/), [Deel open API](https://www.deel.com/en-gb/solutions/open-api/)
- [V] Remote: partners can embed Remote's EOR and payroll; API covers EOR, contractor management and payroll; product footer lists Global Payroll, Contractor of Record and PEO (US); "no limit for companies" wanting to use the partner API; pricing and sandbox not stated on the page. — [Remote API](https://remote.com/platform/remote-api)
- [S] Oyster offers API plus "Oyster Embedded" for white-label workflows; Oyster publishes no price or country list for a standalone payroll product; EOR listed at $699/employee/month. — [Playroll on Oyster competitors](https://www.playroll.com/blog/oyster-hr-competitors), [RemotePass Oyster alternatives](https://www.remotepass.com/blog/oyster-alternatives)
- [S] Playroll has an open API and sells a white-label version for resellers. — [WhichPayroll Playroll alternatives](https://whichpayroll.com/alternatives/playroll)
- [S] Remofirst has no public API. — [WhichPayroll Playroll vs RemoFirst](https://whichpayroll.com/compare/playroll-vs-remofirst)
- [S] Papaya Global's public API (self-serve keys, sandbox, webhooks) is payments orchestration rather than full payroll; pricing is quote-based. — [Supergood Papaya Global](https://supergood.ai/api-report-card/papaya-global)
- [S] Multiplier: global payroll from about $20 per person; "US PEO: Not offered". — [WhichPayroll Deel vs Multiplier](https://whichpayroll.com/compare/deel-vs-multiplier)
- [U] US price points conflict across comparison sites: Deel US Payroll $19 or $29 per employee per month (one adds a $1,000 setup per entity); Deel US PEO $79 or $125; Remote PEO from $99; Rippling payroll "$8 base + $8 per employee". — [CheckThat Deel pricing](https://checkthat.ai/brands/deel/pricing), [ComplianceAIScout EOR comparison](https://www.complianceaiscout.com/best/eor)
- [V-s] Justworks and TriNet are PEOs with gated partner APIs (see Section 2); no white-label or resale API was found for either. — [Justworks press](https://www.justworks.com/press/company-news/launching-our-new-partner-api-with-electric), [TriNet marketplace partners](https://www.trinet.com/partners/marketplace-partners)

### Inferences
- Difference from embedded payroll APIs such as Check: with Deel/Remote the platform is the payroll provider (or the co-employer under a PEO, or the legal employer under EOR) and the HR company resells or embeds it; the HR company does not hold the payroll-operator role. This section could not source Check's own partner requirements, so the comparison rests on the platforms' descriptions only.
- PEO and EOR change the employment relationship (co-employment or the platform as legal employer), which is a different product from "customer keeps its payroll provider".
- A 180-day typical deployment for Deel Embedded is a larger build than a single provider's time API.

### Gaps
- Whether Deel Embedded's API includes US domestic payroll and US PEO, or only EOR/contractor flows, is not stated on the page read.
- Partner or white-label pricing and revenue share: none published by Deel, Remote, Oyster or Playroll.
- Rippling: no partner or white-label API for running payroll on a third party's behalf was found; only the App Shop integration programme.
- Papaya, Oyster, Multiplier, Remofirst and Playroll vendor pages were not read directly.
- Check's partner requirements (US entity, compliance obligations) were not retrieved in this round.

## Section 5 — Referral and reseller models

### Takeaway
Referral programmes exist at Gusto and ADP with published or semi-published rewards; they involve no API by themselves, and none of the terms read addresses a foreign referrer.

### Cited Findings
- [V-s] Gusto channel partner terms: Gusto pays the partner a percentage of Gusto revenue from new customers referred, at a rate set in its Partner Program Guide (rate not public), plus a one-time fee per qualified firm referral. — [Gusto channel terms](https://gusto.com/legal/terms/channelterms)
- [V-s] Gusto's PartnerStack listing advertises up to $1,000 per new customer referral; a "$300 per new customer who runs payroll, plus $200 bonus" offer ended 31 January 2026. — [PartnerStack Gusto](https://market.partnerstack.com/page/gusto)
- [U] ADP Referral Rewards terms (old page): revenue share of 10% of annualised net processing revenue for referred clients with 1-49 employees, paid within 45 days after the ADP fiscal quarter. — [ADP Referral Rewards terms](https://www.adpreferralrewards.com/ADP/partner/printpartnerterms)
- [U] ADP accountant programme: secondary sources cite up to 75% of monthly payroll fees for the first 12 months, or tiers of 25% then 50%. — [The Appy Hour ADP deck (2021)](https://www.theappyhour.com/wp-content/uploads/2021/07/ADPs-Accountant-Partner-Program-7.7.21.pdf), [Katie Ferro blog](https://www.katieferro.com/blog/adp-partner-program-benefits-to-bookkeepers-their-clients-104)
- [V] ADP Marketplace partners are set up in billing as either "referral or e-commerce" sellers. — [ADP Becoming a Partner (PDF)](https://marketplace.adp.com/downloads/becoming-a-partner.pdf)
- [S] Patriot Software pays a $200 referral fee after two successful billings. — [Patriot accountants](https://www.patriotsoftware.com/accountants/)
- [S] OnPay's partner console is reported to include a revenue share; no rate found. — [FirstHR comparison](https://firsthr.app/compare/payroll/payroll-software-for-accountants)

### Inferences
- Most referral programmes are built for US accountants and bookkeepers; a software company would more likely fall under channel/affiliate terms (Gusto via PartnerStack) than accountant terms.
- Referral income and data integration are separate tracks: a referral agreement does not grant API access (Gusto still requires pre-approval and security review).

### Gaps
- Paychex, OnPay and QuickBooks Payroll referral commission rates were not found on vendor pages.
- Current (2026) ADP referral rates are unconfirmed; sources are several years old.
- Whether Gusto's or ADP's referral terms pay non-US entities was not checked on the terms pages.

## Key question 1 — Which option gives a real two-way integration with the least contract and compliance burden?

### Takeaway
No single option found is both two-way and low-burden: direct provider APIs give the real write (hours into the pay run) at the cost of one partner approval and security review per provider, while unified APIs give one contract but mostly read.

### Cited Findings
- [V] Gusto direct: time sheets batch (500 per request) and payroll sync, plus employee and benefit writes; needs pre-approval, security review (SOC 2 Type 2 / ISO 27001 / pen test, or a controls description) and a partner checklist. — [Gusto reference](https://docs.gusto.com/app-integrations/reference), [Gusto security review](https://docs.gusto.com/app-integrations/docs/security-review)
- [V] Paychex direct: worker sync both directions and check creation with hours and earnings; questionnaire, sandbox build, demo; one-client-at-a-time approval. — [Paychex use cases](https://developer.paychex.com/use_cases), [Paychex partner process](https://developer.paychex.com/partner)
- [V] Finch: one contract, read of organisation and pay statements, deduction write on Pro; no hours write. — [Finch Deductions overview](https://developer.tryfinch.com/products/deductions/Overview.md), [Finch pricing](https://www.tryfinch.com/pricing)
- [V] Going through a unified API does not remove Gusto's own review. — [Gusto security review](https://docs.gusto.com/app-integrations/docs/security-review)
- [V-s] Square Labor API accepts imported timecards under Square's standard developer platform. — [Square Labor API](https://developer.squareup.com/docs/labor-api/how-it-works)

### Inferences
- Burden ranking by documented steps (lightest first): file export (none) < unified API read (one vendor contract) < Square < Gusto / Paychex / Paylocity (application, security review, demo) < ADP Marketplace (three-team review, pen test, source-code analysis, revenue share) < Deel/Remote embedded (resale partnership, about 180 days).
- A combination appears in the facts: read via a unified API plus write via file export or one direct provider API covers both directions without a contract per provider for the read half.

### Gaps
- No source compares approval effort across providers on a like-for-like basis; the ranking above is inferred from published steps only.

## Key question 2 — What limits exist?

### Takeaway
The recurring limits are read-only scope, per-connection fees, per-employer authorisation, slow refresh on assisted connections, and insert-only pay-data writes.

### Cited Findings
- [V] Finch Starter is read-only and capped at 15 connections and employers under 500 employees; assisted connections refresh every 7 days and need 1-2 weeks setup. — [Finch pricing](https://www.tryfinch.com/pricing), [Finch integration types](https://developer.tryfinch.com/integrations/integration-types.md)
- [V] Merge Launch: daily sync; $65 per linked account beyond 10. — [Merge pricing](https://www.merge.dev/pricing)
- [V] Paychex: each client must approve the app in Flex; requests are made one client at a time. — [Paychex partner process](https://developer.paychex.com/partner)
- [V] Gusto: scopes are assigned by Gusto and calls outside them are rejected in production. — [Gusto introduction](https://docs.gusto.com/app-integrations/docs/introduction)
- [V-s] Paylocity Pay Entry is insert-only; Punch Import only for open pay periods and needs the client to own Time & Labor. — [Paylocity Pay Entry overview](https://developer.paylocity.com/integrations/reference/pay-entry-overview), [Paylocity Punch Import overview](https://developer.paylocity.com/integrations/reference/punch-import-overview)
- [V-s] ADP: the client pays for API Central per employee per month; Payroll Output is partner-only. — [ADP API Central editions](https://apps.adp.com/en/apps/410612/adp-api-central-for-adp-workforce-now/editions), [ADP API Central features](https://apps.adp.com/en-us/apps/410612/adp-api-central-for-adp-workforce-now/features)
- [V-s] Intuit Payroll Compensation API is not available in sandbox. — [Intuit Payroll API get started](https://developer.intuit.com/app/developer/qbo/docs/workflows/integrate-with-payroll-api/get-started)
- [S] Paycor timecard import has no status API and still needs admin approval in Paygrid. — [Bindbee Paycor guide](https://bindbee.dev/blog/paycor-api)
- [V] Finch Benefits product is read-only and in Early Access. — [Finch Deductions overview](https://developer.tryfinch.com/products/deductions/Overview.md)

### Inferences
- Asynchronous and insert-only writes mean the HR product needs its own reconciliation (did the batch land, was it edited in the payroll system afterwards).

### Gaps
- No "beta" label was found on any time or pay-data write endpoint in the pages read; Merge and Gusto per-endpoint beta flags were not inspected.

## Key question 3 — What must the HR company still build?

### Takeaway
None of the options supplies US payroll semantics on the HR side; the product must hold US employee data, compute or classify hours, and map its codes to each customer's payroll codes.

### Cited Findings
- [V-s] Paylocity Punch Import sends raw punches and Paylocity's rules engine calculates hours; cost-centre data must match Paylocity's. — [Paylocity Punch Import overview](https://developer.paylocity.com/integrations/reference/punch-import-overview)
- [V-s] Paychex complex check creation requires mapping workers between the two systems, synchronising jobs, and handling overtime-exempt workers and line dating. — [Paychex ComplexCheck](https://developer.paychex.com/ComplexCheck)
- [V-s] Paychex: an earning must exist before a deduction can be placed on a check; recurring earnings are applied automatically. — [Paychex SimpleCheck](https://developer.paychex.com/SimpleCheck)
- [V] Gusto payroll sync takes approved time sheet data, so approval state must exist in the time product. — [Gusto app-integrations index](https://docs.gusto.com/app-integrations/llms.txt)
- [V] Merge requires reading `/meta` per linked account because required write fields differ by integration. — [Merge docs index](https://docs.merge.dev/llms.txt)
- [V] Kombo on Paylocity: phone formats are constrained and country is always US. — [Kombo Paylocity connector](https://docs.kombo.dev/hris/connectors/paylocity.md)

### Inferences
- Build list implied by the above: US employee fields (SSN handling, US address and state, FLSA exempt/non-exempt flag, pay frequency); hours-based leave balances and accrual; overtime classification where the payroll API takes hours by earning type (Gusto, Paychex) rather than raw punches (Paylocity, UKG WFM); a per-customer earnings-code and worker-ID mapping; pay-period calendars aligned to the provider's open period; batch status tracking and reconciliation; an employer-facing connect/authorise flow per provider.
- Where the payroll system computes overtime from punches, the HR product's own overtime figures are advisory only; where it takes classified hours, the HR product carries the calculation.

### Gaps
- No vendor page read lists the mandatory field set for employee creation on Gusto or Paychex; field-level requirements are unverified.

## Key question 4 — Which options explicitly accept or exclude non-US software companies?

### Takeaway
No page read in this round explicitly accepts or excludes a foreign-incorporated software company; the stated screens are security posture, customer track record and shared clients.

### Cited Findings
- [V] Gusto: restricted to approved partners; "early-stage products without a track record serving small businesses aren't currently supported"; no country rule on the introduction or security-review pages. — [Gusto introduction](https://docs.gusto.com/app-integrations/docs/introduction), [Gusto security review](https://docs.gusto.com/app-integrations/docs/security-review)
- [V] ADP's partner guide states no country rule; review is by Sales, Security and Legal. — [ADP Becoming a Partner (PDF)](https://marketplace.adp.com/downloads/becoming-a-partner.pdf)
- [V-s] ADP evaluates strategic fit including a substantial number of shared clients. — [ADP why marketplace](https://esi.apps.adp.com/en-US/pages/why-marketplace)
- [V] Paychex, Rippling and TriNet partner pages state no country eligibility. — [Paychex partner process](https://developer.paychex.com/partner), [Rippling developers](https://www.rippling.com/developers), [TriNet marketplace partners](https://www.trinet.com/partners/marketplace-partners)
- [V] Finch and Merge pricing pages state no country restriction. — [Finch pricing](https://www.tryfinch.com/pricing), [Merge pricing](https://www.merge.dev/pricing)
- [V] Remote says there is "no limit for companies" wanting to use its partner API. — [Remote API](https://remote.com/platform/remote-api)
- [S] Knit offers India data residency alongside US and EU. — [Nango comparison](https://nango.dev/blog/best-unified-apis-for-hris-ats-payroll/)

### Inferences
- The practical screens for a small Indian company with no US customers yet are Gusto's small-business track-record condition and ADP's shared-clients condition, not nationality.
- Security evidence (SOC 2 Type 2 or ISO 27001, penetration test) is requested by Gusto and ADP regardless of origin; Gusto accepts a controls description in place of a formal audit.

### Gaps
- Legal terms (Gusto developer terms, ADP Developer Participation Agreement, Paychex partner agreement, Intuit programme terms) were not read for contracting-entity, tax or export-control clauses.
- No first-hand account of a non-US HR vendor being approved or refused by any of these programmes was found.
