# Feature: Bulk invite — employees from a CSV, invited in one job

| Field | Value |
|---|---|
| **Feature ID** | `W-73.7` · from `W-73` |
| **Promoted to** | `docs/target-state/features/W-73-7-bulk-invite.md` |
| **Owner** | claude (founder) |
| **Apps touched** | `code/backend/core` (employeeimport, invitation, job), `code/backend/worker` (listener), `code/frontend/src/core/employee` |
| **Related gaps** | — |
| **Status** | Built on `dev-claude-W-73-7` |
| **Written by** | claude for the founder, 2026-10-07 |
| **Blocked by** | `W-73.3` (`roleIds` on employee invitations) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `core` (+ worker job that calls it, as `W-52.1`) | 1 |
| Flyway migration | none — the job table from `W-52` holds progress | 1 |
| Externally testable behaviour | a 50-row CSV creates 50 employees and sends 50 invitations, with a row-by-row result file | 1 |
| Frontend area | `core/employee` | 1 |

## 1. Problem

| Fact | Evidence |
|---|---|
| Employees are added one at a time | `EmployeeCreate.jsx` |
| Leave has an import (`core.leave.import`, `NavigationCatalogue.java:168`), employees do not | catalogue |
| Prior payroll import shows the shape: template, upload, dry run, import, history | `W-47.6`, `payroll/prior-payroll` |

## 2. Scope

**In scope**

- Template CSV: `employee_number, first_name, last_name, work_email, mobile, date_of_joining, department, designation, location, give_access (Y/N), roles (hr;manager)`
- **Dry run**: validate every row (duplicates by number and email, unknown department, bad date, unknown role); return a per-row result; nothing written
- **Import**: a worker job (`W-52` queue) creates employees, then invitations for `give_access=Y` with the roles; per-row outcome written to a result CSV in the document store (`kind EXPORT`); progress on the job
- Screen `/employees/import`: download template, upload, dry run table, Import, history of jobs with the result file
- "Invite all without access" button on the employee list → one job that invites every employee with a work email and no `user_account_id`, role `employee` only
- Actions: `core.employee.create` for both

**Out of scope**

- Updating existing employees from the file
- Salary structures in the same file
- More than 1,000 rows per file

## 3. Flow

```
hr --> /employees/import --> upload CSV --> POST /api/v1/employees/import/dry-run --> [row results]
                        --> Import --> POST /api/v1/employees/import --> 202 {jobId} --> queue "import"
worker --> EmployeeImportListener --> for each row: create employee -> (if Y) create invitation {roleIds}
       --> result CSV -> document store --> job DONE {createdCount, invitedCount, failedCount, resultDocumentId}
hr --> history --> download result
```

## 4. Backend changes

| Layer | File | Change |
|---|---|---|
| Controller | `core/.../employee/EmployeeImportController.java` (new) | `POST /import/dry-run` (multipart), `POST /import` (multipart → job), `GET /import/jobs` |
| Service | `EmployeeImportService` | parse (same CSV reader the leave import uses), validate, `importRows(jobId)`; each row its own transaction so one failure does not roll back fifty |
| Worker | `worker/.../listener/EmployeeImportListener` | queue consumer calling the service, as the pay run listener does (`W-29.4`) |
| Service | `InvitationService` | `inviteAllWithoutAccess()` — one job, batches of 100 |
| DTO | `ImportRowResult(row, status OK|ERROR, message, employeeId?, invitationId?)` | new |

**API contract**

| Method | Path | Request | Response | Auth |
|---|---|---|---|---|
| GET | `/api/v1/employees/import/template` | — | CSV | `core.employee.create` |
| POST | `/api/v1/employees/import/dry-run` | multipart file | `[ImportRowResult]` | `core.employee.create` |
| POST | `/api/v1/employees/import` | multipart file | `202 {jobId}` | `core.employee.create` |
| GET | `/api/v1/employees/import/jobs` | — | `[{jobId, kind, status, progressPercentage, totalCount, createdCount, invitedCount, failedCount, resultDocumentId, errorMessage, startedAt}]` | `core.employee.create` |
| GET | `/api/v1/employees/import/jobs/{jobId}/result` | — | CSV | `core.employee.create` |
| POST | `/api/v1/employee-invitations/invite-all` | — | `202 {jobId}` | `core.employee.create` |
| GET | `/api/v1/employee-invitations/invite-all/count` | — | `{count}` | `core.employee.create` |

