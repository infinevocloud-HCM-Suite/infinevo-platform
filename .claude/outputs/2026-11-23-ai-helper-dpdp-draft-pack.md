# AI helper — DPDP draft pack for legal review

**DRAFT for a lawyer to review. This is not legal advice.** It was written by a non-lawyer from law-firm summaries and Microsoft's public pages. The DPDP Act and Rules were not read in the original. Every draft text below is a starting point for the lawyer to correct.

Date: 23 Nov 2026. Product: Infinevo HCM Suite (HR and payroll SaaS, India). Sources: `D:\tmp\research\research_notes\AI helper implementation options\models_and_dpdp.md` and `D:\tmp\research\reports\AI helper implementation options.md`.

Terms used: **Data fiduciary** = the party that decides why and how personal data is used. **Data processor** = a party that handles the data on the fiduciary's behalf. **Sub-processor** = a processor hired by the processor. **DPA** = data processing agreement, the contract annex on personal data.

## 1. Summary

| Topic | Position today | Status |
|---|---|---|
| Data fiduciary | The customer (the employer) | Stays responsible for what its processors do |
| Data processor | Infinevo | Duties reach Infinevo through the customer contract |
| Sub-processor | Microsoft (Azure OpenAI) | Governed by Microsoft's Data Protection Addendum |
| Legal basis relied on | "Purposes of employment" (section 7(i)), no consent needed | To confirm — Q2, Q3 |
| On or off | Off for every customer. Switched on per customer by the platform admin | Built |
| Model | OpenAI gpt-4.1-mini, hosted by Microsoft, South India region, regional (Standard) deployment | Model retires 14 Apr 2027 — Q7 |
| Access | Private network only. No API keys | Built |
| Sent to the model | Names, salary and payslip figures, leave and attendance data — only when the user asks about them | Leave text may hold health reasons — Q12 |
| Masked before storing or sending | PAN, Aadhaar, bank account numbers, IFSC, UAN, ESI numbers, passport numbers, passwords, tokens | Built |
| What the helper can reach | Only what the signed-in user may already see. It acts only after the user presses Confirm | Built |
| Chat history | In the customer's own tenant data, in India. Deleted after 30 days | One-year log rule may conflict — Q4 |
| Usage logs | Counts only. Never chat text | Built |
| Model training | Microsoft does not use the data to train models or share it with OpenAI | Microsoft public page |
| Microsoft abuse monitoring | Microsoft may keep prompts flagged for abuse review. Period not confirmed | Q5, Q6 |
| Cross-border today | Processed and stored in India | A later model may be processed in an Asia-Pacific data zone or globally. Customer told first |
| DPDP substantive duties | Not yet in force. Most sources say 13 May 2027, some 14 May | Q1 |

## 2. Draft sub-processor entry (for the DPA annex)

| Field | Draft entry |
|---|---|
| Sub-processor | Microsoft (Azure OpenAI service), contracted through Infinevo's Azure subscription |
| Purpose | To generate answers in the in-app AI helper, when the customer has switched it on |
| Data categories | Employee names; salary and payslip figures; leave and attendance records; the text the user types. Only what the user asks about |
| Not sent | PAN, Aadhaar, bank account number, IFSC, UAN, ESI number, passport number, passwords and tokens. These are masked first |
| Location | Processed and stored in Microsoft's India geography (South India region) |
| Retention at Microsoft | The model keeps no prompts or answers. Microsoft may keep prompts flagged for abuse review, in the India geography, for a period set by Microsoft **[period to confirm — Q5]** |
| Retention at Infinevo | Chat history 30 days, in the customer's tenant data in India |
| Safeguards | Private network link; no API keys; masking before sending; the helper sees only what the signed-in user may see; no use for model training; monthly request cap per customer |
| Change of location or sub-processor | Written notice before any change **[30 days suggested — Q8]** |

Do not write "nothing is retained by Microsoft". Do not quote "30 days" for Microsoft until the lawyer confirms it in Microsoft's contract terms.

## 3. Draft customer opt-in text

| The text must state | Covered |
|---|---|
| 1. The helper is off until the customer switches it on | Yes |
| 2. What data reaches the AI model and what is masked | Yes |
| 3. Who the AI provider is, and that it does not train on the data | Yes |
| 4. Where the data is processed, and that the customer is told before this changes | Yes |
| 5. How long chat history is kept, and that Microsoft may keep flagged prompts | Yes |
| 6. That the customer is responsible for telling its employees | Yes |

