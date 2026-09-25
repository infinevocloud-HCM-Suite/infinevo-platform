# 12 — Core contracts

> What Core provides to Payroll, HRMS, the frontend and the admin console: tables, APIs,
> Java seams and permission codes. Compiled 2026-09-24 from the 36 Core specs and the
> code on `main`. **Built** rows come from the code; **spec** rows from the spec, and can
> still change. Every other area's spec cites this file, not a guess.
>
> §5 lists what the Core specs must fix before they are built. §6 lists the decisions taken
> here. §7 lists the open questions for the founder.

Paths: `F/` = `docs/target-state/features/`, `M/` = `code/backend/migration/src/main/resources/db/migration/`.

## 1. Tables

All in `core`, all with `tenant_id` + RLS + FK to `core.tenant` (`D-58`), unless said.

| Area | Table | Key columns / uniqueness | State |
|---|---|---|---|
| Identity | `core.user_account` | keycloak_user_id, email, status; unique `(tenant_id, keycloak_user_id)` | built `M/core/V009` |
| Authz | `reference.action` | `code` PK, `module` ∈ core/hrms/payroll; prefix must equal module | built `V020:19-22` |
| Authz | `core.role`, `core.role_action`, `core.user_role` | unique `(tenant_id, code)`; `(tenant_id, role_id, action_code)`; `(tenant_id, user_account_id, role_id)`. Seven system roles seeded per tenant by trigger | built `V021–V023` |
| Tenant | `core.tenant` | `name` only today | built `V001` — **needs `timezone`, `leave_year_start_month`, `country_code`** (§6) |
| Subscription | `core.subscription`, `core.subscription_module` | one per tenant; `(tenant_id, module)` where not revoked | spec `F/W-12-1:115-126` |
| Employee | `core.employee` | employee_number unique per tenant, status, is_deleted, dept/desig/location ids | built `V010, V014, V024` — **needs `user_account_id`** (§6) |
| Employee | `_personal`, `_contact`, `_identification`, `_employment`, `_bank` | one per employee | built `V015–V019` |
| Org | `core.department`, `core.designation`, `core.work_location` | code unique per tenant; one filing address per tenant | built `V011–V013` |
| Org | `core.reporting_line` | employee_id, manager_id, kind, effective_from/to | spec `F/W-14-2:124-128` |
| Approvals | `core.approval_definition`, `_instance`, `_step`, `_delegation` | flow_type, subject_table + subject_id, status, step decision | spec `F/W-15-1:120`, `W-15-2:114`, `W-15-3:104` |
| Holidays | `core.holiday_calendar`, `core.holiday`, `core.holiday_calendar_location` | is_default; from/to dates; link needs unique `(tenant_id, work_location_id)` | spec `F/W-17:106-123` |
| LOP | `core.lop_policy` | working_day_basis, configured_days_per_month, weekends_payable, holidays_payable, lop_rounding | spec `F/W-18-1:110-118` |
| Leave | `core.leave_type`, `leave_policy`, `leave_policy_eligibility`, `leave_allocation`, `leave_request`, `leave_request_document`, `leave_consumption`, `leave_monthly_lop`, `leave_import_log` | days `numeric(10,2)` (§5); allocation unique `(tenant_id, employee_id, leave_type_id, leave_year)`; consumption append-only | spec `F/W-16-*` |
| Pay input | `core.pay_input` | employee_id, period `char(7)` YYYY-MM, kind, quantity `numeric(10,2)`, amount `numeric(19,4)`, source_module, source_ref, reverses_id, locked; **unique `(tenant_id, source_module, source_ref)` where reverses_id is null** (§6) | spec `F/W-19:123-131` |
| Attendance | `core.attendance` | unique `(tenant_id, employee_id, attendance_date)`; status, source | spec `F/W-39-1` |
| Overtime | `core.overtime_request` | to be specced in `W-39.2` | — |
| Notify | `core.notification_template`, `core.notification`, `core.reminder_rule` | event, channel, status; rule gains `day_of_week`, `anchor`, `last_executed_at`, `repeat_count` (§6) | spec `F/W-20-1:111`, `W-20-2:109` |
| Documents | `core.document` | employee_id NULL, kind, blob_path, checksum; `kind` gains `EXPORT`, `PAYSLIP` | spec `F/W-21:108-111` |
| Audit | `core.audit_log` | append-only, `app_user` cannot update or delete | built `V008` |
| Retention | `core.retention_run` + tenant retention columns | | spec `F/W-22-2:98-106` |
| Reports | `core.report_definition`, `core.report_schedule` | code, source enum, columns jsonb, required_action | spec `F/W-23-1:110`, `W-23-2:114` |
| Setup | `core.tenant_setup_step`, `core.user_invitation`, `core.employee_invitation` | step_code, module; token_hash unique | spec `F/W-24-1:127`, `W-24-2:124` |
| Jobs | `core.job_status`, `core.shedlock` | job_id, tenant_id uuid, status, progress | built `V006` |

