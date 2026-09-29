package com.infinevo.worker.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.PermissionReadService;
import com.infinevo.core.document.DocumentLinkService;
import com.infinevo.core.notification.NotificationEvent;
import com.infinevo.core.notification.NotificationService;
import com.infinevo.core.report.ExportService;
import com.infinevo.core.report.ReportDefinition;
import com.infinevo.core.report.ReportDefinitionRepository;
import com.infinevo.core.report.ReportSchedule;
import com.infinevo.core.report.ReportScheduleRepository;
import com.infinevo.core.report.ReportScheduleServiceImpl;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.worker.scheduler.SendSlot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs due report schedules across every tenant (W-23.2, spec section 3).
 *
 * <pre>
 * core.list_tenants_for_sweep()  -- as worker_user, which W-20.2 granted EXECUTE
 *   for each tenant: bind it, read its timezone, find due schedules (isDue)
 *     claim the schedule        -- last_run_at + RUNNING, only if nobody else has
 *     ExportService.exportForJob -- the definition's rows into core.document
 *     signedLink(doc, 7 days)   -- contracts section 6 decision 8
 *     compose(SCHEDULED_REPORT) per recipient -- W-20.1's template, escaping and outbox
 *     record SUCCESS or FAILED on the schedule
 * </pre>
 *
 * <p><strong>Claim first.</strong> The schedule is marked as run for this period before anything is
 * produced, so a crash part-way through leaves a missed report and a {@code RUNNING} status to see,
 * never a second file and a second email to every recipient on the next sweep.
 *
 * <p><strong>No caller.</strong> The worker has nobody signed in, so the export runs through
 * {@link ExportService#exportForJob}, which trusts the caller was already checked.
 *
 * <p><strong>Re-checked at run time (manager's review item B-4).</strong> The definition's
 * {@code required_action} was checked once, against whoever saved the schedule, at save time —
 * but a definition's source, columns or required action can change afterwards
 * ({@code ReportDefinitionServiceImpl#update}), and the saver can lose the grant. Either would leave
 * a schedule quietly emailing a different export, or one its owner could no longer even see, to
 * addresses that may be outside the tenant. So before every run this checks the schedule's
 * {@code owner_user_account_id} against the definition's <em>current</em> {@code required_action},
 * through {@link PermissionReadService} — the same read {@code PermissionReadServiceImpl} answers for
 * a live request, just for a stored account rather than the current caller. A schedule with no owner
 * on record (saved before this check existed, or by a non-JWT actor) fails the same way a schedule
 * whose owner no longer holds the action does: refused, not skipped.
 */
@Component
public class ReportScheduleEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduleEvaluator.class);

    public static final Duration LINK_TTL = Duration.ofDays(7);

    private static final String ACTOR = "worker";

    public record TenantSweepTarget(UUID tenantId, String timezone) {}

    private final JdbcTemplate jdbcTemplate;
    private final ReportScheduleRepository schedules;
    private final ReportDefinitionRepository definitions;
    private final ExportService exportService;
    private final DocumentLinkService linkService;
    private final NotificationService notificationService;
    private final PermissionReadService permissionReadService;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ReportScheduleEvaluator(
            JdbcTemplate jdbcTemplate,
            ReportScheduleRepository schedules,
            ReportDefinitionRepository definitions,
            ExportService exportService,
            DocumentLinkService linkService,
            NotificationService notificationService,
            PermissionReadService permissionReadService,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper,
            @Autowired(required = false) Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.schedules = Objects.requireNonNull(schedules, "schedules must not be null");
        this.definitions = Objects.requireNonNull(definitions, "definitions must not be null");
        this.exportService = Objects.requireNonNull(exportService, "exportService must not be null");
        this.linkService = Objects.requireNonNull(linkService, "linkService must not be null");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService must not be null");
        this.permissionReadService =
                Objects.requireNonNull(permissionReadService, "permissionReadService must not be null");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Scheduled(cron = "${worker.report.cron:0 */15 * * * *}")
    @SchedulerLock(name = "report_schedule_evaluator", lockAtMostFor = "PT14M", lockAtLeastFor = "PT1M")
    public void evaluateSchedules() {
        log.info("Starting scheduled reports evaluation sweep");
        for (TenantSweepTarget target : listTenants()) {
            try {
                evaluateTenant(target.tenantId(), target.timezone());
            } catch (RuntimeException e) {
                log.error("Error evaluating report schedules for tenant {}", target.tenantId(), e);
            }
        }
        log.info("Finished scheduled reports evaluation sweep");
    }

    /** Every tenant, through W-20.2's SECURITY DEFINER function, on the worker's own pool. */
    public List<TenantSweepTarget> listTenants() {
        return jdbcTemplate.query(
                "SELECT tenant_id, timezone FROM core.list_tenants_for_sweep()",
                (rs, rowNum) -> new TenantSweepTarget((UUID) rs.getObject("tenant_id"), rs.getString("timezone")));
    }

    public void evaluateTenant(UUID tenantId, String timezone) {
        ZoneId zone = resolveZone(tenantId, timezone);
        ZonedDateTime now = clock.instant().atZone(zone);

        boolean wasBound = TenantContext.isBound();
        try {
            TenantContext.set(tenantId);
            List<ReportSchedule> due =
                    transactions.execute(status -> schedules.findByTenantIdAndIsActiveTrue(tenantId));
            if (due != null) {
                for (ReportSchedule schedule : due) {
                    if (isDue(schedule, now, zone)) {
                        executeSchedule(schedule);
                    }
                }
            }
        } finally {
            if (!wasBound) {
                TenantContext.clear();
            }
        }
    }

    /** Claims, exports, links, notifies and records one due schedule. Never throws. */
    public void executeSchedule(ReportSchedule schedule) {
        UUID tenantId = schedule.getTenantId();
        Instant startedAt = clock.instant();

        Integer claimed = transactions.execute(
                status -> schedules.claim(schedule.getId(), tenantId, schedule.getLastRunAt(), startedAt));
        if (claimed == null || claimed == 0) {
            log.info("Report schedule {} was already claimed for this period; skipping", schedule.getId());
            return;
        }

        UUID documentId = null;
        try {
            ReportDefinition definition = transactions.execute(status -> definitions
                    .findByIdAndTenantId(schedule.getDefinitionId(), tenantId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Report definition " + schedule.getDefinitionId() + " no longer exists")));
            requireOwnerStillHoldsTheRequiredAction(schedule, definition);

            ExportService.ExportResponse export =
                    exportService.exportForJob(schedule.getDefinitionId(), parseFilters(schedule.getFilters()));
            documentId = export.documentId();
            DocumentLinkService.SignedLink link = linkService.signedLink(documentId, LINK_TTL);

            List<String> recipients = ReportScheduleServiceImpl.recipientsOf(schedule.getRecipientEmails());
            int failed = 0;
            for (String recipient : recipients) {
                try {
                    List<UUID> written = notificationService.compose(
                            NotificationEvent.SCHEDULED_REPORT,
                            null,
                            Map.of(
                                    NotificationService.RECIPIENT_EMAIL,
                                    recipient,
                                    NotificationService.SUBJECT_REF,
                                    "report_schedule:" + schedule.getId(),
                                    "report_name",
                                    definition.getName(),
                                    "link",
                                    link.url(),
                                    "expires_at",
                                    link.expiresAt().toString()));
                    if (written.isEmpty()) {
                        failed++;
                    }
                } catch (RuntimeException e) {
                    // The address is personal data: count it, do not log it.
                    failed++;
                    log.warn("Report schedule {} could not notify one recipient", schedule.getId(), e);
                }
            }

            String status = failed == 0 ? ReportSchedule.STATUS_SUCCESS : ReportSchedule.STATUS_FAILED;
            record(schedule, startedAt, status, documentId);
            log.info(
                    "Ran report schedule {} in tenant {}: document {}, {} of {} recipient(s) notified",
                    schedule.getId(),
                    tenantId,
                    documentId,
                    recipients.size() - failed,
                    recipients.size());
        } catch (RuntimeException e) {
            log.error("Report schedule {} in tenant {} failed", schedule.getId(), tenantId, e);
            record(schedule, startedAt, ReportSchedule.STATUS_FAILED, documentId);
        }
    }

    /**
     * Manager's review item B-4. Refuses a schedule whose owner cannot be shown, right now, to hold
     * the definition's {@code required_action} — because it changed since the schedule was saved, or
     * the owner's grant was revoked, or no owner was ever recorded. Reads directly through
     * {@link PermissionReadService}, uncached: this runs at most once per schedule per sweep, and a
     * stale cached "yes" is exactly the failure this check exists to catch.
     *
     * @throws IllegalStateException the owner does not currently hold the action, or there is no owner
     */
    private void requireOwnerStillHoldsTheRequiredAction(ReportSchedule schedule, ReportDefinition definition) {
        UUID ownerId = schedule.getOwnerUserAccountId();
        if (ownerId == null) {
            throw new IllegalStateException("Report schedule " + schedule.getId()
                    + " has no owner on record to re-check " + definition.getRequiredAction() + " against");
        }
        if (!permissionReadService.actionsOf(ownerId).contains(definition.getRequiredAction())) {
            throw new IllegalStateException("Report schedule " + schedule.getId() + "'s owner " + ownerId
                    + " no longer holds " + definition.getRequiredAction());
        }
    }

    /** Writes the outcome onto a fresh copy: the claim changed the row under the one in hand. */
    private void record(ReportSchedule schedule, Instant at, String status, UUID documentId) {
        try {
            transactions.executeWithoutResult(tx -> schedules
                    .findByIdAndTenantId(schedule.getId(), schedule.getTenantId())
                    .ifPresent(fresh -> {
                        fresh.recordRun(at, status, documentId, ACTOR);
                        schedules.save(fresh);
                    }));
        } catch (RuntimeException e) {
            log.error("Could not record the outcome of report schedule {}", schedule.getId(), e);
        }
    }

    /**
     * Whether a schedule is due at the given tenant-local time: past its send time, on its day, and not
     * yet run this period. A 31st falls back to a shorter month's last day.
     *
     * <p>"Its day" is the day of the send slot being evaluated, not the calendar day of the sweep:
     * a 23:50 slot is picked up by the first sweep after midnight, and a run recorded then counts
     * against the day it was for ({@link SendSlot}).
     */
    public static boolean isDue(ReportSchedule schedule, ZonedDateTime now, ZoneId zone) {
        if (!schedule.isActive()) {
            return false;
        }

        LocalTime sendTime = schedule.getSendAtLocalTime();
        Optional<LocalDate> slot = SendSlot.dateDue(now.withZoneSameInstant(zone), sendTime);
        if (slot.isEmpty()) {
            return false;
        }

        LocalDate today = slot.get();
        Instant lastRunAt = schedule.getLastRunAt();
        LocalDate lastRunDate = lastRunAt != null ? SendSlot.slotDateOf(lastRunAt, zone, sendTime) : null;

        String cadence = schedule.getCadence() != null ? schedule.getCadence().toUpperCase() : "";

        return switch (cadence) {
            case "DAILY" -> {
                // If it already ran today, not due
                yield lastRunDate == null || !lastRunDate.equals(today);
            }
            case "WEEKLY" -> {
                int dayOfWeek = today.getDayOfWeek().getValue(); // 1 = Monday ... 7 = Sunday
                int targetDay = schedule.getDayOfPeriod() != null ? schedule.getDayOfPeriod() : 1;
                if (dayOfWeek != targetDay) {
                    yield false;
                }
                yield lastRunDate == null || !lastRunDate.equals(today);
            }
            case "MONTHLY" -> {
                // Month-end clamping: if dayOfPeriod is e.g. 31, clamp to last day of current month (e.g. Feb 28/29,
                // Apr 30)
                int requestedDay = schedule.getDayOfPeriod() != null ? schedule.getDayOfPeriod() : 1;
                int maxDayOfMonth = today.lengthOfMonth();
                int effectiveDay = Math.min(requestedDay, maxDayOfMonth);

                if (today.getDayOfMonth() != effectiveDay) {
                    yield false;
                }
                if (lastRunDate != null
                        && lastRunDate.getYear() == today.getYear()
                        && lastRunDate.getMonth() == today.getMonth()) {
                    yield false;
                }
                yield true;
            }
            default -> false;
        };
    }

    private static ZoneId resolveZone(UUID tenantId, String timezone) {
        if (timezone == null || timezone.isBlank()) {
            log.warn("Tenant {} has no timezone set; report schedules run on UTC", tenantId);
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (RuntimeException e) {
            log.warn("Tenant {} has an unreadable timezone; report schedules run on UTC", tenantId);
            return ZoneOffset.UTC;
        }
    }

    private Map<String, String> parseFilters(String filtersJson) {
        if (filtersJson == null || filtersJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(filtersJson, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("The schedule's filters are not a JSON object of text values", e);
        }
    }
}
