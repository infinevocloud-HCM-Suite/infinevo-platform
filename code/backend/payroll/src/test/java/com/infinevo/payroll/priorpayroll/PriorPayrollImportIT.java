package com.infinevo.payroll.priorpayroll;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.document.DocumentService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
 * W-38.1 §7: The acceptance test for prior payroll import.
 * A file of 6 good and 2 bad rows =>
 * 1. As dry run first => 0 rows saved, log COMPLETED_WITH_ERRORS 6/2, error file has 2 errors.
 * 2. Actual import => 6 rows saved, log COMPLETED_WITH_ERRORS 6/2, error file has 2 errors.
 * 3. Importing it again => 0 new rows, 6 ALREADY_IMPORTED + 2 validation errors = 8 failed.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PriorPayrollImportIT extends AbstractIntegrationTest {

    private static final String FY = "2026-2027";

    @Autowired
    private PriorPayrollImportService importService;

    @Autowired
    private DocumentService documentService;

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
        PayrollTestSchema.insertEmployee(TENANT_A, "EMP-01", LocalDate.of(2025, 1, 1));
        PayrollTestSchema.insertEmployee(TENANT_A, "EMP-02", LocalDate.of(2025, 1, 1));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("acceptance test: dry-run, actual import with partial success, and duplicate re-import")
    void importAcceptanceFlow() throws Exception {
        TenantContext.set(TENANT_A);

        String csv =
                """
                employee_number,period,gross_earnings,epf_employee,esi_employee,professional_tax,tds,net_pay
                EMP-01,2026-04,100000,1800,0,200,5000,93000
                EMP-01,2026-05,100000,1800,0,200,5000,93000
                EMP-01,2026-06,100000,1800,0,200,5000,93000
                EMP-02,2026-04,120000,1800,0,200,6000,112000
                EMP-02,2026-05,120000,1800,0,200,6000,112000
                EMP-02,2026-06,120000,1800,0,200,6000,112000
                EMP-UNKNOWN,2026-04,100000,0,0,0,0,100000
                EMP-01,2026-07,100000,0,0,0,0,110000
                """;

        UUID docId = PayrollTestSchema.insertDocument(
                TENANT_A, "prior_payroll_test.csv", csv.getBytes(StandardCharsets.UTF_8));

        // 1. Dry run
        PriorPayrollImportResponse dryRunResponse = importService.importFile(docId, FY, true);
        assertThat(dryRunResponse.isDryRun()).isTrue();
        assertThat(dryRunResponse.status()).isEqualTo(PriorPayrollImportStatus.COMPLETED_WITH_ERRORS);
        assertThat(dryRunResponse.rowsTotal()).isEqualTo(8);
        assertThat(dryRunResponse.rowsImported()).isEqualTo(6);
        assertThat(dryRunResponse.rowsFailed()).isEqualTo(2);
        assertThat(dryRunResponse.errorDocumentId()).isNotNull();

        // Verify error document has the 2 bad line numbers (8 and 9)
        List<String> dryRunErrors = readDocumentLines(dryRunResponse.errorDocumentId());
        assertThat(dryRunErrors).hasSize(3); // header + 2 error lines
        assertThat(dryRunErrors.get(1)).contains("8").contains("UNKNOWN_EMPLOYEE");
        assertThat(dryRunErrors.get(2)).contains("9").contains("NET_TOO_HIGH");

        // Verify 0 rows in DB after dry run
        assertThat(countPriorPayrollMonths(TENANT_A)).isEqualTo(0);

        // 2. Actual import
        PriorPayrollImportResponse actualResponse = importService.importFile(docId, FY, false);
        assertThat(actualResponse.isDryRun()).isFalse();
        assertThat(actualResponse.status()).isEqualTo(PriorPayrollImportStatus.COMPLETED_WITH_ERRORS);
        assertThat(actualResponse.rowsTotal()).isEqualTo(8);
        assertThat(actualResponse.rowsImported()).isEqualTo(6);
        assertThat(actualResponse.rowsFailed()).isEqualTo(2);
        assertThat(actualResponse.errorDocumentId()).isNotNull();

        List<String> actualErrors = readDocumentLines(actualResponse.errorDocumentId());
        assertThat(actualErrors).hasSize(3);
        assertThat(actualErrors.get(1)).contains("8").contains("UNKNOWN_EMPLOYEE");
        assertThat(actualErrors.get(2)).contains("9").contains("NET_TOO_HIGH");

        // Verify exactly 6 rows committed in DB
        assertThat(countPriorPayrollMonths(TENANT_A)).isEqualTo(6);

        // 3. Re-importing the same file again
        PriorPayrollImportResponse reimportResponse = importService.importFile(docId, FY, false);
        assertThat(reimportResponse.status()).isEqualTo(PriorPayrollImportStatus.COMPLETED_WITH_ERRORS);
        assertThat(reimportResponse.rowsTotal()).isEqualTo(8);
        assertThat(reimportResponse.rowsImported()).isEqualTo(0);
        assertThat(reimportResponse.rowsFailed()).isEqualTo(8);
        assertThat(reimportResponse.errorDocumentId()).isNotNull();

        List<String> reimportErrors = readDocumentLines(reimportResponse.errorDocumentId());
        // 1 header + 8 error rows
        assertThat(reimportErrors).hasSize(9);
        // The first 6 rows failed with ALREADY_IMPORTED
        for (int i = 1; i <= 6; i++) {
            assertThat(reimportErrors.get(i)).contains("ALREADY_IMPORTED");
        }
        assertThat(reimportErrors.get(7)).contains("UNKNOWN_EMPLOYEE");
        assertThat(reimportErrors.get(8)).contains("NET_TOO_HIGH");

        // DB still has exactly 6 rows
        assertThat(countPriorPayrollMonths(TENANT_A)).isEqualTo(6);
    }

    private List<String> readDocumentLines(UUID documentId) throws Exception {
        DocumentService.DocumentContent content = documentService.open(documentId);
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(content.content(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private int countPriorPayrollMonths(UUID tenantId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM payroll.prior_payroll_month WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