## 2. REST APIs other areas call or extend

Base path `/api/v1`. Permission = `@RequiresAction`. Every Core endpoint **must** carry one
(`EndpointGuardCoverageTest`), so the specs that say only "Bearer" get the codes below.

| Endpoint | Permission | State |
|---|---|---|
| `GET /me` | none | built |
| `GET /navigation` — also returns the caller's action codes (§6) | none | spec W-12.3 |
| `GET /actions`, `/roles`; `POST/PUT/DELETE /roles`; `PUT /users/{id}/roles` | `core.role.read` / `.manage` / `.assign` | built |
| `POST /tenants`; `PUT /tenants/{id}/subscription/*` | `core.tenant.provision` | spec W-12.1 |
| `GET /tenants/{id}/subscription` | `core.tenant.read` | spec W-12.1 |
| `/employees` CRUD, `/employees?q&status&page` | `core.employee.*` | built / spec W-13.3 |
| `/employees/{id}/personal|contact|employment|identification|bank` | `core.employee.read/update`, `core.employee_identification.*`, `core.employee_bank.*` | built |
| `/departments`, `/designations`, `/work-locations` | `core.org.read` / `.manage` | built |
| `/employees/{id}/reporting-line`, `/manager-chain`, `/org-chart` | `core.org.read` / `core.reporting_line.manage` | spec W-14.2 |
| `/approval-definitions` | `core.approval_definition.manage` | spec W-15.1 |
| `/approvals/pending`, `/approvals/{id}`, `POST /approvals/steps/{id}/decide` (body: decision, comment, **approvedAmount?**) | `core.approval.decide` | spec W-15.2 |
| `/approval-delegations`, `/approvals/{id}/history`, `POST /approvals/{id}/reassign` | `core.approval.delegate` / `.read` / `.manage` | spec W-15.3 |
| `/holiday-calendars`, `/holidays?workLocationId&from&to` | `core.holiday.manage` / `.read` | spec W-17 |
| `/lop-policy` | `core.lop_policy.manage` / `.read` | spec W-18.1 |
| `/leave-types`, `/{id}/policy` | `core.leave_type.manage` / `.read` | spec W-16.1 |
| `/employees/{id}/leave-balances`, `/leave-allocations`, `/accrue` | `core.leave_balance.manage` / `core.leave.read*` | spec W-16.2 |
| `/leave-requests` (+ `POST /leave-requests/on-behalf` for admin entry, §6) | `core.leave.apply` / `.read*` / `.manage` | spec W-16.3 |
| `/leave-imports` | `core.leave_balance.manage` | spec W-16.4b |
| `/pay-inputs`, `/pay-inputs/periods/{p}/lock`, `/{id}/reverse` | `core.pay_input.read` / `.write` / `.lock` | spec W-19 |
| `/attendance` | `core.attendance.read` / `.manage` | spec W-39.1 |
| `/notifications`, `/notification-templates` | recipient / `core.notification_template.manage` | spec W-20.1 |
| `/reminder-rules` | `core.reminder_rule.manage` | spec W-20.2 |
| `/documents`, `/documents/{id}/link` | `core.document.read` / `.read_own` / `.upload` / `.delete` | spec W-21 |
| `/audit` | `core.audit.read` | built |
| `/report-definitions`, `/exports`, `/report-schedules` | `core.report.read` / `.manage`, per-definition `required_action`, `core.report_schedule.manage` | spec W-23 |
| `/setup-checklist` | `core.tenant.read` / `.manage` | spec W-24.1 |
| `/user-invitations`, `/employee-invitations` (list, resend, revoke, decline) | `core.user.manage` / `core.employee.create` | spec W-24.2 |
| `/me/panels` and `/me/*` | `*_own` actions, not the role | spec W-25 |
| `/jobs/{jobId}` | `core.job.read` | built, guard missing |

## 3. Java seams

