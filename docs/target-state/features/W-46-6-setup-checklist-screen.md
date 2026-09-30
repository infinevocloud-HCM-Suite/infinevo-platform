# Feature: Setup checklist screen

| Field | Value |
|---|---|
| **Feature ID** | `W-46.6` · `CORE-05` |
| **Spec file** | `docs/target-state/features/W-46-6-setup-checklist-screen.md` |
| **Owner** | biren |
| **Apps touched** | `code/frontend/src/core/setup` · `code/backend/core` — `NavigationCatalogue.java` menu item only |
| **Related gaps** | none |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-30 — named by `W-46-3a` §14 row 2; split from the invitation screens, which are `W-46.7` |
| **Blocked by** | `W-45` only. `W-24.1` is on `main` (`288f9fa`) |
| **Size** | **S** |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` — one menu item | 1 |
| Flyway migration | none | 1 |
| Externally testable behaviour | an administrator sees the tenant's setup steps with progress and can skip one with a reason | 1 |
| Frontend area | `src/core/setup` | 1 |

Within cap. Checklist plus invitations in one ticket crossed two behaviours and two areas.

---

## 1. Problem

`W-24.1` builds the checklist API and nothing renders it. `W-24-1-setup-checklist.md` §2 says "a frontend ticket consumes this"; none did until this one (`W-46-3a-org-setup-screens.md:164`).

| Frozen screen | Ported? |
|---|---|
| `legacy/Payroll-Fend-react/src/pages/mainPages/dashboardPage/onboardingDashboard.js` | **in shape only**: a progress bar over a list of steps. Its step list is hard-coded (`:18`) and its fetch is commented out (`:107-138`) |
| `legacy/Payroll-Fend-react/src/pages/mainPages/organizationRegister/setupNewOrganization.js` | **no** — a wizard. The target is a checklist whose steps are detected, not walked (`W-24-1` §1) |
| HRMS | nothing |

## 2. Scope

**In scope**

- One page at `/setup`: progress, and the steps grouped by module
- Each step shows one state: done, skipped (with its reason), to do, or new
- "Open" on a step goes to the screen that completes it, when that screen is in the navigation feed
- Skip with a required reason, for holders of `core.tenant.manage`
- Menu item `core.setup`

**Out of scope**

- Un-skip — no endpoint (`W-24-1` §4)
- Marking a step done by hand — completion is detected, never declared
- The screens the steps point to — `W-46.1`, `W-46.3a`, `W-47.*`
- Invitations — `W-46.7`

## 3. Flow

```
[admin] --> /setup --> setupService.get()            GET  /v1/setup-checklist
        --> Skip   --> setupService.skip(code, reason) POST /v1/setup-checklist/{code}/skip
                   --> refetch
        --> Open   --> navigate(path for the step)
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Config | `code/backend/core/.../navigation/NavigationCatalogue.java` | add `core.setup` (`nav.setup`, `/setup`, target `/api/v1/setup-checklist`, module `null`, action `core.tenant.read`) |

The validator refuses an item whose endpoint does not exist (`NavigationCatalogueValidator.java:58-68`), so the item rides here.

**API contract** — consumed, as on `main` (`288f9fa`):

| Call | Returns | Permission |
|---|---|---|
| `GET /api/v1/setup-checklist` | `steps[]`, `completedCount`, `skippedCount`, `totalCount`, `newCount`, `progressPercentage` | `core.tenant.read` |
| `POST /api/v1/setup-checklist/{stepCode}/skip` body `{reason}` | the step; `400` when the reason is blank or over 500 characters; `404` for an unknown step | `core.tenant.manage` |

Each step: `code`, `label`, `module` (`null`, `HRMS`, `PAYROLL`), `displayOrder`, `completed`, `skipped`, `skipReason`, `completedAt`, `firstSeenAt`, `newStep`.

Citations: `SetupChecklistController.java:26,36-37,43-52`, `SetupChecklistResponse.java`, `SetupStepResponse.java`.

## 5. Frontend changes

`W-45` contract throughout.

