# Case-study pack — Infinevo HCM Suite

> 2026-11-13. Rough notes for the case-study writer. Every fact is from the repository; sources are listed under each section.

| # | Item | Status | Where |
|---|---|---|---|
| 1 | Screenshots, 6 | Ready | `d:\tmp\infinevo-case-study\` — PNG, 2880×1620 |
| 2 | The four legacy apps | Ready | §2 |
| 3 | What they became | Ready | §3 |
| 4 | Migration facts | Ready | §4 |
| 5 | Case-study facts | Ready, results partly open | §5 |
| 6 | Source for "4 apps → 1 suite in 6 weeks" | Ready, with one caution | §6 |
| 7 | Written OK to publish | **Not ready** — draft wording only | §7 |
| 8 | Buyer questions | Drafted, to be corrected | §8 |

## 1. Screenshots

| File | Screen |
|---|---|
| `1-payroll-dashboard.png` | Payroll dashboard: current run, EPF, ESI, professional tax, TDS |
| `2-employees.png` | Employee list |
| `3-pay-runs.png` | Pay runs list |
| `4-salary-components.png` | Salary components |
| `5-leave-allocations.png` | Leave allocations |
| `6-projects.png` | Projects (HRMS) |

- All taken from the test company "Infinevo Test Co" on the live platform. No customer data.
- The signed-in user name, two celebrity names and five team members' names typed in by testers were replaced with made-up names at capture time. Nothing was changed in the system.
- The pay-run dates run to March 2028 because testers ran future months. Crop or avoid if that reads oddly.

## 2. The four legacy apps

| App | What it did | Built with | Size |
|---|---|---|---|
| HRMS Backend | Employees, timesheets, leave, attendance, notifications, user access | Java 21, Spring Boot 3.2, MySQL, own JWT login | 25 controllers, 39 tables, ~23k lines |
| HRMS Frontend | Timesheet entry and approval, HR admin panel | React 18, Vite, MUI | 75 files, ~42k lines |
| Payroll Backend | Salary, pay runs, statutory (PF, ESI, PT), tax declarations, company setup | Java 17, Spring Boot 3.2, MySQL, Keycloak login | 61 controllers, 95 tables, ~82k lines |
| Payroll Frontend | Payroll setup, employee onboarding, pay-run processing, settings | React 18, Create React App, Ant Design, Redux | 216 files, ~116k lines, 158 routes |

- Two databases, two logins, two design systems, hosted on DigitalOcean. Nothing shared between the two frontends.
- The only link between the two products was one REST call for loss-of-pay days.
- No automated tests, no schema migration tool, no audit trail.

Sources: `legacy/docs/ARCHITECTURE.md:13`, `:317-320`, `:342-345`, `:351-357`; `docs/target-state/01-platform-shape.md:75`.

## 3. What they became

| Layer | Who gets it | Holds |
|---|---|---|
| Core | Every customer | Employee master, org structure, leave, holidays, approvals, single sign-on, roles, documents, audit trail, notifications, self-service portal — 21 capabilities |
| HRMS module | Customers who buy HRMS | Attendance, clock in/out, regularization, overtime, projects, tasks, timesheets, dashboards — 12 capabilities |
| Payroll module | Customers who buy Payroll | Salary structure, components, pay runs, off-cycle runs, PF/ESI/PT, income tax, Form 16, claims, payslips — 17 capabilities |

- One repository, one backend, one frontend, one PostgreSQL database, one login.
- A customer buys HRMS, Payroll or both, and adds a module with a switch; no re-onboarding.
- Multi-tenant: every table carries the tenant and the database itself keeps tenants apart.
- Runs on Azure: Container Apps, PostgreSQL 16, Redis, Blob Storage, Key Vault, Front Door, Application Insights, all defined as code (Bicep).

Sources: `docs/target-state/README.md:15-19`; `docs/target-state/01-platform-shape.md:56-140`, `:170-180`; `infra/azure/modules/`.

## 4. Migration facts

| Date | What happened |
|---|---|
| 11 Sep 2026 | Target design drafted |
| 13 Sep 2026 | Four apps frozen as reference; new repository started |
| 18–19 Sep 2026 | Azure infrastructure as code, networking, tenant isolation merged |
| 23 Sep – 4 Oct 2026 | Core, then Payroll and HRMS modules built |
| 9 Oct 2026 | Test company live on Azure |
| 13 Oct 2026 | Browser test suite per role blocks every merge |
| 16 Oct 2026 | Migration rules and field mapping written |
| 26 Oct 2026 | Migration engine run for the company: both legacy databases into the new one |
| 1 Nov 2026 | Migration job removed from Azure — migration done |

| Approach | Detail |
|---|---|
| Rebuild, not lift-and-shift | Legacy code frozen and read for logic; nothing edited in place |
| Spec first | 186 written specs, one per ticket |
| Rules before rows | Deduplication and merge rules agreed before any data moved |
| Data moved | One company, 28 employees, 21,032 rows from 94 legacy tables; the two employee records merged into one per person |
| Checks | 1,242 schema migration scripts, 639 backend test classes, 224 frontend test files, per-role browser tests in CI |

| Layer | Before | After |
|---|---|---|
| Backend | 2 × Spring Boot 3.2 (Java 17 and 21) | 1 × Spring Boot 3.5, Java 21, three enforced modules |
| Frontend | React + MUI, React + Ant Design | 1 × React 18, Vite, Ant Design 5 |
| Database | 2 × MySQL, 131 tables, schema by Hibernate | 1 × PostgreSQL 16, four schemas, Flyway |
| Login | Custom JWT and Keycloak | Keycloak only |
| Files | Cloudinary | Azure Blob Storage |
| Hosting | DigitalOcean | Azure Container Apps |

Sources: `git log` (first commit `defcefa4` 2026-09-13; `b3234d01`, `7e6e58e0`, `c1cb5eec`; `1f8abe35`, `47702415` 2026-10-26); `.claude/work/active-work.md:119`, `:145`; `.claude/outputs/2026-10-26-prod-28-employee-row-counts.md:3-5`, `:104`; `code/backend/pom.xml:10`, `:45`; `infra/azure/modules/postgres.bicep:53`.

## 5. Case-study facts

| Part | Notes |
|---|---|
| Context | Infinevo Cloud ran HR and payroll as two separate products for Indian companies: timesheets, leave and attendance in one, salary, statutory and tax in the other |
| Problem | Two logins, two databases, the same employee kept twice, no audit trail, no tests, one customer per install. Selling both products meant onboarding twice |
| What was done | One multi-tenant suite designed, built and deployed on Azure; the company's live data migrated and merged |
| Results on record | 4 apps → 1; 2 databases → 1; 2 logins → 1; 131 tables → one schema set with tenant isolation; audit trail on every change; automated deploy pipeline; first company migrated |

**Not on record, do not claim without the founder:** cost savings, performance figures, user counts, and that the legacy system is switched off. The first pay run on the new suite for the migrated company (September 2026) was still in draft on 10 Nov 2026.

Sources: `legacy/docs/ARCHITECTURE.md:329-336`; `docs/target-state/01-platform-shape.md:60-82`; `.claude/work/active-work.md:29`.

## 6. Source for "4 apps → 1 suite in 6 weeks"

| Milestone | Date | Record |
|---|---|---|
| Start: four apps frozen, repository created | 13 Sep 2026 | commits `defcefa4`, `52275ce4` |
| End: company's data migrated into the suite on Azure | 26 Oct 2026 | commits `1f8abe35`, `47702415`; row-count report dated 2026-10-26 |
| Elapsed | 43 days = 6 weeks and 1 day | |

- No document states "6 weeks" in words; the figure comes from these two dates.
- Counting to the day the migration job was retired (1 Nov 2026) gives 7 weeks. Data fixes and screen work continued into November.
- Safe wording: "from freeze to migrated data in six weeks".

## 7. Written OK to publish

Only the founder can give this. Draft wording to send from a company address:

> Infinevo Cloud confirms that [publisher] may publish the case study "Four applications to one HCM suite" and the six attached screenshots, on [where], from [date]. The screenshots show test data only. Infinevo Cloud may ask for the case study to be corrected or withdrawn at any time.
> — [name, title, date]

## 8. Buyer questions (drafts — correct as needed)

| Question | Draft answer |
|---|---|
| Can I buy only Payroll, or only HRMS? | Yes. Either or both. Employee records, leave and approvals come with every plan |
| What happens if I add the other module later? | It is switched on. Employees already exist, so there is no second onboarding |
| Is my data kept apart from other customers? | Yes. Every record carries the company it belongs to and the database itself enforces the separation |
| Does it handle Indian statutory rules? | PF, ESI, professional tax, TDS under both tax regimes, and Form 16 |
| Can I bring my existing data? | Employees and leave balances import from CSV; prior payroll for a mid-year start can be imported |
| Where is it hosted? | Microsoft Azure, with backups and an audit trail of every change |
| Can employees use it themselves? | Yes: leave, attendance, timesheets, payslips and documents in one self-service portal, usable on a phone |

Sources: `docs/target-state/01-platform-shape.md:122-140`, `:170-180`; `.claude/work/active-work.md:86-90`.