A dry run or import of a file in which any row names a role other than `employee` also needs `core.role.assign` — `W-73.3`'s rule (`EmployeeInvitationController.ROLE_ASSIGN_ACTION`); `403` otherwise.

## 5. Frontend changes

| File | Change |
|---|---|
| `core/employee/EmployeeImport.jsx` (new) | template, upload, dry-run table with error rows first, Import, job history with progress (poll `GET /jobs/{id}`, `W-52`) |
| `core/employee/EmployeeList.jsx` | buttons **Import** and **Invite all without access** (with a count in the confirm dialog) |
| `core/employee/employeeImportService.js` (new) | the calls |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `/employees/import` | `EmployeeImport` | `core.employee.create` (route under the Employees item; no new menu entry) |

## 6. Database changes

None. Job rows in the existing job table; result file in the document store.

## 7. Tests

| Type | File | Covers |
|---|---|---|
| Unit | `EmployeeImportParserTest` | header check, date formats, roles split, Y/N |
| Integration | `EmployeeImportIT` | dry run writes nothing; import with one bad row creates the others; duplicates refused; result file has one line per row |
| Integration | `InviteAllIT` | only employees without `user_account_id` and with a work email are invited; idempotent |
| Unit | `EmployeeImport.test.jsx` | error rows shown first; Import disabled until a clean dry run or "import valid rows only" ticked |

## 8. Verification

| Check | Expected |
|---|---|
| 50-row file, 2 bad rows | Dry run: 48 OK, 2 errors with row numbers; Import: 48 employees, invitations for the `Y` rows, result CSV |
| Invite all without access | Employees without accounts get one email each; a second click invites nobody |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Email burst hits the Brevo rate | medium | invitations queued through the notification outbox; the worker paces them |
| Half-imported file on a crash | low | per-row transactions; the result file says exactly which rows landed |

## 10. As built — changes from the draft (2026-10-08)

| Draft said | Built | Why |
|---|---|---|
| Classes under `core/.../employee/` | `core/.../employeeimport/` | 16 test contexts scan `com.infinevo.core.employee` without job, document or invitation beans (e.g. `CoreFeatureTestApp.java:36`); a bean there needing them breaks those contexts |
| Queue `employee-import` | Queue `import` | Already provisioned and unused (`infra/azure/modules/storage.bicep:109`); no infra change |
| File carried on the queue | File held in the job row (`core.job_status.result_payload`); the message carries only the kind | Queue messages are capped at 48 KB (`QueueMessage.java:17`); 1,000 rows exceed it |
| Same CSV reader as the leave import | Own RFC 4180 reader (`EmployeeImportParser`) | The leave reader is a private `split(",")` (`LeaveImportServiceImpl.java:293`) that breaks on a quoted comma |
| Poll `GET /jobs/{id}` | Poll `GET /employees/import/jobs` | `/jobs/{id}` needs `core.job.read` (`JobStatusController.java:29`), which HR may not hold |
| — | `GET /import/jobs/{jobId}/result`, `GET /employee-invitations/invite-all/count` | Result download under `core.employee.create`; the count the confirm dialog shows (§5) |
| Invite all: every employee with a work email and no account | Active employees only, and none with a live pending invitation | A terminated employee must not get a portal invitation; the pending check makes a second click invite nobody |
| `InvitationService.inviteAllWithoutAccess()` | `InvitationService.employeesWithoutAccess()` lists them; `EmployeeImportServiceImpl` invites each through `createEmployeeInvitation` | `InvitationServiceImpl` is `@Transactional` at class level, so a loop inside it would be one transaction, not one per invitation |
| — | No retry on a failed job | Rows commit one by one; a redelivery would report landed rows as duplicates |
| — | Roles with `give_access` N is a row error | Roles are granted on acceptance of the invitation |

## 11. Rollback

Delete the created employees from the result file's ids (soft delete, `W-13.4`); revoke their invitations.
