package com.infinevo.worker.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.document.Document;
import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentRepository;
import com.infinevo.core.job.service.JobService;
import com.infinevo.core.report.ExportService;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration test proving async export consumer behaviour (W-23.2 §7).
 *
 * <p>Covers:
 * <ol>
 *   <li>A job created in the database and consumed by {@link AsyncExportConsumer} lands in
 *       {@code COMPLETED} state with the document id as {@code result_payload}.</li>
 *   <li>A redelivered message (job already {@code COMPLETED}) is silently dropped: the export
 *       service is not called a second time and no second document is written.</li>
 * </ol>
 */
@SpringBootTest(classes = com.infinevo.worker.InfinevoWorkerApplication.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, ReportWorkerTestSchema.Initializer.class})
class AsyncExportIT extends AbstractIntegrationTest {

    @Autowired
    private AsyncExportConsumer consumer;

    @Autowired
    private JobService jobService;

    @Autowired
    private DocumentRepository documentRepository;

    @SpyBean
    private ExportService exportService;

    @Autowired
    private ObjectMapper objectMapper;

    private static UUID tenantId;
    private static UUID defId;

    @BeforeAll
    static void initSchema() throws Exception {
        ReportWorkerTestSchema.jdbcUrl();
        tenantId = UUID.randomUUID();
        defId = UUID.randomUUID();

        try (Connection conn = ReportWorkerTestSchema.migrationConnection()) {
            ReportWorkerTestSchema.insertTenant(conn, tenantId, "Async Export Tenant", "UTC");
            ReportWorkerTestSchema.insertReportDefinition(
                    conn,
                    defId,
                    tenantId,
                    "ASYNC_AUDIT_DEF",
                    "Async Audit Export",
                    "audit_log",
                    "[\"occurred_at\", \"actor\"]",
                    "core.audit.read",
                    "CSV");
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Consumer processes job to COMPLETED and stores documentId in result_payload")
    void consumerCompletesJobWithDocumentId() throws Exception {
        String jobId = "async-export-it-" + UUID.randomUUID();

        // Arrange: create a QUEUED job and assemble the export request payload
        TenantContext.set(tenantId);
        jobService.createJob(
                jobId,
                tenantId,
                "report",
                objectMapper.writeValueAsString(new ExportService.ExportRequest(defId, null)));

        String payload = objectMapper.writeValueAsString(new ExportService.ExportRequest(defId, null));
        QueueMessage<String> message = QueueMessage.of(jobId, tenantId, "report", payload);

        // Act
        consumer.onMessage(message);

        // Assert 1: job status is COMPLETED and result_payload matches a created document id
        String jdbcUrl = ReportWorkerTestSchema.jdbcUrl();
        try (Connection conn = ReportWorkerTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT status, result_payload FROM core.job_status WHERE job_id = ?")) {
            ps.setString(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("COMPLETED");
                String resultPayload = rs.getString("result_payload");
                assertThat(resultPayload).isNotNull();

                // Assert 2: a document with that id exists
                UUID documentId = UUID.fromString(resultPayload);
                TenantContext.set(tenantId);
                List<Document> docs = documentRepository.findAll().stream()
                        .filter(d -> documentId.equals(d.getId()))
                        .toList();
                assertThat(docs).hasSize(1);
                assertThat(docs.get(0).getKind()).isEqualTo(DocumentKind.EXPORT);
            }
        }
    }

    @Test
    @DisplayName("A redelivered message for a COMPLETED job is dropped without producing a second document")
    void redeliveredMessageIsDropped() throws Exception {
        String jobId = "async-export-redelivery-" + UUID.randomUUID();

        TenantContext.set(tenantId);
        jobService.createJob(
                jobId,
                tenantId,
                "report",
                objectMapper.writeValueAsString(new ExportService.ExportRequest(defId, null)));

        String payload = objectMapper.writeValueAsString(new ExportService.ExportRequest(defId, null));
        QueueMessage<String> message = QueueMessage.of(jobId, tenantId, "report", payload);

        // First delivery: should complete normally
        consumer.onMessage(message);

        // Capture document count after first delivery
        TenantContext.set(tenantId);
        long docsAfterFirst = documentRepository.findAll().stream()
                .filter(d -> tenantId.equals(d.getTenantId()))
                .count();

        // Reset spy invocation tracking for second delivery
        Mockito.clearInvocations(exportService);

        // Second delivery of the exact same message (redelivery scenario)
        consumer.onMessage(message);

        // Export service must NOT have been called again
        verify(exportService, never()).export(Mockito.any(), Mockito.any());

        // Document count must not have increased
        TenantContext.set(tenantId);
        long docsAfterSecond = documentRepository.findAll().stream()
                .filter(d -> tenantId.equals(d.getTenantId()))
                .count();
        assertThat(docsAfterSecond)
                .as("Redelivery must not produce a second document")
                .isEqualTo(docsAfterFirst);
    }
}
