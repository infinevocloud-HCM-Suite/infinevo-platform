# Feature: EPF and ESI settings say what each switch does

| Field | Value |
|---|---|
| **Feature ID** | `W-76.4` · `PAY-STATUTORY` |
| **Owner** | claude |
| **Apps touched** | `code/frontend` (`src/payroll/settings`) |
| **Related gaps** | founder 2026-11-01: employer PF / ESI structure switch is display only and must say so; EDLI / admin "in CTC" means something only when they are in the salary structure |
| **Status** | Ready to merge — `dev-claude-w76` |
| **Blocked by** | nothing; matches the pay-run rule in `W-76.3` |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | The EPF and ESI settings screens explain each structure switch and keep EDLI / admin "in CTC" off while they are out of the structure | 1 |
| Frontend area | `payroll/settings` | 1 |

## Changes

| File | Change |
|---|---|
| `payroll/settings/EpfScreen.jsx` | Notes: employer share "Display only. Employer PF is always paid when EPF is on and the employee is ticked for PF."; EDLI / admin structure "On: shown in the salary and paid in the pay run. Off: neither." EDLI / admin "in CTC" switches disabled, with a hint, while their structure switch is off; saved as off then |
| `payroll/settings/EsiScreen.jsx` | Note: employer share "Display only. Employer ESI is always paid when ESI is on and the employee is ticked for ESI." |

No backend change, no table, no Flyway script.

## Tests

| File | Covers |
|---|---|
| `payroll/settings/EpfScreen.test.jsx` | EDLI "in CTC" disabled while EDLI is out of the structure, admin's enabled while admin is in; a stored EDLI "in CTC" with structure off is saved as off |

## Verification

```bash
cd code/frontend
npx vitest run src/payroll/settings
npm run lint
```

| Check | Expected |
|---|---|
| Vitest | all pass |
| Lint | `0 problems` |

## Rollback

Revert the commit.
