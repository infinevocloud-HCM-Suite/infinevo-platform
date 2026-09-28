# Feature: Employee self-service actions

| Field | Value |
|---|---|
| **Feature ID** | `W-46.5` · from ticket #62 · `CORE-19`, `CORE-07` |
| **Spec file** | `docs/target-state/features/W-46-5-self-service-actions.md` |
| **Owner** | unassigned |
| **Apps touched** | `code/frontend/src/core/portal` only. No backend, no migration |
| **Related gaps** | DEBT-026 (prevented), DEBT-013 (discounted) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-28 |
| **Blocked by** | `W-45`; `W-25` (the portal frame and read panels, karma); `W-16.3` (apply, withdraw, cancel endpoints, karma). `W-13.4` for the `update_own` sections is on `main` (`e699699`) |
| **Size** | **M** |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | none | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an employee applies for leave, follows it, and keeps their own contact details current, all from `/me`, and never sees anything that is not theirs | 1 |
| Frontend area | `src/core/portal` | 1 |

Within cap.

---

## 1. Problem

**The portal reads; nobody writes.** `W-25` ships the portal frame at `/me` and five read panels (`W-25-self-service-portal.md` §2, §5) and says "Editing anything" is out of scope, "applying for leave is `W-16.3`'s screen". `W-16.3` says the opposite: "the apply-for-leave screen — the portal's is `W-25`, and this ticket ships no frontend" (`W-16-3-request-approval.md:67`). Both point at each other; the screen is unowned. This ticket owns it, and every other write an employee makes about themselves.

| Frozen screen | What the employee could do | Ported? |
|---|---|---|
| `legacy/HRMS_Frontend/src/components/Leaves/Leave.jsx:1249-1369` | apply: from, to, half-day period, reason, late-application reason, attachment | **yes**, the fields; MUI markup rewritten |
| `legacy/HRMS_Frontend/src/components/Leaves/LeaveBalance.jsx` | see balance | read panel — `W-25` |
| `legacy/Payroll-Fend-react/src/pages/mainPages/userPortal/userProfile.js` | see profile | read panel — `W-25`; **edit** of contact is new |
| `legacy/Payroll-Fend-react/src/pages/mainPages/userPortal/ChangePassword.js` | change password | **no** — Keycloak account console owns it (`CORE-02`) |
| `legacy/Payroll-Fend-react/src/pages/mainPages/userPortal/userInvestment/*`, `Reimbursement/*` | declare, claim | Payroll — `W-47.3`, `W-47.4` |
| `legacy/Payroll-Bend-SBoot/.../EmployyePortalContoller.java` | portal endpoints | replaced by `W-25`'s `/me/panels`; the typo is not carried (DEBT-013) |

## 2. Scope

**In scope**