Only `core` and `shared` may be referenced. A module never touches another module.

| Need | Seam | State |
|---|---|---|
| Who am I, which tenant | `shared.tenant.TenantContext.require()` | built |
| Permission check | `@RequiresAction("code")`; `PermissionService.holds/require` | built |
| Module check | `@RequiresModule(PlatformModule)` in `shared`; `EntitlementSource` port implemented in core (same shape as `ActionSource`) | spec W-12.2, corrected §5 |
| Employee read | `core.employee.EmployeeService.get(UUID)` | built |
| Org master read | `OrgMasterService.get(UUID)` — **add** | § 6 |
| Reporting chain | `ReportingLineService.chainAbove(employee, asOf)` | spec W-14.2 |
| Start an approval | `ApprovalService.start(ApprovalFlowType, SubjectRef(table, id), employeeId)` → `instanceId` | spec W-15.2 |
| Learn the outcome | Module implements `ApprovalOutcomeHandler` (a Spring bean keyed by `flowType()`), `onApproved(instance, decisions)` / `onRejected(...)`, called once, after commit | spec W-15.2, sharpened §6 |
| Flow types | `ApprovalFlowType` enum in core: LEAVE, REGULARIZATION, OVERTIME, REIMBURSEMENT, PROOF_OF_INVESTMENT, **PAY_RUN, TIMESHEET** | § 6 |
| Approver kinds | REPORTING_MANAGER, APPROVER_LEVEL_n, ROLE, **PROJECT_MANAGER** (resolved by a module-supplied `ApproverResolver`) | § 6 |
| Holidays | `HolidayQueryService.isHoliday(locationId, date)`, **`holidaysBetween(locationId, from, to)`** | spec W-17 + §6 |
| Working-day basis | `WorkingDayBasisCalculator.basisFor(tenantId, period, employeeId)` → `{payableDays, divisor, policyId}`; reads the weekday set from the pay schedule (`W-28`) | spec W-18.1 |
| Leave balance | `LeaveBalanceService.balanceOf(employee, type, asOf)` | spec W-16.2 |
| LOP days | read `core.pay_input` kind `LOP_DAYS`, `quantity` in days | spec W-16.4a |
| Write a pay input | `PayInputService.record(PayInputCommand)`: employeeId, `YearMonth period`, `PayInputKind kind`, quantity, `Money amount` (positive; kind decides sign), sourceModule, sourceRef | spec W-19, sharpened §6 |
| Read pay inputs | `PayInputService.forEmployee(employee, period)`, **`forPeriod(period)`** batch | § 6 |
| Lock a period | `PayInputService.lock(period)` — per tenant per period | spec W-19 |
| Pay input kinds | `LOP_DAYS, OVERTIME, REIMBURSEMENT, AD_HOC_DEDUCTION, ONE_TIME_PAYOUT` | § 6 |
| Attendance | `AttendanceQuery.days(employeeId, from, to)` | spec W-39.1 |
| Send a notification | `NotificationService.compose(NotificationEvent, recipientEmployeeId, Map data)`; events listed in §6 | spec W-20.1 |
| Reminder rule | rows in `core.reminder_rule`; module supplies a `ReminderAudienceResolver` bean | § 6 |
| Store a document | `DocumentService.store(DocumentKind, employeeId?, filename, InputStream)` → `documentId`; `DocumentLinkService.signedLink(documentId, Duration)` | spec W-21, sharpened §6 |
| Report source | module registers a `ReportSource` bean: `code()`, `columns()`, `rows(filters)` | § 6 |
| Setup step | module registers a `SetupStepChecker` bean | spec W-24.1 |
| Portal panel | module registers a `PortalPanelProvider` bean: `code()`, `module()`, `panel(employeeId)` | § 6 |
| Enqueue a job | `shared.queue.QueueProducer.send(queue, QueueMessage.of(jobId, tenantId, queue, payload))`; queues: `payrun`, `import`, `report`, **`notification`** | built + §6 |
| Consume a job | implement `shared.queue.QueueConsumer<T>` in `worker` | built, **no consumer loop runs yet** (§5) |
| Job progress | `core.job.service.JobService.createJob / markRunning / updateProgress / markCompleted / markFailed` | built |
| Cache | `shared.cache.CacheService` with `TenantCacheKeyGenerator` | built |
| Audit | `@shared.audit.Audited` on the entity | built |
| Employee events | **none exist.** Modules poll or read; no event on create or terminate | gap, accepted for now |

