package com.infinevo.payroll.priorpayroll;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

/**
 * W-38.1 §7: Row-Level Security integration test for prior payroll.
 * Covers:
 * - As app_user, tenant A cannot read tenant B's rows or logs
 * - Through the service, tenant A cannot read tenant B's logs or rows
 * - A raw INSERT with tenant B's id under tenant A's context is refused by RLS
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PriorPayrollRlsIT extends AbstractIntegrationTest {

    private static final String FY = "2026-2027";

    @Autowired
    private PriorPayrollImportService importService;

    @Autowired
    private PriorPayrollService priorPayrollService;

    private UUID docAId;
    private UUID docBId;
    private UUID importAId;
    private UUID importBId;
    private UUID empAId;
    private UUID empBId;
    private UUID monthAId;
    private UUID monthBId;

    @BeforeAll
    static void applySchema() throws Exception {
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
        PayrollTestSchema.seedTenants();

        // Documents
        docAId = PayrollTestSchema.insertDocument(TENANT_A, "docA.csv", "testA".getBytes(StandardCharsets.UTF_8));
        docBId = PayrollTestSchema.insertDocument(TENANT_B, "docB.csv", "testB".getBytes(StandardCharsets.UTF_8));

        // Employees
        empAId = PayrollTestSchema.insertEmployee(TENANT_A, "EMP-A", LocalDate.of(2025, 1, 1));
        empBId = PayrollTestSchema.insertEmployee(TENANT_B, "EMP-B", LocalDate.of(2025, 1, 1));

        // Import logs
        importAId = UUID.randomUUID();
        importBId = UUID.randomUUID();

        monthAId = UUID.randomUUID();
        monthBId = UUID.randomUUID();

        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            // Insert logs
            insertLog(conn, importAId, TENANT_A, docAId, "COMPLETED");
            insertLog(conn, importBId, TENANT_B, docBId, "COMPLETED");

            // Insert months
            insertMonth(conn, monthAId, TENANT_A, empAId, "2026-04", importAId);
            insertMonth(conn, monthBId, TENANT_B, empBId, "2026-04", importBId);
        }
    }

    private void insertLog(Connection conn, UUID id, UUID tenantId, UUID docId, String status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                INSERT INTO payroll.prior_payroll_import_log
                    (id, tenant_id, source_document_id, financial_year, status, is_dry_run, rows_total, rows_imported)
                VALUES (?, ?, ?, ?, ?, false, 1, 1)
                """)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, docId);
            ps.setString(4, FY);
            ps.setString(5, status);
            ps.executeUpdate();
        }
    }

    private void insertMonth(Connection conn, UUID id, UUID tenantId, UUID empId, String period, UUID importId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                INSERT INTO payroll.prior_payroll_month
                    (id, tenant_id, employee_id, period, gross_earnings, epf_employee, esi_employee,
                     professional_tax, tds, net_pay, import_id)
                VALUES (?, ?, ?, ?, 100000, 1800, 0, 200, 5000, 93000, ?)
                """)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, empId);
            ps.setString(4, period);
            ps.setObject(5, importId);
            ps.executeUpdate();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Through the service, Tenant A cannot read Tenant B's import logs or months")
    void serviceEnforcesTenantIsolation() {
        TenantContext.set(TENANT_A);

        // Tenant A can get its own import log
        assertThat(importService.getImport(importAId)).isNotNull();
        // Tenant A cannot get Tenant B's import log
        assertThatThrownBy(() -> importService.getImport(importBId)).isInstanceOf(PriorPayrollNotFoundException.class);

        // Listing import logs under Tenant A only contains Tenant A
        var logPage = importService.listImports(PageRequest.of(0, 10));
        assertThat(logPage.getContent()).hasSize(1);
        assertThat(logPage.getContent().get(0).id()).isEqualTo(importAId);

        // Listing months under Tenant A only contains Tenant A
        var monthPage = priorPayrollService.months(FY, null, PageRequest.of(0, 10));
        assertThat(monthPage.getContent()).hasSize(1);
        assertThat(monthPage.getContent().get(0).id()).isEqualTo(monthAId);

        // Tenant A attempting to delete Tenant B's month gets 404 / NotFoundException
        assertThatThrownBy(() -> priorPayrollService.delete(monthBId))
                .isInstanceOf(PriorPayrollNotFoundException.class);
    }

    @Test
    @DisplayName("Under raw app_user connection with RLS, Tenant A only sees Tenant A rows")
    void rawAppUserConnectionHidesOtherTenant() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            PayrollTestSchema.bindTenant(conn, TENANT_A);

            // Log table
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM payroll.prior_payroll_import_log")) {
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }

            // Month table
            try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM payroll.prior_payroll_month")) {
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
        }
    }

    @Test
    @DisplayName("Under raw app_user connection bound to Tenant A, inserting a row for Tenant B is refused by RLS")
    void crossTenantInsertRefusedByRls() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            PayrollTestSchema.bindTenant(conn, TENANT_A);

            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO payroll.prior_payroll_month
                        (id, tenant_id, employee_id, period, gross_earnings, epf_employee, esi_employee,
                         professional_tax, tds, net_pay, import_id)
                    VALUES (gen_random_uuid(), ?, ?, '2026-05', 100000, 1800, 0, 200, 5000, 93000, ?)
                    """)) {
                ps.setObject(1, TENANT_B);
                ps.setObject(2, empBId);
                ps.setObject(3, importBId);

                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("row-level security");
            }
        }
    }
}
