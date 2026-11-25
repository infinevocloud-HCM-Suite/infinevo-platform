# Foreign (India-incorporated) software partner offering US payroll through a third-party provider — eligibility and obligations

Research date: 2026-11-24. Facts only, not legal advice.

Source tags used on every finding: **[P]** primary (statute, regulator, vendor's own terms or docs) · **[S]** secondary (law firm, vendor blog, trade press, compliance vendor) · **[U]** unconfirmed (search summary only, page not opened, or sources conflict).

| Question | Short answer | Confidence |
|---|---|---|
| Do embedded payroll vendors require a US entity? | No public vendor page says yes or no. Access is sales-gated at Check, Salsa and Zeal. Must be asked on the first call. | Low — gap |
| Has an India-origin company done it? | Yes. Zoho Payroll US runs on Check; Check files the taxes and moves the money. | High [P] |
| Who is the regulated party? | The provider: reporting agent (Form 8655), money-transmitter licensee, ACH originator relationship. The employer stays liable for its taxes. | High [P] |
| Does the software partner need a state licence? | Not shown anywhere for a partner that never touches funds; Maine's definition is wide and needs a lawyer's read. | Medium |
| SOC 2 needed? | Not law. Gusto asks for SOC 2 Type 2 / ISO 27001 / pen-test reports, or a questionnaire if none exist. | High [P] for Gusto |
| Can US payroll data be processed from India? | No federal or state ban found. India is not a "country of concern" under 28 CFR 202. Section 7216 covers income-tax returns, not payroll returns. Limits come from contracts. | Medium-high |
| Same as a payment gateway? | No. Heavier: per-employer verification, tax-agency authorisations, funding-failure handling, year-end filings, product and security review before go-live. | High |

## 1. Entity: do embedded payroll and tax-engine vendors require a US legal entity, and how do Indian companies structure it?

### Takeaway
No vendor publishes a rule that the platform partner must be a US entity, and none publishes that a foreign one is accepted; every embedded provider is sales-gated, so this is the first question for the vendor. The one clear India-origin precedent is Zoho Payroll US, which is built on Check — Check does the tax filing and money movement, Zoho does the product and support.

### Cited Findings
- [P] Zoho Payroll's US terms say Check Technologies Inc provides the payroll processing, tax filing and payment services through an integration, and that funds move through "Check Payments LLC, a licensed money transmitter" or custodial accounts at partner banks. — [Zoho Payroll US – Check terms](https://www.zoho.com/us/payroll/zchq-terms/)
- [P] Zoho's knowledge base: "Check is a payroll-as-a-service API provider"; through the partnership Zoho offers payroll calculation, "filing and paying payroll taxes electronically", direct deposit and garnishments. — [Zoho KB – About Check](https://www.zoho.com/us/payroll/kb/employer/general/about-check.md)
- [P] Zoho's employer setup requires accepting the Check Payroll User Service Terms, authorising a payment service provider and authorising a tax reporting agent. — [Zoho – service terms and authorization steps](https://www.zoho.com/us/payroll/help/employer/getting-started/complete-service-terms-and-authorization-steps.html)
- [P] Zoho itself handles support for Zoho Payroll US (Mon–Fri 9am–7pm ET, US phone number); complaints about Check's services go to Check. — [Zoho Payroll US – Check terms](https://www.zoho.com/us/payroll/zchq-terms/)
- [S] Zoho Payroll launched in the US around December 2024 and is offered in all 50 states. — [CPA Practice Advisor, Dec 2024](https://www.cpapracticeadvisor.com/2024/12/13/zoho-releases-new-payroll-solution-in-the-u-s/152935/); [Zoho blog](https://www.zoho.com/blog/payroll/payroll-software-for-usa.html)
- [U] The Zoho terms name only "Zoho" as the contracting party; which Zoho entity (Zoho Corporation, US, or Zoho Corporation Pvt Ltd, India) signed with Check is not stated on the pages read. — [Zoho Payroll US – Check terms](https://www.zoho.com/us/payroll/zchq-terms/)
- [P] Check's public docs contain no statement that a partner must be a US company and no statement on data location; the only "U.S." reference is to the employer's account at a "U.S. bank". — [Check docs index](https://docs.checkhq.com/llms.txt)
- [P] Zeal: API access requires a key and "if you haven't received sandbox or production access please contact sales". — [Zeal docs FAQ](https://docs.zeal.com/docs/faqs)
- [S] Salsa issues sandbox and production keys only to approved platform partners (third-party API catalogue, not Salsa's own page). — [apis.io – Salsa](https://apis.io/providers/salsa/)
- [P] Gusto's employer-side API validates that the employer's bank routing number is a valid US routing number. — [Gusto Embedded – Onboard a company](https://docs.gusto.com/embedded-payroll/docs/onboard-a-company)
- [P] Keka (India) markets a US payroll product; its US page footer reads "Keka Technologies" and the page does not say whether it files taxes itself or through a partner. — [Keka US info page](https://www.keka.com/us/llm-info)
- [U] Keka's marketing claims 50-state payroll compliance and a US headquarters in Portland, Oregon with offices in New York and San Francisco (search summary of Keka's own landing pages; not verified against a filing). — [Keka US payroll page](https://www.keka.com/lp/us/payroll)
- [P] Stripe Atlas's signup doc says that if a founder lives in India "Atlas is not suitable for you" without first taking advice on India's overseas-investment (ODI) rules; Stripe's Indian founder guide describes a US C-corp owned by Indian LLPs as one route. — [Stripe Atlas signup](https://docs.stripe.com/atlas/signup); [Stripe – Indian founder guide](https://docs.stripe.com/atlas/indian-founder-guide)
- [P] Stripe Atlas costs a one-off US$500; without an SSN or ITIN the EIN "generally takes 10–25 business days" to appear in the dashboard and 6–8 weeks or longer by mail. — [Stripe Atlas signup](https://docs.stripe.com/atlas/signup)
- [S] The IRS online EIN application requires an SSN or ITIN; founders without one file Form SS-4 by fax (response in about four business days), by mail (four to five weeks) or by phone on the IRS international line. — [Stripe – non-US founders and US bank accounts](https://stripe.com/resources/more/can-non-us-founders-open-a-us-business-bank-account)
- [S] Typical bank-opening documents for foreign founders: formation documents, EIN confirmation letter (CP575 or 147C), bylaws, passports, proof of address, business description, expected deposits; some banks reject registered-agent addresses and virtual mailboxes. — [Stripe – non-US founders and US bank accounts](https://stripe.com/resources/more/can-non-us-founders-open-a-us-business-bank-account)
- [U] Mercury requires the company to be formed in the US; a March 2026 article says Mercury's eligibility page now requires a principal place of business that is not a registered-agent address, PO box or mailbox, and that India is not on its prohibited-country list. Mercury's own page returned HTTP 403 and was not read. — [Mercury eligibility (not readable)](https://support.mercury.com/hc/en-us/articles/28770467511060); [Chittorgarh, 2026](https://www.chittorgarh.com/article/us-llc-bank-account-for-indian-founders-why-youre-getting-rejected-and-what-works-in-2026/653/)
- [P] An Indian company's investment in a US subsidiary is governed by the Foreign Exchange Management (Overseas Investment) Rules, 2022 and RBI's Overseas Investment Directions, 2022. — [Income Tax India – OI Rules 2022](https://incometaxindia.gov.in/Documents/Provisions%20for%20NR/FEM-Overseas-Investment-Rules-2022.htm); [RBI Master Direction](https://www.rbi.org.in/scripts/BS_ViewMasDirections.aspx?id=12381)
- [S] A Delaware incorporation controlled from India must be reported even at zero capital, and funds are remitted only after the AD bank assigns a Unique Identification Number. — [Jurigram – ODI rules for a US subsidiary](https://jurigram.com/blog/corporate-law/odi-rules-for-an-indian-company-investing-in-a-us-subsidiary)

### Inferences
- A US entity is very likely needed in practice even if no vendor says so in public: the provider's contract, US bank account for revenue share, and US buyers' contracting preferences all point that way. Zoho and Keka both present a US face.
- The cheaper structure for an existing Indian company is a wholly owned US subsidiary under the ODI route, not a parent "flip"; a flip changes the shareholders' holding and has Indian tax consequences that were not researched here.
- Realistic elapsed time for entity + EIN + bank account from India is roughly 4–10 weeks, driven by the EIN and bank onboarding, not by Delaware filing.

### Gaps
- No vendor partner agreement (Check, Gusto Embedded, Salsa, Zeal, ADP) was publicly readable; Gusto's Embedded terms page (flows.gusto.com/terms) and API policy returned 403/404. Entity requirement must be confirmed by each vendor.
- Tax-engine-only vendors (Symmetry, Vertex) and data aggregators (Finch) were not checked for entity rules. A calculation-only engine moves no money and files nothing, so the entity question is likely lighter — unconfirmed.
- Nothing public found on how greytHR, Darwinbox, RazorpayX Payroll, Multiplier, Deel, Remote or Rippling structured US payroll; not researched in depth.
- Flip versus subsidiary: Indian capital-gains, ODI limits, cost and transfer-pricing set-up need an Indian CA and US counsel.
- Brex rules for foreign founders not checked.

## 2. Regulatory role: who is the regulated party, state payroll-processor licensing, money transmission, Nacha

### Takeaway
In the embedded model the provider holds the regulated roles — reporting agent on Form 8655, money-transmitter licensee, and the ACH relationship — while the employer stays liable for its own taxes. State money-transmission law is moving toward exempting payroll processors (Nevada 2025, Maryland and Delaware 2026), but Maine still licenses payroll processors as such, and whether a software partner that never holds funds falls inside any of these definitions is a lawyer question.

### Cited Findings
**Reporting agent**
- [P] Gusto Embedded: "A new company will have to complete the Direct Deposit Authorization and Form 8655 at minimum, plus any State-specific authorization forms." — [Gusto Embedded – Company forms](https://docs.gusto.com/embedded-payroll/docs/company-form)
- [P] Rev. Proc. 2012-32 sets the requirements for completing and submitting Form 8655 (Reporting Agent Authorization). — [IRS Rev. Proc. 2012-32](https://www.irs.gov/pub/irs-drop/rp-12-32.pdf)
- [S] Form 8655 does not move federal employment-tax liability to the agent; the employer remains liable. — [Paychex – Form 8655](https://www.paychex.com/articles/payroll-taxes/form-8655)
- [P] Zoho's terms: Zoho and Check are not obliged to pay wages or taxes or file returns if the employer has not remitted funds on time; the employer remains responsible for its obligations under law. — [Zoho Payroll US – Check terms](https://www.zoho.com/us/payroll/zchq-terms/)

**Money transmission — who holds the licence**
- [P] Gusto: processing payroll "requires a Money Transmitter License (MTL)" and "The licensee for the MTL is Gusto"; "All partners must implement Payroll Receipts" showing the licensee's name, address, phone and licence links. — [Gusto Embedded – Payroll receipts](https://docs.gusto.com/embedded-payroll/docs/payroll-receipts)
- [P] Check Payments LLC (NMLS #2103307), a subsidiary of Check Technologies, Inc., holds money-transmitter licences in 40 states and DC; "In all other U.S. states, Check operates under statutory or regulatory exemptions from licensing requirements." — [Check – Licenses](https://www.checkhq.com/company/licenses)

**Money transmission — state position and 2025–2026 changes**
- [P] Nevada A.B. 430 exempts payroll processing from the state's MTMA, effective 1 Oct 2025. — [CSBS MTMA legislative update, Aug 2026](https://www.csbs.org/mtma-legislative-update-august-2026)
- [P] Maryland S.B. 261 exempts agents of the payor providing payroll processing, effective 1 Oct 2026. — [CSBS MTMA legislative update, Aug 2026](https://www.csbs.org/mtma-legislative-update-august-2026); [Maryland OFR advisory](https://labor.maryland.gov/finance/advisories/advisory-ind-exemption-money-transmitter-licensure.shtml)
- [P] Delaware S.B. 18 (signed 6 July 2026) exempts a payroll processor acting as agent of the payor if there is a written agreement with the employer, the employer holds the processor out to employees as its payroll provider, and the employer's obligation to the employee is not extinguished if the processor fails to pay; exempt licensees "may allow their current license to expire on December 31, 2026". Implementation is one year from enactment or on publication of final regulations. — [Delaware State Bank Commissioner notice, 20 Aug 2026](https://banking.delaware.gov/wp-content/uploads/sites/73/2026/08/Payroll-Processor-Notice-Aug-21-2026.pdf)
- [P] Minnesota S.F. 4097 exempts payroll processing providers from the MTMA (effective 1 Aug 2024); Iowa H.F. 2262 gives a limited payroll-processor exemption; Nebraska L.B. 717 exempts small payroll processors (fewer than 20 employees or serving fewer than 50 Nebraska employees), effective 26 Feb 2026. — [CSBS MTMA legislative update, Aug 2026](https://www.csbs.org/mtma-legislative-update-august-2026)
- [P] Michigan had two competing 2026 bills: H.B. 5544 (MTMA including payroll processors) and S.B. 835 (MTMA exempting payroll processors); outcome not shown. — [CSBS MTMA legislative update, Aug 2026](https://www.csbs.org/mtma-legislative-update-august-2026)
- [P] Full MTMA enactments listed by CSBS include Texas (effective 1 Sep 2023), Connecticut (partial, 1 Oct 2024), Massachusetts (1 Jan 2026), Illinois (1 Jan 2026), Virginia (1 July 2026), Louisiana (1 July 2026), Oklahoma (1 Nov 2026). — [CSBS MTMA legislative update, Aug 2026](https://www.csbs.org/mtma-legislative-update-august-2026)
- [P] California treats payroll processing under an agent-of-payee style exemption in its Money Transmission Act (DFPI opinion letters). — [DFPI – exemptions for agent of payee and payroll processing](https://dfpi.ca.gov/rules-enforcement/laws-and-regulations/opinion-letters-by-law-subject/exemptions-for-agent-of-payee-and-payroll-processing-service/)
- [S] California, Ohio, North Carolina and Washington expressly exclude payroll processing from money transmission; most states have not expressly addressed the point. — [Troutman Pepper transcript, 2024](https://www.troutman.com/wp-content/uploads/2024/07/Transcript_CFP_Navigating_New_Compliance_Challenges.pdf); [Morrison Foerster, 2019](https://www.mofo.com/resources/insights/191017-payroll-money-transmission)
- [U] A vendor site reports 31 states have enacted the MTMA in whole or part as of mid-2026; figure not verified against CSBS. — search summary only, see [CSBS update](https://www.csbs.org/mtma-legislative-update-august-2026)

**State payroll-processor licensing**
- [P] Maine licenses payroll processors under 10 M.R.S. Chapter 222: "Payroll processor" means a person that provides payroll processing service for one or more employers, and a licence is required before providing services in Maine; applications run through NMLS; surety bond or letter of credit required. — [Maine statute 10 M.R.S. §1495-D](https://legislature.maine.gov/legis/statutes/10/title10sec1495-D.html); [Maine BCCP – payroll processor](https://www.maine.gov/pfr/consumercredit/industry/licensing/payroll_processor.htm) (regulator page returned 403; content from search summary)
- [P] Maine offers a "Restricted Payroll Processor License" and "Full and Limited" licences; non-bank processors are licensed by the Bureau of Consumer Credit Protection. — [Maine BFI – payroll processors](https://www.maine.gov/pfr/financialinstitutions/industry-tools/payroll-processors)

**Nacha**
- [P] A Third-Party Sender (TPS) "acts on behalf of an Originator to transmit entries through an Originating Depository Financial Institution" without a direct Originator–ODFI agreement; a nested TPS "originates through another TPS rather than directly with" an ODFI. — [Nacha – Third parties in the ACH Network](https://nacha.org/content/third-parties-ach-network)
- [P] A TPS must run an annual Rules Compliance Audit and a risk assessment; it "cannot rely on a Rules Compliance Audit or a Risk Assessment completed by another TPS in a chain". ODFIs must identify in Nacha's Risk Management Portal which TPS customers allow nested TPS relationships. — [Nacha – TPS roles and responsibilities](https://www.nacha.org/rules/third-party-sender-roles-and-responsibilities); [Nacha – TPS registration](https://www.nacha.org/rules/third-party-sender-registration)
- [P] From 20 March 2026 originators of wage credits to consumer accounts must use the company entry description "PAYROLL". — [Nacha – company entry descriptions](https://www.nacha.org/rules/risk-management-topics-company-entry-descriptions)
- [P] In Zoho's Check arrangement the bank pulls funds from the employer's account by ACH into a custodial account and pays wages and taxes from there; the employer signs the debit authorisation with Check. — [Zoho Payroll US – Check terms](https://www.zoho.com/us/payroll/zchq-terms/); [Check – Employer debit authorization](https://www.checkhq.com/company/employer-debit-authorization)

### Inferences
- A partner that only sends payroll instructions by API, never holds employer funds and has no origination agreement with the provider is not a TPS or nested TPS on the Nacha definitions; the provider (or its bank) is the party in the ACH chain. This is the structure Zoho's terms describe. Vendor must confirm in the contract.
- The provider's licences and exemptions cover the provider. Nothing read says they extend to, or are needed by, the software partner.
- Maine's definition ("provides payroll processing service") is broad enough that a partner branding the service as its own should ask counsel whether it or only the underlying provider needs the Maine licence.
- The 2025–2026 trend (Nevada, Maryland, Delaware, Nebraska) is toward exempting payroll processors from money-transmitter licensing; this lowers the provider's burden and does not create one for the partner.

### Gaps
- No source found for payroll-processor-specific registration in Nevada, Texas, Minnesota or Connecticut beyond their money-transmission statutes; one search returned only Maine. Unconfirmed whether any other state has a Maine-style payroll-processor licence.
- No vendor document read states in terms "the partner is not a third-party sender" or "the partner needs no licence". Ask each vendor for its written regulatory-roles statement.
- Maine bond amounts and fee schedule not retrieved (regulator page blocked).
- Whether a partner earning revenue share on payroll volume changes any state's analysis — lawyer to confirm.

## 3. Security and audit expectations (SOC 2 / SOC 1)

### Takeaway
SOC reports are a contract and sales requirement, not a legal one. Gusto's published security review asks for current audits (SOC 2 Type 2, ISO 27001, PCI DSS) and pen-test reports, with a questionnaire fallback if the partner has no formal audit; a first SOC 2 Type II typically takes six to twelve months and tens of thousands of dollars.

### Cited Findings
- [P] Gusto's security review asks for "All current security related audits and supporting documentation" (examples: SOC 2 Type 2, ISO 27001 + SOA, PCI DSS), "Penetration test report(s)", security and privacy policies (incident response, privacy, physical security, retention/destruction), and named security and incident-response contacts. — [Gusto – Security review](https://docs.gusto.com/app-integrations/docs/security-review)
- [P] Without a formal audit, Gusto's questionnaire covers background checks, MFA, encryption at rest and in transit, access control, vulnerability scanning and remediation, third-party vendor management, rate-limit protections and API security. — [Gusto – Security review](https://docs.gusto.com/app-integrations/docs/security-review)
- [S] Gusto's review runs through VISO Trust; Partnerships reviews the application in 1–2 weeks, the security review takes about 2–4 weeks after the questionnaire is submitted; holders of SOC 2 Type 2, ISO 27001 or PCI certificates get a simplified questionnaire. (Described by Finch for the app-integration route; a direct Embedded partnership may differ.) — [Finch – Gusto security reviews](https://developer.tryfinch.com/implementation-guide/Integration-Preparation/Security-Reviews)
- [P] Gusto itself holds SOC reports and gives customers access to SOC reports and bridge letters on request. — [Gusto – request SOC reports](https://support.gusto.com/article/105983845100000/Request-access-to-SOC-reports-and-bridge-letters)
- [S] Check's own blog lists SOC 2 Type II among the things to look for in an embedded payroll provider (marketing content). — [Check blog](https://www.checkhq.com/resources/blog/3-things-to-look-for-in-an-embedded-payroll-partner)
- [S] SOC 2 "is not a government-issued certification or a universal legal requirement". — [Intercert](https://www.intercert.com/blogs/soc-2-vs-pci-dss-payment-platforms-bank-partnerships)
- [S] Cost of a first SOC 2 Type II (compliance-vendor figures, 2026): audit fee about $12,000–$20,000 for small companies per one source, $12,000–$60,000 per another; total first-year spend $30,000–$80,000+ for a 50–100 person SaaS company; audit fee is 30–40% of total; readiness assessment $10,000–$15,000; automation platform $10,000–$20,000 a year. — [Scrut, 2026](https://www.scrut.io/soc-2/cost-of-soc-2-audit); [Drata](https://drata.com/grc-central/soc-2/how-much-does-a-soc-2-audit-cost); [Bright Defense, 2026](https://www.brightdefense.com/blog/soc-2-audit-costs/)
- [S] Timeline: six to twelve months from a standing start; observation window usually six months, three months is the technical minimum and "rarely accepted by enterprise customers". — [Beancount.io, July 2026](https://beancount.io/blog/2026/07/14/soc-2-type-ii-small-saas-audit-cost-guide); [soc2auditors.org](https://soc2auditors.org/insights/soc-2-audit-cost-for-startups/)

### Inferences
- In an embedded arrangement SOC 1 Type II (controls relevant to customers' financial reporting) is primarily the payroll provider's report, because the provider calculates, files and pays. The partner is asked for SOC 2. US mid-market buyers' auditors may still ask the partner for a SOC 1 or for the provider's SOC 1 plus a bridge letter — unconfirmed, no source read.
- A small partner with no SOC 2 can start with Gusto-style questionnaire plus a recent pen test, and should expect a contractual deadline to obtain SOC 2; the deadline itself is not published anywhere read.
- All cost figures come from compliance vendors with an interest in the number; treat as ranges and get auditor quotes.

### Gaps
- Check, Salsa, Zeal and ADP partner security requirements are not public (Check's product-review help page is password-protected).
- No primary source (AICPA) was read on who needs SOC 1 versus SOC 2.
- No source found on what US mid-market buyers ask HR/payroll vendors in security questionnaires; common knowledge only, not cited.
- Whether an existing ISO 27001 certificate is accepted in place of SOC 2 by Check — Gusto lists it as acceptable; others unconfirmed.

## 4. Data location and access from India

### Takeaway
No US federal or state rule was found that bars a private company from storing or processing US employees' payroll data in India. India is not a "country of concern" under the DOJ bulk-data rule, section 7216 covers income-tax returns only, and Publication 1075 covers data received from the IRS by agencies. The practical limits are contractual: provider terms and US customers' data-residency clauses.

### Cited Findings
- [P] 28 CFR §202.601 lists the countries of concern as China, Cuba, Iran, North Korea, Russia and Venezuela. India is not listed. — [28 CFR 202.601 (Cornell LII)](https://www.law.cornell.edu/cfr/text/28/202.601)
- [P] The rule implements Executive Order 14117; final rule published 8 Jan 2025; DOJ compliance guide 11 Apr 2025, FAQs updated 24 Sep 2025. — [Federal Register, 8 Jan 2025](https://www.federalregister.gov/documents/2025/01/08/2024-31486/preventing-access-to-us-sensitive-personal-data-and-government-related-data-by-countries-of-concern); [DOJ Data Security Program](https://www.justice.gov/nsd/data-security)
- [P] Treas. Reg. §301.7216-1(b)(1): "The term tax return means any return (or amended return) of income tax imposed by chapter 1 of the Internal Revenue Code." Employment-tax returns (Form 941/940, chapters 21–24) are outside that definition. — [26 CFR 301.7216-1 (Cornell LII)](https://www.law.cornell.edu/cfr/text/26/301.7216-1)
- [P] Where 7216 does apply, taxpayer consent is required before disclosure to a preparer outside the US, and a US preparer generally may not send the taxpayer's SSN overseas even with consent. — [26 CFR 301.7216-3 (Cornell LII)](https://www.law.cornell.edu/cfr/text/26/301.7216-3); [Rev. Proc. 2008-35](https://irs.gov/pub/irs-drop/rp-08-35.pdf)
- [P] IRS Publication 1075 guides "recipient agencies, agents, or contractors" protecting Federal Tax Information received from the IRS by federal, state and local agencies. — [IRS Safeguards Program](https://www.irs.gov/privacy-disclosure/safeguards-program)
- [S] Outside California, comprehensive state privacy laws generally do not apply to personal information processed in the employment context; the CCPA does cover employee data. — [JD Supra – state privacy law contracting requirements, 2026](https://www.jdsupra.com/legalnews/the-paper-trail-state-privacy-law-5620636/)
- [S] CCPA regulations §7051 require specific terms in every service-provider contract (no selling or sharing, named business purposes, flow-down to subcontractors); under §7050(e) a vendor without a compliant contract is not a "service provider". Statutory base is Cal. Civ. Code §1798.100(d). — [CCPA regs §7051](https://prighter.com/resources/laws/ccpa-regulations/sections/7051); [CCPA statute (CPPA)](https://cppa.ca.gov/regulations/pdf/ccpa_statute_eff_20260101.pdf)
- [P] Zoho's terms: the employer authorises Zoho to disclose employer and employee data to Check; Check may share it with banks and retains it as law and tax filing require. — [Zoho Payroll US – Check terms](https://www.zoho.com/us/payroll/zchq-terms/)
- [P] Check's and Gusto's public developer docs say nothing on partner data location or offshore API access. — [Check docs index](https://docs.checkhq.com/llms.txt); [Gusto – Security review](https://docs.gusto.com/app-integrations/docs/security-review)

### Inferences
- An embedded partner is not the reporting agent and is not preparing income-tax returns, so section 7216's offshore-consent and SSN rules do not reach it on the regulation's own definition. A lawyer should confirm, particularly if the product ever touches W-2 data in a way that feeds an income-tax product.
- Publication 1075 does not apply to payroll data a private vendor collects from employers; it applies to FTI that agencies receive from the IRS.
- The DOJ rule does not restrict processing in India. It would matter only if the Indian company's staff, vendors or investors were "covered persons" tied to a listed country.
- Hosting on Azure India is legal but is a sales obstacle: Zoho runs a US data centre and US-hours support line. Expect US buyers and possibly the provider to ask for US-region hosting and to ask who in India can see SSNs and bank details.

### Gaps
- Publication 1075's offshore restriction text was not retrieved; the Safeguards page read does not mention offshore. Unconfirmed either way, and not relevant unless the customer is a government agency.
- No state SSN statute restricting offshore processing was found, but state SSN-protection and breach-notification laws (all 50 states) were not read individually. Breach notification will apply regardless of where data sits; cite-level confirmation needed.
- No provider contract was read for offshore-access clauses. Ask each vendor: may partner staff in India access the API and SSN/bank data; must data be stored in the US.
- What US customers write into contracts (US residency, US-based support, background-checked staff) — no source read; treat as an expectation to test with prospects.
- State and federal government-contractor rules (some states bar offshore handling of state data) not researched; relevant only for public-sector customers.

## 5. Tax and commercial exposure of the Indian company

### Takeaway
Headline only: US federal income tax turns on whether the Indian company has a US trade or business and, under the India–US treaty, a permanent establishment; state sales tax on SaaS turns on economic nexus, which applies to foreign sellers the same way and has no treaty protection. A US subsidiary moves US revenue and US filings into the subsidiary and adds transfer pricing between the two companies. All of this needs a cross-border tax adviser.

### Cited Findings
- [P] Under the India–US treaty a "permanent establishment" is "a fixed place of business through which the business of an enterprise is wholly or partly carried on" (Article 5). — [IRS – India technical explanation](https://www.stayexempt.irs.gov/pub/irs-trty/inditech.pdf)
- [P] A foreign corporation should file a protective Form 1120-F if it concludes it has no US tax liability under a treaty (for example, income not attributable to a US permanent establishment); not filing risks losing deductions and credits against effectively connected income. — [IRS – Form 1120-F filing responsibilities](https://www.irs.gov/businesses/international-businesses/foreign-corporation-form-1120-f-filing-responsibilities)
- [S] When US trade-or-business status is uncertain, filing a protective 1120-F reporting no income is generally advisable. — [KPMG, 2022](https://home.kpmg/kpmg-us/content/dam/kpmg/pdf/2022/irs-focus-foreign-corp-returns-itr-0922.pdf)
- [S] SaaS is taxable in some form in roughly 22–26 states plus DC depending on the source (TaxCloud: 24 + DC as of Oct 2026; Kintsugi: 22 + DC as of Sep 2026; Anrok: 26). — [TaxCloud](https://taxcloud.com/blog/saas-sales-tax-by-state/); [Kintsugi](https://trykintsugi.com/blog/sales-tax-on-saas.md); [Anrok](https://www.anrok.com/saas-sales-tax-by-state)
- [S] Economic nexus threshold is $100,000 of annual sales in most states and $500,000 in California, Texas and New York; all states with a sales tax have adopted economic nexus since Wayfair (2018). — [TaxCloud](https://taxcloud.com/blog/saas-sales-tax-by-state/); [Anrok](https://www.anrok.com/saas-sales-tax-by-state)
- [U] TaxCloud reports California and Colorado are set to begin taxing SaaS statewide on 1 Jan 2027 — single secondary source, not verified. — [TaxCloud](https://taxcloud.com/blog/saas-sales-tax-by-state/)
- [S] Physical presence also creates nexus; a single remote employee in a state can be enough. — [Stripe – software sales tax for SaaS](https://stripe.com/resources/more/software-sales-tax-for-saas-companies)

### Inferences
- An Indian company selling SaaS remotely with no US staff, office or dependent agent has a treaty argument against a US permanent establishment; hiring a US salesperson or support agent with authority to conclude contracts weakens it.
- Sales tax is a state tax and income-tax treaties do not block it, so the Indian company can owe sales-tax registration in SaaS-taxing states once it crosses a threshold, with or without a US entity.
- Whether payroll revenue share paid by a US provider to an Indian company is US-source income subject to withholding, and which treaty article covers it, was not found in any source — adviser to confirm.
- With a US subsidiary as the contracting and billing entity: the subsidiary files Form 1120 and state returns and collects sales tax; the Indian parent needs an intercompany agreement at arm's length and ODI reporting in India.

### Gaps
- No source read on US-source characterisation of SaaS fees or revenue share for a foreign corporation, on withholding (Form W-8BEN-E), or on state income-tax nexus.
- No source read on Indian-side treatment (transfer pricing, GST on export of services, POEM risk for the US subsidiary).
- State-by-state SaaS taxability lists conflict; verify per state before registering.

## 6. Comparison with a payment gateway

### Takeaway
It is not a "sign up, get keys, integrate" exercise. A gateway verifies each merchant once and then moves money; an embedded payroll provider reviews the platform (commercial, product, security), verifies each employer, collects tax-agency authorisations per employer, manages funding failures before paying wages, and files quarterly and year-end returns.

### Cited Findings
| Step | Payment gateway (Stripe Connect) | Embedded payroll (Check / Gusto Embedded) | Source |
|---|---|---|---|
| Getting access | Self-serve; platform picks platform country and capabilities | Sales-gated; keys issued to approved partners | [Stripe – required verification](https://docs.stripe.com/connect/required-verification-information); [Zeal FAQ](https://docs.zeal.com/docs/faqs) |
| Platform review | Platform-country and capability rules | Partnerships review, then security review (audits, pen test, policies); Check runs a "Product Review" with technical requirements such as API retry handling | [Gusto – Security review](https://docs.gusto.com/app-integrations/docs/security-review); [Check – product review (password-gated)](https://help.checkhq.com/1d74274a2e7580ab8bc8d0e127d623a0) |
| Per-customer verification | Company name, address, tax ID, representative, owners, external bank account | Legal name, legal address, FEIN that "must match with what the IRS has on file", signatory, industry, head counts; provider may request verification documents and may decline the employer | [Stripe – required verification](https://docs.stripe.com/connect/required-verification-information); [Check – company enrollment](https://docs.checkhq.com/docs/company-enrollment.md); [Zoho–Check terms](https://www.zoho.com/us/payroll/zchq-terms/) |
| Bank account | External account for payouts | Employer debit account verified; document requests possible; wire-funding option | [Check docs index](https://docs.checkhq.com/llms.txt) |
| Approval before first use | Capabilities paused if information missing | "Finishing company onboarding automatically triggers Gusto's internal approval process which must be completed before a company can process payroll" | [Gusto – Onboard a company](https://docs.gusto.com/embedded-payroll/docs/onboard-a-company) |
| Tax-agency authorisation | None | Form 8655 plus state-specific forms per employer; Gusto's state tax setup "must be completed using Gusto's co-branded Onboarding Form" (no public API) | [Gusto – Company forms](https://docs.gusto.com/embedded-payroll/docs/company-form); [Gusto – Embedded onboarding](https://docs.gusto.com/app-integrations/docs/embedded-payroll-onboarding) |
| Funding and return risk | Chargebacks and refunds after the sale | Employer is debited before payday; a failed debit creates a funding-failure requirement and "an employer with outstanding funding failure requirements will be unable to run payroll until these funds are recovered"; each failure is a "funding strike"; repeat failures lengthen the processing period or remove the employer; some partners are on a "low-risk funding policy" | [Check – resolve funding failures](https://docs.checkhq.com/docs/resolve-funding-failures.md) |
| Regulatory disclosures in the partner's UI | Statement descriptor | "All partners must implement Payroll Receipts" naming the licensed money transmitter | [Gusto – Payroll receipts](https://docs.gusto.com/embedded-payroll/docs/payroll-receipts) |
| Year-end | 1099-K handled by the gateway | W-2s, quarterly and annual filings by the provider; partner builds W-2 preview, tax-statement delivery and filings views | [Check docs index](https://docs.checkhq.com/llms.txt); [Zoho–Check terms](https://www.zoho.com/us/payroll/zchq-terms/) |
| Support | Gateway supports merchants on payments | Partner runs first-line support (Zoho: US phone line, 9am–7pm ET); provider handles complaints about its own services | [Zoho–Check terms](https://www.zoho.com/us/payroll/zchq-terms/) |

All rows are [P] (vendor docs or terms) except where the page was gated, as marked.

### Inferences
- The partner's build is larger than a gateway integration: onboarding flows, tax setup, requirement tracking, funding-failure handling, receipts, tax documents, employee self-service — plus an operations and support function in US hours.
- Time to go live is months, not days: Gusto's partnership and security reviews alone are about 3–6 weeks on the app-integration route, before commercial terms, build and product review. No vendor publishes an end-to-end figure.
- Loss allocation for unrecovered funding failures (who eats a returned debit after wages are paid) is not in public docs and is a key commercial term to ask about.

### Gaps
- No published end-to-end go-live timeline for any embedded payroll provider.
- Razorpay onboarding was not read; comparison uses Stripe Connect only. The Stripe page fetched rendered as a requirements tool, so the field list is from its row definitions, not a narrative.
- Partner liability for losses, minimum fees and volume commitments are contract terms not public for any vendor.
- Check's default processing period (commonly described as 4-day or 2-day ACH) was not confirmed on the page read.

## 7. Checklist before the first vendor call

### Takeaway
No vendor publishes its partner intake form, so this list is assembled from what the public docs show providers verifying and reviewing; it is a preparation list, not a vendor requirement list.

### Cited Findings
- [P] Security pack Gusto asks for: current audit reports (SOC 2 Type 2 / ISO 27001 + SOA / PCI DSS), penetration-test report, incident-response, privacy, physical-security and retention/destruction policies, named security contact and incident-response contact. — [Gusto – Security review](https://docs.gusto.com/app-integrations/docs/security-review)
- [P] If no audit exists, be ready to answer on background checks, MFA, encryption at rest and in transit, access control, vulnerability scanning, vendor management, rate limiting and API security. — [Gusto – Security review](https://docs.gusto.com/app-integrations/docs/security-review)
- [S] Gusto's embedded-partner guidance asks whether the platform plans to handle the flow of funds itself or leave it to the provider, and how often payroll runs. — [Gusto Embedded blog – selecting a partner](https://embedded.gusto.com/blog/selecting-embedded-payroll-partner/)
- [P] Per-employer data the provider will need from the partner's onboarding flow: legal name, FEIN, legal address, phone, signatory, industry, employee and contractor counts. — [Check – company enrollment](https://docs.checkhq.com/docs/company-enrollment.md)
- [P] Provider performs due diligence on employers and employees for legal compliance, anti-fraud and credit analysis and may deny service. — [Zoho–Check terms](https://www.zoho.com/us/payroll/zchq-terms/)
- [S] Bank-account opening pack for the US entity: formation documents, EIN letter (CP575/147C), bylaws, passports, proof of address, business description, expected monthly deposits. — [Stripe – non-US founders and US bank accounts](https://stripe.com/resources/more/can-non-us-founders-open-a-us-business-bank-account)

### Inferences
Prepare, in this order:

| Item | Why |
|---|---|
| Entity facts: Indian company details, ownership chart, whether a US subsidiary exists or is planned, target date | First eligibility question; see section 1 |
| Target customers: employer size band, industries, states, expected employers and employees in year 1 and year 2, pay frequencies, contractor share | Drives provider risk and pricing; Check uses industry and head counts in enrollment |
| Product scope: full-service payroll or calculation-only; white-label or co-branded; who does support and in what hours | Sets the regulatory role and the build |
| Money flow: confirm the partner will not hold or touch funds | Keeps the partner outside money-transmission and Nacha TPS roles |
| Security pack: ISO 27001 or SOC 2 status and date, latest pen test, policy set, named security and incident contacts | Gusto's stated review inputs |
| Data map: where US data will be hosted (Azure region), who in India can access SSN and bank data, sub-processors | Not published by vendors; expect the question |
| Support plan: US-hours coverage, escalation path to provider | Zoho precedent |
| Financials: revenue, funding, runway | Standard commercial diligence; not confirmed in any public form |
| Questions for the vendor: non-US partner accepted? US entity required? offshore access allowed? SOC 2 deadline? who bears funding-failure losses? minimum fees? regulatory-roles statement in writing? Maine position? | The gaps in sections 1–6 |

### Gaps
- No actual partner intake form from Check, Gusto Embedded, Salsa, Zeal or ADP was readable; volumes and financial thresholds they ask for are unconfirmed.
- Minimum scale a provider will accept from a new partner is not public.