## 4. Permission codes

The catalogue (`M/reference/V020__action.sql`) forces `module = prefix`, and a module filter
would strip `hrms.*` codes from a Payroll-only tenant. So **every Core feature gets a `core.*`
code**, in one new migration owned by a small ticket `W-11.3 Catalogue correction`:

| Change | Codes |
|---|---|
| Rename `hrms.*` → `core.*` (Core features misfiled) | `leave.apply/read/read_own/read_team/approve`, `leave_type.manage`, `leave_balance.manage`, `holiday.read/manage`, `attendance.read/read_own/read_team/manage/export` |
| Stay `hrms.*` (HRMS-only experience) | `hrms.attendance.mark`, `hrms.timesheet.*`, `hrms.project.*`, `hrms.overtime.request` |
| Add | `core.reporting_line.manage`, `core.approval_definition.manage`, `core.approval.read/decide/delegate/manage`, `core.lop_policy.read/manage`, `core.leave.manage`, `core.pay_input.read/write/lock`, `core.overtime.read/manage`, `core.notification_template.manage`, `core.reminder_rule.manage`, `core.document.read/read_own/upload/delete`, `core.report.read/manage`, `core.report_schedule.manage`, `core.job.read` |
| Fix | `core.tenant.provision` must not be in the tenant-seeded `platform-admin` role (`V022:86-88`) |

`W-39.1` is updated to use `core.attendance.*` once `W-11.3` lands; until then it builds
against the `hrms.*` names.

## 5. Corrections the Core specs need before build

| # | Spec | Fix |
|---|---|---|
| 1 | W-19 | Lock cannot flip `locked` on rows: `app_user` has no UPDATE. Lock = a row in a new `core.pay_input_period_lock (tenant_id, period)`; the insert trigger refuses writes for a locked period. Add `REVOKE UPDATE, DELETE` for append-only. Add the unique key in §1 |
| 2 | W-16.4a | Honour `exceed_balance_mode` (`noLimit`, `yearEndLimit` + limit, `markAsLOP`); only `markAsLOP` produces LOP. Same-month second approval writes a delta row. Multi-month leave: LOP lands on the calendar month of each date (legacy `LeaveRequestServiceImpl.java:380-464`) |
| 3 | W-16.1 / 16.2 | Accrual: monthly, yearly. Reset: yearly, monthly, quarterly, halfYearly (legacy `addLeaveTypes.js:403-474`). Drop the encashment reference in W-16.2. Specify `leave_policy_eligibility` columns. Accrual runs on `worker` under ShedLock, not W-20.2 |
| 4 | W-16.3 | Add `LeaveRequestStatus` (`DRAFT, PENDING, APPROVED, REJECTED, CANCELLED, WITHDRAWN`), the withdraw endpoint, `half_day_period` ∈ first/second, and admin on-behalf entry (`D-35`) |
| 5 | W-16-4 (two files) | Rename to `W-16-4a-consumption-lop.md`, `W-16-4b-leave-import.md` |
| 6 | W-15.1 | Define the step JSON (kind, assignee, `escalate_after_days` default 3). Add PAY_RUN and TIMESHEET flows and PROJECT_MANAGER kind. Proof of investment is per-item approval with `approvedAmount`, then a final decision (legacy `AdminProofOfInvestmentController.java:283-379`) |
| 7 | W-15.2 | Decide payload gains optional `approvedAmount`. A rejected instance ends; resubmission starts a new instance. Handler runs after commit. Fix the circular block with W-16.3: W-15.2 tests with a stub flow, not leave |
| 8 | W-15.3 | Add `POST /approvals/{id}/reassign` (admin), owned here |
| 9 | W-17 | Three tables, so three scripts (exception noted). Unique `(tenant_id, work_location_id)` on the link; one `is_default` per tenant |
| 10 | W-18.1 / 18.2 | Weekday set is read from the pay schedule (`D-27`); rename to `WorkingDayBasisCalculator` everywhere; basis values `ACTUAL_DAYS, ORG_DAYS(n), FIXED_30`; `lop_rounding` default `HALF_UP_2`. W-18.2: no policy ⇒ employee fails the run, never a null stamp |
| 11 | W-12.1 | Tenant create takes name, country_code, timezone, leave_year_start_month. `SubscriptionService` bumps the permission version on module change. Gate by `core.tenant.provision`, not a realm role |
| 12 | W-12.2 | `PlatformModule` enum and `EntitlementSource` port live in `shared`; core implements the port. `MODULE_NOT_ENTITLED` already exists. Add `TENANT_SUSPENDED` |
| 13 | W-12.3 | Feed returns the caller's action codes too |
| 14 | W-13.x | Add `user_account_id UUID NULL` on `core.employee` (legacy HRMS FK pattern, `OurUsers.java:49-52`). Personal and contact PUT accept `core.employee.update_own` for self |
| 15 | W-20.1 / 20.2 | Event list from legacy (`application.properties:52-65`): `POI_REMINDER, IT_DECLARATION_REMINDER/LOCK/RELEASE, POI_SUBMITTED, PAYSLIP_READY, USER_INVITATION, EMPLOYEE_INVITATION, CREDENTIALS, LEAVE_*, APPROVAL_PENDING/DECIDED, TIMESHEET_REMINDER`. Reminder rule columns per §1. Add `/reminder-rules` API and the `notification` queue |
| 16 | W-21 | Add `DocumentKind.EXPORT, PAYSLIP`; `blob_path` works without an employee. Emailed links live 7 days, interactive 15 min. Upload limit 10 MB; types pdf, jpg, png, xlsx, csv |
| 17 | W-22.2 / 20.2 / 23.2 | Tenant sweeps use a `SECURITY DEFINER` tenant list. W-22.2 covers `notification` in its flow; W-20.2 owns the scheduler |
| 18 | W-23.1 / 23.2 | `ReportSource` interface per §3; async export = a `report` job, polled at `/jobs/{id}` |
| 19 | W-24.2 | `role_ids` → join table; add decline with reason, list/resend/revoke for employee invitations |
| 20 | W-25 | Panels via `PortalPanelProvider`; endpoints listed per panel |
| 21 | W-52 | Build the consumer loop (`receiveMessages`); producer bean in `app` too; idempotency covers RUNNING; retries then FAILED. Spec says `V003`/varchar — code is `V006`/uuid, spec follows code |
| 22 | W-53 | Delete the unused `core.cache.PermissionCacheService` / `PermissionInvalidationService`; TTL is 10 min |
| 23 | All | Day counts are `numeric(10,2)` (`CONVENTIONS.md:37`). `02-data-model.md` fixes: `reference.action` not `core.action`; approvals replace five paths; pay input is written for every tenant; `timesheet_notification` folds into `core.notification` |

