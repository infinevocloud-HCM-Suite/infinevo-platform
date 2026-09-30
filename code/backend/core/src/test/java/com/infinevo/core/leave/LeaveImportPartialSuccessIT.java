package com.infinevo.core.leave;

import static com.infinevo.core.leave.LeaveTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentResponse;
import com.infinevo.core.document.DocumentService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-16.4b, spec section 7 &amp; 8 — {@code LeaveImportPartialSuccessIT}.
 *
 * <p><strong>THE CRITICAL TEST:</strong> Verifies partial success semantics for bulk leave imports.
 * The good rows are committed individually and are NOT rolled back when bad rows are encountered.
 * Proves absence of a single transaction wrapping the entire loop.
 */
@SpringBootTest(classes = LeaveTestApp.class)
@ContextConfiguration(initializers = com.infinevo.shared.test.PostgresTestContainerInitializer.class)
class LeaveImportPartialSuccessIT extends AbstractIntegrationTest {

    @Autowired
    private LeaveImportService leaveImportService;

    @Autowired
    private LeaveTypeService leaveTypeService;

    @Autowired
    private DocumentService documentService;

    private UUID typeSlId;

    @BeforeAll
    static void applySchema() throws Exception {
        LeaveTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        LeaveTestSchema.clearAll();
    }

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.clear();
        LeaveTestSchema.clearAll();
        LeaveTestSchema.seedTenants();

        TenantContext.set(TENANT_A);

        // Seed 5 valid employees
        LeaveTestSchema.insertEmployee(TENANT_A, "EMP-1", "Alice", "alice@acme.com");
        LeaveTestSchema.insertEmployee(TENANT_A, "EMP-2", "Bob", "bob@acme.com");
        LeaveTestSchema.insertEmployee(TENANT_A, "EMP-3", "Charlie", "charlie@acme.com");
        LeaveTestSchema.insertEmployee(TENANT_A, "EMP-4", "Diana", "diana@acme.com");
        LeaveTestSchema.insertEmployee(TENANT_A, "EMP-5", "Edward", "edward@acme.com");
        LeaveTestSchema.insertEmployee(TENANT_A, "EMP-6", "Fiona", "fiona@acme.com");

        // Seed 1 active leave type with policy
        LeaveTypeResponse slResp = leaveTypeService.createLeaveType(
                new LeaveTypeRequest("Sick Leave", "SL", true, LeaveUnit.DAYS, false, LocalDate.of(2026, 1, 1), null));
        typeSlId = slResp.id();

        leaveTypeService.setPolicy(
                typeSlId,
                new LeavePolicyRequest(
                        java.math.BigDecimal.valueOf(20),
                        false,
                        null,
                        null,
                        false,
                        null,
                        false,
                        null,
                        null,
                        false,
                        null,
                        null,
                        false,
                        false,
                        ExceedBalanceMode.NO_LIMIT,
                        null,
                        false,
                        null,
                        null,
                        LocalDate.of(2026, 1, 1),
                        List.of()));
    }

    @AfterEach
    void tearDown() throws SQLException {
        TenantContext.clear();
        LeaveTestSchema.clearAll();
    }

    @Test
    @DisplayName("valid rows are committed after bad rows are encountered; partial success is guaranteed")
    void partialSuccessCommitsValidRowsAndReportsErrors() throws SQLException {
        TenantContext.set(TENANT_A);

        UUID docId = UUID.randomUUID();
        UUID errDocId = UUID.randomUUID();

        // CSV containing 5 good rows and 2 bad rows
        String csv =
                """
                employee_number,leave_type_code,days
                EMP-1,SL,10.00
                EMP-2,SL,10.00
                EMP-BAD1,SL,10.00
                EMP-3,SL,10.00
                EMP-6,SL,not-a-number
                EMP-4,SL,10.00
                EMP-5,SL,10.00
                """;

        DocumentResponse docMeta = new DocumentResponse(
                docId,
                null,
                DocumentKind.EXPORT,
                "opening_balances.csv",
                "text/csv",
                csv.length(),
                "testchecksum",
                Instant.now(),
                "admin");

        when(documentService.open(docId))
                .thenReturn(new DocumentService.DocumentContent(
                        docMeta, new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));

        when(documentService.store(eq(DocumentKind.EXPORT), any(), any(), any(InputStream.class)))
                .thenReturn(errDocId);

        // Insert document rows to satisfy FK constraint on source_document_id and error_document_id
        LeaveTestSchema.insertDocumentRow(TENANT_A, docId);
        LeaveTestSchema.insertDocumentRow(TENANT_A, errDocId);

        // Execute import
        LeaveImportResultResponse response = leaveImportService.importLeaves(TENANT_A, docId, "2026", false);

        // Verify response DTO
        assertThat(response.status()).isEqualTo(ImportStatus.COMPLETED_WITH_ERRORS);
        assertThat(response.rowsTotal()).isEqualTo(7);
        assertThat(response.rowsImported()).isEqualTo(5);
        assertThat(response.rowsFailed()).isEqualTo(2);
        assertThat(response.errorDocumentId()).isEqualTo(errDocId);

        // Direct DB verification: assert exactly 5 allocation records exist in core.leave_allocation
        try (Connection conn = LeaveTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core.leave_allocation WHERE tenant_id = ?")) {
            ps.setObject(1, TENANT_A);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1))
                        .as("Exactly 5 valid allocation rows must be committed")
                        .isEqualTo(5);
            }
        }

        // Direct DB verification: assert import log reflects final counts and status
        try (Connection conn = LeaveTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT status, rows_total, rows_imported, rows_failed, error_document_id FROM core.leave_import_log WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, TENANT_A);
            ps.setObject(2, response.id());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("COMPLETED_WITH_ERRORS");
                assertThat(rs.getInt("rows_total")).isEqualTo(7);
                assertThat(rs.getInt("rows_imported")).isEqualTo(5);
                assertThat(rs.getInt("rows_failed")).isEqualTo(2);
                assertThat((UUID) rs.getObject("error_document_id")).isEqualTo(errDocId);
            }
        }

        // Verify error report document was generated and stored with error details
        ArgumentCaptor<InputStream> streamCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(documentService, org.mockito.Mockito.atLeastOnce())
                .store(eq(DocumentKind.EXPORT), any(), any(), streamCaptor.capture());

        byte[] capturedErrorBytes;
        try {
            capturedErrorBytes = streamCaptor.getValue().readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String errorReport = new String(capturedErrorBytes, StandardCharsets.UTF_8);
        assertThat(errorReport).contains("line_number,employee_number,leave_type_code,days,error_reason");
        assertThat(errorReport).contains("4,EMP-BAD1,SL,10.00,EMPLOYEE_NOT_FOUND");
        assertThat(errorReport).contains("6,EMP-6,SL,not-a-number,INVALID_DAYS");
    }
}
