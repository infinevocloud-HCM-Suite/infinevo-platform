# End-to-end suite (Playwright)

Browser tests against the running local stack, one project per seeded role. They prove what the
unit tests cannot: that every screen a role can reach opens, shows words rather than ids, and
that one leave request travels from employee to manager and back.

## Run

| Step | Command (from the repo root unless noted) |
|---|---|
| 1. Stack up | `docker compose -f infra/docker/compose.yml up -d` — frontend :5173, API :8080, Keycloak :8081 |
| 2. Seed | the seed in `infra/docker/seed/` must have run (Acme, Globex and the users below) |
| 3. Browser, once | `cd code/frontend && npm run e2e:install` |
| 4. Run | `cd code/frontend && npm run e2e` |
| Watch it | `npm run e2e:ui` |
| One role | `npm run e2e -- --project=hr` (`setup` runs first on its own) |
| One flow | `npm run e2e -- --project=flows` |
| Report | `npx playwright show-report e2e/playwright-report` |

| Variable | Default | Meaning |
|---|---|---|
| `E2E_BASE_URL` | `http://localhost:5173` | where the frontend answers |
| `E2E_PASSWORD` | `Local_dev_pw1!` | the one seeded password (D-36) |

Sign-in is Keycloak's own login page (realm `infinevo`, client `react-app`). The `setup` project
signs each role in once, dismisses the first-sign-in Welcome page, and saves the browser state to
`e2e/.auth/<role>.json`; every other project replays it. `.auth/`, `test-results/` and
`playwright-report/` are git-ignored.

## Who signs in

| Project | User | Tenant | Expected home |
|---|---|---|---|
| `employee` | `employee.globex` (GLBX-0001, Gautam Employee) | Globex | `/me` |
| `manager` | `manager.globex` (reporting manager of GLBX-0001) | Globex | `/approvals` |
| `hr` | `hr.globex` | Globex | `/hrms/dashboard` |
| `payroll-officer` | `payroll.globex` | Globex | `/payroll/dashboard` |
| `finance` | `finance.globex` | Globex | `/payroll/dashboard` |
| `tenant-admin` | `admin.globex` | Globex | `/setup` while setup is unfinished, else `/payroll/dashboard` |
| `platform-admin` | `staff.infinevo` | platform | `/admin` |

`admin.acme` and `employee.acme` (Acme, Payroll only) are listed in `fixtures/roles.js` but not
signed in; see "Adding a role".

## What it checks

| Test | In every role spec | What fails it |
|---|---|---|
| landing | sign-in lands on the role's home | wrong path; an error screen; a dashboard with no figure or card |
| menu walk | opens every sidebar leaf | error screen; no heading; a table whose first row shows a raw UUID, an ISO `yyyy-mm-dd` or an `ALL_CAPS_CODE`, or whose header has an empty cell — all offenders on a screen are reported together (soft assertions) |
| buttons | clicks every safe button on each list screen, up to 25 per screen | an error screen or error dialog after a click; the opened drawer/modal is closed and a navigation undone |
| portal tabs | `employee.spec.js` only: every `/me` tab | an error screen; a UUID in My Profile; no "Apply for Leave" on My Leave |

Buttons whose label is destructive or saves data are never clicked blind: Delete, Remove, Revoke,
Cancel, Reject, Terminate, Disable, Sign out, Stop, Mark paid, Pay, Approve, Compute, Lock and the
plain Save/Submit/Send/Import family (`DESTRUCTIVE_LABELS` in `fixtures/helpers.js`).

A screenshot of every screen visited lands in `e2e/test-results/screens/<role>/<slug>.png`.

| Flow (`flows/leave.spec.js`, serial) | Role |
|---|---|
| 1. create leave type "E2E Casual" (DAYS, paid, Valid From today) with a 12-day, no-accrual, no-limit policy | hr |
| 2. allocate it to GLBX-0001 for this year | hr |
| 3. apply for one day of it next Monday and submit | employee |
| 4. View Item from `/approvals` (outcome recorded); back; Approve with a comment | manager |
| 5. approve the second step (the LEAVE definition is reporting manager, then the `hr` role) | hr |
| 6. My Leave shows it Approved | employee |

The flow reuses what an earlier run left behind: an existing type, allocation or request for the
same date is not created twice (the policy is re-saved, which is idempotent). If the request already
reads Approved, steps 4 and 5 are skipped.

Two screens cannot complete the flow on their own, so the flow rewrites those two requests on the
wire (`page.route`) and a separate "screen findings" describe, outside the serial chain and without
retries, performs each action through the screen alone so the defect stays a failing test:

| FINDING test | Screen | What the server says |
|---|---|---|
| 7. Save Policy | `/leave/types` › Policy › Save Policy | Was `effectiveFrom must not be null`; fixed as `D-55` (the server dates a policy without a date from the current leave year). Passes |
| 8. Allocate | `/leave/allocations` › Allocate › Save Allocation | Was `No effective policy found for leave type <uuid>`; fixed as `D-93` (the window follows the company's leave-year start). Passes |
| 9. View Item | `/leave/requests/:id` as manager | Was 400 "connection is in auto-commit mode…"; fixed as `D-92` (direct reports read inside a transaction). Passes |

The leave year is assumed to start on 1 April (`LEAVE_YEAR_START_MONTH` in the flow); the server
names that date when it refuses an earlier one.

Error screens recognised (`findErrorScreen`): the shell's 404, Module Not Subscribed, the two
AccessProblem titles, No Modules Available, Navigation Unavailable, the portal's Panel Not
Available and "Panel component not found", any antd `Result` with status 403/404/500/error/warning,
"Something went wrong", and an open SweetAlert2 error dialog.

## Adding a role

1. Seed the user and give it the role in Keycloak and the tenant.
2. Add an entry to `ROLES` in `fixtures/roles.js`: username, tenant, and every path
   `NavigationService.homePath` may answer for it in `homes`.
3. Create `<role>.spec.js` with `defineRoleSuite('<role>')`; add role-specific tests in the same
   file. The config builds the project from the `ROLES` key, so the file name must match it.
4. Run `npm run e2e -- --project=setup --project=<role>`.

## Layout

| Path | Holds |
|---|---|
| `playwright.config.js` | projects: `setup`, one per role, `flows` |
| `auth.setup.js` | signs every role in, saves `.auth/<role>.json` |
| `fixtures/roles.js` | users, homes, base URL, password |
| `fixtures/login.js` | `login`, `landHome`, `openAs` |
| `fixtures/helpers.js` | `expectNoErrorScreen`, `collectMenu`, `checkTable`, `clickEveryButton`, dialog and form helpers |
| `fixtures/roleSuite.js` | the three tests every role spec runs |
| `<role>.spec.js` | one per role |
| `flows/leave.spec.js` | the multi-role leave scenario |

Lint: `npx eslint e2e --ext .js` (its own `.eslintrc.cjs`; `npm run lint` covers `src/` only).
Vitest only includes `src/**/*.test.{js,jsx}`, so `npm test` never picks these specs up.
