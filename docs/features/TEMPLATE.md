# Feature: <NAME>

> Copy this file to `docs/features/<FEAT-ID>-<slug>.md` and fill it in.
> Structure derived from the archived `FEATURE_DEVELOPMENT_TRACKER.md`.
> One file per feature. Do not create a combined tracker.

| Field | Value |
|---|---|
| **Feature ID** | FEAT-000 |
| **Owner** | |
| **Apps touched** | HRMS_Backend / HRMS_Frontend / Payroll-Bend-SBoot / Payroll-Fend-react |
| **Related gaps** | BUG-00x, DEBT-0xx (`GAP_INVENTORY.md`) |
| **Status** | Draft / Approved / In progress / Verified |
| **Approved by** | *(founder approval is required before implementation — `CONVENTIONS.md` rule 1)* |
| **Approved on** | |

---

## 1. Problem

What is wrong or missing today. Cite evidence — `file:line`, a gap ID, or a
reproduction. Not a solution.

## 2. Scope

**In scope**

-

**Out of scope**

-

## 3. Flow

```
[Actor] --> [Screen] --> [Endpoint] --> [Service] --> [Table]
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | | |
| Service | | |
| ServiceImpl | | |
| Entity | | |
| Repository | | |
| DTO | | |
| Enumeration | | |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| | | | | |

## 5. Frontend changes

| File | Change |
|---|---|
| | |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| | | |

## 6. Database changes

> Flyway only. Never `ddl-auto`. See `CONVENTIONS.md` rule 4.

| Migration | Tables | Tenant-aware? | Reversible? |
|---|---|---|---|
| `V__` | | yes / no | |

- [ ] `tenant_id` present on every new table (`CONVENTIONS.md` rule 7)
- [ ] Index on `tenant_id` plus lookup columns (DEBT-018)
- [ ] Money columns are `BigDecimal` with explicit precision and scale (`CONVENTIONS.md` §2)
- [ ] Expand / contract sequencing — no destructive step

## 7. Tests

> The `implementer` agent must add tests for what it changes.

| Type | File | Covers |
|---|---|---|
| Unit | | |
| Integration | | |

## 8. Verification

How to prove it works. Commands, expected output, screens to check.

```bash
# build / test commands
```

| Check | Expected | Result |
|---|---|---|
| | | |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| | | |

## 10. Rollback

What to do if this goes wrong in production.