Draft text (about 130 words):

> **Switch on the AI helper**
>
> The AI helper answers questions using your organisation's HR and payroll data. It reads only what the signed-in user is already allowed to see, and it changes nothing until the user presses Confirm.
>
> Names, salary and payslip figures, leave and attendance data are sent to the AI model when a user asks about them. PAN, Aadhaar, bank, IFSC, UAN, ESI and passport numbers are masked first.
>
> The model is run by Microsoft (Azure OpenAI) in India. Microsoft does not use your data to train models. Microsoft may keep prompts it flags for abuse review. We will tell you before the place of processing changes.
>
> Chat history is kept in your data in India and deleted after 30 days.
>
> You are responsible for telling your employees. A notice is provided.
>
> [ ] I confirm on behalf of my organisation.

The switch itself is flipped by the platform admin. How the customer's decision is recorded is Q9.

## 4. Draft employee notices

| Version | Where shown | Limit |
|---|---|---|
| Short | First time a user opens the helper | Under 100 words |
| Long | Help page | Under 250 words |

Short (about 75 words):

> **About the AI helper**
>
> This helper answers questions using your HR and payroll records. It sees only what you are already allowed to see. It does nothing until you press Confirm.
>
> Your question, and details such as names, salary, leave and attendance, are sent to an AI model run by Microsoft in India. PAN, Aadhaar and bank numbers are hidden first. Chats are deleted after 30 days.
>
> Answers can be wrong. Check important figures. [Learn more]

Long (about 215 words):

