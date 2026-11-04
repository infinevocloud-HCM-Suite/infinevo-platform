# Feature: The self-service portal uses the new look

| Field | Value |
|---|---|
| **Feature ID** | `W-83.4` · `FE-SHELL` |
| **Owner** | krushna |
| **Apps touched** | `code/frontend` (`src/shell/portal`, `.eslintrc.cjs`) |
| **Related gaps** | founder 2026-11-04: UI redesign |
| **Status** | Ready |
| **Blocked by** | `W-83.1` |

## What and why

The `/me` screens live in the shell, not in `core`, so they are their own ticket. They pick up the
theme from `W-83.1`, but their page headers and cards need to match. Nothing about how a screen
works changes.

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | `/me` screens use the shared header and card | 1 |
| Frontend area | `shell/portal` | 1 |

## Changes

| File | Change |
|---|---|
| `shell/portal/panels/MyProfile.jsx` | `<Card variant="borderless">`, tokens. Keep the Row/Col grid (`D-53`) |
| `shell/portal/panels/MyLeave.jsx` | Same. Keep expired tags and their last-place order (`W-77.2`), and equal-height cards (`D-56`) |
| `shell/portal/panels/MyWfh.jsx` | Same |
| `shell/portal/panels/MyPayslips.jsx` | Same; amounts use `MoneyText` |
| `shell/portal/PayslipPage.jsx` | `PageHeader`. Do not touch `PayslipDocument` (`W-82.1` leaves it too) |
| `shell/portal/PortalLayout.jsx` | Tokens only. Tab order stays as `D-87` set it |
| `.eslintrc.cjs` | Hex rule on for `src/shell/**/*.jsx`. The folder has none today, so this keeps it that way |

## Out of scope

Payslip Download (founder 2026-11-04, still held back). Circular balance meters, digital ID card,
address update request, leave expiry banner, payslip card gallery.

## Standing rules

Creates no table, no migration, no backend code. `MoneyText` only displays a server value. No module
references another; the shell composes them as today. Every element `id`, `data-testid`, route and
service call stays as on `main`.

## Tests

No new behaviour. Existing `shell/portal` suites stay green, including the `D-87` tab order test.

## Verification

```bash
cd code/frontend && npx vitest run && npm run lint && npm run build
grep -rnE "#[0-9a-fA-F]{3,8}\b" src/shell --include=*.jsx --exclude=*.test.jsx
```

The first line passes. The grep prints nothing. The `e2e` job is green, including its squeezed-text
check on `/me` tabs. By eye, at 1280 px and 375 px: every `/me` tab, and one payslip.

## Risks

Nothing unusual.

## Rollback

Revert the squash commit. No data changes.
