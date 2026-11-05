# Feature: Light sidebar, one typeface, tables that feel alive

| Field | Value |
|---|---|
| **Feature ID** | `W-83.7` · `FE-SHELL` |
| **Owner** | Claude (`dev-claude-w83`) |
| **Apps touched** | `code/frontend` (`src/shared/theme.js`, `src/shared/ui`, `src/shell`, `index.html`) |
| **Related gaps** | founder 2026-11-05: "a professional and latest styling look and feel" |
| **Status** | Ready |
| **Blocked by** | `W-83.5` |

## What and why

`W-83.5` fixes density and the top bar. Three things still date the product: a dark navy side menu
with no icons (`shell/AppShell.jsx:151-161`, `theme.js` `siderBg`), the system font stack
(`theme.js:27-28`), and tables that do nothing on hover and lose their header on scroll. Current
SaaS products (Rippling, Deel, Linear) use a white side menu with line icons, one typeface, sticky
headers and actions that appear on the row. Nothing about how a screen works changes.

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | The side menu is white with icons and collapses; tables keep their header while scrolling | 1 |
| Frontend area | app chrome: `shared`, `shell` | 1 |

## Changes

| Rule | Where | Value |
|---|---|---|
| White side menu | `theme.js` `Layout.siderBg`, `Menu` | `siderBg: colorBgContainer`; `Menu` light: `itemSelectedBg: colorInfoBg`, `itemSelectedColor: colorPrimary`; 1 px right border |
| One line icon per top-level item | `shell/navigation` item builder (`AppShell.jsx:263`) | `@ant-design/icons` outlined set, 16 px; a fixed map from module key to icon, with `AppstoreOutlined` as the fallback |
| Collapses to icons | `AppShell.jsx:182-189` | `Sider collapsible`, trigger at the bottom, width 64 when collapsed; the choice is kept in `localStorage` key `shell.sider.collapsed`; phone drawer unchanged |
| One typeface | `index.html`, `theme.js` `fontFamily` | Inter, self-hosted under `public/fonts` (no Google Fonts call), weights 400, 500, 600; `font-feature-settings: 'tnum'` on `MoneyText` and date cells |
| Sticky table header | `shared/ui` wrapper or `Table` default via `ConfigProvider` | `sticky` on every `Table` with more than 10 rows per page |
| Hover row actions | `shared/ui/RowActions.jsx` (new) | Icon buttons that show on row hover and on keyboard focus; replace "Actions" columns one screen at a time, starting with `/employees` |
| Status as a dot | `shared/ui/StatusDot.jsx` (new) | 8 px dot plus plain text; `Tag` stays for counts and roles |
| Borders, not shadows | `theme.js` `Card.boxShadowTertiary`, `Button.primaryShadow` | Removed; 1 px `colorBorderSecondary` everywhere |
| Focus ring | `theme.js` `controlOutline` | 2 px `colorPrimary` at 30 % |

## Out of scope

Dark mode. Any change under `core`, `hrms` or `payroll` except swapping the `/employees` Actions
column to `RowActions`. Card grids for lists.

## Standing rules

Creates no table, no migration, no backend code, no money handling. No module references another.
Every element `id`, `data-testid`, permission check, route and service call stays as on `main`.
`shell/phone.css` and the `W-80.1` phone behaviour stay.

## Tests

| File | Covers |
|---|---|
| `shell/AppShell.test.jsx` | Collapse toggles the width and is remembered; every top-level item has an icon |
| `shared/ui/RowActions.test.jsx` | Actions are reachable by keyboard without hover |
| `shared/ui/StatusDot.test.jsx` | Renders the text, and the colour comes from a token |
| `shared/theme.test.js` | `siderBg` is white; no shadow tokens left |

## Verification

```bash
cd code/frontend && npx vitest run && npm run lint && npm run build
git diff --name-only origin/main...HEAD | grep -vE "^code/frontend/(src/(shared|shell)/|index.html|public/fonts/)|EmployeeList|\.md$"
grep -rn "fonts.googleapis" src index.html
```

The first line passes. The second and third print nothing. The `e2e` job is green. By eye at
1440 px and 375 px: `/employees` with the menu open and collapsed, a scroll through 50 rows with
the header fixed, hover on a row.

## Risks

Inter changes line widths by a few percent, so a label that just fitted may wrap. Check
`/payroll/runs` and `/hrms/timesheet`, which are the densest.

## Rollback

Revert the squash commit. No data changes. `localStorage` key is ignored once the code is gone.
