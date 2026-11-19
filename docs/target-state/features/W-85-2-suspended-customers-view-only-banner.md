# Feature: Suspended customers see a banner and a view-only app — frontend

| Field | Value |
|---|---|
| **Feature ID** | `W-85.2` · `CORE-SECURITY` |
| **Owner** | krushna (`dev-krushna`) |
| **Apps touched** | `code/frontend/src/shell`, `code/frontend/src/shared/api` (one handler) |
| **Related gaps** | founder 2026-11-13 roadmap, "Priority 1 — customer lifecycle" |
| **Status** | Ready — approved 2026-11-19. Not built. |
| **Approved by** | founder |
| **Approved on** | 2026-11-19 |
| **Blocked by** | `W-85.1` — it adds `subscriptionStatus` to the navigation feed and lets reads through |

## Founder decision (2026-11-19)

| Customer status | Read and export | Change |
|---|---|---|
| `ACTIVE`, `PAST_DUE` | Yes | Yes |
| `SUSPENDED`, `CANCELLED` | **Yes — in Core, HRMS and Payroll** | **No — anywhere** |

A suspended or cancelled customer keeps its records for statutory retention: employees,
attendance, leave, payslips. It can export them. It cannot change anything.

## Split (founder, 2026-11-19)

| Ticket | Holds | Blocked by |
|---|---|---|
| `W-85.1` | Backend: the write guard, cancelled treated as suspended, reads let through, status in the navigation feed | nothing |
| `W-85.2` | Frontend: the banner and view-only mode in place of the full-screen Suspended page | `W-85.1` |

## Size

| Axis | This spec | Limit | Status |
|---|---|:---:|---|
| Backend module | none | 1 | Pass |
| Flyway migration | none | 1 | Pass |
| Testable behaviour | a suspended tenant's user sees the banner, every screen loads, write buttons are hidden | 1 | Pass |
| Frontend area | `shell` (plus one handler in `shared/api/client.js`) | 1 | Pass |

Size **S**.

---

## 1. Problem

| # | Today | Evidence |
|---|---|---|
| 5 | The frontend replaces the whole app with a full-screen "Suspended" page on any `TENANT_SUSPENDED` reply. One blocked save would hide every screen. | `code/frontend/src/shell/ShellBoundary.jsx:95-97,117-119`; `code/frontend/src/shared/api/client.js:99,111-117` |
| 6 | No API tells the frontend the subscription status. | `code/backend/core/src/main/java/com/infinevo/core/navigation/NavigationResponse.java:20-27` |

## 2. Scope

**In scope**

- The full-screen Suspended page is replaced by a banner and view-only mode.
- Write buttons are hidden in one central place, `useCan`.
- A refused write shows a toast instead of replacing the app.

**Out of scope**

- Every backend change — `W-85.1`.
- The mobile shell (`code/frontend/src/mobile/`) — it does not use `ShellBoundary`; a blocked write there shows the server's message.

## 3. Flow

```
GET /api/v1/navigation --> subscriptionStatus --> useNavigation.viewOnly
        viewOnly --> AppShell shows the banner
        viewOnly --> useCan returns false for every action that is not .read / .read_own
a write refused with TENANT_SUSPENDED --> client.js --> toast, the screen stays
```

## 4. Backend changes

None. **API used:** `GET /api/v1/navigation` (field `subscriptionStatus`, added by `W-85.1`).

## 5. Frontend changes

| File | Change |
|---|---|
| `code/frontend/src/shell/navigation/useNavigation.js` | Expose `subscriptionStatus` from the feed and `viewOnly` (`SUSPENDED` or `CANCELLED`). |
| `code/frontend/src/shell/navigation/useCan.js` | When `viewOnly`, return `false` unless the action code ends in `.read` or `.read_own`. This is the one central place that hides write buttons; `useCan` already has 115 call sites. |
| `code/frontend/src/shell/AppShell.jsx` | Show the banner above the page content when `viewOnly`: "Your subscription is suspended. You can view and export your records. Changes are switched off. Contact support to restore service." |
| `code/frontend/src/shell/ShellBoundary.jsx` | Remove the `suspended` state and the full-screen `<Suspended />` swap (lines 95-101, 117-119). |
| `code/frontend/src/shared/api/client.js` | On `TENANT_SUSPENDED`, the handler now shows a toast "Changes are switched off while the subscription is suspended" instead of replacing the app. |
| `code/frontend/src/shell/screens/Suspended.jsx`, `screens/index.js` | Delete the screen and its export. |
| `code/frontend/src/shell/phone.css` or the shell's existing stylesheet | Banner style. (`core/core-ui.css` named in the first draft is not the shell's file.) |

**Routes added:** none.

A button that does not use `useCan` stays clickable; the server refuses the write and the
toast explains. That is acceptable for this ticket.

## 6. Database changes

None.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Component | `code/frontend/src/shell/AppShell.test.jsx` | Banner shows when the feed says `SUSPENDED` or `CANCELLED`, not for `ACTIVE` or `PAST_DUE`; replace the old "renders Suspended screen full-page" case (line 299) |
| Component | `code/frontend/src/shell/navigation/useCan.test.js` | View-only: `core.employee.read` true, `core.employee.manage` false |
| Component | `code/frontend/src/shell/ShellBoundary.test.jsx` | A `TENANT_SUSPENDED` reply no longer replaces the children |

## 8. Verification

```bash
cd code/frontend && npm test -- src/shell && npm run lint
```

| Check | Expected | Result |
|---|---|---|
| Open any screen as a suspended tenant's HR | Banner shows, screens load, write buttons gated by `useCan` are hidden | not run |
| Same for a cancelled tenant | Same | not run |
| Press a write button that is not gated by `useCan` | Toast "Changes are switched off while the subscription is suspended"; the screen stays | not run |
| Export from a list screen | The export starts | not run |
| `ACTIVE` and `PAST_DUE` tenants | No banner, all buttons as today | not run |
| Platform admin lifts the suspension; the user reloads | Banner gone, buttons back | not run |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A button outside `useCan` looks active | Medium | Server refuses; toast explains |
| An action code that reads but does not end in `.read` is hidden by mistake (for example an export action) | Medium | List the action codes that do not end in `.read` / `.read_own` at build time and allow the read-only ones by name in `useCan`; put the list in the pull request |

## 10. Rollback

Revert the commit. The full-screen Suspended page returns. No data is changed.
