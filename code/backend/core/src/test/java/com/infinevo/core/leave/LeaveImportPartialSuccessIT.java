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

    /**
     * The leave year in progress. A policy dated before it is refused and a closed year is never
     * rewritten, so a fixed year here would start failing the day that year ends.
     */
    private static final int YEAR = LocalDate.now(java.time.ZoneOffset.UTC).getYear();

    @Autowired
    private LeaveImportService leaveImportService;

    @Autowired
    private LeaveTypeService leaveTypeService;

    @Autowired
    private DocumentService documentService;

    private UUID typeSlId;
    private UUID policyId;
    private UUID emp3Id;

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

        // Seed valid employees
        LeaveTestSchema.insertEmployee(TENANT_A, "EMP-1", "Alice", "alice@acme.com");
        LeaveTestSchema.insertEmployee(TENANT_A, "EMP-2", "Bob", "bob@acme.com");
        emp3Id = LeaveTestSchema.insertEmployee(TENANT_A, "EMP-3", "Charlie", "charlie@acme.com");
        LeaveTestSchema.insertEmployee(TENANT_A, "EMP-4", "Diana", "diana@acme.com");
        LeaveTestSchema.insertEmployee(TENANT_A, "EMP-5", "Edward", "edward@acme.com");
        LeaveTestSchema.insertEmployee(TENANT_A, "EMP-6", "Fiona", "fiona@acme.com");

        // Seed 1 active leave type with policy
        LeaveTypeResponse slResp = leaveTypeService.createLeaveType(
                new LeaveTypeRequest("Sick Leave", "SL", true, LeaveUnit.DAYS, false, LocalDate.of(YEAR, 1, 1), null));
        typeSlId = slResp.id();

        LeavePolicyResponse polResp = leaveTypeService.setPolicy(
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
                        LocalDate.of(YEAR, 1, 1),
                        List.of()));
        policyId = polResp.id();
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

        // Pre-create allocation for EMP-3 to trigger a DB-level write failure (UNIQUE constraint violation)
        // during the write loop, proving partial success semantics when writes fail mid-loop.
        try (Connection conn = LeaveTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.leave_allocation
                        (id, tenant_id, employee_id, leave_type_id, leave_year, year_start_date, year_end_date,
                         entitlement_days, accrued_days, carried_forward_days, pro_rate_factor, policy_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, TENANT_A);
            ps.setObject(3, emp3Id);
            ps.setObject(4, typeSlId);
            ps.setString(5, String.valueOf(YEAR));
            ps.setDate(6, java.sql.Date.valueOf(LocalDate.of(YEAR, 1, 1)));
            ps.setDate(7, java.sql.Date.valueOf(LocalDate.of(YEAR, 12, 31)));
            ps.setBigDecimal(8, java.math.BigDecimal.valueOf(15));
            ps.setBigDecimal(9, java.math.BigDecimal.ZERO);
            ps.setBigDecimal(10, java.math.BigDecimal.ZERO);
            ps.setBigDecimal(11, java.math.BigDecimal.ONE);
            ps.setObject(12, policyId);
            ps.executeUpdate();
        }

        // CSV containing:
        // EMP-1: valid (writes successfully)
        // EMP-2: valid (writes successfully)
        // EMP-BAD1: invalid employee (rejected during validation)
        // EMP-3: passes validation, but FAILS during DB write loop (DUPLICATE_ALLOCATION)
        // EMP-6: invalid days (rejected during validation)
        // EMP-4: valid (writes successfully, proving EMP-3 write failure did not stop the loop)
        // EMP-5: valid (writes successfully)
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
        LeaveImportResultResponse response =
                leaveImportService.importLeaves(TENANT_A, docId, String.valueOf(YEAR), false);

        // Verify response DTO
        assertThat(response.status()).isEqualTo(ImportStatus.COMPLETED_WITH_ERRORS);
        assertThat(response.rowsTotal()).isEqualTo(7);
        assertThat(response.rowsImported()).isEqualTo(4);
        assertThat(response.rowsFailed()).isEqualTo(3);
        assertThat(response.errorDocumentId()).isEqualTo(errDocId);

        // Direct DB verification: assert exactly 5 allocation records exist in core.leave_allocation
        // (1 pre-existing EMP-3 allocation + 4 newly imported allocations for EMP-1, EMP-2, EMP-4, EMP-5)
        try (Connection conn = LeaveTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core.leave_allocation WHERE tenant_id = ?")) {
            ps.setObject(1, TENANT_A);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1))
                        .as("Exactly 5 allocation rows (1 pre-existing + 4 newly imported) must exist in DB")
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
                assertThat(rs.getInt("rows_imported")).isEqualTo(4);
                assertThat(rs.getInt("rows_failed")).isEqualTo(3);
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
        assertThat(errorReport).contains("5,EMP-3,SL,10.00,DUPLICATE_ALLOCATION");
        assertThat(errorReport).contains("6,EMP-6,SL,not-a-number,INVALID_DAYS");
    }
}
