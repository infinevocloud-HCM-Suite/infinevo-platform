# Rent the payroll engine, own the product

| Question | Short answer |
|---|---|
| Can Infinevo sell its own US payroll on a third party, the way it would use a payment gateway? | Yes. An embedded payroll API sits behind our screens and brand and does the calculation, tax filing and money movement. |
| Are Gusto and ADP competitors or suppliers? | Both. Their payroll applications compete with us; their separate programmes "Gusto Embedded" and "ADP Embedded Payroll" are white-label supply. |
| Has an India-origin company done this? | Yes. Zoho Payroll US runs on Check. Which Zoho company signed the contract is not stated. |
| Can we keep our own Java pay run for the US? | Only with a tax engine plus a filing service. That route leaves tax deposits, the bank partner, registrations, notices and liability unsolved. |
| Biggest unknown | Whether any provider signs an India-incorporated partner. No vendor publishes a rule either way. |
| What is not verified | Every wholesale price and every launch time. None is public; the figures found are vendor or third-party claims. |

The third parties the founder is looking for exist, and they are a different thing from the Gusto or ADP application. Seven US vendors sell payroll as invisible infrastructure: our customer sees Infinevo's screens, and the vendor calculates pay, files the returns, deposits the tax and moves the money under its own licences. The vendor's name still appears in a few legal places, such as the employer's tax-agent authorisation. This is heavier than a payment gateway: access is by sales approval, each employer is verified, and we run first-line support. The second route, our own US pay run on a licensed tax engine, keeps the calculation in our code but hands us the hardest parts of payroll. The recommendation is the embedded route, after a US HRMS release that needs no vendor at all.

Marks used below: **(V)** read on the vendor's or regulator's own page; **(S)** secondary source or search summary only; **unconfirmed** means no source answered it.

## 1. Seven kinds of third party, only two are invisible

| Kind | Names | What our customer sees | What it is to Infinevo |
|---|---|---|---|
| Embedded payroll API | Check, Gusto Embedded, ADP Embedded Payroll, Salsa, Zeal, Everee, Rollfi | Our screens and brand | **Infrastructure**: calculates, files, moves money |
| Tax calculation engine | Symmetry, Vertex, BSI TaxFactory | Nothing | **Infrastructure**: calculates only |
| Filing service | TaxBandits, Greenshades, Aatrix, MasterTax, BSI ComplianceFactory | Nothing, or a signature step | Infrastructure for returns; deposits only in some |
| Payroll application | Gusto, ADP RUN and Workforce Now, Paychex Flex, Paylocity, Paycor, Rippling, QuickBooks Payroll | The vendor's own product | **Competitor**, or the system our HRMS customer keeps |
| Connector | Finch, Merge, Kombo, Knit; ADP API Central; Paychex and Gusto app-integration APIs | Their existing payroll provider | **Connector** to someone else's payroll, not our payroll |
| Global payroll, EOR, PEO | Deel, Remote, Oyster, Playroll | Our brand is possible | The platform is the payroll operator; we resell |
| Referral | Gusto, ADP | The vendor's own product | Commission only, no product |

