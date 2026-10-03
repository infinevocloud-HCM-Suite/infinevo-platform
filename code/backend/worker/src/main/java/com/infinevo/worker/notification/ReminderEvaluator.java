package com.infinevo.worker.notification;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.notification.Anchor;
import com.infinevo.core.notification.NotificationService;
import com.infinevo.core.notification.ReminderAnchorResolver;
import com.infinevo.core.notification.ReminderAudienceResolver;
import com.infinevo.core.notification.ReminderRecipient;
import com.infinevo.core.notification.ReminderRule;
import com.infinevo.core.notification.ReminderRuleRepository;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.worker.scheduler.SendSlot;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled evaluator for reminder rules across all tenants (W-20.2).
 *
 * <p>Protected by ShedLock against multi-replica duplicate execution (DEBT-021).
 * Enumerates tenants via PostgreSQL {@code SECURITY DEFINER} function {@code core.list_tenants_for_sweep()},
 * binds each tenant into {@link TenantContext} under Row-Level Security, and evaluates due rules
 * against the tenant's configured timezone ({@code core.tenant.timezone}).
 */
@Component
public class ReminderEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ReminderEvaluator.class);

    private final JdbcTemplate jdbcTemplate;
    private final ReminderRuleRepository reminderRuleRepository;
    private final NotificationService notificationService;
    private final EmployeeRepository employeeRepository;
    private final List<ReminderAudienceResolver> audienceResolvers;
    private final List<ReminderAnchorResolver> anchorResolvers;
    private final Clock clock;

    public ReminderEvaluator(
            JdbcTemplate jdbcTemplate,
            ReminderRuleRepository reminderRuleRepository,
            NotificationService notificationService,
            EmployeeRepository employeeRepository,
            List<ReminderAudienceResolver> audienceResolvers,
            List<ReminderAnchorResolver> anchorResolvers,
            @Autowired(required = false) Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.reminderRuleRepository =
                Objects.requireNonNull(reminderRuleRepository, "reminderRuleRepository must not be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
        this.audienceResolvers = audienceResolvers != null ? audienceResolvers : List.of();
        this.anchorResolvers = anchorResolvers != null ? anchorResolvers : List.of();
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Scheduled(cron = "${worker.reminder.cron:0 */15 * * * *}")
    @SchedulerLock(name = "reminder_evaluator", lockAtMostFor = "PT14M", lockAtLeastFor = "PT1M")
    public void evaluateReminders() {
        log.info("Starting reminder evaluation sweep with cluster lock");
        int executed = runSweep();
        log.info("Reminder evaluation sweep completed: {} rules executed", executed);
    }

    public int runSweep() {
        List<TenantRecord> tenants = jdbcTemplate.query(
                "SELECT tenant_id, timezone FROM core.list_tenants_for_sweep()",
                (rs, rowNum) -> new TenantRecord((UUID) rs.getObject("tenant_id"), rs.getString("timezone")));

        Instant now = clock.instant();
        int totalExecuted = 0;

        for (TenantRecord tenant : tenants) {
            UUID tenantId = tenant.tenantId();
            ZoneId zone = resolveTimezone(tenantId, tenant.timezone());

            boolean wasBound = TenantContext.isBound();
            try {
                TenantContext.set(tenantId);
                totalExecuted += evaluateTenant(tenantId, zone, now);
            } catch (Exception e) {
                log.error("Failed to evaluate reminder rules for tenant {}: {}", tenantId, e.getMessage(), e);
            } finally {
                if (!wasBound) {
                    TenantContext.clear();
                }
            }
        }

        return totalExecuted;
    }

    int evaluateTenant(UUID tenantId, ZoneId zone, Instant now) {
        ZonedDateTime localNow = now.atZone(zone);

        int executedCount = 0;
        for (ReminderRule rule : reminderRuleRepository.findByTenantIdAndIsActiveTrue(tenantId)) {
            try {
                // The slot being evaluated: today once the send time has passed, or yesterday's
                // slot for a short grace after midnight, so a 23:50 rule is not skipped every day
                // because no 15-minute tick lands between 23:50 and 00:00 (SendSlot).
                Optional<LocalDate> slot = SendSlot.dateDue(localNow, rule.getSendAtLocalTime());
                if (slot.isEmpty()) {
                    continue;
                }
                LocalDate slotDate = slot.get();
                if (isRuleDue(rule, zone, slotDate, rule.getSendAtLocalTime(), slotDate.getDayOfWeek())
                        && executeRule(rule, tenantId, zone, slotDate, now)) {
                    executedCount++;
                }
            } catch (RuntimeException e) {
                // One rule's failure must not cost the tenant's other rules this sweep.
                log.error("Reminder rule {} failed in tenant {}; it runs again next sweep", rule.getId(), tenantId, e);
            }
        }
        return executedCount;
    }

    /**
     * Whether a rule is due at this tenant-local moment. {@code localDate} is the send slot's day
     * ({@link SendSlot#dateDue}); a past run counts against the slot it was for, so a 23:50 rule run
     * at 00:01 does not block the next day's slot ({@link SendSlot#slotDateOf}).
     *
     * <ul>
     *   <li>Never before its send time, and never twice for one slot.
     *   <li>WEEKLY: on its day of the week, until {@code max_repeats}.
     *   <li>Offset: from {@code offset_days} before the anchor date up to that date, not after it — a
     *       reminder for a deadline that has passed reminds nobody of anything. Once in each window;
     *       again every {@code repeat_every_days} if set, up to {@code max_repeats}. A new anchor date —
     *       next year's declaration deadline — opens a new window and the count starts again.
     * </ul>
     */
    boolean isRuleDue(ReminderRule rule, ZoneId zone, LocalDate localDate, LocalTime localTime, DayOfWeek dayOfWeek) {
        if (localTime.isBefore(rule.getSendAtLocalTime())) {
            return false;
        }
        LocalDate lastExecDate = lastSlotDate(rule, zone);
        if (localDate.equals(lastExecDate)) {
            return false;
        }

        if (rule.getAnchor() == Anchor.WEEKLY) {
            if (rule.getMaxRepeats() != null && rule.getRepeatCount() >= rule.getMaxRepeats()) {
                return false;
            }
            return rule.getDayOfWeek() != null && rule.getDayOfWeek() == dayOfWeek.getValue();
        }

        Optional<LocalDate> anchorDate = anchorDate(rule);
        if (anchorDate.isEmpty()) {
            return false;
        }
        LocalDate windowStart = anchorDate.get().minusDays(rule.getOffsetDays());
        if (localDate.isBefore(windowStart) || localDate.isAfter(anchorDate.get())) {
            return false;
        }
        if (lastExecDate == null || lastExecDate.isBefore(windowStart)) {
            return true; // the first reminder for this deadline
        }
        if (rule.getMaxRepeats() != null && rule.getRepeatCount() >= rule.getMaxRepeats()) {
            return false;
        }
        return rule.getRepeatEveryDays() != null
                && rule.getRepeatEveryDays() > 0
                && ChronoUnit.DAYS.between(lastExecDate, localDate) >= rule.getRepeatEveryDays();
    }

    /** The slot day of the rule's last run, or null if it never ran. */
    private static LocalDate lastSlotDate(ReminderRule rule, ZoneId zone) {
        return rule.getLastExecutedAt() == null
                ? null
                : SendSlot.slotDateOf(rule.getLastExecutedAt(), zone, rule.getSendAtLocalTime());
    }

    /**
     * Resolves the recipients, claims the rule, then composes it for each recipient. Returns false if
     * another sweep had already claimed it or the audience has no resolver.
     *
     * <p>Recipients are resolved before the claim, so a failure there leaves the rule unclaimed and it
     * is tried again next sweep. After the claim, a crash leaves a missed reminder, never a second one.
     */
    boolean executeRule(ReminderRule rule, UUID tenantId, ZoneId zone, LocalDate localDate, Instant now) {
        Optional<ReminderAudienceResolver> resolver = audienceResolvers.stream()
                .filter(r -> r.audience().equalsIgnoreCase(rule.getAudience()))
                .findFirst();
        if (resolver.isEmpty()) {
            log.warn("No audience resolver for audience {} on rule {}", rule.getAudience(), rule.getId());
            return false;
        }

        Optional<LocalDate> anchorDate = rule.getAnchor() == Anchor.WEEKLY ? Optional.empty() : anchorDate(rule);
        LocalDate lastExecDate = lastSlotDate(rule, zone);
        boolean newCycle = anchorDate.isPresent()
                && (lastExecDate == null
                        || lastExecDate.isBefore(anchorDate.get().minusDays(rule.getOffsetDays())));

        List<ReminderRecipient> recipients = resolver.get().recipients(rule, tenantId, localDate);

        // Claim before composing: a crash below leaves a missed reminder, never a second one to everyone.
        int claimed = reminderRuleRepository.claimRun(rule.getId(), tenantId, rule.getLastExecutedAt(), now, newCycle);
        if (claimed == 0) {
            log.info("Reminder rule {} was already run by another sweep; skipping", rule.getId());
            return false;
        }
        rule.setLastExecutedAt(now);
        rule.setRepeatCount(newCycle ? 1 : rule.getRepeatCount() + 1);

        LocalDate dueDate = anchorDate.orElse(localDate);
        int failed = 0;
        for (ReminderRecipient recipient : recipients) {
            try {
                Map<String, Object> data =
                        buildPlaceholderData(rule, recipient.employeeId(), tenantId, localDate, dueDate);
                // What the audience supplies replaces what the sweep filled in under the same name (W-43.1).
                data.putAll(recipient.placeholders());
                notificationService.compose(rule.getEvent(), recipient.employeeId(), data);
            } catch (RuntimeException e) {
                failed++;
                log.warn("Reminder rule {} could not notify one recipient", rule.getId(), e);
            }
        }
        log.info(
                "Executed reminder rule {} (repeatCount={}): {} of {} recipient(s) notified",
                rule.getId(),
                rule.getRepeatCount(),
                recipients.size() - failed,
                recipients.size());
        return true;
    }

    /**
     * The values a reminder template can use ({@code ReminderRuleService.SUPPLIED_PLACEHOLDERS}).
     * {@code due_date} is the deadline an offset rule counts from, or today for a weekly one.
     */
    Map<String, Object> buildPlaceholderData(
            ReminderRule rule, UUID recipientId, UUID tenantId, LocalDate localDate, LocalDate dueDate) {
        Map<String, Object> data = new HashMap<>();

        String employeeName = "Employee";
        if (recipientId != null) {
            Optional<Employee> emp = employeeRepository.findByIdAndTenantIdAndDeletedFalse(recipientId, tenantId);
            if (emp.isPresent()) {
                employeeName = emp.get().getFirstName()
                        + (emp.get().getLastName() != null ? " " + emp.get().getLastName() : "");
            }
        }
        data.put("employee_name", employeeName);
        data.put("due_date", dueDate.toString());
        data.put("week_start", localDate.with(DayOfWeek.MONDAY).toString());

        int year = localDate.getYear();
        int month = localDate.getMonthValue();
        String fy = month >= 4 ? year + "-" + (year + 1) : (year - 1) + "-" + year;
        data.put("financial_year", fy);
        data.put("period", localDate.getMonth().name());
        data.put(NotificationService.SUBJECT_REF, "reminder_rule:" + rule.getId());

        return data;
    }

    private Optional<LocalDate> anchorDate(ReminderRule rule) {
        Optional<ReminderAnchorResolver> resolver = anchorResolvers.stream()
                .filter(r -> r.anchor() == rule.getAnchor())
                .findFirst();
        if (resolver.isEmpty()) {
            log.debug("No anchor resolver for {}; skipping rule {}", rule.getAnchor(), rule.getId());
            return Optional.empty();
        }
        return resolver.get().resolveAnchorDate(rule, rule.getTenantId());
    }

    private ZoneId resolveTimezone(UUID tenantId, String timezoneStr) {
        if (timezoneStr != null && !timezoneStr.isBlank()) {
            try {
                return ZoneId.of(timezoneStr.trim());
            } catch (Exception e) {
                log.warn("Tenant {} has invalid timezone '{}'; falling back to UTC", tenantId, timezoneStr);
            }
        } else {
            log.warn("Tenant {} has no timezone set; falling back to UTC", tenantId);
        }
        return ZoneOffset.UTC;
    }

    private record TenantRecord(UUID tenantId, String timezone) {}
}
