# Feature: The remaining Core screens use the new look

| Field | Value |
|---|---|
| **Feature ID** | `W-83.3` · `FE-CORE` |
| **Owner** | krushna |
| **Apps touched** | `code/frontend` (`src/core`, `.eslintrc.cjs`) |
| **Related gaps** | founder 2026-11-04: UI redesign |
| **Status** | Ready |
| **Blocked by** | `W-83.2` |

## What and why

`W-83.2` covers 22 Core files. The rest only have the new theme, so their headers and cards do not
match yet. This ticket finishes Core and switches on the lint rule, so hard-coded colours cannot
come back. Nothing about how a screen works changes.

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | No Core screen has a hard-coded colour or its own page header | 1 |
| Frontend area | `core` | 1 |

## Changes

| Kind | Files | Change |
|---|---|---|
| Pages | `admin/PlatformHome`, `TenantCreate`, `TenantDetail`, `TenantList` · `holiday/CalendarHolidays`, `HolidayLookup` · `leave/Allocations`, `EmployeeLeave`, `LeaveImport`, `LeaveRequestDetail`, `RecordLeave` · `org/WorkLocationForm` · `settings/CompanyProfile` · `setup/SetupChecklist` · `wfh/WfhAllocations`, `WfhImport` | `PageHeader`, `<Card variant="borderless">`, tokens |
| Parts | `admin/AuditTab` · `approvals/DecideModal` · `employee/TerminateModal`, `employeeTabs`, `tabs/DocumentsTab`, `tabs/ReportingLineTab`, `tabs/SectionTab` · `invitation/InvitationTable` · `leave/EmployeePicker`, `LeaveRequestFields`, `PolicyForm` · `portal/EditOwnSection`, `MyDocumentsPanel` · `setup/SkipStepModal` · `users/InvitationsTab`, `UsersTab` | Tokens only, where they hold a literal. Leave alone otherwise |
| Public page | `invitation/AcceptInvitation` | Tokens only, no `PageHeader`. Its hex colours go |
| Lint | `.eslintrc.cjs` | Hex rule on for `src/core/**/*.jsx` |

`org/Departments` and `org/Designations` render through `MasterTable` (`W-83.2`), so they should need
no change. Hex colours to remove today: `AcceptInvitation` 3, `LeaveImport` 2, `EmployeeLeave` 1.

## Out of scope

This ticket adds nothing new; anything a screen already does stays. These items from the old draft
become their own tickets only if the founder asks.

| Group | Item |
|---|---|
| Not in the code | Cost centre, head of department, sub-departments, SEZ, department and location headcounts |
| Other new behaviour | Balance impact calculator, bulk credit, password strength meter, live header preview, holiday calendar view, circular progress ring, JSON diff drawer |

Phone-specific fixed widths stay with `W-80.2`.

## Standing rules

Creates no table, no migration, no backend code, no money handling. No module references another.
Every element `id`, `data-testid`, permission check, route and service call stays as on `main`.

## Tests

No new behaviour, so no new tests beyond the lint rule. Existing suites and the `e2e` job stay
green; a test changes only if it pinned a heading level, and the commit says which.

## Verification

```bash
cd code/frontend && npx vitest run && npm run lint && npm run build
grep -rnE "#[0-9a-fA-F]{3,8}\b" src/core --include=*.jsx --exclude=*.test.jsx
```

The first line passes. The grep prints nothing. The `e2e` job is green. By eye, at 1280 px and
375 px: `/admin`, `/holidays/lookup`, `/leave/allocations`, `/setup`, `/settings/company`.

## Risks

Nothing unusual. The public invitation page sits outside the shell, so check it signed out.

## Rollback

Revert the squash commit. No data changes.
