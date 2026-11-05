# Feature: Tighter tables, quieter chrome: the new look becomes visible

| Field | Value |
|---|---|
| **Feature ID** | `W-83.5` · `FE-SHELL` |
| **Owner** | Claude (`dev-claude-w83`), reassigned 2026-11-05 |
| **Apps touched** | `code/frontend` (`src/shared/theme.js`, `src/shared/ui/PageHeader.jsx`, `src/shell`) |
| **Related gaps** | founder 2026-11-05: "the screen changes are not so visible and professional" |
| **Status** | Done — on `main` `02a94f5e`, 2026-11-05 |
| **Blocked by** | nothing (`W-83.1` on main) |

## What and why

`W-83.1`–`.4` moved colours into the theme but changed no layout, so on Azure dev the new look is
hard to see. What reads as professional is density, hierarchy and quiet chrome, not colour. This
ticket sets those once, in the theme and the shell, so every module picks them up without a
per-screen sweep. Nothing about how a screen works changes.

Seen on `/employees` 2026-11-05: rows about 60 px tall (32 px avatar, `core/employee/EmployeeList.jsx:152`),
three bold things per row (`:136`, `:156`, `:164`), a top bar carrying logo, company name, tagline,
three role chips and the user menu (`shell/Header.jsx:197-237`), and columns spread across a
1920 px screen because content has no maximum width (`shell/AppShell.jsx:182-200`).

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | Tables are 44 px per row and the top bar shows only logo, company, user | 1 |
| Frontend area | app chrome: `shared/theme.js`, `PageHeader`, `shell` | 1 |

Per-screen emphasis clean-up is not in this ticket. Core gets it in `W-83.6`; HRMS and
Payroll get it inside `W-81.1` and `W-82.1`, which are not started (one line added to each spec).

## Step 0: the founder sees it before it is built

Set the theme values below on a branch, run the app, and send the founder screenshots of
`/employees`, `/hrms/timesheet` and `/payroll/runs` at 1440 px. Build the rest only after the
founder says yes. This is a half-day and replaces weeks of "still not quite right".

## The rules

| Rule | Where it is set | Value |
|---|---|---|
| A table row is 44 px | `shared/theme.js` `components.Table` (`:70`) | `cellPaddingBlock: 10`, `cellPaddingInline: 12`, `cellFontSize: 14`, `headerBg` stays |
| `size="small"` tables stay small | the 34 screens that set it | Untouched; `cellPaddingBlockSM: 6` so they tighten too |
| One bold per row | `PageHeader` and `W-83.6`, `W-81.1`, `W-82.1` | The row's name is `strong`; ids and codes are `type="secondary"`; no `fontWeight` literals |
| An avatar in a list is 24 px | `core/employee/EmployeeList.jsx:152` (the only list avatar) | `size="small"`, no border |
| Content has a maximum width | `shell/AppShell.jsx` `Content` | `maxWidth: 1440`, centred, `padding: token.paddingLG` |
| Top bar: logo, company name, user menu | `shell/Header.jsx:213-228` | Tagline and role chips move into the user dropdown, as two menu items; `data-testid="tenant-tagline"` and `"role-chips"` stay on them |
| Side menu rows are 40 px | `shared/theme.js` `components.Menu` (`:59`) | `itemHeight: 40`, `itemMarginBlock: 2`, `itemMarginInline: 8`, `fontSize: 14` |
| Brand block matches the header | `shell/AppShell.jsx:120-136` | Height `headerHeight`, product name only; drop "Human Capital Management" |
| Page header is tighter | `shared/ui/PageHeader.jsx:26,51` | `marginBottom: token.margin`; title stays `level={4}`; subtitle one sentence, `fontSizeSM` |
| Impersonation banner is one line | `shell/Header.jsx:244-249` | `Alert` `banner`, `padding: token.paddingXS token.padding`, message unchanged |
| Headings use one scale | `shared/theme.js:30-33` | `fontSizeHeading3: 20`, `fontSizeHeading4: 16`; h1 and h2 unused in screens |

Every value comes from `theme.js` or a component prop. No hex or pixel literals in screens; the
`W-83.1` lint rule enforces this for `shared`, `core` and `shell`.

**Phone (`W-80.1`):** nothing here changes below 768 px except row height, which also applies.
`shell/phone.css` stays. Check `/employees` at 375 px scrolls sideways as before.

## Out of scope

| Why | Item |
|---|---|
| Per-screen | Bold sweep in Core (`W-83.6`), HRMS (`W-81.1`), Payroll (`W-82.1`); the 37 empty states |
| Not asked | Dark mode, a new font, icons in the side menu, a collapsed sidebar, dashboard cards |
| Behaviour | Anything a screen does: routes, permissions, ids, service calls, Approve and Pay |

## Standing rules

Creates no table, no migration, no backend code, no money handling. No module references another.
Every element `id`, `data-testid`, permission check, route and service call stays as on `main`. The
two `data-testid`s on the header move with their element and stay queryable.

## Tests

| File | Covers |
|---|---|
| `shared/theme.test.js` (new) | `Table.cellPaddingBlock` is 10 and `Menu.itemHeight` is 40, so a later edit cannot quietly undo density |
| `shell/Header.test.jsx` | Role chips and tagline render inside the open user menu, not in the bar; `compact` unchanged |
| `shell/AppShell.test.jsx` | Brand block shows the product name only |
| `core/employee/EmployeeList.test.jsx` | Still 9 green; avatar is `size="small"` |

## Verification

```bash
cd code/frontend && npx vitest run && npm run lint && npm run build
git diff --name-only origin/main...HEAD | grep -vE "^code/frontend/src/(shared|shell)/|EmployeeList|\.md$"
git -C ../.. diff --stat origin/main...HEAD -- code/backend
```

The first line passes. The second and third print nothing. The `e2e` job is green. By eye at
1440 px and 375 px, against a screenshot of `main` taken the same day: `/employees` rows are
44 px, the top bar has three things, content stops at 1440 px. The founder said yes at Step 0.

## Risks

The header `e2e` specs may query role chips in the bar; if so they move to the dropdown in the
same commit, and the commit says which. Nothing else unusual.

## Rollback

Revert the squash commit. No data changes.
