# Feature: HRMS screens use the new look; timesheet weekends turn grey

| Field | Value |
|---|---|
| **Feature ID** | `W-81.1` · `FE-HRMS` |
| **Owner** | krushna |
| **Apps touched** | `code/frontend` (`src/hrms`, `.eslintrc.cjs`) |
| **Related gaps** | founder 2026-11-04: UI redesign; reverses the look from `D-137` |
| **Status** | Ready |
| **Blocked by** | `W-83.1` |

## What and why

HRMS screens pick up the theme from `W-83.1`, but their headers and cards need to match Core. The
weekly timesheet also marks weekends in red and pink (`D-137`), which reads as an error for a normal
non-working day. The founder decided on 2026-11-04 to make them neutral grey. Nothing else about how
a screen works changes.

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | HRMS screens show the new look, with no hard-coded colours | 1 |
| Frontend area | `hrms` | 1 |

## Changes

| Kind | Files | Change |
|---|---|---|
| Pages | `attendance/AttendanceLog`, `AttendanceSettings`, `MyAttendance` · `dashboard/HrmsDashboardPage` · `projects/MyWork`, `ProjectList`, `ProjectPage` · `requests/MyOvertime`, `MyRegularizations`, `OvertimeDetail`, `OvertimeLog`, `RegularizationDetail`, `RegularizationLog` · `timesheet/TimesheetList`, `TimesheetWeek` · `timesheetreview/EntryView`, `ReviewPage`, `WeekView` | `PageHeader`, `<Card variant="borderless">`, tokens |
| Parts | Every other non-test `.jsx` in `src/hrms` | Tokens only, where they hold a literal |
| Lint | `.eslintrc.cjs` | Hex rule on for `src/hrms/**/*.jsx` |

**Timesheet weekends** (`hrms/timesheet/TimesheetWeek.jsx`)

| Line | Today | After |
|---|---|---|
| `:311` | Weekend header `#cf1322` | `token.colorTextSecondary`, weight 600 |
| `:329-330` | Box `#fff1f0` with `#ffa39e` border; weekday `#ffffff` with `#d9d9d9` | Weekend `token.colorBgLayout` with `token.colorBorderSecondary`; weekday `token.colorBgContainer` with `token.colorBorder` |
| `:351`, `:372` | Weekend input border `#ffccc7` | Remove, so inputs use the theme border |
| `:440` | Total cell `#fff1f0` with `#ffa39e` | `token.colorBgLayout` with `token.colorBorderSecondary` |
| `:445` | Total text `#cf1322` | Remove. Over the daily limit stays `type="danger"` |

Red now only means a problem: a day over its limit and an invalid cell stay red.

**Stays exactly as it is** (founder 2026-11-04)

The description box stays inline in each day cell, and stays mandatory when hours are entered
(`D-105`, `hrms/timesheet/weekGrid.js:227`). No popover.

**Emphasis (`W-83.5`, founder 2026-11-05):** one bold text per table row, the name; ids and codes `type="secondary"`; no `fontWeight` literals. Row height comes from the theme.

## Out of scope

Each of these is new behaviour, and becomes its own ticket only if the founder asks.

| Group | Item |
|---|---|
| Not in the code | Billable hours and ratio, hourly rates, allocation %, geofencing or IP limits, Copy Previous Week, overtime multipliers |
| Other new behaviour | Sparklines, trend badges, leave-dot calendar, activity feed, live ticking clock, batch approval, kanban board, status filter pills, project health |

**The HRMS dashboard shows no payroll figure.** The old draft's "Total Payroll Disbursed" card would
pull payroll data into `hrms`, which the module rule forbids. The page shows only the blocks in the
`W-44` reply. Phone-specific fixed widths, such as the day column's `width: 140`, stay with `W-80.3`.

## Standing rules

Creates no table, no migration, no backend code, no money handling. `hrms` imports nothing from
`payroll`. Every element `id`, `data-testid`, permission check, route and service call stays as on
`main`.

## Tests

| File | Covers |
|---|---|
| `hrms/timesheet/TimesheetWeek.test.jsx:289` | Replaces the `D-137` test: weekend header and box show no red or pink, and differ from a weekday box. An over-limit weekend total still shows as danger |
| `hrms/timesheet/weekGrid.test.js` | Unchanged: hours with no description is still refused |

## Verification

```bash
cd code/frontend && npx vitest run && npm run lint && npm run build
grep -rnE "#[0-9a-fA-F]{3,8}\b" src/hrms --include=*.jsx --exclude=*.test.jsx
```

The first line passes. The grep prints nothing. The `e2e` job is green. By eye, at 1280 px and
375 px: `/hrms/dashboard`, `/hrms/attendance`, `/hrms/projects`, and a timesheet week with hours on a
Saturday. Save with no description is refused; a day over the limit shows red.

## Risks

Removing the weekend highlight could make someone enter hours in the wrong column. The grey and the
day name in the header should be enough; say so at merge if it does not look clear by eye.

## Rollback

Revert the squash commit. No data changes.