Two names cause the confusion. ADP API Central is a paid add-on that lets an existing ADP client reach its own ADP data; it is not a way to sell payroll. Paychex's developer programme links our app to an employer's own Paychex Flex account, one client at a time, and no white-label programme appears on Paychex's pages.
- (V) [ADP Embedded Payroll](https://www.adp.com/what-we-offer/products/adp-embedded-payroll.md); (S) [ADP API Central listing](https://apps.adp.com/en-US/apps/410612/adp-api-central-for-adp-workforce-now/editions); (V) [Paychex partner process](https://developer.paychex.com/partner)

"Invisible" has limits. Zoho's US employers accept Check's service terms and authorise a tax reporting agent during set-up. Gusto requires every partner to show payroll receipts naming Gusto as the licensed money transmitter. Salsa states "Always your branding, no co-branding".
- (V) [Zoho authorisation steps](https://www.zoho.com/us/payroll/help/employer/getting-started/complete-service-terms-and-authorization-steps.html); (V) [Gusto payroll receipts](https://docs.gusto.com/embedded-payroll/docs/payroll-receipts); (V) [Salsa product](https://www.salsa.dev/product)

## 2. Family 1: embedded payroll does the whole job behind our screens

All seven calculate pay and tax, file returns, deposit tax, move money and produce W-2 and 1099 forms. What stays with us on all of them: screens, data sync, pricing, the customer relationship and first-line support.

| Vendor | Notable scope | Integration and sandbox | Java | Public price signal | Entry bar | Known status |
|---|---|---|---|---|---|---|
| **Check** | Most complete API; garnishments, benefits, tax notices; prebuilt components | REST, API key; no self-serve key described; features switched on by Check staff | None listed; plain REST | None wholesale; Check suggests partners charge $6–10 per employee a month (retail) | Sales and product review | Independent; powers Zoho, Wave, Homebase; last confirmed funding 2022 (S) |
| **Gusto Embedded** | Full payroll on Gusto's engine; prebuilt Flows and React kit | OAuth2; **self-serve demo account** | **Official Java client** | None; billed by active companies | "Not able to support all partner use cases"; commercial, security and implementation reviews before production | Part of Gusto; HiBob and Xero run on it (S) |
| **ADP Embedded Payroll** | Payroll on ADP's small-business engine; ADP specialists help with support | APIs, prebuilt screens, a kit of unstated language; sandbox not stated | Not stated | "Revenue share", no figures | Sales form | Active; partners FieldClock, Clover |
| **Salsa** | US and Canada; clean API for pushing hours and pay data | REST and GraphQL; keys provisioned by Salsa | None; OpenAPI file | None | **Customers must pay 25,000+ workers in total; no pre-launch platforms** | Series A in 2025 (S) |
| **Zeal** | Staffing and gig tilt; states partners are not liable for wrong tax calculations | REST, API key; sandbox through sales | None | None on site; secondary sources conflict | Sales contact | Independent; last confirmed funding 2021 (S) |
| **Everee** | Staffing and gig tilt; claims "long-term compliance liability" | REST; simulators usable before access; embed by iframe | Not covered | "Pricing calculator", no figure | Not stated | Independent, active |
| **Rollfi** | Payroll and benefits; a white-label tier needing no developer | Thin public docs | Not stated | None | Not stated; small named customers | Owned by Priority (Nasdaq: PRTH) since 2025 |

- (V) [Check docs](https://docs.checkhq.com/) · [Check sandbox and production](https://docs.checkhq.com/docs/understanding-sandbox-and-production-environments.md) · [Check pricing blog](https://www.checkhq.com/resources/blog/how-to-make-a-real-world-business-case-for-embedded-payroll)
- (V) [Gusto introduction](https://docs.gusto.com/embedded-payroll/docs/introduction) · [Gusto API clients](https://docs.gusto.com/embedded-payroll/docs/api-clients) · [Gusto getting started](https://docs.gusto.com/embedded-payroll/docs/getting-started.md)
- (V) [Salsa](https://www.salsa.dev/) · [Zeal FAQs](https://docs.zeal.com/docs/faqs.md) · [Everee](https://www.everee.com/paas/) · [Rollfi](https://www.rollfi.xyz/)

**What Infinevo must still build.**

| Piece | Why |
|---|---|
| US employer and employee onboarding | The provider needs legal name, federal tax ID, address, signatory, bank account and W-4 data from our flow. |
| Mapping of earnings, hours, leave and deductions to the provider's objects | Our HRMS owns attendance and leave; the provider's engine calculates. |
| Weekly, biweekly and twice-monthly pay periods | Our pay run allows monthly only. |
| Pay-run, payslip, tax-setup and W-2 screens | Or embed the provider's components. |
| Funding-failure handling, payroll receipts, webhook handling | Required by the providers' go-live reviews. |
| Pricing, billing and US-hours first-line support | Zoho runs its own US support line on top of Check. |

Trade-off: on this route the provider's engine is the record for US pay, so our own Java pay run does not calculate US pay.

Not verified: wholesale price, minimum commitment and contract length for every vendor; who pays for the provider's own errors (a comparison article says this sits in each partner contract); launch times, which range from "1–3 weeks" (Zeal, white label) to "two to four months" (an article's unsourced estimate for Check).
- (S) [FintechSpecs comparison](https://fintechspecs.com/blog/best-embedded-payroll-api-vertical-saas/)

## 3. Family 2: a tax engine calculates, a filing service files, nobody deposits

| Vendor | What it does | What stays with us | Integration and sandbox | Java | Public price signal | Entry bar | Known status |
|---|---|---|---|---|---|---|---|
| **Symmetry Tax Engine** | Tax amounts for federal, 50 states, local; add-ons for address-to-tax and W-4 forms | All records, year-to-date totals, filing, deposits | Hosted JSON API or on-premise kit; staging key at trial start | **Java kit (JDK 11+)** or plain HTTPS | Quote only; one buyer-data site shows a median near $75,000 a year from 3 purchases (S) | Trial, then licence | Owned by Gusto since 2021 |
| **Vertex Payroll Tax** | Same role, US and Canada | Same | REST; on-premise in Docker | Not stated | None | Sales | Active |
| **BSI TaxFactory** | Same role, plus garnishment calculation | Same | No public API docs | Not stated | None | Sales; enterprise focus | Active |
| **TaxBandits API** | E-files 941, 940, W-2, 1099, state returns, new-hire reports | Deposits; data; employer signature | REST, **free sandbox** | **Java kit** | Form 941 from $5.95 (retail) | Self-serve account | Active |
| **Greenshades** | Filing centre; managed tier executes state filings and payments | Customer "responsible for timely filing and payment"; most local returns | Cloud plus "open API" | Not stated | None | Sales | Active |
| **Aatrix (Sovos)** | Forms and payments built into 60+ payroll products | Not stated | Partner integration; modern REST unconfirmed | Not stated | About $10 per filing plus per-employee fee; pages disagree (S) | Partner agreement | Owned by Sovos since 2024 (S) |
| **MasterTax (ADP)** | Back-office software that schedules deposits, creates payments, produces returns | We operate it; agent authorisations, bank, liability | No public API reference | Not stated | None | Sales; sale to a non-US software firm unconfirmed | Owned by ADP |
| **BSI ComplianceFactory** | "Schedule, pay, assess and file" payroll taxes | Whose bank account pays is not stated | No public docs | Not stated | None | Sales | Active |

- (V) [Symmetry: what the engine is](https://docs.symmetry.com/ste/overview/what-is-the-ste.md) · [Symmetry platform support](https://docs.symmetry.com/ste/overview/platform-and-language-support.md) · [Symmetry pricing](https://www.symmetry.com/solutions/pricing) · (S) [Vendr](https://www.vendr.com/marketplace/symmetry-software)
- (V) [Vertex](https://www.vertexinc.com/solutions/products/vertex-payroll-tax) · [BSI TaxFactory](https://bsi.com/products-and-services/bsi-taxfactory) · [BSI ComplianceFactory](https://bsi.com/products-and-services/bsi-compliancefactory/)
- (V) [TaxBandits for payroll software](https://developer.taxbandits.com/api-for-payroll-software/) · [Greenshades](https://go.greenshades.com/platform/tax-and-compliance/managed-payroll-taxes) · [MasterTax](https://www.mastertax.com/) · (S) [Aatrix pricing](https://efile.aatrix.com/pages/public/PackagePricing.aspx)

Symmetry fits our stack best: one stateless call takes gross pay and returns tax lines, and the request carries no name, SSN or street address.
- (V) [Symmetry calculation call](https://docs.symmetry.com/ste/api-reference/payroll-calculations/compute-gross-to-net-tax-withholding)

Symmetry's docs now list a "Tax Filing" product, but its pages sit behind a login, so its scope is unconfirmed.
- (V) [Symmetry docs](https://docs.symmetry.com/)

No standalone garnishment API and no maintained open-source US tax library were found. No vendor in this family states whether it contracts with a non-US company.

**What Infinevo must still build:** a full US pay run (multi-frequency periods, calendar tax year, year-to-date storage, W-4 elections, work and home locations), the return data for the filer, and everything in the "unsolved" table in section 6.

## 4. Family 3: connectors reach a customer's existing payroll, they do not make ours

| Option | What it does | What stays with us | Integration and sandbox | Java | Public price signal | Entry bar | Known status |
|---|---|---|---|---|---|---|---|
| **Finch** | Reads employees and pay statements; writes deductions only | Hours cannot be pushed | One API; sandbox on all plans | Plain REST | $65 per connection a month, read-only, up to 15 connections | Self-serve starter | Active |
| **Merge** | Reads HR data; writes vary by provider | Payroll write unconfirmed | One API | Plain REST | $650 a month for 10 accounts, then $65 each | Self-serve | Active |
| **Kombo, Knit** | Read; create employee on some providers | Hours cannot be pushed | One API | Plain REST | Knit from $499 a month (S) | Demo-led | Knit offers India data residency (S) |
| **Gusto app integrations** | **Writes time sheets into the pay run**; employees, benefits | Approved hours, code mapping | REST; scopes set by Gusto | Plain REST | Not published | Pre-approval and security review; early-stage products without a small-business track record "aren't currently supported" | Active |
| **Paychex Flex APIs** | Worker sync; creates checks with hours and earnings | Mapping; each client approves in Flex | Sandbox after approval | Plain REST | Not published | Questionnaire, evaluation, demo | Active |
| **ADP Marketplace** | Pay-data input for RUN and Workforce Now | Mapping | Gated | Plain REST | Client pays API Central $2.00–2.50 per employee a month; listings conflict (S) | Sales, security and legal review; penetration test; "shared clients" screen | Active |
| **Paylocity, Square** | Punch or pay-entry import; Square timecard import | Mapping | Paylocity gated; Square standard developer platform | Plain REST | Not published | Paylocity partner approval | Active |
| **File export** | Customer uploads our CSV to its payroll | Per-customer code mapping; one-way, manual | None needed | n/a | Free | None | Works with ADP, Paychex, Gusto |

- (V) [Finch pricing](https://www.tryfinch.com/pricing) · [Finch deductions](https://developer.tryfinch.com/products/deductions/Overview.md) · [Merge pricing](https://www.merge.dev/pricing) · (S) [Nango comparison](https://nango.dev/blog/best-unified-apis-for-hris-ats-payroll/)
- (V) [Gusto app-integration introduction](https://docs.gusto.com/app-integrations/docs/introduction) · [Gusto security review](https://docs.gusto.com/app-integrations/docs/security-review) · [Paychex use cases](https://developer.paychex.com/use_cases) · [ADP becoming a partner](https://marketplace.adp.com/downloads/becoming-a-partner.pdf)

No unified API found can push hours or earnings into a US pay run. The real write exists only on direct provider APIs, at the cost of one approval and one security review per provider. Using a unified API does not remove Gusto's own review.

This family is for the HRMS-only product, where the customer keeps its payroll provider. It carries no tax or money liability.

**What Infinevo must still build:** US employee fields, hours-based leave, overtime classification, a per-customer mapping of earnings codes and worker IDs, pay-period calendars, batch status tracking and a connect screen per provider.

## 5. Family 4: global platforms resell their payroll; referral pays a commission

| Option | What it does | What stays with us | Integration and sandbox | Java | Public price signal | Entry bar | Known status |
|---|---|---|---|---|---|---|---|
| **Deel Embedded** | Deel's employment and payroll infrastructure under our brand; "Deel remains invisible" | Screens, resale | API; Deel engineer assigned; "typically about 180 days" (vendor claim) | Plain REST | Partner price not public | Partnership | Whether the API covers US domestic payroll is not stated |
| **Remote** | Embeds EOR, contractor and payroll; US offered as a PEO | Screens, resale | API; sandbox not stated | Plain REST | Not public | "No limit for companies" | Active |
| **Oyster, Playroll** | White-label global hiring | Resale | Not read on vendor pages | — | Not public | — | (S) only |
| **Referral: Gusto, ADP** | We send the customer; they sell their product | Nothing; no API access comes with it | None | n/a | Gusto up to $1,000 per referral (S); ADP rates dated and unconfirmed | Channel terms | Payment to a non-US referrer not checked |

- (V) [Deel Embedded](https://developer.deel.com/api/embedded/introduction) · [Remote API](https://remote.com/platform/remote-api) · (S) [Gusto on PartnerStack](https://market.partnerstack.com/page/gusto)

A PEO or EOR changes who the employer is, which is a different product from payroll software. This family does not answer the founder's question and is not recommended as the main route.

## 6. Two "we built it" routes: our screens on their engine, or our engine on their tables

| | Route A: embedded engine | Route B: own US pay run + tax engine + filing service |
|---|---|---|
| Who calculates | The provider | Our Java pay run calling Symmetry or Vertex |
| Returns and W-2 | Provider | Filing service, from data we prepare |
| Tax deposits | Provider | **Unsolved** |
| Wages and tax money movement | Provider, under its licences and banks | **Unsolved**; needs a US bank partner |
| Tax-agent role (Form 8655) | Provider | Us, or the employer signs each return itself |
| Brand | Ours, with the provider named on legal forms | Fully ours |
| Vendors to contract | One | Three or more |
| What we build | Product layer and support | Whole US pay engine plus a tax operations function |
| Java fit | Gusto has a client; others plain REST | Symmetry Java kit; TaxBandits Java kit |
| Proven by an India-origin company | Zoho on Check | No example found |
| Size | L | Much larger than L, and ongoing |

**What Route B leaves unsolved.**

| Gap | Why it stays open | Who could cover it |
|---|---|---|
| Deposit schedules and tax deposits | Engines calculate only; TaxBandits' pages show returns, no deposits | Employer's own federal and state logins, MasterTax, a managed service |
| Bank partner | Sending wage and tax debits for many employers makes us a "third-party sender" with our own yearly audit and risk assessment | A US bank that will sponsor us |
| Agent status | People named on an IRS e-file application must be US citizens or permanent residents (from an inspector-general report, not IRS.gov) | Employer signs with its own PIN through a transmitter |
| Employer state registrations | Not part of any engine or form filer | ADP Registration Services; CorpNet API (S) |
| Notices, amendments, penalty relief | A reporting agent cannot represent the employer in an examination | Managed services |
| Liability | The employer stays liable by IRS rule; our contract decides what we absorb | Nobody; it is a contract term |

- (V) [IRS third-party arrangements](https://www.irs.gov/node/54906) · (V) [Nacha third-party sender duties](https://www.nacha.org/rules/third-party-sender-roles-and-responsibilities) · (S) [Inspector-general report](https://www.oversight.gov/sites/default/files/documents/reports/2026-05/2026ier006fr.pdf) · (V) [TaxBandits 94x](https://developer.taxbandits.com/docs/94xForms/Overview)

**Recommendation: Route A.** Route B keeps our pay run and removes one middleman, but its open items are the ones that carry penalties and need US banking and US staff. A light Route B, where the employer deposits its own taxes, is buildable but is a weaker product than what US buyers get from any competitor. Whether a foreign company can be a reporting agent was not found on a primary source.

## 7. Heavier than a payment gateway at every step

| Step | Payment gateway | Embedded payroll |
|---|---|---|
| Getting keys | Self-serve | Sales-gated at most vendors; Gusto gives a demo account only |
| Review of us | Light | Commercial, product and security review |
| Each customer | Verified once | Tax ID matched to IRS records, bank verified, provider may decline the employer |
| Government forms | None | Form 8655 plus state forms per employer |
| Money risk | Refunds after the sale | Employer is debited before payday; a failed debit blocks payroll until recovered |
| Year-end | Little | W-2s and annual returns; we build the views |
| Support | Gateway helps the merchant | We run first-line support |

- (V) [Check funding failures](https://docs.checkhq.com/docs/resolve-funding-failures.md) · [Check company enrollment](https://docs.checkhq.com/docs/company-enrollment.md) · [Gusto company forms](https://docs.gusto.com/embedded-payroll/docs/company-form) · [Gusto onboarding](https://docs.gusto.com/embedded-payroll/docs/onboard-a-company) · [Zoho–Check terms](https://www.zoho.com/us/payroll/zchq-terms/)

Who pays when a debit fails after wages have gone out is not in any public document; it is a key term to ask about.

## 8. What an India-incorporated company must have in place

| Item | What the notes show | Status |
|---|---|---|
| US entity | No vendor says required or not required; Zoho shows a US support line; Keka's US office claim is unverified | **Unconfirmed; first question on every call** |
| Route to a US entity | An Indian company's US subsidiary falls under India's 2022 overseas-investment rules and must be reported | Needs an Indian CA and US counsel |
| Regulated roles | The provider is the reporting agent, the licensed money transmitter and the party in the bank chain | High confidence |
| Our own licence | None shown for a partner that never touches funds; Maine licenses "payroll processors" under a wide definition | Lawyer to read Maine |
| Security audit | Not law; Gusto asks for SOC 2 Type 2 or ISO 27001 plus a penetration test, or a controls questionnaire if none exists | Confirmed for Gusto only |
| Data location | No US federal or state ban found on processing US payroll data in India | Medium-high confidence |
| "Country of concern" rule | 28 CFR 202.601 lists China, Cuba, Iran, North Korea, Russia and Venezuela; **India is not listed** | Confirmed on the regulation text |
| Tax-preparer offshore rule (section 7216) | Covers income-tax returns, not payroll returns | Confirmed on the regulation text; lawyer to confirm for us |
| Every employer | Must be a US business with a US tax ID and US bank account | Follows from provider checks |

- (V) [Zoho–Check terms](https://www.zoho.com/us/payroll/zchq-terms/) · [Check licences](https://www.checkhq.com/company/licenses) · [Gusto security review](https://docs.gusto.com/app-integrations/docs/security-review)
- (V) [28 CFR 202.601](https://www.law.cornell.edu/cfr/text/28/202.601) · [26 CFR 301.7216-1](https://www.law.cornell.edu/cfr/text/26/301.7216-1) · [Maine statute](https://legislature.maine.gov/legis/statutes/10/title10sec1495-D.html) · [India overseas-investment rules](https://incometaxindia.gov.in/Documents/Provisions%20for%20NR/FEM-Overseas-Investment-Rules-2022.htm)

Hosting in India is lawful but is a sales obstacle. Zoho runs a US-hours support line with a US phone number, so expect providers and US buyers to ask where the data sits and who in India can see SSNs and bank details. Our decision D-18 fixes hosting to Central India and was written for Indian data, so US hosting is a new founder decision.

A first SOC 2 Type 2 is described by compliance vendors as taking six to twelve months; those sources sell the service, so get auditor quotes.
- (S) [Scrut](https://www.scrut.io/soc-2/cost-of-soc-2-audit) · [Drata](https://drata.com/grc-central/soc-2/how-much-does-a-soc-2-audit-cost)

US tax exposure of the Indian company (permanent establishment, state sales tax on software, revenue share paid to India) was only sketched and needs a cross-border tax adviser.

## 9. Recommended order of action

| Order | Action | Size | Depends on |
|---|---|---|---|
| 1 | First calls with Check, Gusto Embedded, ADP Embedded, Everee and Rollfi, using section 10 | S | Nothing |
| 2 | Open a Gusto demo account and try the Java client against our data model | S | Nothing |
| 3 | Decide the US entity question with an Indian CA and US counsel | S | Answers from 1 |
| 4 | Assemble the security pack: penetration test, policies, named contacts; start SOC 2 | M | Nothing |
| 5 | Decide where US tenant data is hosted | S | Answers from 1 |
| 6 | Make the platform country-aware, then ship US HRMS with file export | M, then L | Nothing external |
| 7 | Add one direct connector (Gusto time sheets) and a read connector (Finch) if customers ask | M | 4, 6 |
| 8 | Build US payroll on the signed embedded provider | L | 1, 3, 4, 6 |
| 9 | Revisit Route B only at scale, starting with a Symmetry trial | L+ | 8 |

Check comes first because it is the proven route for an India-origin company. Gusto is the only one a Java team can test today, but Gusto warns partners to seek pre-approval "before getting too deep into your build". Salsa is left out until our customers pay 25,000 workers. Zeal's customers are almost all staffing and gig platforms, so its fit is weak.

Steps 1 to 5 cost little and decide everything after them, so they run before any US payroll code is written.

## 10. Questions for the first call

| Ask | Exact questions |
|---|---|
| **Every embedded provider** | Will you sign an India-incorporated company, or must the partner be a US entity? |
| | May our staff in India call the API and see SSN and bank data, and must US data be stored in the US? |
| | Do you require SOC 2 from us, by when, and is ISO 27001 or a questionnaire accepted meanwhile? |
| | What are the wholesale price, monthly minimum, set-up fee and contract length? |
| | Who bears the loss when an employer's debit fails after wages are paid? |
| | Who pays penalties caused by your error, and is that in the partner contract? |
| | Will you state in writing that we are not a third-party sender and need no licence, including in Maine? |
| | Can we get a sandbox key before signing? |
| | Which pay frequencies and states are live in production? |
| | Can we push hours, earnings, leave and deductions from our HRMS, and can we rebuild every screen ourselves? |
| | Who answers the employer and the employee: us, you, or both? |
| | What is your smallest live partner, and do you take a partner with no US customers yet? |
| **Check** | Which Zoho entity is your contracting party, and can we follow the same structure? |
| **Gusto Embedded** | Which partner use cases do you not support, and can we have pre-approval before we build? |
| **ADP Embedded** | What is the revenue share, and what are the kit's languages? |
| **Symmetry, Vertex** | Do you license to a non-US company, where is the hosted API run, and what do you retain from each call? |
| | Do you calculate garnishments, and what exactly does Symmetry Tax Filing do: returns only, or deposits too? |
| **TaxBandits, Greenshades** | Can a payment ride with the return, and can you make deposits? |
| | Can the employer sign with its own PIN so that we need no IRS agent status? |
| **Finch, Merge** | Which US providers are automated and which are assisted, and can any take an hours write? |
| **Gusto, Paychex, ADP connector programmes** | Do you accept a non-US partner with no shared clients yet, and what does the review need? |
| **Deel, Remote** | Does your embedded API cover US domestic payroll, and who is the employer of record? |

## 11. What changed from the earlier report

| Earlier report said | The new notes show |
|---|---|
| Zoho built its own US payroll | Zoho Payroll US runs on Check; Check files the taxes and moves the money. |
| India's position under the DOJ data rule was unverified | India is not on the list of six countries of concern. |
| Salsa was a candidate | Salsa publicly declines platforms below 25,000 workers, including pre-launch ones. |
| Finch costs $50 or $65 | Finch's own page shows $65 per connection a month, read-only, capped at 15 connections. |
| Unified API or file export were the only ways to send hours | Direct provider APIs (Gusto, Paychex, ADP, Paylocity, Square) accept hours; no unified API found does. |
| On the engine route Infinevo becomes the reporting agent | Not necessarily: the employer can sign its own return through a transmitter. Agent status also has a US citizen or resident condition for the people named. |
| No engine vendor files or deposits | Still true of the engines themselves; BSI and ADP sell separate filing-and-payment products, and Symmetry lists a gated Tax Filing product. |
| Security audit expectations were unsourced | Gusto's review inputs are now read from its own page. |
| Embedded shortlist was five names | Everee and Rollfi are added; ADP API Central and Paychex are confirmed as connectors, not embedded payroll. |

The earlier recommendation stands: US HRMS first, then embedded payroll.

## Conclusion

The founder's instinct is right, with one correction: the gateway-style supplier exists, but it is a division of the payroll industry, not a neutral utility. Gusto and ADP sell the infrastructure and the competing application from the same house, and Symmetry, the best-documented tax engine, belongs to Gusto. Independence from competitors is therefore a reason to weigh Check and Everee, not only a matter of features.

The Zoho precedent turns the main unknown from "is this possible for an Indian company" into "on what terms". Those terms — entity, data access from India, audit deadline, loss on failed debits — are all contract answers that no public page gives, which is why five short calls are worth more than any further desk research.
