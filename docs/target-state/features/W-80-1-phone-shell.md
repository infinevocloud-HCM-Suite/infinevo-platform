# Feature: Every screen reads cleanly in a phone browser

| Field | Value |
|---|---|
| **Feature ID** | `W-80.1` · `PLAT` (frontend shell) |
| **Owner** | claude |
| **Apps touched** | `code/frontend` (`src/shell`, plus `src/test/setup.js`) |
| **Related gaps** | founder 2026-11-03: "if opened in a mobile browser it should look clean" |
| **Status** | Ready |
| **Blocked by** | nothing |

## What and why

Opened on a phone today, the fixed 220 px side menu covers the screen
(`src/shell/AppShell.jsx:111-115,160`), the header packs name, tagline, role chips and user name
in one row (`src/shell/Header.jsx:184-232`), and only 3 of 76 table screens scroll sideways, so
the rest widen the page. This ticket fixes all of it in the shell, once, for every screen.

"Phone" means below Ant Design's `md` breakpoint (768 px wide). At 768 px and above nothing
changes.

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | Below 768 px the shell shows a menu button and fits the screen | 1 |
| Frontend area | `shell` | 1 |

Fixed pixel widths inside individual screens (44 files) are **not** here: `W-80.2` (core),
`W-80.3` (hrms), `W-80.4` (payroll), written when needed. This ticket caps inputs at the
screen width, which hides most of them.

## Changes

| File | Change |
|---|---|
| `shell/AppShell.jsx` | `Grid.useBreakpoint()`; phone when `md === false` (unknown counts as desktop, so no flash). Phone: no `Sider`; the same brand block and menu in a left `Drawer` (`rootClassName="shell-nav-drawer"`, width `siderWidth`, dark), closed on every route change; content `marginLeft: 0`, padding `token.paddingSM` instead of `paddingLG`. Imports `phone.css` |
| `shell/Header.jsx` | New prop `onOpenMenu`: when set, a `MenuOutlined` button (`aria-label="Open menu"`) leads the header. New prop `compact`: hides tagline, role chips and the user's name; padding `token.padding`; logo max 96 px; tenant name ellipsed |
| `shell/phone.css` (new) | Under `@media (max-width: 767px)` only: table bodies scroll sideways (`.ant-table-content`, `.ant-table-body` `overflow-x: auto`); drawers other than the menu open full width; inputs, selects, pickers and number fields capped at `max-width: 100%`; card bodies and page headers padding 12 px; Space rows wrap |
| `test/setup.js` | The `matchMedia` stub answers `min-width` / `max-width` queries against `window.innerWidth` (jsdom: 1024), so tests stay desktop unless they set a phone width |

Modals need nothing: Ant Design already caps them at `calc(100vw - 32px)`.

## Standing rules

Creates no table, no migration, no backend code, no money handling. No module references
another: everything is in `shell`.

## Tests

| File | Covers |
|---|---|
| `shell/AppShell.test.jsx` | At 1024 px the side menu shows and no menu button; at 375 px no side menu, a menu button, the drawer opens with the feed's items and closes after a click |
| `shell/Header.test.jsx` | `compact` hides tagline, role chips and name; `onOpenMenu` shows the button and calls back |

## Verification

```bash
cd code/frontend && npx vitest run && npm run lint && npm run build
```

All pass. By eye: `npm run dev`, browser dev tools at 375 × 812 — menu button opens the menu,
a wide table scrolls inside the page, nothing runs off the right edge.