| File | Change |
|---|---|
| `src/core/setup/setupService.js` | **new.** `get()` and `skip(code, reason)` over `apiClient` |
| `src/core/setup/stepLinks.js` | **new.** Step code to path: `WORK_LOCATION` → `/org/work-locations`, `EMPLOYEE` → `/employees`. Payroll codes (`PAY_SCHEDULE`, `SALARY_COMPONENTS`, `EPF`, `ESI`, `PROFESSIONAL_TAX`) get their paths when `W-47` adds the screens |
| `src/core/setup/SetupChecklist.jsx` | **new.** Ant `Progress` from `progressPercentage`; one `List` per module group in `displayOrder`; a `Tag` per state; "Open" only when `stepLinks` has the code **and** the path is in the feed; "Skip" only with `useCan('core.tenant.manage')` and only on a step that is neither done nor skipped |
| `src/core/setup/SkipStepModal.jsx` | **new.** Required reason, 500 characters at most; shows the `400` message from the envelope |
| `src/core/index.js` | `routes` gains `/setup` |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/setup` | `SetupChecklist` | inside `AppShell`; present when the feed carries `core.setup` |

**States shown**

| Step | Tag |
|---|---|
| `completed` | Done, with `completedAt` |
| `skipped` | Skipped, with `skipReason` |
| `newStep` and not completed | New — listed, and the page says it is not counted in progress yet (`newCount`) |
| otherwise | To do |

The percentage is the server's. The page never computes progress.

## 6. Database changes

None.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `src/core/setup/setupService.test.js` | paths and methods; no `axios` |
| Component | `SetupChecklist.test.jsx` | an HRMS-only feed renders no payroll group; each of the four tags; the server's percentage is shown unchanged; no Skip without `core.tenant.manage`; no Open for a step whose path is not in the feed |
| Component | `SkipStepModal.test.jsx` | blank reason is not sent; a `400` shows the message; success refetches |
| IT | `code/backend/core/.../navigation/NavigationIT.java` (existing) | the feed carries `core.setup` for a holder of `core.tenant.read` and not without it |

## 8. Verification

```bash
cd code/frontend && npm ci && npm run lint && npm test
cd code/backend && ./mvnw -pl core -am verify
docker compose -f infra/docker/compose.yml up -d
```

| Check | Expected |
|---|---|
| menu | admin@acme.local sees "Setup"; a user without `core.tenant.read` does not |
| Acme (Payroll) | core and payroll steps; work location and employee show Done |
| HRMS-only tenant | no payroll group |
| skip | skipping EPF with a reason shows Skipped and the reason; `psql`: `skipped = true` on that row for Acme only |
| skip without reason | the modal refuses; nothing is sent |
| open | "Open" on Work location lands on `/org/work-locations` |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Payroll steps have no "Open" until `W-47` | certain | the step still shows its state; `stepLinks` is one line per step later |
| A reader mistakes "New" for "To do" | low | the page states new steps are not counted yet |

## 10. Rollback

Revert the branch. The menu item goes with it.

## 11. Standing rules

| Rule | This ticket |
|---|---|
| `tenant_id` + RLS | no table |
| Flyway | no script |
| `Money` / `BigDecimal` | no money |
| Index | none |
| Expand / contract | nothing |
| No module references another | `src/core/setup` imports `@shared/*` and `@shell/screens` only |

## 12. Gap inventory

No BUG or DEBT row covers the setup checklist (`legacy/docs/GAP_INVENTORY.md`).

## 13. Implementer tasks

| # | Area | Task |
|---|---|---|
| 1 | `code/backend/core` | `core.setup` in `NavigationCatalogue.java`; `NavigationIT` case |
| 2 | `src/core/setup` | service, `stepLinks`, `SetupChecklist`, `SkipStepModal`, tests; `src/core/index.js` registration |

## 14. Decisions and open questions

| # | Question | Answer |
|---|---|---|
| 1 | Why not show prior payroll and organisation tax? | They are not in the catalogue until `W-38` and `W-36.3` build them (`SetupStepCatalogue.java`, decision at the `W-24.1` merge). The page renders whatever the API returns, so they appear with no frontend change |
| 2 | Should the page land here after first login? | No. A landing redirect is the shell's decision; not in this ticket |
