package com.infinevo.worker.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.document.Document;
import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentRepository;
import com.infinevo.core.notification.Notification;
import com.infinevo.core.notification.NotificationRepository;
import com.infinevo.core.report.ReportSchedule;
import com.infinevo.core.report.ReportScheduleRepository;
import com.infinevo.shared.queue.QueueProducer;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration test verifying scheduled report signed link duration and document kind (W-23.2 §7).
 *
 * <p>Proves:
 * <ol>
 *   <li>The emailed link is signed for 7 days (contracts §6 decision 8), not the default 15 minutes.</li>
 *   <li>The created document has {@code kind = EXPORT} and no {@code employee_id}.</li>
 * </ol>
 */
@SpringBootTest(classes = com.infinevo.worker.InfinevoWorkerApplication.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, ReportWorkerTestSchema.Initializer.class})
class ScheduledReportLinkIT extends AbstractIntegrationTest {

    @Autowired
    private ReportScheduleEvaluator evaluator;

    @Autowired
    private ReportScheduleRepository scheduleRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockBean
    private QueueProducer queueProducer;

    private static UUID tenantId;
    private static UUID defId;
    private static UUID ownerId;

    @BeforeAll
    static void initSchema() throws Exception {
        ReportWorkerTestSchema.jdbcUrl();
        tenantId = UUID.randomUUID();
        defId = UUID.randomUUID();

        try (Connection conn = ReportWorkerTestSchema.migrationConnection()) {
            ReportWorkerTestSchema.insertTenant(conn, tenantId, "Link Test Tenant", "UTC");
            ReportWorkerTestSchema.insertReportDefinition(
                    conn,
                    defId,
                    tenantId,
                    "LINK_DEF",
                    "Link Test Export",
                    "audit_log",
                    "[\"occurred_at\"]",
                    "core.audit.read",
                    "CSV");
            // B-4: the evaluator re-checks this account's actions before running the schedule.
            ownerId = ReportWorkerTestSchema.insertTenantAdminAccount(conn, tenantId);
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Emailed link is signed for 7 days, document has kind=EXPORT and null employee_id")
    void emailedLinkSignedForSevenDaysAndDocumentIsExport() {
        UUID scheduleId = UUID.randomUUID();
        String recipient = "auditor-" + UUID.randomUUID() + "@example.com";

        ReportSchedule schedule =
                new ReportSchedule(tenantId, defId, "DAILY", null, LocalTime.MIDNIGHT, "{}", recipient, true, "admin");
        schedule.setId(scheduleId);
        schedule.setOwnerUserAccountId(ownerId);

        TenantContext.set(tenantId);
        scheduleRepository.save(schedule);

        Instant beforeSweep = Instant.now();

        // Run evaluator sweep for tenant
        evaluator.evaluateTenant(tenantId, "UTC");

        // 1. Verify document kind and null employee_id
        ReportSchedule updated =
                scheduleRepository.findByIdAndTenantId(scheduleId, tenantId).orElseThrow();
        assertThat(updated.getLastDocumentId()).isNotNull();

        Document doc = documentRepository.findById(updated.getLastDocumentId()).orElseThrow();
        assertThat(doc.getKind()).isEqualTo(DocumentKind.EXPORT);
        assertThat(doc.getEmployeeId()).isNull();

        // 2. Verify notification body and 7-day expiration
        List<Notification> notifs = notificationRepository.findAll().stream()
                .filter(n -> tenantId.equals(n.getTenantId()) && recipient.equals(n.getRecipientEmail()))
                .toList();
        assertThat(notifs).hasSize(1);
        Notification notif = notifs.get(0);

        // The SCHEDULED_REPORT template (V096) ends "The link works until ${expires_at}."
        String body = notif.getBody();
        Matcher until = Pattern.compile("works until (\\S+?Z)\\.").matcher(body);
        assertThat(until.find()).as("the expiry in the email body").isTrue();
        Instant expiresAt = Instant.parse(until.group(1));

        Duration ttl = Duration.between(beforeSweep, expiresAt);
        // Must be ~7 days (allow a few minutes of clock slack), definitely far greater than 15 minutes!
        assertThat(ttl.toHours()).isBetween(167L, 169L); // 7 days = 168 hours
    }
}
