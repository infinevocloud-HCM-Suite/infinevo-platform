package com.infinevo.payroll.priorpayroll;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * W-38.1 §7: Integration test for partial success semantics.
 * Proves that a good row appearing AFTER a bad row is committed to the database,
 * and is not rolled back by failures occurring earlier or later in the import loop.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PriorPayrollPartialSuccessIT extends AbstractIntegrationTest {

    private static final String FY = "2026-2027";

    @Autowired
    private PriorPayrollImportService importService;

    private UUID emp1Id;
    private UUID emp2Id;
    private UUID emp3Id;

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
    void setUp() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
        PayrollTestSchema.seedTenants();

        TenantContext.set(TENANT_A);
        emp1Id = PayrollTestSchema.insertEmployee(TENANT_A, "EMP-01", LocalDate.of(2025, 1, 1));
        emp2Id = PayrollTestSchema.insertEmployee(TENANT_A, "EMP-02", LocalDate.of(2025, 1, 1));
        emp3Id = PayrollTestSchema.insertEmployee(TENANT_A, "EMP-03", LocalDate.of(2025, 1, 1));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a good row after a bad row is committed, not rolled back")
    void goodRowAfterBadRowIsCommitted() throws Exception {
        TenantContext.set(TENANT_A);

        // Pre-insert an import log and a row for EMP-02 (2026-04) to trigger a DB-level unique constraint collision
        UUID initialImportLogId = UUID.randomUUID();
        UUID dummyDocId =
                PayrollTestSchema.insertDocument(TENANT_A, "dummy.csv", "test".getBytes(StandardCharsets.UTF_8));
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO payroll.prior_payroll_import_log
                        (id, tenant_id, source_document_id, financial_year, status, is_dry_run)
                    VALUES (?, ?, ?, ?, 'COMPLETED', false)
                    """)) {
                ps.setObject(1, initialImportLogId);
                ps.setObject(2, TENANT_A);
                ps.setObject(3, dummyDocId);
                ps.setString(4, FY);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO payroll.prior_payroll_month
                        (id, tenant_id, employee_id, period, gross_earnings, epf_employee, esi_employee,
                         professional_tax, tds, net_pay, import_id)
                    VALUES (gen_random_uuid(), ?, ?, '2026-04', 100000, 1800, 0, 200, 5000, 93000, ?)
                    """)) {
                ps.setObject(1, TENANT_A);
                ps.setObject(2, emp2Id);
                ps.setObject(3, initialImportLogId);
                ps.executeUpdate();
            }
        }

        // CSV arrangement:
        // Line 2: EMP-01, 2026-04 -> VALID (commits)
        // Line 3: EMP-BAD, 2026-04 -> INVALID EMPLOYEE (validation fails)
        // Line 4: EMP-01, 2026-05 -> VALID (commits, proving loop continues after validation error)
        // Line 5: EMP-02, 2026-04 -> FAILS AT DB WRITE (ALREADY_IMPORTED)
        // Line 6: EMP-03, 2026-04 -> VALID (commits, proving loop continues after DB write failure)
        String csv =
                """
                employee_number,period,gross_earnings,epf_employee,esi_employee,professional_tax,tds,net_pay
                EMP-01,2026-04,100000,1800,0,200,5000,93000
                EMP-BAD,2026-04,100000,0,0,0,0,100000
                EMP-01,2026-05,100000,1800,0,200,5000,93000
                EMP-02,2026-04,100000,1800,0,200,5000,93000
                EMP-03,2026-04,100000,1800,0,200,5000,93000
                """;

        UUID docId =
                PayrollTestSchema.insertDocument(TENANT_A, "partial_success.csv", csv.getBytes(StandardCharsets.UTF_8));

        PriorPayrollImportResponse response = importService.importFile(docId, FY, false);

        assertThat(response.status()).isEqualTo(PriorPayrollImportStatus.COMPLETED_WITH_ERRORS);
        assertThat(response.rowsTotal()).isEqualTo(5);
        assertThat(response.rowsImported()).isEqualTo(3);
        assertThat(response.rowsFailed()).isEqualTo(2);

        // Verify that the rows after bad rows are committed in DB
        // Total rows in DB should be 1 (pre-existing) + 3 (newly imported) = 4
        assertThat(countRowsForEmployee(TENANT_A, emp1Id)).isEqualTo(2); // 2026-04 and 2026-05
        assertThat(countRowsForEmployee(TENANT_A, emp2Id)).isEqualTo(1); // pre-existing only
        assertThat(countRowsForEmployee(TENANT_A, emp3Id)).isEqualTo(1); // 2026-04
    }

    private int countRowsForEmployee(UUID tenantId, UUID employeeId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM payroll.prior_payroll_month WHERE tenant_id = ? AND employee_id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
