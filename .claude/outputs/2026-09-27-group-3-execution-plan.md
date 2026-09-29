# Group 3 Core Platform Implementation Plan

> **Scope**: Resolution of review blockers on `dev-devashis` (W-20.1, W-23.1, W-21) followed by the sequential implementation of downstream worker features (W-20.2, W-22.2, W-23.2).
> **Branch**: `dev-devashis` (local `dev-devashish`)
> **Author**: devashis (Documents, Notifications, Reporting lane)

---

## 1. Ticket Roadmap & Status Overview

| # | Ticket | Name | Group | Current Status | Blocker / Next Action | Target Modules / Specs |
|---|---|---|---|---|---|---|
| **1** | **W-20.1** | Notifications | 3 | Sent back twice (`dd1cda1`) | Fix Item 2 (`active=false`) & Item 3 (`compose` transaction), add tests | `code/backend/core` |
| **2** | **W-23.1** | Reporting & Export | 3 | Sent back twice (`dd1cda1`) | Fix Item 4 (`required_action` validation) & Item 5 (soft-delete & RLS tests) | `code/backend/core` |
| **3** | **W-21** | Document Store | 3 | Code approvable | Fix Spec Item 6 (reconcile doc with `InputStreamResource` streaming) | `docs/target-state/features/W-21-document-store.md` |
| **4** | **W-20.2** | Delivery Scheduler | 3 | Spec approved, unstarted | Blocked on W-20.1 merge; needs W-12.1 (merged on `main`) | `worker`, `core`, `migration` |
| **5** | **W-22.2** | Audit Retention | 3 | Spec approved, unstarted | Blocked on W-20.1 and W-20.2 | `worker`, `migration` |
| **6** | **W-23.2** | Scheduled Reports | 3 | Spec approved, unstarted | Blocked on W-20.2 and W-23.1 | `worker`, `migration`, `core` |

---

## 2. Phase 1: Remediate and Merge `dev-devashis`

Tickets 1, 2, and 3 are already coded on `dev-devashis`. They only require closing the five review findings.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        dev-devashis @ dd1cda1                          │
│                                                                        │
│  [W-20.1 Item 2 & 3]       [W-23.1 Item 4 & 5]      [W-21 Spec Item 6] │
│  • active=false channel    • validate required_     • document private │
│  • REQUIRES_NEW compose      action per source        streaming via    │
│  • tests for both          • soft-delete & RLS IT     InputStream      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
       Local Verification: ./mvnw verify & check-done.mjs W-nn
                                    │
                                    ▼
                 Review approval & Merge to main
