package com.infinevo.worker.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.infinevo.core.document.Document;
import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentRepository;
import com.infinevo.core.notification.Channel;
import com.infinevo.core.notification.Notification;
import com.infinevo.core.notification.NotificationRepository;
import com.infinevo.core.notification.NotificationStatus;
import com.infinevo.core.report.ReportSchedule;
import com.infinevo.core.report.ReportScheduleRepository;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.queue.QueueProducer;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration test proving a due schedule produces a document and queues exactly one notification (W-23.2 §7).
 */
@SpringBootTest(classes = com.infinevo.worker.InfinevoWorkerApplication.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, ReportWorkerTestSchema.Initializer.class})
class ScheduledReportIT extends AbstractIntegrationTest {

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
            ReportWorkerTestSchema.insertTenant(conn, tenantId, "Scheduled Report Tenant", "UTC");
            ReportWorkerTestSchema.insertReportDefinition(
                    conn,
                    defId,
                    tenantId,
                    "AUDIT_DEF",
                    "Audit Log Export",
                    "audit_log",
                    "[\"occurred_at\", \"actor\"]",
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
    @DisplayName("A due schedule produces a document and queues exactly one notification")
    void dueScheduleProducesDocumentAndQueuesNotification() {
        UUID scheduleId = UUID.randomUUID();
        ReportSchedule schedule = new ReportSchedule(
                tenantId, defId, "DAILY", null, LocalTime.MIDNIGHT, "{}", "finance-team@example.com", true, "admin");
        schedule.setId(scheduleId);
        schedule.setOwnerUserAccountId(ownerId);

        TenantContext.set(tenantId);
        scheduleRepository.save(schedule);

        // Due whenever CI runs: a DAILY schedule at 00:00 that has not run today (the evaluator reads the real clock)
        evaluator.evaluateTenant(tenantId, "UTC");

        // 1. Verify document created
        List<Document> documents = documentRepository.findAll().stream()
                .filter(d -> tenantId.equals(d.getTenantId()))
                .toList();
        assertThat(documents).isNotEmpty();
        Document doc = documents.get(0);
        assertThat(doc.getKind()).isEqualTo(DocumentKind.EXPORT);
        assertThat(doc.getEmployeeId()).isNull();

        // 2. Verify notification created
        List<Notification> notifications = notificationRepository.findAll().stream()
                .filter(n ->
                        tenantId.equals(n.getTenantId()) && "finance-team@example.com".equals(n.getRecipientEmail()))
                .toList();
        assertThat(notifications).hasSize(1);
        Notification notif = notifications.get(0);
        assertThat(notif.getChannel()).isEqualTo(Channel.EMAIL);
        assertThat(notif.getStatus()).isEqualTo(NotificationStatus.QUEUED);
        // Composed from the SCHEDULED_REPORT template (V096), with the signed download link in it.
        assertThat(notif.getTemplateId()).isNotNull();
        assertThat(notif.getSubject()).contains("is ready");
        assertThat(notif.getBody()).contains("Download the report").contains("/api/v1/documents/download?t=");

        // 3. Verify queue message sent
        ArgumentCaptor<QueueMessage<String>> messageCaptor = ArgumentCaptor.forClass(QueueMessage.class);
        verify(queueProducer, atLeastOnce()).send(eq("notification"), messageCaptor.capture());
        assertThat(messageCaptor.getValue().getPayload())
                .isEqualTo(notif.getId().toString());

        // 4. Verify schedule updated
        ReportSchedule updatedSchedule =
                scheduleRepository.findByIdAndTenantId(scheduleId, tenantId).orElseThrow();
        assertThat(updatedSchedule.getLastRunStatus()).isEqualTo("SUCCESS");
        assertThat(updatedSchedule.getLastRunAt()).isNotNull();
        assertThat(updatedSchedule.getLastDocumentId()).isEqualTo(doc.getId());
    }
}
