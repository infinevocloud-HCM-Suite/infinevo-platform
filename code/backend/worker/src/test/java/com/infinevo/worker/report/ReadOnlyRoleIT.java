package com.infinevo.worker.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration test for readonly_user role and datasource configuration (W-23.2 §7).
 *
 * <p>Asserts both boundaries:
 * <ol>
 *   <li>The report read datasource connects as {@code readonly_user} and is refused {@code INSERT}.</li>
 *   <li>Its reads are strictly tenant-scoped by Row-Level Security through {@code TenantBindingDataSourceProxy}.</li>
 * </ol>
 */
@SpringBootTest(classes = com.infinevo.worker.InfinevoWorkerApplication.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, ReportWorkerTestSchema.Initializer.class})
class ReadOnlyRoleIT extends AbstractIntegrationTest {

    @Autowired
    @Qualifier("reportReadDataSource")
    private DataSource reportReadDataSource;

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
            ReportWorkerTestSchema.insertTenant(conn, tenantA, "Tenant A", "UTC");
            ReportWorkerTestSchema.insertTenant(conn, tenantB, "Tenant B", "UTC");
            ReportWorkerTestSchema.insertReportDefinition(
                    conn, defA, tenantA, "EMP_A", "Employee A", "employee", "[\"id\"]", "core.report.read", "CSV");
            ReportWorkerTestSchema.insertReportDefinition(
                    conn, defB, tenantB, "EMP_B", "Employee B", "employee", "[\"id\"]", "core.report.read", "CSV");
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("The report read datasource is refused INSERT (permission denied 42501)")
    void insertIsRefusedOnReadDatasource() {
        UUID schedId = UUID.randomUUID();
        TenantContext.set(tenantA);

        assertThatThrownBy(() -> {
                    try (Connection conn = reportReadDataSource.getConnection()) {
                        conn.setAutoCommit(false);
                        try (PreparedStatement ps = conn.prepareStatement(
                                "INSERT INTO core.report_schedule (id, tenant_id, definition_id, cadence, send_at_local_time, recipient_emails) "
                                        + "VALUES (?, ?, ?, 'DAILY', '09:00:00', 'audit@example.com')")) {
                            ps.setObject(1, schedId);
                            ps.setObject(2, tenantA);
                            ps.setObject(3, defA);
                            ps.executeUpdate();
                        }
                    }
                })
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");
    }

    @Test
    @DisplayName("Reads on the report read datasource are tenant-scoped by RLS")
    void readsAreTenantScopedByRls() throws Exception {
        UUID schedA = UUID.randomUUID();
        UUID schedB = UUID.randomUUID();

        // Insert as app_user / migration_user for both tenants
        try (Connection conn = ReportWorkerTestSchema.migrationConnection()) {
            ReportWorkerTestSchema.insertReportSchedule(
                    conn, schedA, tenantA, defA, "DAILY", null, LocalTime.of(9, 0), "a@example.com", true);
            ReportWorkerTestSchema.insertReportSchedule(
                    conn, schedB, tenantB, defB, "DAILY", null, LocalTime.of(9, 0), "b@example.com", true);
        }

        // 1. Bound to Tenant A: only schedA is visible
        TenantContext.set(tenantA);
        try (Connection conn = reportReadDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT id FROM core.report_schedule WHERE id IN (?, ?)")) {
                ps.setObject(1, schedA);
                ps.setObject(2, schedB);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat((UUID) rs.getObject("id")).isEqualTo(schedA);
                    assertThat(rs.next()).isFalse();
                }
            }
        }

        // 2. Bound to Tenant B: only schedB is visible
        TenantContext.set(tenantB);
        try (Connection conn = reportReadDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT id FROM core.report_schedule WHERE id IN (?, ?)")) {
                ps.setObject(1, schedA);
                ps.setObject(2, schedB);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat((UUID) rs.getObject("id")).isEqualTo(schedB);
                    assertThat(rs.next()).isFalse();
                }
            }
        }

        // 3. Unbound: 0 rows visible under RLS
        TenantContext.clear();
        try (Connection conn = reportReadDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM core.report_schedule WHERE id IN (?, ?)")) {
                ps.setObject(1, schedA);
                ps.setObject(2, schedB);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isZero();
                }
            }
        }
    }
}
