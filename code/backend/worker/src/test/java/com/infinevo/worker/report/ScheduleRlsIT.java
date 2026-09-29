package com.infinevo.worker.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.document.Document;
import com.infinevo.core.document.DocumentRepository;
import com.infinevo.core.notification.Notification;
import com.infinevo.core.notification.NotificationRepository;
import com.infinevo.core.report.ReportScheduleRepository;
import com.infinevo.shared.queue.QueueProducer;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration test proving Row-Level Security isolation for scheduled reports (W-23.2 §7).
 *
 * <p>Proves:
 * <ol>
 *   <li>A sweep bound to tenant A runs no schedule of tenant B.</li>
 *   <li>Database sessions bound to tenant A cannot read or mutate tenant B's schedules under RLS.</li>
 * </ol>
 */
@SpringBootTest(classes = com.infinevo.worker.InfinevoWorkerApplication.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, ReportWorkerTestSchema.Initializer.class})
class ScheduleRlsIT extends AbstractIntegrationTest {

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

    private static UUID tenantA;
    private static UUID tenantB;
    private static UUID defA;
    private static UUID defB;

    @BeforeAll
    static void initSchema() throws Exception {
        ReportWorkerTestSchema.jdbcUrl();
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        defA = UUID.randomUUID();
        defB = UUID.randomUUID();

        try (Connection conn = ReportWorkerTestSchema.migrationConnection()) {
            ReportWorkerTestSchema.insertTenant(conn, tenantA, "Tenant A RLS", "UTC");
            ReportWorkerTestSchema.insertTenant(conn, tenantB, "Tenant B RLS", "UTC");
            ReportWorkerTestSchema.insertReportDefinition(
                    conn,
                    defA,
                    tenantA,
                    "RLS_DEF_A",
                    "Export A",
                    "audit_log",
                    "[\"occurred_at\"]",
                    "core.audit.read",
                    "CSV");
            ReportWorkerTestSchema.insertReportDefinition(
                    conn,
                    defB,
                    tenantB,
                    "RLS_DEF_B",
                    "Export B",
                    "audit_log",
                    "[\"occurred_at\"]",
                    "core.audit.read",
                    "CSV");
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A sweep bound to tenant A runs no schedule of tenant B")
    void sweepBoundToTenantARunsNoScheduleOfTenantB() throws Exception {
        UUID schedA = UUID.randomUUID();
        UUID schedB = UUID.randomUUID();
        String emailA = "tenant-a-recip-" + UUID.randomUUID() + "@example.com";
        String emailB = "tenant-b-recip-" + UUID.randomUUID() + "@example.com";

        // Insert both schedules via migration connection (bypassing RLS)
        try (Connection conn = ReportWorkerTestSchema.migrationConnection()) {
            // B-4: the evaluator re-checks the owner's required_action before running, so a schedule
            // meant to actually run needs a real owner who holds it - tenant-admin holds every action.
            UUID ownerA = ReportWorkerTestSchema.insertTenantAdminAccount(conn, tenantA);
            ReportWorkerTestSchema.insertReportSchedule(
                    conn, schedA, tenantA, defA, "DAILY", null, LocalTime.MIDNIGHT, emailA, true, ownerA);
            ReportWorkerTestSchema.insertReportSchedule(
                    conn, schedB, tenantB, defB, "DAILY", null, LocalTime.MIDNIGHT, emailB, true);
        }

        // Run evaluator sweep bound explicitly to tenant A
        TenantContext.set(tenantA);
        try {
            evaluator.evaluateTenant(tenantA, "UTC");
        } finally {
            TenantContext.clear();
        }

        // 1. Verify Tenant A schedule was executed
        try (Connection conn = ReportWorkerTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT last_run_at, last_run_status, last_document_id FROM core.report_schedule WHERE id = ?")) {
            ps.setObject(1, schedA);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getTimestamp("last_run_at")).isNotNull();
                assertThat(rs.getString("last_run_status")).isEqualTo("SUCCESS");
                assertThat((UUID) rs.getObject("last_document_id")).isNotNull();
            }
        }

        // 2. Verify Tenant B schedule was NOT executed (last_run_at is null)
        try (Connection conn = ReportWorkerTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT last_run_at, last_run_status, last_document_id FROM core.report_schedule WHERE id = ?")) {
            ps.setObject(1, schedB);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getTimestamp("last_run_at")).isNull();
                assertThat(rs.getString("last_run_status")).isNull();
                assertThat(rs.getObject("last_document_id")).isNull();
            }
        }

        // 3. Verify Document creation: Tenant A has documents, Tenant B has NONE
        TenantContext.set(tenantA);
        List<Document> docA;
        try {
            docA = documentRepository.findAll().stream()
                    .filter(d -> tenantA.equals(d.getTenantId()))
                    .toList();
        } finally {
            TenantContext.clear();
        }

        TenantContext.set(tenantB);
        List<Document> docB;
        try {
            docB = documentRepository.findAll().stream()
                    .filter(d -> tenantB.equals(d.getTenantId()))
                    .toList();
        } finally {
            TenantContext.clear();
        }

        assertThat(docA).isNotEmpty();
        assertThat(docB).isEmpty();

        // 4. Verify Notification creation: Tenant A recipient received notification, Tenant B recipient received NONE
        TenantContext.set(tenantA);
        List<Notification> notifsA;
        try {
            notifsA = notificationRepository.findAll().stream()
                    .filter(n -> tenantA.equals(n.getTenantId()) && emailA.equals(n.getRecipientEmail()))
                    .toList();
        } finally {
            TenantContext.clear();
        }

        TenantContext.set(tenantB);
        List<Notification> notifsB;
        try {
            notifsB = notificationRepository.findAll().stream()
                    .filter(n -> tenantB.equals(n.getTenantId()) && emailB.equals(n.getRecipientEmail()))
                    .toList();
        } finally {
            TenantContext.clear();
        }

        assertThat(notifsA).hasSize(1);
        assertThat(notifsB).isEmpty();
    }

    @Test
    @DisplayName("RLS hides tenant B's report schedules from session bound to tenant A and blocks cross-tenant insert")
    void rlsHidesTenantBSchedulesAndBlocksCrossTenantInsert() throws Exception {
        UUID schedA = UUID.randomUUID();
        UUID schedB = UUID.randomUUID();

        try (Connection conn = ReportWorkerTestSchema.migrationConnection()) {
            ReportWorkerTestSchema.insertReportSchedule(
                    conn, schedA, tenantA, defA, "DAILY", null, LocalTime.MIDNIGHT, "a@example.com", true);
            ReportWorkerTestSchema.insertReportSchedule(
                    conn, schedB, tenantB, defB, "DAILY", null, LocalTime.MIDNIGHT, "b@example.com", true);
        }

        // Connect as app_user under RLS bound to Tenant A
        try (Connection conn = ReportWorkerTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            TenantContext.set(tenantA);
            TenantContext.setForConnection(conn);

            // Tenant A can see schedA
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM core.report_schedule WHERE id = ?")) {
                ps.setObject(1, schedA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                }
            }

            // Tenant A CANNOT see schedB (returns false / empty)
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM core.report_schedule WHERE id = ?")) {
                ps.setObject(1, schedB);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next())
                            .as("Tenant B schedule must not be visible to Tenant A")
                            .isFalse();
                }
            }

            // Attempt to insert for Tenant B while bound to Tenant A: must fail RLS WITH CHECK policy
            UUID rogueSched = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.report_schedule (id, tenant_id, definition_id, cadence, send_at_local_time, recipient_emails) "
                            + "VALUES (?, ?, ?, 'DAILY', '09:00:00', 'rogue@example.com')")) {
                ps.setObject(1, rogueSched);
                ps.setObject(2, tenantB);
                ps.setObject(3, defB);
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("violates row-level security policy");
            }

            conn.rollback();
        } finally {
            TenantContext.clear();
        }
    }
}
