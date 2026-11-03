# Feature: Leave balances say when a leave type has ended

| Field | Value |
|---|---|
| **Feature ID** | `W-77.1` · `CORE-LEAVE` |
| **Owner** | claude |
| **Apps touched** | `code/backend/core` (`leave`), `code/frontend` (`src/core/leave`) |
| **Related gaps** | founder 2026-11-02: an ended leave type (aksinha's Comp Off, ended 2025-12-30) shows as usable; legacy tags it "Expired" (`legacy/HRMS_Frontend/src/components/Leaves/LeaveBalance.jsx:88-112`) |
| **Status** | **Done** — `663934d1` |
| **Blocked by** | nothing |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | A balance on an ended or switched-off leave type is marked expired | 1 |
| Frontend area | `core/leave` | 1 |

## Rule

A balance is **expired** when its leave type is switched off, or its Valid To is before the as-of date or today, whichever is earlier. The days are still shown, as history; nothing is deleted.

## Changes

| File | Change |
|---|---|
| `core/leave/LeaveBalanceResponse.java` | Adds `leaveTypeValidTo` and `expired` |
| `core/leave/LeaveBalanceServiceImpl.java` | Sets them from the leave type (`computeBalance`) |
| `core/leave/Allocations.jsx` | One column per current leave type only (active, not ended); the expanded rows tag an expired balance "Expired" and list it last |

No table, no Flyway script.

## Tests

| File | Covers |
|---|---|
| `LeaveBalanceServiceTest` | Ended type, switched-off type and current type give `expired` true, true, false; a 31 December as-of (Allocations) does not end a type still running today |
| `core/leave/Allocations.test.jsx` | An ended type gets no column; its balance shows the Expired tag |

## Verification

```bash
cd code/backend && ./mvnw -B -pl core -am verify
cd code/frontend && npx vitest run src/core/leave && npm run lint && npm run build
```
