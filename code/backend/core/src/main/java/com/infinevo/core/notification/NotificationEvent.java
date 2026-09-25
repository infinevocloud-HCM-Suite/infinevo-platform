package com.infinevo.core.notification;

import java.util.Set;

/**
 * Every event that can notify someone (W-20.1, contracts section 5 row 15) — closed, deliberately.
 *
 * <p>The Payroll half is the email-provider template map the frozen system actually sends from
 * ({@code legacy/Payroll-Bend-SBoot/src/main/resources/application.properties:52-65}); the rest are the
 * events this platform's own tickets raise. A new event is a code change and a migration: it needs a
 * default template seeded for every tenant, and {@code core.notification_template} carries a
 * {@code CHECK} over this list ({@code V038}).
 *
 * <p>Each event names the placeholders its caller supplies. A template may use only those — checked
 * against the seeded defaults by {@code NotificationEventTest}, and at render time by
 * {@link TemplateRenderer}, which fails on a placeholder with no value rather than sending
 * {@code ${name}} to a person.
 */
public enum NotificationEvent {
    POI_REMINDER("employee_name", "financial_year", "due_date"),
    POI_SUBMITTED("employee_name", "financial_year"),
    IT_DECLARATION_REMINDER("employee_name", "financial_year", "due_date"),
    IT_DECLARATION_LOCK("employee_name", "financial_year"),
    IT_DECLARATION_RELEASE("employee_name", "financial_year", "due_date"),
    /** Legacy {@code salary.slip} — the one payroll email the frozen system sends ({@code PayRunServiceImpl.java:975}). */
    PAYSLIP_READY("employee_name", "period", "link"),
    /** {@code W-24.2}. The recipient is not an employee yet, so the caller passes {@code recipient_email}. */
    USER_INVITATION("tenant_name", "link"),
    EMPLOYEE_INVITATION("employee_name", "tenant_name", "link"),
    /** One event for both legacy credential mails; the template decides the wording. */
    CREDENTIALS("tenant_name", "link"),
    LEAVE_APPLIED("employee_name", "leave_type", "from_date", "to_date"),
    LEAVE_APPROVED("employee_name", "leave_type", "from_date", "to_date"),
    LEAVE_REJECTED("employee_name", "leave_type", "from_date", "to_date"),
    LEAVE_CANCELLED("employee_name", "leave_type", "from_date", "to_date"),
    APPROVAL_PENDING("employee_name", "requester_name", "request_title"),
    APPROVAL_DECIDED("employee_name", "request_title", "decision"),
    /** Rule-driven by {@code W-20.2}, from HRMS's {@code NotificationSchedular}. */
    TIMESHEET_REMINDER("employee_name", "week_start");

    private final Set<String> placeholders;

    NotificationEvent(String... placeholders) {
        this.placeholders = Set.of(placeholders);
    }

    /** The names a template for this event may use as {@code ${name}}. */
    public Set<String> placeholders() {
        return placeholders;
    }
}
