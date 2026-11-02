# Feature: My Leave says when a leave type has ended

| Field | Value |
|---|---|
| **Feature ID** | `W-77.2` · `CORE-LEAVE` |
| **Owner** | claude |
| **Apps touched** | `code/frontend` (`src/shell/portal`) |
| **Related gaps** | founder 2026-11-02, same as `W-77.1`, on the employee's own page |
| **Status** | In flight — dev-claude |
| **Blocked by** | `W-77.1` (the `expired` field) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | My Leave tags a balance on an ended leave type "Expired" | 1 |
| Frontend area | `shell/portal` | 1 |

## Changes

| File | Change |
|---|---|
| `shell/portal/panels/MyLeave.jsx` | A balance card with `expired` shows an "Expired" tag and "days (expired)" instead of "days available"; expired cards come last |

## Tests

| File | Covers |
|---|---|
| `shell/portal/panels/MyLeave.test.jsx` | Expired card tagged and placed after current ones |

## Verification

```bash
cd code/frontend && npx vitest run src/shell/portal && npm run lint && npm run build
```
