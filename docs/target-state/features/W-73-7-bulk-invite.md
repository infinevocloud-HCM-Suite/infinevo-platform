# Feature: Bulk invite — employees from a CSV, invited in one job

| Field | Value |
|---|---|
| **Feature ID** | `W-73.7` · from `W-73` |
| **Promoted to** | `docs/target-state/features/W-73-7-bulk-invite.md` |
| **Owner** | claude (founder) |
| **Apps touched** | `code/backend/core` (employee, invitation), `code/backend/worker` (job), `code/frontend/src/core/employee` |
| **Related gaps** | — |
| **Status** | Draft |
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
                        --> Import --> POST /api/v1/employees/import --> 202 {jobId} --> queue "employee-import"
worker --> EmployeeImportConsumer --> for each row: create employee -> (if Y) create invitation {roleIds}
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
| GET | `/api/v1/employees/import/jobs` | — | `[{jobId, status, counts, resultDocumentId, startedAt}]` | `core.employee.create` |
| POST | `/api/v1/employee-invitations/invite-all` | — | `202 {jobId}` | `core.employee.create` |

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

## 10. Rollback

Delete the created employees from the result file's ids (soft delete, `W-13.4`); revoke their invitations.
