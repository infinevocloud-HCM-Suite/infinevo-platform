# Feature: AI helper help for every HR, payroll officer and tenant admin screen

| Field | Value |
|---|---|
| **Feature ID** | `W-114.26` · `CORE-AI` |
| **Owner** | Claude (`dev-claude-w114`) |
| **Apps touched** | `code/backend/core/src/main/resources/help/` (content only) |
| **Related gaps** | none in `GAP_INVENTORY.md` |
| **Status** | Ready |
| **Blocked by** | `W-114.11` and `W-114.12` on `main` (help library, role guides) |

Founder, 2026-11-27: the helper is mainly for HR, the payroll officer and the tenant admin, and must
cover all they do. This part is the help layer: a shipped article for every screen those roles use
that has none today, so "how do I …" gets steps and a link. Doing the steps is `W-114.28` and `.29`.

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` (resources only) | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | A "how do I …" question about any of the screens below gets that screen's article | 1 |
| Frontend area | none | 1 |

Size **M**: content only, no code.

## The articles

Each is `core/src/main/resources/help/articles/<id>.md`, in the format and rules of `W-114.11`
(front matter `id`, `title`, `summary`, `roles`, `screen`, `keywords`; a "Who can do this" line;
numbered steps naming the screen's own labels; read from the screen's code, never guessed).
`HelpArticlesMatchTheMenuTest` (in `app`) checks each `screen` against the menu and `sub-routes.json`.

| # | Article id | Screen | Roles |
|---|---|---|---|
| 1 | `bank-file-templates` | `/payroll/settings/bank-files` | payroll-officer, tenant-admin |
| 2 | `bank-file-download` | `/payroll/runs` | payroll-officer |
| 3 | `pay-run-excel-export` | `/payroll/runs` | payroll-officer, finance |
| 4 | `off-cycle-run` | `/payroll/runs` | payroll-officer |
| 5 | `tds-return` | `/payroll/tax/tds-return` | payroll-officer |
| 6 | `form-16` | `/payroll/tax/form-16` | payroll-officer |
| 7 | `employee-tds` | the employee's tax page | payroll-officer |
| 8 | `tds-settings` | `/payroll/settings/tds` | payroll-officer, tenant-admin |
| 9 | `reimbursement-claims` | `/payroll/claims` | payroll-officer |
| 10 | `deductions` | `/payroll/deductions` | payroll-officer |
| 11 | `proof-of-investment-review` | `/payroll/proof-of-investment` | payroll-officer |
| 12 | `prior-payroll` | `/payroll/prior-payroll` | payroll-officer |
| 13 | `tax-recalc-failures` | `/payroll/tax/recalc-failures` | payroll-officer |
| 14 | `payroll-dashboard` | `/payroll/dashboard` | payroll-officer |
| 15 | `flexible-benefits` | `/payroll/settings/fbp` | payroll-officer |
| 16 | `audit-log` | `/audit` | tenant-admin |
| 17 | `onboarding-settings` | `/settings/onboarding` | tenant-admin, hr |
| 18 | `approval-delegations` | `/approvals/delegations` | manager, hr |
| 19 | `leave-import` | `/leave/import` | hr |
| 20 | `wfh-allocations` | `/wfh/allocations` | hr |
| 21 | `wfh-requests-review` | `/wfh/requests` | hr, manager |
| 22 | `wfh-import` | `/wfh/import` | hr |
| 23 | `attendance-settings` | `/hrms/attendance-settings` | hr |
| 24 | `overtime-review` | `/hrms/overtime-requests/all` | hr, manager |
| 25 | `regularizations-review` | `/hrms/regularizations/all` | hr, manager |
| 26 | `projects` | `/hrms/projects` | hr, manager |
| 27 | `hrms-dashboard` | `/hrms/dashboard` | hr |

A screen reached only from another page (a settings tab, the run page's buttons) is added to
`sub-routes.json` with its parent, as `W-114.16` did for the payroll settings tabs.

**Role guides.** `roles/hr.md`, `roles/payroll-officer.md` and `roles/tenant-admin.md` gain a line per
new article, under the heading where the task falls ("Every month", "Every quarter", …), so
`core.my_role_guide` lists them.

**Money and pay run steps** (founder, 2026-11-27): the articles explain them; the helper never takes
them (`W-114.21`), and money decisions stay on the screen.

## Tests

| Type | File | Covers |
|---|---|---|
| Unit (app) | `HelpArticlesMatchTheMenuTest` (existing) | Every new article's screen is on the menu or in `sub-routes.json`; every role guide line names a real article |
| Unit (core) | `HelpLibraryTest` (existing, pinned lists updated) | The library loads every new article |

## Verification

```bash
cd code/backend && ./mvnw -B -pl core,app -am verify
```

| Check | Expected | Result |
|---|---|---|
| The command | Build success | Pass 2026-11-27: `HelpLibraryTest`, `HelpCatalogueTest`, `AiSubRoutesTest`, `HelpArticlesMatchTheMenuTest` |
| As a payroll officer, "how do I make a bank file template?" | Steps from `bank-file-templates`, with a link | not run |

## AI helper

This part is the AI helper's help layer for these screens. Actions are `W-114.28` and `.29`.

**As built (2026-11-27, Claude).** 28 articles (the 27 listed, plus `pay-run-review-checklist` and `hrms-dashboard`), 58 in all. A page that is not itself a menu item uses the menu screen with a `route:` (`bank-files`, `tds`, `fbp`, `form-16` under `/payroll/settings`; `new-off-cycle` under `/payroll/runs`), and the five are added to `sub-routes.json`. Role guides for HR, payroll officer, tenant admin and manager list them.

**Found while writing (not changed):** a manager holds `core.wfh.read_team` but the WFH requests menu item needs `core.wfh.read` (`NavigationCatalogue.java:262-267`), so managers have no menu entry for it; managers cannot open All overtime or All regularisations (`core.overtime.read`, `core.attendance.read` not granted); HR is left out of `core.approval.delegate` on purpose (`V176`).

## Rollback

Delete the articles. Nothing else changes.
