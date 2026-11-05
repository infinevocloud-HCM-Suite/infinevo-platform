# Feature: Core lists show one bold thing per row

| Field | Value |
|---|---|
| **Feature ID** | `W-83.6` · `FE-CORE` |
| **Owner** | krushna |
| **Apps touched** | `code/frontend` (`src/core`) |
| **Related gaps** | founder 2026-11-05: UI not visibly professional |
| **Status** | Ready |
| **Blocked by** | `W-83.5` |

## What and why

`W-83.5` sets density and chrome once. Core lists still make three things bold per row, for
example `core/employee/EmployeeList.jsx:136,156,164`, so nothing stands out. This ticket applies
the `W-83.5` emphasis rule to every Core list and detail screen. Nothing about how a screen works
changes.

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | No Core table row has more than one `strong` text | 1 |
| Frontend area | `core` | 1 |

## Changes

| Replace | With |
|---|---|
| `<Text strong>` on ids, codes, emails, dates, amounts | `<Text type="secondary">` for ids and codes; plain `<Text>` otherwise |
| `fontWeight: 600` or `700` literals | Nothing; `strong` on the row's name only |
| Tables with no `size` (`holiday/HolidayLookup`, `portal/MyDocumentsPanel`) | Leave; the theme now sets the row height |

Detail screens (`employee/EmployeePage`, `leave/LeaveRequestDetail`, `approvals/InstanceDetail`):
labels `type="secondary"`, values plain, the page title the only heading.

## Out of scope

HRMS and Payroll, which get the same rule inside `W-81.1` and `W-82.1`. Empty states.

## Standing rules

Creates no table, no migration, no backend code, no money handling. No module references another.
Every element `id`, `data-testid`, permission check, route and service call stays as on `main`.

## Tests

No new behaviour. Existing suites and the `e2e` job stay green; a test changes only if it pinned
a `strong` element, and the commit says which.

## Verification

```bash
cd code/frontend && npx vitest run && npm run lint && npm run build
grep -rnE "fontWeight: *[67]00" src/core --include=*.jsx --exclude=*.test.jsx
git -C ../.. diff --stat origin/main...HEAD -- code/backend
```

The first line passes. The grep and the diff print nothing. By eye at 1440 px: `/employees`,
`/leave/requests`, `/approvals`, each row has one bold text.

## Risks

Nothing unusual.

## Rollback

Revert the squash commit. No data changes.