- Apply for leave: type (eligible types for me), from, to, half-day and period, reason, attachments (document ids from `W-21` upload when on `main`; the field is hidden until then), save as draft or submit
- My requests: list with status, detail with the approval trail (reusing `W-46.2`'s `LeaveRequestDetail` read-only), submit a draft, withdraw a pending request, cancel an approved one before it starts, each with reason
- Edit my personal and contact sections through the `update_own` path (`W-13.4`; `EmployeeDetailController` `PUT /personal`, `PUT /contact`), reusing `W-46.1`'s `SectionTab`
- Registering these as actions on `W-25`'s `MyLeave` and `MyProfile` panels, not as new panels

**Out of scope**

- New panels, panel ordering, entitlement filtering — `W-25`
- Reading balances, documents, payslips, timesheet — `W-25` panels
- Identification, employment, bank edits by the employee — no `update_own` on them (`W-13.4`), by design
- Change password, photo, preferences — Keycloak / later
- Anything Payroll (declarations, claims) or HRMS (timesheet, regularization) — `W-47.x`, `W-48`, `W-40`

## 3. Flow

```
[employee] --> /me                       (W-25 PortalLayout; panels from /me/panels)
           --> /me/leave                 (W-25 MyLeave panel)  + this ticket's "Apply" and request list
                 Apply                   --> leaveTypeService.eligible(me) ; leaveRequestService.create({..., submit})
                 Submit draft            --> leaveRequestService.submit(id)
                 Withdraw / Cancel       --> leaveRequestService.withdraw(id, reason) / cancel(id, reason)
                 My requests             --> leaveRequestService.list({employeeId: me, page})
           --> /me/profile               (W-25 MyProfile panel) + "Edit" on Personal and Contact
                 Save                    --> employeeService.saveSection(me, 'personal' | 'contact', body)
```

`me` is the employee id the token resolves to — `currentEmployee()` on the server (`W-13.4`); the client learns it from the `MyProfile` panel's response, never from storage.

## 4. Backend changes

None. `core.leave.apply`, `core.leave.read_own`, `core.employee.update_own` exist or arrive with `W-16.3` and `W-11.3`.

## 5. Frontend changes

`W-45` contract throughout. Files live under `src/core/portal`; `W-25` places its panels under `src/shell/portal` (`W-25` §5), which contradicts `W-45` §5b (a screen is not shell code). **Recorded as doc drift, not resolved here:** whichever folder `W-25` lands in, this ticket imports its panels from `@shell/portal` or `@core/portal` and the lint rule for `src/core/**` gains that one allowance if needed.

| File | Change |
|---|---|
| `src/core/leave/leaveRequestService.js` (from `W-46.2`) | add `create(body)`, `submit(id)`. If `W-46.2` has not merged, this ticket creates the file with all seven calls and `W-46.2` extends it |
| `src/core/portal/ApplyLeave.jsx` | **new.** `LeaveRequestFields` from `W-46.2` (type, from, to, half-day, period, reason, attachments) with two buttons: Save draft (`submit: false`), Submit. Working days and the balance after, from the response and the `MyLeave` balance. A `409`/validation from `exceed_balance_mode` shows the envelope message. Fields from `Leave.jsx:1249-1369`; the late-application reason is dropped — no such rule in `W-16.1` |
| `src/core/portal/MyRequests.jsx` | **new.** Table: type, dates, days, status `Tag`, submitted; row opens `LeaveRequestDetail` (read-only); actions Submit (draft), Withdraw (pending), Cancel (approved, before start) each with a reason `Modal` |
| `src/core/portal/EditOwnSection.jsx` | **new.** Wraps `W-46.1`'s `SectionTab` for `personal` and `contact` with the caller's own id; hidden without `core.employee.update_own` |
| `src/core/portal/index.js` | **new.** Exports `leaveActions` (`ApplyLeave`, `MyRequests`) and `profileActions` (`EditOwnSection`) for the `W-25` panels to render in their action slots |
| `src/core/index.js` | `routes` gains the two below |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/me/leave/apply` | `ApplyLeave` | inside `PortalLayout`; rendered only when `/me/panels` returned `leave` and the caller holds `core.leave.apply` |
| `/me/leave/requests/:id` | `LeaveRequestDetail` (read-only) | same |

**Contract with `W-25`.** `MyLeave` and `MyProfile` accept an `actions` slot. If `W-25` merges without one, this ticket adds the slot to those two panel files — the only `W-25` files it touches.

## 6. Database changes

None.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `src/core/leave/leaveRequestService.test.js` | `create` posts to `/v1/leave-requests` with `submit`; `submit` posts to `/{id}/submit` |
| Component | `ApplyLeave.test.jsx` | eligible types come from `eligible(me)`; Save draft sends `submit: false`; Submit sends `true`; a balance error renders the envelope message; attachments hidden when the upload service is absent |
| Component | `MyRequests.test.jsx` | Withdraw only on `PENDING`; Cancel only on `APPROVED` with a future start; each requires a reason |
| Component | `EditOwnSection.test.jsx` | saves through `saveSection(me, ...)`; hidden without `core.employee.update_own` |
| Component | `src/core/portal/isolation.test.jsx` | no service call in this folder ever takes an employee id other than `me` |

## 8. Verification

```bash
cd code/frontend && npm ci && npm run lint && npm test
docker compose -f infra/docker/compose.yml up -d
# browser: log in as an employee-role user of Acme, open /me
```

| Check | Expected |
|---|---|
| apply | Casual leave, two days, submit; `MyRequests` shows `PENDING`; the manager's inbox (`W-46.4`) shows it |
| draft | save a draft; it lists as `DRAFT` with Submit available |
| withdraw | withdraw the pending request with a reason; status `WITHDRAWN`; the manager's inbox row is gone |
| balance | with `YEAR_END_LIMIT` on the type, applying beyond the limit shows the refusal message |
| profile | change mobile number in Contact; reload; `/employees/:id` as admin shows the new number |
| not mine | typing another employee's request id into `/me/leave/requests/:id` shows the not-found or forbidden envelope |
| Payroll-only | a Payroll-only tenant's employee sees Apply and the request list; nothing mentions HRMS (`D-35`: leave is Core) |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `W-25` lands its panels in `src/shell/portal` and the lint rule blocks importing them from `src/core` | high | one allowance in `.eslintrc.cjs` for `@shell/portal`; recorded above as drift for the founder to settle in `W-25` |
| `W-46.2` and this ticket both create `leaveRequestService.js` | medium | §5 row 1 says who creates and who extends; whichever merges second rebases |
| The employee id leaks in from a route or storage | low | `isolation.test.jsx`; `me` comes only from the profile panel response |
| Attachments need `W-21` | medium | field hidden until the upload service exists; nothing else waits |

## 10. Rollback

Frontend only. Revert the branch; `W-25`'s read panels keep working.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | no script |
| `Money` / `BigDecimal` | no money |
| Index | none |
| Expand / contract | nothing |
| No module references another | `src/core/portal` imports `@shared/*`, `@shell/screens`, `@core/leave`, `@core/employee` and the `W-25` panels; nothing from `@payroll` or `@hrms` |
| No write to `legacy/` | read only |

## 12. Gap inventory

| ID | Overlap | Decision |
|---|---|---|
| DEBT-026 (admin/user file pairs) | apply vs record-on-behalf; own vs admin section edit | **prevented**: `LeaveRequestFields` and `SectionTab` are shared with `W-46.2` and `W-46.1` |
| DEBT-013 (`EmployyePortalContoller`) | portal endpoints | **discounted** — replaced by `W-25`'s `/me/panels`; nothing ported from that class |

## 13. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `src/core/leave` | `create`, `submit` on the service; tests |
| 2 | `src/core/portal` | `ApplyLeave`, `MyRequests`, tests |
| 3 | `src/core/portal` | `EditOwnSection`, `index.js`, isolation test; `src/core/index.js` registration; the panel action slots if `W-25` lacks them |

## 14. Decisions and doc drift

| # | Question | Answer |
|---|---|---|
| 1 | Who owns the apply-for-leave screen? | **This ticket.** `W-25` §2 and `W-16-3:67` each assign it to the other |
| 2 | Actions inside `W-25`'s panels or separate pages? | **Inside the panels**, via an `actions` slot. The employee should not learn two portals |
| 3 | Late-application reason (`Leave.jsx:1279`)? | **Dropped.** No policy rule in `W-16.1` reads it; the free-text reason covers it |

**Doc drift for the founder:** `W-25-self-service-portal.md` §5 places screens in `src/shell/portal`; `W-45-frontend-shell.md` §5b forbids `src/core` importing `@shell/*` except `@shell/screens`. One of the two changes; recommend `W-25` moves its panels to `src/core/portal`. Not edited here.