> **How the AI helper uses your data**
>
> **What it does.** The helper answers questions about HR and payroll, such as your leave balance or payslip. Your employer has chosen to switch it on.
>
> **What it can see.** Only what you can already see in the application. A manager or HR user sees what their role allows.
>
> **What is sent to the AI.** Your question, and the records needed to answer it. This can include names, salary and payslip figures, leave and attendance data. Do not type sensitive details the helper does not need.
>
> **What is hidden.** PAN, Aadhaar, bank account, IFSC, UAN, ESI and passport numbers, passwords and tokens are masked before anything is stored or sent.
>
> **Who runs the AI.** Microsoft (Azure OpenAI), in India. Microsoft does not use your data to train AI models. Microsoft may keep a prompt it flags for abuse review.
>
> **How long chats are kept.** 30 days, in your employer's data in India. Then they are deleted.
>
> **Actions.** The helper never changes anything on its own. It shows what it will do and waits for you to press Confirm.
>
> **Accuracy.** Answers can be wrong. Check important figures on the payslip or leave screen.
>
> **Questions or complaints.** Contact [employer's grievance contact]. Your employer decides how your data is used. Infinevo provides the software.

## 5. Draft clause list for the DPA addendum

| Clause | Draft content |
|---|---|
| Instructions | Infinevo uses AI-helper data only on the customer's instructions and only to provide the helper. Switching the helper on is the instruction. No use for model training, cross-customer analysis or marketing |
| Sub-processor | Customer authorises Microsoft (Azure OpenAI) as in section 2. Infinevo keeps a current list and gives notice before adding or replacing one |
| Security measures | Masking of listed identifiers; private network; no API keys; access limited to the signed-in user's permissions; Confirm before any action; monthly cap; usage logs without chat text |
| Breach notice | Infinevo tells the customer without delay after it learns of a breach, with enough detail for the customer to meet its own deadlines **[hours to set — Q10]** |
| Deletion | Chat history deleted after 30 days and on switch-off or contract end. Microsoft's abuse-review store described as it is, not as "nothing kept" **[log retention — Q4]** |
| Audit information | Infinevo gives, on request, a written description of the measures and Microsoft's published terms. No on-site audit of Microsoft |
| Cross-border change notice | Processing is in India today. Infinevo gives written notice before moving to an Asia-Pacific data zone or global processing, and keeps a record of place of processing. No transfer to a country the government restricts **[customer's right to object or switch off — Q8]** |

## 6. Checklist before the duties start (May 2027)

| # | Item | Owner | Status |
|---|---|---|---|
| 1 | Confirm the commencement date and whether it was brought forward | Lawyer | |
| 2 | Confirm the legal basis and whether employee notice is owed | Lawyer | |
| 3 | Finalise sub-processor entry and DPA addendum | Lawyer | |
| 4 | Finalise opt-in text and both employee notices | Lawyer / Founder | |
| 5 | Read Microsoft's Data Protection Addendum and Product Terms for the abuse-monitoring retention period | Lawyer / Founder | |
| 6 | Decide which logs must be kept one year and reconcile with 30-day chat deletion | Lawyer / Engineering | |
| 7 | Record each customer's opt-in (who, when, which text version) | Engineering | |
| 8 | Show the first-use notice and publish the help page | Engineering | |
| 9 | Block global deployments by Azure Policy, as evidence for the India promise | Engineering | |
| 10 | Decide the model after gpt-4.1-mini retires (14 Apr 2027) and send the change notice if processing leaves India | Founder / Engineering | |
| 11 | Write the breach procedure, with customer notice times | Founder / Lawyer | |
| 12 | Add a grievance contact field per customer, shown in the help page | Engineering | |
| 13 | Check contract wording for customers under sector rules (SEBI, RBI) | Lawyer | |

## 7. Questions for the lawyer

| # | Question | Why it matters |
|---|---|---|
| 1 | Is the commencement date for the substantive duties 13 May 2027 or 14 May 2027, and was the proposal of January 2026 to shorten the period ever notified? | Sources disagree; it sets every deadline in section 6. |
| 2 | Can the customer rely on the "purposes of employment" ground (section 7(i)) for an AI helper that answers questions from employees, managers and HR users? | Law firms read the ground narrowly; if it fails, consent is needed. |
| 3 | Is a section 5 notice owed to employees when the employment ground is used, and if not, should one be given anyway? | Sources disagree; one says an employer giving no notice "will struggle to defend that position". |
| 4 | Does the Rules' one-year minimum for personal data, traffic data and processing logs cover chat text, or only audit records, and can chat text still be deleted at 30 days? | The product promises 30-day deletion; the two may conflict. |
| 5 | What retention period do Microsoft's Data Protection Addendum and Product Terms state for the abuse-monitoring store? | The long-quoted "30 days" is no longer on Microsoft's public pages; we must not misstate it. |
| 6 | Does possible human review of flagged prompts by Microsoft staff, with no commitment that reviewers are in India, need to be disclosed or treated as a transfer? | Microsoft commits to in-region reviewers only for the EEA. |
| 7 | If the next model is processed in an Asia-Pacific data zone or globally, what must the contract and notices say, given the cross-border rule works by a list of restricted countries and none is named yet? | gpt-4.1-mini retires on 14 Apr 2027 and no India-only successor exists today. |
| 8 | What notice period, and what customer right (object, switch off, terminate), should apply to a change of sub-processor or place of processing? | A template suggests 30 days; the retirement date may leave little room. |
| 9 | Since the platform admin flips the switch, what record of the customer's own decision is needed (order form, written instruction, in-app tick by the tenant admin)? | The customer is the fiduciary and must own and evidence the decision. |
| 10 | What breach-notice time should Infinevo promise, given the customer owes the Board notice "without delay", detail within 72 hours, and CERT-In a report within six hours? | Infinevo's promise must let the customer meet its deadlines. |
| 11 | Does the Act require a written contract between fiduciary and processor (section 8(2)), and does it flow down to Microsoft? | The research could not confirm this section from any source. |
| 12 | Leave reasons may contain health details and the helper covers people other than current employees (contractors, former employees, family under benefits); does the employment ground still hold for these? | The ground is reported not to cover those groups; masking does not remove free text. |
| 13 | Is masking Aadhaar and PAN before sending enough to meet the Aadhaar Act, the Aadhaar Data Vault rules and any PAN confidentiality rule? | Aadhaar findings came from search summaries; no PAN source was found. |
| 14 | Should the DPA offer India-only processing as a contract term for customers regulated by SEBI or RBI? | Such customers may require it, and only the regional deployment gives it. |
| 15 | Which employee rights apply on the employment ground (access, correction, erasure, grievance), and must the helper support any of them? | Sources say only grievance redressal clearly applies; the notices assume this. |
| 16 | Could Infinevo or a large customer be notified as a Significant Data Fiduciary, and what would change? | That status adds a Data Protection Officer, yearly audit and possible localisation. |