```

---

### Ticket 1: W-20.1 — Notifications

#### Finding 2: `active=false` does not switch a channel off
* **Problem**: In [`NotificationTemplateRepository.java`](file:///d:/ashtsiddhi/HCM/infinevo-platform/code/backend/core/src/main/java/com/infinevo/core/notification/NotificationTemplateRepository.java), the lookup queries `ActiveTrue`. When an admin sets `active = false` effective today, the query skips today's inactive row, matches the older seeded default (`2026-01-01`), and continues sending.
* **Remediation**:
  1. In [`NotificationTemplateRepository.java`](file:///d:/ashtsiddhi/HCM/infinevo-platform/code/backend/core/src/main/java/com/infinevo/core/notification/NotificationTemplateRepository.java):
     Remove `ActiveTrue` from the effective date lookup:
     ```java
     Optional<NotificationTemplate> findFirstByTenantIdAndEventAndChannelAndLocaleAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
             UUID tenantId, NotificationEvent event, Channel channel, String locale, LocalDate onDate);
     ```
  2. In [`NotificationServiceImpl.java`](file:///d:/ashtsiddhi/HCM/infinevo-platform/code/backend/core/src/main/java/com/infinevo/core/notification/NotificationServiceImpl.java) (`compose` method):
     Lookup the newest effective template for each channel. If `!template.isActive()`, log and **skip that channel** (do not render or enqueue). If all channels for the event are switched off, return an empty list (`List.of()`) without error.
  3. In [`NotificationTemplateRequest.java`](file:///d:/ashtsiddhi/HCM/infinevo-platform/code/backend/core/src/main/java/com/infinevo/core/notification/NotificationTemplateRequest.java):
     Update the `@param active` Javadoc: specify that `active=false` disables the event on that channel.
* **Tests to add**:
  * Unit test in [`NotificationServiceTest.java`](file:///d:/ashtsiddhi/HCM/infinevo-platform/code/backend/core/src/test/java/com/infinevo/core/notification/NotificationServiceTest.java): Save an inactive template (`active=false`); assert that `compose` omits that channel and does not fallback to the older active seeded default.

#### Finding 3: `compose` joins caller's transaction and fails business flows
* **Problem**: In [`NotificationServiceImpl.java:102`](file:///d:/ashtsiddhi/HCM/infinevo-platform/code/backend/core/src/main/java/com/infinevo/core/notification/NotificationServiceImpl.java#L102), `compose` uses default `@Transactional` (`REQUIRED`). It throws on legacy malformed email addresses, missing placeholders, or missing templates, marking the caller's outer transaction (such as a leave approval) rollback-only.
* **Remediation**:
  1. Change transaction propagation to `@Transactional(propagation = Propagation.REQUIRES_NEW)` on `compose` so notification persistence is isolated from the caller's transaction.
  2. In `NotificationServiceImpl.email(...)`: If an employee's legacy email fails syntax validation or is empty, log a warning and return `null` (disabling EMAIL for that recipient) rather than throwing `ValidationException`.
  3. In `NotificationServiceImpl.compose(...)`: Wrap template rendering in a structured try-catch block; if an unhandled template rendering or placeholder error occurs, log an error and return empty rather than aborting caller execution.
* **Tests to add**:
  * Integration test: Trigger a notification inside an outer `@Transactional` method with an invalid recipient email / missing placeholder; assert that the outer transaction commits successfully and is not rolled back.

---

### Ticket 2: W-23.1 — Reporting & Export

#### Finding 4: `required_action` only checked for existence
* **Problem**: In [`ReportDefinitionServiceImpl.java:182-186`](file:///d:/ashtsiddhi/HCM/infinevo-platform/code/backend/core/src/main/java/com/infinevo/core/report/ReportDefinitionServiceImpl.java#L182-L186), `required_action` is only validated against `actions.existsById()`. A user could assign `core.me.read` to an employee data export.
* **Remediation**:
  1. In [`ReportSource.java`](file:///d:/ashtsiddhi/HCM/infinevo-platform/code/backend/core/src/main/java/com/infinevo/core/report/ReportSource.java):
     Add `Set<String> permissibleActions()` declaring the permitted authorization codes for that specific source:
     * `EmployeeReportSource`: `Set.of("core.employee.export", "core.employee.read")`
     * `OrgMasterReportSource`: `Set.of("core.org.read")`
     * `AuditLogReportSource`: `Set.of("core.audit.read")`
  2. In [`ReportDefinitionServiceImpl.java`](file:///d:/ashtsiddhi/HCM/infinevo-platform/code/backend/core/src/main/java/com/infinevo/core/report/ReportDefinitionServiceImpl.java):
     Verify `source.permissibleActions().contains(request.requiredAction())`; reject unauthorized action codes with `ValidationException("Action " + requiredAction + " is not permissible for source " + sourceCode)`.
* **Tests to add**:
  * Unit test in `ReportDefinitionServiceTest.java`: attempting to register a report definition with an action code not in the source's allow-list fails with HTTP 400 `ValidationException`.

#### Finding 5: No soft-delete test and RLS IT cannot fail
* **Problem**: In [`ExportRlsIT.java`](file:///d:/ashtsiddhi/HCM/infinevo-platform/code/backend/core/src/test/java/com/infinevo/core/report/ExportRlsIT.java), no soft-deleted employee is seeded. Additionally, [`EmployeeReportSource.java:82`](file:///d:/ashtsiddhi/HCM/infinevo-platform/code/backend/core/src/main/java/com/infinevo/core/report/source/EmployeeReportSource.java#L82) includes `WHERE e.tenantId = :tenant`, so the test passes even if PostgreSQL Row-Level Security is disabled.
* **Remediation**:
  1. In [`ExportRlsIT.java`](file:///d:/ashtsiddhi/HCM/infinevo-platform/code/backend/core/src/test/java/com/infinevo/core/report/ExportRlsIT.java):
     * Insert a soft-deleted employee (`deleted = true`) in Tenant A; export employees and assert that the soft-deleted record is excluded from both the CSV content and row count.
     * Add a direct database assertion over a raw `app_user` connection without the application's `WHERE tenant_id = ...` clause (mirroring `AuditRlsIT.java:85-103`) to prove PostgreSQL RLS itself isolates `core.report_definition` and `core.employee`.

---

### Ticket 3: W-21 — Document Store

#### Finding 6: Spec contradicts code streaming implementation
* **Problem**: [`W-21-document-store.md:71,109`](file:///d:/ashtsiddhi/HCM/infinevo-platform/docs/target-state/features/W-21-document-store.md#L71) asserts *"The bytes never pass through the application. The app issues a link; Blob serves the file."* However, under `W-51` network perimeter rules, storage accounts use private endpoints with no public blob access. Consequently, [`DocumentDownloadController.java:99`](file:///d:/ashtsiddhi/HCM/infinevo-platform/code/backend/core/src/main/java/com/infinevo/core/document/DocumentDownloadController.java#L99) streams bytes via `InputStreamResource`.
* **Remediation**:
  * Update [`W-21-document-store.md`](file:///d:/ashtsiddhi/HCM/infinevo-platform/docs/target-state/features/W-21-document-store.md) in § 3 (Flow), § 4 (Backend changes), and § 14 (As-built):
    Clarify that because storage is private and unexposed to direct client traffic, `DocumentDownloadController` authenticates the signed HMAC token and streams content directly using an `InputStreamResource` without buffering the file into memory.

---

### Phase 1 Local Verification Checklist

```bash
# 1. Run spotless formatting check & unit/integration test suite
./mvnw -B clean verify