## 6. Decisions taken in this document

| # | Decision | Why |
|---|---|---|
| 1 | One `core.*` code per Core feature; `W-11.3` renames the misfiled `hrms.*` codes | The catalogue ties prefix to module; a Payroll-only tenant would lose leave and holidays |
| 2 | Pay input idempotency: unique `(tenant_id, source_module, source_ref)` on non-reversal rows; amounts positive, kind decides the sign | Legacy convention (`EmployeePayRunServiceImpl.java:337-363`) |
| 3 | An input that arrives after its period is locked goes to the next open period; an off-cycle run (`W-30`) collects only inputs tagged with its run id | Nothing in legacy locks; this is the simplest rule that never loses an input |
| 4 | Platform-admin writes to another tenant's rows go through `SECURITY DEFINER` functions owned by `migration_user`, never a bypass role for `app_user` | Keeps RLS absolute for the application connection |
| 5 | Restricted holidays carry the flag only; no quota | Legacy has no quota (`Holiday.java:32-33`) |
| 6 | Escalation default 3 working days; admin reassignment is `W-15.3` | Legacy has no escalation; a default is needed for the first definition |
| 7 | Tenant gains `timezone`, `country_code`, `leave_year_start_month` in `W-12.1` | Reminders need local time (legacy `Organization.java:61-62`); leave year is per tenant |
| 8 | Emailed document links live 7 days | Matches `W-23.2` |
| 9 | No employee domain events yet | No consumer needs one before `W-29`; revisit there |

## 7. Founder decisions, 2026-09-25

| # | Question | Decision |
|---|---|---|
| 1 | Default leave year | **Financial year, April–March.** `core.tenant.leave_year_start_month` defaults to `4`; a tenant may change it (`W-12.1`) |
| 2 | `W-38` prior payroll import source | **A fixed spreadsheet template**, one row per employee per month; `W-38` validates and loads it through `W-16.4b`'s import pattern (document upload, dry run, error file) |

## Related

- Design: `01-platform-shape.md`, `02-data-model.md`, `07-decisions.md`
- Specs: `features/`
