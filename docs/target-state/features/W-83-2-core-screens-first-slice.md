# Feature: The first 22 Core screen files use the new look

| Field | Value |
|---|---|
| **Feature ID** | `W-83.2` · `FE-CORE` |
| **Owner** | krushna |
| **Apps touched** | `code/frontend` (`src/core`) |
| **Related gaps** | founder 2026-11-04: review of `feature/ui-professional-specs` |
| **Status** | Ready |
| **Blocked by** | `W-83.1` |

## What and why

Krushna's branch restyled these screens (`e0f690db`) and the screens look right, but the styles
are pasted inline, one badge shows a wrong number, and four headers may squeeze on a phone. This
ticket keeps the look and moves it onto `W-83.1`'s pieces. Nothing about how a screen works changes.

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | These screens show the new header and no hard-coded colours | 1 |
| Frontend area | `core` | 1 |

## Changes

Start from the same 22 files on `origin/feature/ui-professional-specs`, on your branch from `main`
after `W-83.1` lands:

| Folder | Files |
|---|---|
| `core/approvals` | `Definitions`, `Delegations`, `Inbox`, `InstanceDetail` |
| `core/audit`, `core/roles`, `core/users` | `AuditLogScreen`, `RolesScreen`, `UsersScreen` |
| `core/employee` | `EmployeeCreate`, `EmployeeImport`, `EmployeeList`, `EmployeePage`, `tabs/OverviewTab` |
| `core/holiday`, `core/org` | `Calendars`, `MasterTable`, `WorkLocations` |
| `core/leave`, `core/wfh` | `LeaveRequests`, `LeaveTypes`, `WfhPolicies`, `WfhRequests` |
| `core/portal` | `ApplyLeave`, `ApplyWfh`, `MyRequests` |

In every file:

| Replace | With |
|---|---|
| Inline card border, radius and shadow | `<Card variant="borderless">` (theme, `W-83.1`) |
| Hand-built title, subtitle, badge and actions row | `PageHeader` |
| Hex colours | `theme.useToken()` values or a component prop |
| `borderRadius` on buttons, inputs and tags | Nothing: the theme sets them |
| `backgroundColor: '#2563eb'` on New Employee | Nothing: it overrides the theme's hover colour |

**Fixes from the review** (line numbers are the branch's)

| File | Fix |
|---|---|
| `EmployeeList.jsx:250` | "{total} Active Staff" counts whatever the filter returns, so it calls terminated people active. The badge reads "{total} employees" |
| `EmployeeList.jsx` | `statusColorStyles` goes; status uses `Tag color` as on `main`. The dead `icon` branch on `Avatar` goes |
| `UsersScreen.jsx:28`, `RolesScreen.jsx:80`, `AuditLogScreen.jsx:21`, `EmployeeList.jsx:237` | Headers that do not wrap become `PageHeader`. Remove the outer `padding: 24` on the first three so they line up with the others |
| `MyRequests.jsx` | Leave `rowKey` as on `main`; only the style change stays |

**Badges**

| Screen | `count` |
|---|---|
| `EmployeeList` | `{total} employees` |
| `Inbox` | `{total} pending` |
| `Calendars`, `WorkLocations`, `LeaveTypes` | The list's length with its noun |
| `LeaveRequests`, `WfhRequests` | The total with its noun |
| Every other screen | None |

Labels with no information go: "RBAC Matrix", "Multi-step Engine", "CSV Import Engine", "Security
Trail", "Identity & Access", "Org Master", "HR Onboarding", "Temporary Authority", "Remote Work
Rules", "Remote Work", "Employee Self-Service".

**Subtitles:** plain English, and only what is true. "Immutable chronological record…" on the audit
log goes. Keep every subtitle already on `main`.

## Out of scope

The other Core screens (`W-83.3`). Every new feature in the old W-80 draft: employee stepper, Save
Draft, inbox metric cards, one-click approve, editable role matrix, card grids.

## Standing rules

Creates no table, no migration, no backend code, no money handling. No module references another.
Every element `id`, `data-testid`, permission check, route and service call stays as on `main`.

## Tests

| File | Covers |
|---|---|
| `core/employee/EmployeeList.test.jsx` | Filtered to Terminated, the badge reads "N employees" and "Active" appears nowhere in it |

Existing suites and the `e2e` job stay green unchanged. A test may change only if it pinned a
heading level or a removed label, and the commit says which.

## Verification

```bash
cd code/frontend && npx vitest run && npm run lint && npm run build
git diff --relative --name-only origin/main...HEAD -- src/core | xargs grep -nE "#[0-9a-fA-F]{3,8}\b"
git -C ../.. diff --stat origin/main...HEAD -- code/backend
```

The first line passes. The grep prints nothing. The last prints nothing. The `e2e` job is green.
By eye, at 1280 px and 375 px: `/employees`, `/users`, `/roles`, `/audit`, `/approvals`. Headers
wrap and nothing runs off the right edge.

## Risks

A form move in `EmployeeCreate` could drop a field. `EmployeeCreate.test.jsx` and the `e2e` job
guard it.

## Rollback

Revert the squash commit. No data changes. Delete `feature/ui-professional-specs` after this lands.