# 2. Run Definition of Done verification scripts
node .claude/scripts/check-done.mjs W-20.1
node .claude/scripts/check-done.mjs W-21
node .claude/scripts/check-done.mjs W-23.1
```
*(Ready for manual commit & push by user)*

---

## 3. Phase 2: Downstream Group 3 Implementation

```
Phase 1 Merged to main (W-20.1, W-21, W-23.1)
  │
  ▼
Ticket 4: W-20.2 Delivery Scheduler
  ├── Flyway V041 (core.reminder_rule, core.list_tenants_for_sweep)
  ├── Core: /api/v1/reminder-rules CRUD API
  └── Worker: NotificationDeliveryWorker & ReminderRuleSweepJob
  │
  ├─────────────────────────────────────────┐
  ▼                                         ▼
Ticket 5: W-22.2 Audit Retention          Ticket 6: W-23.2 Scheduled Reports
  ├── Flyway (core.retention_run)           ├── Flyway (core.report_schedule)
  └── Worker: Retention sweep on            ├── Worker: Async export queue consumer
      audit_log & notification              └── Worker: Report delivery scheduler
```

---

### Ticket 4: W-20.2 — Delivery Scheduler
* **Prerequisites**: `W-20.1` merged; `W-12.1` on `main` (provides `core.tenant.timezone`); `W-11.3` on `main` (provides `core.reminder_rule.manage`).
* **Components to Implement**:
  1. **Database (`code/backend/migration`)**:
     * Migration `V041__reminder_rule.sql`:
       * Table `core.reminder_rule` (`id`, `tenant_id`, `event`, `days_before`, `target_role`, `is_active`, audit columns) with tenant RLS.
       * `SECURITY DEFINER` function `core.list_tenants_for_sweep()` allowing worker batch jobs to iterate across active tenants safely.
  2. **Core API (`code/backend/core`)**:
     * CRUD endpoints at `/api/v1/reminder-rules` guarded with `@RequiresAction("core.reminder_rule.manage")`.
  3. **Worker Processing (`code/backend/worker`)**:
     * `NotificationDeliveryWorker`: Polls `core.notification` where `status = 'QUEUED'`, delivers emails via provider, updates status to `SENT` or `FAILED`.
     * `ReminderRuleSweepJob`: Scheduled task running under `@SchedulerLock`. For each tenant, evaluates rules against employee milestone dates at local midnight (using `core.tenant.timezone`) and calls `NotificationService.compose(...)`.
* **Testing**:
  * Integration tests with Testcontainers verifying queue consumption, idempotent delivery, and timezone-aware reminder composition.

---

### Ticket 5: W-22.2 — Audit Retention Sweep
* **Prerequisites**: `W-22.1` on `main`; `W-20.1` and `W-20.2` merged (reuses `core.list_tenants_for_sweep()` and worker scheduler lock).
* **Components to Implement**:
  1. **Database (`code/backend/migration`)**:
     * Migration creating `core.retention_run` table (`id`, `tenant_id`, `run_started_at`, `run_completed_at`, `audit_rows_purged`, `notification_rows_purged`).
  2. **Worker Sweep Engine (`code/backend/worker`)**:
     * `AuditRetentionJob`: Nightly `@Scheduled` job with cluster lock (`@SchedulerLock`).
     * Retention windows:
       * `core.audit_log`: 7-year statutory window.
       * `core.notification`: `core.tenant.notification_retention_months` (default: 12 months).
     * Batch chunk deletion (`DELETE ... WHERE id IN (SELECT id ... LIMIT 1000)`) to prevent table-level locks or transaction log exhaustion.
* **Testing**:
  * Integration tests asserting records older than retention policies are removed and newer records are strictly preserved.

---

### Ticket 6: W-23.2 — Scheduled Reports on Worker
* **Prerequisites**: `W-23.1` merged; `W-20.2` merged; `W-52.1` on `main`.
* **Components to Implement**:
  1. **Database (`code/backend/migration`)**:
     * Migration creating `core.report_schedule` table (`id`, `tenant_id`, `definition_id`, `cron_expression`, `recipient_emails`, `is_active`, RLS).
  2. **Async Export Pipeline (`code/backend/core` + `worker`)**:
     * Update `ExportController` to accept `?async=true`, returning `202 Accepted` and enqueuing an export task on Azure Storage Queue.
     * Implement queue listener in `worker`: pulls export messages, runs streaming generation via `ExportService`, stores artifact in `DocumentService.store(EXPORT, ...)`.
     * Generates a 7-day signed link and triggers completion notification via `NotificationService.compose(...)`.
  3. **Scheduled Report Job (`code/backend/worker`)**:
     * Worker scheduler matching active `report_schedule` cron triggers and submitting export tasks to the queue.
* **Testing**:
  * End-to-end integration test: schedule triggers -> worker builds Excel stream -> uploads to blob -> delivers 7-day signed link to recipient.

---

## 4. Execution Protocol for the Developer

1. **Local Editing**: Make all code and test modifications on local branch `dev-devashish`.
2. **Local Validation**: Run `mvn clean verify` and `check-done.mjs` locally.
3. **Local Commit**: Commit changes with descriptive messages conforming to repository standards.
4. **Push & PR**: When ready, push the branch to remote and trigger CI verification.
