# Feature: The new look lives in the theme and shared page pieces

| Field | Value |
|---|---|
| **Feature ID** | `W-83.1` · `FE-SHARED` |
| **Owner** | krushna |
| **Apps touched** | `code/frontend` (`src/shared`, `.eslintrc.cjs`) |
| **Related gaps** | founder 2026-11-04: UI redesign, split from the `feature/ui-professional-specs` review |
| **Status** | Ready |
| **Blocked by** | nothing |

## What and why

The redesign moves the product to a royal blue, sky blue and slate look. Krushna's branch set that
look in `shared/theme.js` (`e0f690db`), then pasted the same styles into each screen: 186 added
lines carry hex colours, one card style appears 21 times and one badge 18 times. W-45 §5a and
`shared/README.md:103` forbid that, because the next restyle would mean editing every screen again.

This ticket puts the look in one place. The screen tickets (`W-83.2`–`.4`, `W-81.1`, `W-82.1`)
then use it and paste nothing.

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | Every screen picks up the new look from the theme | 1 |
| Frontend area | `shared` | 1 |

## Changes

Start from current `main`. Take `theme.js` from `origin/feature/ui-professional-specs`. Do not
bring over that branch's three specs or its tracker edits; these tickets replace them.

| File | Change |
|---|---|
| `shared/theme.js` | The branch's values, with the changes below |
| `shared/ui/PageHeader.jsx` (new) | One page header, built from theme tokens |
| `shared/ui/MoneyText.jsx` (new) | An amount in `formatMoney` text with `fontVariantNumeric: 'tabular-nums'`. No arithmetic |
| `shared/README.md` | Say screens use `PageHeader` and `MoneyText`; add the rule's name |
| `.eslintrc.cjs` | Move the top-level `no-restricted-syntax` list into a `const` and add the hex rule, switched on for `src/shared/**/*.jsx` only |

**Theme changes against the branch**

| Token | Value | Why |
|---|---|---|
| `fontFamily` | Remove `'Plus Jakarta Sans'` and `'Inter'` | Neither is loaded, so the font would vary by machine |
| File comment | Keep "One place, so a restyle is not 158 screens of work" | It is the rule this ticket enforces |
| `colorInfoBg`, `colorInfoBorder`, `colorInfoText` | `#e0f2fe`, `#bae6fd`, `#0369a1` | The sky badge colours, as tokens |
| `Card.boxShadowTertiary` | `0 0 0 1px #e2e8f0, 0 1px 3px 0 rgba(15, 23, 42, 0.05)` | `<Card variant="borderless">` gets the border and shadow with no inline style |
| `Card.borderRadiusLG` | `12` | One radius; the branch's screens mixed 12 and 16 |
| `Tag.borderRadiusSM` | `12` | Every tag is a soft pill with no inline style |

**`PageHeader` props**

| Prop | Type | Shows |
|---|---|---|
| `title` | string, required | A level 4 heading |
| `subtitle` | node | Secondary text under the title |
| `count` | string | A sky badge beside the title. Only for a real count, such as "12 employees". **Never a decorative label** |
| `onBack`, `backLabel` | function, string | A back button before the title |
| `actions` | node | The right side |

The outer row sets `flexWrap: 'wrap'` and a gap, so the actions drop to the next line on a phone
(`W-80.1`). A bottom border uses `colorBorderSecondary`.

**The hex rule**, for `W-83.3`, `W-83.4`, `W-81.1` and `W-82.1` to switch on for their own folders:

```js
{ selector: "Literal[value=/#[0-9a-fA-F]{3,8}\\b/]", message: 'Use a theme token, not a hex colour (W-45 §5a).' },
{ selector: "TemplateElement[value.raw=/#[0-9a-fA-F]{3,8}\\b/]", message: 'Use a theme token, not a hex colour (W-45 §5a).' },
```

An override replaces a rule's options, so each folder's block spreads the base list and adds these.

## Out of scope

Any screen file. Fixed pixel widths inside screens stay with `W-80.2`–`.4`, unwritten.

## Standing rules

Creates no table, no migration, no backend code. `MoneyText` only displays a value from the server
and does no calculation. `shared` imports no module.

## Tests

| File | Covers |
|---|---|
| `shared/ui/PageHeader.test.jsx` | Title is a heading; subtitle, count and actions show when given and not otherwise; back button calls `onBack`; the row wraps |
| `shared/ui/MoneyText.test.jsx` | Same text as `formatMoney` for a value and for an empty one; tabular figures set |

## Verification

```bash
cd code/frontend && npx vitest run && npm run lint && npm run build
grep -rnE "#[0-9a-fA-F]{3,8}\b" src/shared --include=*.jsx
```

The first line passes. The grep prints nothing. By eye: `npm run dev`, the new colours show on the
menu, buttons and tables at 1280 px and at 375 px.

## Risks

Until `W-82.1` lands, payroll screens with hard-coded colours keep the old blue and green beside
the new theme (for example `payroll/payrun/RunPage.jsx:467,493,498,523,532,541`). Accepted: it is
cosmetic and short-lived.

## Rollback

Revert the squash commit. No data changes.
