# US payroll and tax: research brief for sayeed

| | |
|---|---|
| Ticket | `W-118` US market support (logged, not specced) |
| Your task | Research only. No product code, no spec yet |
| Goal | Find out how we add US payroll tax to Infinevo, and what it costs us in code |
| Hand back | One short findings file in `.claude/outputs/`, table first |
| Read first | `.claude/outputs/reports/US payroll provider integrations.md`, then `US payroll and HRMS support.md` in the same folder |

## Where we stand

| Point | Position today |
|---|---|
| Direction | Sell US HRMS first, then US payroll through a third-party provider behind our own screens |
| Not decided | Which provider, and whether we keep our own US pay run |
| Why not build the tax rules ourselves | US tax is set by the federal government, 50 states and thousands of cities, and rates change during the year |
| Biggest unknown | Whether a provider will sign an Indian company; the founder asks this on the vendor calls |

The direction comes from desk research. It is not final, and your hands-on work may change it.

## The two routes to compare

| | Route A: provider runs payroll | Route B: our pay run with a tax engine |
|---|---|---|
| Who works out the tax | The provider | Our Java pay run calls a tax engine |
| Who files and pays the tax | The provider | A filing service files; paying the tax stays with us |
| Providers | Check, Gusto Embedded, ADP Embedded | Symmetry or Vertex, plus TaxBandits or Greenshades |
| What we build | Screens, employer and employee setup, data sync | A full US pay run, year-end forms, tax operations |

## What to research

| # | Question | How |
|---|---|---|
| 1 | What does one US payslip need? | Learn federal income tax (Form W-4, IRS Publication 15-T), Social Security and Medicare, federal and state unemployment tax, state and local income tax |
| 2 | What must the employer file and pay, and when? | Forms 941, 940, W-2; deposit schedules; state quarterly reports |
| 3 | Can we run a US pay run through Gusto Embedded? | Open the free demo account, use the Java client, create a company and an employee, run one payroll |
| 4 | Can a tax engine fit our pay run? | Ask Symmetry for a trial key; send one gross-pay request and read the tax lines that come back |
| 5 | Can a filing service take our data? | Use the free TaxBandits test account; submit one sample Form 941 and one W-2 |
| 6 | Which of our fields are missing for the US? | List what each provider asks for (SSN, W-4 answers, home and work address, pay frequency) against our employee and salary tables |
| 7 | What must change in our code? | Confirm the list below and add anything missed |
| 8 | What does a US customer do today that we cannot? | Weekly and bi-weekly pay, hourly pay, overtime at 1.5 times, pre-tax deductions such as 401(k) |

Start with 1 and 2 so the rest makes sense. Then do 3, since it needs no contract.

## What is fixed to India in our code

| Area | Today |
|---|---|
| Pay period | One calendar month, stored as `YYYY-MM`; also used in Core for pay inputs, overtime and leave |
| Tax year | April to March, used by about 60 payroll files |
| Money | No currency; screens show the rupee sign |
| Statutory lines | PF, ESI, PT and TDS run for every customer, with no country check |
| Employee fields | PAN, Aadhaar, UAN, IFSC; India state list |

Evidence:
- `code/backend/migration/src/main/resources/db/migration/payroll/V054__pay_schedule.sql:7`
- `code/backend/migration/src/main/resources/db/migration/payroll/V055__payrun.sql:7-13`
- `code/backend/payroll/src/main/java/com/infinevo/payroll/taxdeclaration/FinancialYear.java:66`
- `code/backend/shared/src/main/java/com/infinevo/shared/money/Money.java:26-46`
- `code/backend/payroll/src/main/java/com/infinevo/payroll/statutory/payrun/StatutoryLineContributor.java:54-56`
- `code/backend/payroll/src/main/java/com/infinevo/payroll/tds/TaxLineContributor.java:36-38`

The place a US tax source would plug in is `PayLineContributor`.
- `code/backend/payroll/src/main/java/com/infinevo/payroll/payrun/PayLineContributor.java:14-25`

## Rules for this research

- Use test and demo accounts only. Enter no real customer or employee data.
- Sign nothing and agree to no price. Pass any contract or sales question to the founder.
- Keep trial code outside the repo, or on a throwaway branch that is never merged.
- Mark each finding as tested by you, read on the vendor's page, or not confirmed.
- Do not use an LLM answer as a source for a tax rate or rule. Use the IRS, state and vendor pages.

## What to hand back

| Section | Content |
|---|---|
| What worked | Which demo or trial you ran, and what came back |
| Field gaps | Our fields against what the provider needs |
| Code changes | The list above, corrected and sized S, M or L |
| Route A against Route B | What you found easy and hard in each |
| Open questions | What only a vendor or a US payroll expert can answer |

## Useful links

- IRS Publication 15 (employer's tax guide): https://www.irs.gov/publications/p15
- IRS Publication 15-T (federal withholding method): https://www.irs.gov/publications/p15t
- Gusto Embedded docs: https://docs.gusto.com/embedded-payroll/docs/introduction
- Check docs: https://docs.checkhq.com/
- Symmetry Tax Engine docs: https://docs.symmetry.com/ste/overview/what-is-the-ste.md
- TaxBandits for payroll software: https://developer.taxbandits.com/api-for-payroll-software/
