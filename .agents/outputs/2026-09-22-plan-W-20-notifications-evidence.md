# W-20 Notifications — Evidence

| # | Question | Answer | Evidence |
|---|---|---|---|
| 1 | HRMS Notification entity | `notifications` table; recipient-focused; no email field | legacy/HRMS_Backend/entity/Notification.java:1–54 |
| 2 | Five reminder tables | ApprovalReminder, EmployeeReminder, EscalationReminder, HrReminder, SupervisorReminder | legacy/HRMS_Backend/entity/notificationconfig/*.java; DB_SCHEMA.md:376 |
| 3 | Payroll Reminder entity | `claimsanddeclarations/Reminder.java` (used by FBP, POI, IT Declaration) | FEATURE_MAP.md:261 |
| 4 | Email delivery | Brevo API (HRMS: MailService.java; Payroll: BrevoEmailService.java) | legacy/HRMS_Backend/service/MailService.java:15,85; legacy/Payroll-Bend-SBoot/service/BrevoEmailService.java |
| 5 | Payroll sends email for payroll event | Yes — salary slip after payrun finalization | legacy/Payroll-Bend-SBoot/serviceimpl/payruns/PayRunServiceImpl.java:975 |
| 6 | Scheduler found | Four @Scheduled tasks; only HRMS timesheet schedulers send notifications | See schedulers section below |

---

## 1. HRMS Notifications Entity

**Entity**: `Notification.java`
**Table**: `notifications`
**File**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Notification.java`

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | Auto-generated |
| `message` | VARCHAR | Notification text |
| `recipientId` | VARCHAR | User/admin ID; NOT an email field |
| `senderId` | VARCHAR | System or user ID |
| `type` | VARCHAR | SUBMISSION, APPROVAL, REJECTION, REMINDER (line 24) |
| `isRead` | BOOLEAN | Read flag |
| `createdAt` | DATETIME | Timestamp |
| `timesheetId` | VARCHAR | Related object ID if applicable |

**How created**: Via `NotificationService.createNotification()` (called from ReminderScheduler, line 37–43).

**How read**: In-app query on `notifications` table by `recipientId`. No email link visible in this entity.

**Citation**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Notification.java:1–54`

---

## 2. HRMS Five Reminder Configuration Tables

**Finding**: Five separate `Reminder` entities define timesheet submission/approval reminder schedules per role or scenario.

### ApprovalReminder
**Entity**: `entity/notificationconfig/ApprovalReminder.java`
**Stores**: Reminder schedule for approval-level notifications (used in NotificationSchedular.java:80–91).

**Citation**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/notificationconfig/ApprovalReminder.java`

### EmployeeReminder
**Entity**: `entity/notificationconfig/EmployeeReminder.java`
**Stores**: Employee-level timesheet submission reminders (used in NotificationSchedular.java:67–78).

**Citation**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/notificationconfig/EmployeeReminder.java`

### EscalationReminder
**Entity**: `entity/notificationconfig/EscalationReminder.java`
**Stores**: Escalation logic for overdue submissions (used in NotificationSchedular.java:119–135).

**Citation**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/notificationconfig/EscalationReminder.java`

### HrReminder
**Entity**: `entity/notificationconfig/HrReminder.java`
**Stores**: HR-specific reminder schedule (used in NotificationSchedular.java:106–117).

**Citation**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/notificationconfig/HrReminder.java`

### SupervisorReminder
**Entity**: `entity/notificationconfig/SupervisorReminder.java`
**Stores**: Supervisor/manager notification schedule (used in NotificationSchedular.java:93–104).

**Citation**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/notificationconfig/SupervisorReminder.java`

Each Reminder entity is bound to a repository and invoked by the `NotificationSchedular` during `@Scheduled` ticks. All are timesheet-focused; no employee record or payroll lifecycle reminders.

**Citation (all)**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/notificationconfig/` + `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/scheduler/NotificationSchedular.java:54–65`

---

## 3. Payroll Reminder Entity

**Entity**: `claimsanddeclarations/Reminder.java`
**Scope**: Used by IT Declaration, POI (Proof of Investment), and Flexible Benefit Plan (FBP) reminder workflows.
**Used by**: `ReimbursementClaimServiceImpl`, `POIReminderService`, `ITDeclarationReminderService`.

**Citation**: FEATURE_MAP.md:261 ("entity/claimsanddeclarations/Reminder.java") — this is a live table used only for tax/investment/reimbursement reminders, not payroll events.

---

## 4. Email Delivery: Brevo Integration

### HRMS (MailService.java)
**File**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/service/MailService.java`

**Configuration**:
- API Key: `${brevo.api.key}` (line 15, from `application.properties`:48)
- From Email: `${brevo.from.email}` (line 18, from `application.properties`:49)
- From Name: `${brevo.from.name}` (line 19, from `application.properties`:50)

**API Endpoint**: `https://api.brevo.com/v3/smtp/email` (line 85)

**Template**: None — messages are composed as plain text/HTML inline. The body is passed directly, not via template ID.

**Methods**:
- `sendEmail(to, subject, body)` — generic method (line 41)
- `sendEmail(to, cc, subject, body)` — with CC list (line 45)
- `sendStatusUpdate(to, status)` — hardcoded for timesheet status (line 103)

**Citation**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/service/MailService.java:1–108`

### Payroll (BrevoEmailService.java)
**File**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/service/BrevoEmailService.java`

**Configuration**:
- API Key: `${brevo.api.key}` (line 30)
- API URL: `${brevo.api.url}` (line 33)
- Sender Name: `${brevo.email.sender.name}` (line 36)
- Sender Email: `${brevo.email.sender.email}` (line 39)

**Templates**: Via Brevo Template IDs stored in `EmailTemplateConfig` (line 46, 79).

**Template IDs used**:
- `POI_REMINDER` (line 202)
- `POI_SUBMISSION` (line 403)
- `SALARY_SLIP` (line 430) — *payroll event email*
- `EMPLOYEE_INVITATION` (line 503)
- `USER_INVITATION` (line 536)
- `EMPLOYEE_CREDENTIALS` (line 551)
- `USER_CREDENTIALS` (line 572)

**Methods**:
- `sendEmail(templateKey, recipientEmail, recipientName, templateParams, replyToEmail, replyToName)` — generic template-based send (line 63)
- `sendHtmlEmail(recipientEmail, recipientName, subject, htmlContent)` — bespoke HTML (line 98)
- `sendPOIReminder(...)` (line 148) — POI deadline notification
- `sendPOISubmissionEmail(...)` (line 354) — POI submission confirmation
- `sendSalarySlipEmail(employee, org, params)` (line 416) — **payroll salary slip email**

**Citation**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/service/BrevoEmailService.java:1–600`

---

## 5. Payroll Email for Payroll Events: Salary Slip After Payrun

**Finding**: Payroll DOES send email for a payroll event — specifically, salary slip emails when a pay run is finalized.

**Trigger**: `PayRunServiceImpl.java`, called after pay run completion.

**Code**:
```java
// PayRunServiceImpl.java lines 965–989
params.put("employee_name", employee.getFirstName() + " " + employee.getLastName());
params.put("employee_number", employee.getEmployeeNumber());
params.put("month", payRun.getProcessingPeriod());
params.put("payment_date", LocalDate.now().toString());
params.put("net_salary", epr.getNetPay());

String token = payslipTokenService.generateToken(...);
params.put("emp_portal_link", portalBaseUrl + "/public/payslips/" + ...);

boolean sent = brevoEmailService.sendSalarySlipEmail(employee, org, params);
if (sent) {
    log.info("Salary slip email sent to {}", employee.getEmployeeNumber());
}
```

**Template**: `SALARY_SLIP` (Brevo template ID injected via `EmailTemplateConfig`).

**Citation**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/payruns/PayRunServiceImpl.java:975–984`

---

## 6. Schedulers: @Scheduled and Quartz

**Finding**: Four `@Scheduled` tasks exist; none is Quartz. All are Spring `@Scheduled` with fixed-rate or cron expressions.

### HRMS

#### NotificationSchedular
**File**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/scheduler/NotificationSchedular.java`

**Method**: `processAllReminders()` (line 54–65)
- **Trigger**: `@Scheduled(fixedRate = 60000)` — every 60 seconds
- **Calls**: Sequentially invokes all five reminder types (line 58–62)
- **Action**: Queries enabled reminder configs and calls `notificationSchedularService` to run them
- **Result**: Creates `Notification` entries if conditions match (day/time); sends emails NOT from scheduler itself but from the service layer

**Citation**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/scheduler/NotificationSchedular.java:54–65`

#### ReminderScheduler
**File**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/scheduler/ReminderScheduler.java`

**Methods** (lines 27–120):
- `sendFirstReminder()` (line 27) — `@Scheduled(cron = "0 59 23 ? * SAT")` **COMMENTED OUT** (line 26)
- `sendSecondReminder()` (line 49) — `@Scheduled(cron = "0 59 23 ? * SUN")` **COMMENTED OUT** (line 48)
- `sendSupervisorAlert()` (line 71) — `@Scheduled(cron = "0 0 14 ? * MON")` **COMMENTED OUT** (line 70)
- `sendAdminFirstAlert()` (line 94) — `@Scheduled(cron = "0 0 14 ? * TUE")` **COMMENTED OUT** (line 93)
- `sendAdminSecondAlert()` (line 100) — `@Scheduled(cron = "0 0 14 ? * WED")` **COMMENTED OUT** (line 99)

**Status**: Defined but disabled (all `@Scheduled` annotations are commented out). If enabled, they would send reminder notifications via `notificationService.createNotification()` (line 37–43).

**Citation**: `legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/scheduler/ReminderScheduler.java:1–127`

### Payroll

#### POIReminderScheduler
**File**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/scheduler/POIReminderScheduler.java`

**Method**: `evaluatePOIReminders()` (line 18–22)
- **Trigger**: `@Scheduled(cron = "0 0 9 * * ?", zone = "UTC")` — daily at 09:00 UTC
- **Calls**: `poiReminderService.processReminders()`
- **Action**: Evaluates Proof of Investment (tax declaration) deadline reminders per employee; sends emails if conditions met (via `BrevoEmailService`)
- **Scope**: Tax/investment workflow, not payroll event

**Citation**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/scheduler/POIReminderScheduler.java:1–23`

#### ITDeclarationAutoLockScheduler
**File**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/scheduler/ITDeclarationAutoLockScheduler.java`

**Method**: `autoLockExpiredDeclarations()` (line 25–71)
- **Trigger**: `@Scheduled(cron = "0 0 0 * * *", zone = "UTC")` — daily at 00:00 UTC (midnight)
- **Action**: Finds active IT declarations past their deadline and locks them (line 46–49); optionally sends lock notification emails (line 56–61, conditional on `sendMailOnEmployeeLevelItLockAndRelease` and `sendMailOnItDeclarationLock`)
- **Scope**: Income Tax Declaration workflow, not payroll event

**Citation**: `legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/scheduler/ITDeclarationAutoLockScheduler.java:1–72`

---

## Summary

| Aspect | Finding |
|---|---|
| **Notifications today** | In-app only; created by scheduler; stored in `notifications` table per recipient |
| **Email delivery** | Brevo API (HTTPS POST to `api.brevo.com/v3/smtp/email`) |
| **HRMS reminder config** | Five separate reminder entities (approval, employee, escalation, HR, supervisor); all timesheet-focused |
| **Payroll reminder config** | Single `Reminder` entity used for tax/investment/FBP workflows only; no payroll event reminders |
| **Payroll sends email?** | Yes — salary slip emails after payrun finalization (via `PayRunServiceImpl.java:975`) |
| **Templates** | HRMS: inline text; Payroll: Brevo template IDs (POI_REMINDER, SALARY_SLIP, etc.) |
| **Schedulers** | Four `@Scheduled` tasks: NotificationSchedular (60s), ReminderScheduler (cron, disabled), POIReminderScheduler (9 AM daily), ITDeclarationAutoLockScheduler (midnight daily) |
| **Quartz** | Not used |
| **Gap** | No notification framework for payroll events (e.g., payrun started, finalized, error); only salary slip email exists |

**Implication for W-20**: Target system must build a general notifications table with `notification_template`, `reminder_rule`, and a scheduler that:
1. Pulls reminders from rules.
2. Composes messages via templates.
3. Sends email via a queue worker (decoupled from app).
4. Logs delivery status for audit.

Payroll already sends salary slip email; new system must retain and extend this pattern to all payroll events.
