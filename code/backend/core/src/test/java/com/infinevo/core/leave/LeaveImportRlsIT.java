package com.infinevo.core.leave;

import static com.infinevo.core.leave.LeaveTestSchema.TENANT_A;
import static com.infinevo.core.leave.LeaveTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-16.4b, spec section 7 &amp; 9 — {@code LeaveImportRlsIT}.
 *
 * <p>Verifies tenant isolation on {@code core.leave_import_log}:
 * <ul>
 *   <li>Tenant A cannot read Tenant B's import logs via service or raw SQL.
 *   <li>Row-level security policy {@code tenant_isolation} strictly hides rows across tenant boundaries.
 *   <li>Cross-tenant updates and deletes affect 0 rows.
 * </ul>
 */
@SpringBootTest(classes = LeaveTestApp.class)
@ContextConfiguration(initializers = PostgresTestContainerInitializer.class)
class LeaveImportRlsIT extends AbstractIntegrationTest {

    @Autowired
    private LeaveImportService importService;

    private UUID docAId;
    private UUID docBId;
    private UUID importAId;
    private UUID importBId;

    @BeforeAll
    static void applySchema() throws Exception {
        LeaveTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        LeaveTestSchema.clearAll();
    }

    @BeforeEach
    void seed() throws Exception {
        TenantContext.clear();
        LeaveTestSchema.seedTenants();
        LeaveTestSchema.clearAll();

        docAId = UUID.randomUUID();
        docBId = UUID.randomUUID();
        importAId = UUID.randomUUID();
        importBId = UUID.randomUUID();

        // Satisfy document FK constraints
        LeaveTestSchema.insertDocumentRow(TENANT_A, docAId);
        LeaveTestSchema.insertDocumentRow(TENANT_B, docBId);

        // Seed import log for Tenant A
        insertImportLogRow(importAId, TENANT_A, docAId, "2026", 10, 10, 0, "COMPLETED");

        // Seed import log for Tenant B
        insertImportLogRow(importBId, TENANT_B, docBId, "2026", 5, 4, 1, "COMPLETED_WITH_ERRORS");
    }

    private void insertImportLogRow(
            UUID id,
            UUID tenantId,
            UUID docId,
            String leaveYear,
            int rowsTotal,
            int rowsImported,
            int rowsFailed,
            String status)
            throws SQLException {
        try (Connection conn = LeaveTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.leave_import_log
                        (id, tenant_id, source_document_id, leave_year, is_dry_run, rows_total, rows_imported, rows_failed, status, started_at, finished_at)
                        VALUES (?, ?, ?, ?, false, ?, ?, ?, ?, ?, ?)
                        """)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, docId);
            ps.setString(4, leaveYear);
            ps.setInt(5, rowsTotal);
            ps.setInt(6, rowsImported);
            ps.setInt(7, rowsFailed);
            ps.setString(8, status);
            ps.setTimestamp(9, Timestamp.from(Instant.now()));
            ps.setTimestamp(10, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Service level listing and fetching only sees the bound tenant")
    void serviceImportLogsOnlySeeBoundTenant() {
        // Tenant A view
        TenantContext.set(TENANT_A);
        Page<LeaveImportResultResponse> pageA = importService.listImports(TENANT_A, PageRequest.of(0, 10));
        assertThat(pageA.getTotalElements()).isEqualTo(1);
        assertThat(pageA.getContent().get(0).id()).isEqualTo(importAId);

        LeaveImportResultResponse fetchedA = importService.getImport(TENANT_A, importAId);
        assertThat(fetchedA.id()).isEqualTo(importAId);
        assertThat(fetchedA.status()).isEqualTo(ImportStatus.COMPLETED);

        // Requesting Tenant B log from Tenant A context throws not found
        assertThatThrownBy(() -> importService.getImport(TENANT_A, importBId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Leave import not found");

        // Tenant B view
        TenantContext.set(TENANT_B);
        Page<LeaveImportResultResponse> pageB = importService.listImports(TENANT_B, PageRequest.of(0, 10));
        assertThat(pageB.getTotalElements()).isEqualTo(1);
        assertThat(pageB.getContent().get(0).id()).isEqualTo(importBId);

        LeaveImportResultResponse fetchedB = importService.getImport(TENANT_B, importBId);
        assertThat(fetchedB.id()).isEqualTo(importBId);
        assertThat(fetchedB.status()).isEqualTo(ImportStatus.COMPLETED_WITH_ERRORS);

        // Requesting Tenant A log from Tenant B context throws not found
        assertThatThrownBy(() -> importService.getImport(TENANT_B, importAId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Leave import not found");
    }

    @Test
    @DisplayName("Row-level security alone hides the other tenant's import logs on raw app_user connection")
    void rlsHidesTheOtherTenantOnRawConnection() throws SQLException {
        assertThat(LeaveTestSchema.visibleLeaveImportLogCount(TENANT_A)).isEqualTo(1);
        assertThat(LeaveTestSchema.visibleLeaveImportLogCount(TENANT_B)).isEqualTo(1);

        try (Connection conn = LeaveTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            LeaveTestSchema.bindTenant(conn, TENANT_A);

            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM core.leave_import_log WHERE id = ?")) {
                ps.setObject(1, importBId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1))
                            .as("Tenant A must not see Tenant B's import log")
                            .isZero();
                }
            }
        }
    }

    @Test
    @DisplayName("Raw UPDATE across tenant boundary affects 0 rows")
    void rawUpdateAcrossBoundaryAffectsZeroRows() throws SQLException {
        try (Connection conn = LeaveTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            LeaveTestSchema.bindTenant(conn, TENANT_A);

            try (PreparedStatement ps =
                    conn.prepareStatement("UPDATE core.leave_import_log SET status = 'FAILED' WHERE id = ?")) {
                ps.setObject(1, importBId);
                int affected = ps.executeUpdate();
                assertThat(affected)
                        .as("UPDATE across boundary must affect 0 rows")
                        .isZero();
            }
        }
    }

    @Test
    @DisplayName("Raw DELETE across tenant boundary affects 0 rows")
    void rawDeleteAcrossBoundaryAffectsZeroRows() throws SQLException {
        try (Connection conn = LeaveTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            LeaveTestSchema.bindTenant(conn, TENANT_A);

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM core.leave_import_log WHERE id = ?")) {
                ps.setObject(1, importBId);
                int affected = ps.executeUpdate();
                assertThat(affected)
                        .as("DELETE across boundary must affect 0 rows")
                        .isZero();
            }
        }

        // Verify Tenant B's import log row is still present
        assertThat(LeaveTestSchema.visibleLeaveImportLogCount(TENANT_B)).isEqualTo(1);
    }
}
