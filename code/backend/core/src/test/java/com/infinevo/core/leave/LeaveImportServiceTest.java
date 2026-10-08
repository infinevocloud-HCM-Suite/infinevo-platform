package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.document.DocumentKind;
import com.infinevo.core.document.DocumentResponse;
import com.infinevo.core.document.DocumentService;
import com.infinevo.core.employee.Employee;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LeaveImportServiceTest {

    private DocumentService documentService;
    private LeaveImportLogRepository importLogRepository;
    private LeaveImportRowValidator rowValidator;
    private LeaveImportAllocationHelper allocationHelper;
    private LeaveImportServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();
    private final UUID errorDocId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentService.class);
        importLogRepository = mock(LeaveImportLogRepository.class);
        rowValidator = mock(LeaveImportRowValidator.class);
        allocationHelper = mock(LeaveImportAllocationHelper.class);

        service = new LeaveImportServiceImpl(documentService, importLogRepository, rowValidator, allocationHelper);

        when(allocationHelper.saveLog(any(LeaveImportLog.class))).thenAnswer(inv -> {
            LeaveImportLog l = inv.getArgument(0);
            if (l.getId() == null) {
                l.setId(UUID.randomUUID());
            }
            return l;
        });

        when(allocationHelper.getTenantLeaveYearStartMonth(tenantId)).thenReturn(1);

        when(documentService.store(eq(DocumentKind.EXPORT), any(), any(), any(InputStream.class)))
                .thenReturn(errorDocId);
    }

    private DocumentService.DocumentContent mockCsvContent(String csv) {
        DocumentResponse meta = new DocumentResponse(
                documentId,
                null,
                DocumentKind.EXPORT,
                null,
                "import.csv",
                "text/csv",
                csv.length(),
                "dummychecksum",
                Instant.now(),
                "system");
        return new DocumentService.DocumentContent(
                meta, new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("5 good and 2 bad rows imports 5 and reports 2 with status COMPLETED_WITH_ERRORS")
    void partialSuccessImportsGoodAndReportsBad() {
        String csv =
                """
                employee_number,leave_type_code,days
                EMP-1,SL,10.00
                EMP-2,SL,10.00
                EMP-3,SL,10.00
                EMP-4,SL,10.00
                EMP-5,SL,10.00
                EMP-BAD1,SL,10.00
                EMP-BAD2,SL,10.00
                """;

        when(documentService.open(documentId)).thenReturn(mockCsvContent(csv));

        List<LeaveImportRowValidator.ValidatedRow> valids = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Employee emp = mock(Employee.class);
            when(emp.getId()).thenReturn(UUID.randomUUID());
            LeaveType lt = mock(LeaveType.class);
            when(lt.getId()).thenReturn(UUID.randomUUID());
            LeaveImportRow row = new LeaveImportRow(i + 1, "EMP-" + i, "SL", "10.00");
            valids.add(new LeaveImportRowValidator.ValidatedRow(row, emp, lt, new BigDecimal("10.00")));
        }

        List<LeaveImportError> errors = List.of(
                new LeaveImportError(7, "EMP-BAD1", "SL", "10.00", "EMPLOYEE_NOT_FOUND"),
                new LeaveImportError(8, "EMP-BAD2", "SL", "10.00", "EMPLOYEE_NOT_FOUND"));

        when(rowValidator.validate(eq(tenantId), any()))
                .thenReturn(new LeaveImportRowValidator.ValidationResult(valids, errors));

        LeaveImportResultResponse resp = service.importLeaves(tenantId, documentId, "2026", false);

        assertThat(resp.status()).isEqualTo(ImportStatus.COMPLETED_WITH_ERRORS);
        assertThat(resp.rowsTotal()).isEqualTo(7);
        assertThat(resp.rowsImported()).isEqualTo(5);
        assertThat(resp.rowsFailed()).isEqualTo(2);
        assertThat(resp.errorDocumentId()).isEqualTo(errorDocId);

        verify(allocationHelper, times(5)).createOneAllocation(eq(tenantId), any(LeaveAllocationRequest.class));
        verify(documentService, times(1)).store(eq(DocumentKind.EXPORT), any(), any(), any());
    }

    @Test
    @DisplayName("dry run imports 0, reports errors, and creates no allocations")
    void dryRunReportsErrorsWithoutAllocations() {
        String csv =
                """
                employee_number,leave_type_code,days
                EMP-1,SL,10.00
                EMP-BAD1,SL,10.00
                """;

        when(documentService.open(documentId)).thenReturn(mockCsvContent(csv));

        Employee emp = mock(Employee.class);
        when(emp.getId()).thenReturn(UUID.randomUUID());
        LeaveType lt = mock(LeaveType.class);
        when(lt.getId()).thenReturn(UUID.randomUUID());
        LeaveImportRow row = new LeaveImportRow(2, "EMP-1", "SL", "10.00");
        var valids = List.of(new LeaveImportRowValidator.ValidatedRow(row, emp, lt, new BigDecimal("10.00")));

        var errors = List.of(new LeaveImportError(3, "EMP-BAD1", "SL", "10.00", "EMPLOYEE_NOT_FOUND"));

        when(rowValidator.validate(eq(tenantId), any()))
                .thenReturn(new LeaveImportRowValidator.ValidationResult(valids, errors));

        LeaveImportResultResponse resp = service.importLeaves(tenantId, documentId, "2026", true);

        assertThat(resp.isDryRun()).isTrue();
        assertThat(resp.status()).isEqualTo(ImportStatus.COMPLETED_WITH_ERRORS);
        assertThat(resp.rowsTotal()).isEqualTo(2);
        assertThat(resp.rowsImported()).isEqualTo(0);
        assertThat(resp.rowsFailed()).isEqualTo(1);
        assertThat(resp.errorDocumentId()).isEqualTo(errorDocId);

        verify(allocationHelper, never()).createOneAllocation(any(), any());
    }

    @Test
    @DisplayName("all valid rows import with status COMPLETED and rowsFailed = 0")
    void allValidRowsImportCleanly() {
        String csv =
                """
                employee_number,leave_type_code,days
                EMP-1,SL,10.00
                """;

        when(documentService.open(documentId)).thenReturn(mockCsvContent(csv));

        Employee emp = mock(Employee.class);
        when(emp.getId()).thenReturn(UUID.randomUUID());
        LeaveType lt = mock(LeaveType.class);
        when(lt.getId()).thenReturn(UUID.randomUUID());
        LeaveImportRow row = new LeaveImportRow(2, "EMP-1", "SL", "10.00");
        var valids = List.of(new LeaveImportRowValidator.ValidatedRow(row, emp, lt, new BigDecimal("10.00")));

        when(rowValidator.validate(eq(tenantId), any()))
                .thenReturn(new LeaveImportRowValidator.ValidationResult(valids, List.of()));

        LeaveImportResultResponse resp = service.importLeaves(tenantId, documentId, "2026", false);

        assertThat(resp.status()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(resp.rowsTotal()).isEqualTo(1);
        assertThat(resp.rowsImported()).isEqualTo(1);
        assertThat(resp.rowsFailed()).isEqualTo(0);
        assertThat(resp.errorDocumentId()).isNull();

        verify(allocationHelper, times(1)).createOneAllocation(eq(tenantId), any(LeaveAllocationRequest.class));
        verify(documentService, never()).store(any(), any(), any(), any());
    }

    @Test
    @DisplayName("all invalid rows results in COMPLETED_WITH_ERRORS and 0 imported")
    void allInvalidRows() {
        String csv =
                """
                employee_number,leave_type_code,days
                EMP-BAD1,SL,10.00
                """;

        when(documentService.open(documentId)).thenReturn(mockCsvContent(csv));

        var errors = List.of(new LeaveImportError(2, "EMP-BAD1", "SL", "10.00", "EMPLOYEE_NOT_FOUND"));

        when(rowValidator.validate(eq(tenantId), any()))
                .thenReturn(new LeaveImportRowValidator.ValidationResult(List.of(), errors));

        LeaveImportResultResponse resp = service.importLeaves(tenantId, documentId, "2026", false);

        assertThat(resp.status()).isEqualTo(ImportStatus.COMPLETED_WITH_ERRORS);
        assertThat(resp.rowsTotal()).isEqualTo(1);
        assertThat(resp.rowsImported()).isEqualTo(0);
        assertThat(resp.rowsFailed()).isEqualTo(1);
        assertThat(resp.errorDocumentId()).isEqualTo(errorDocId);

        verify(allocationHelper, never()).createOneAllocation(any(), any());
        verify(documentService, times(1)).store(eq(DocumentKind.EXPORT), any(), any(), any());
    }

    @Test
    @DisplayName("document open failure records FAILED status with finishedAt in import log and rethrows (F-11)")
    void openDocumentFailureMarksLogAsFailedAndRethrows() {
        List<ImportStatus> savedStatuses = new ArrayList<>();
        when(allocationHelper.saveLog(any(LeaveImportLog.class))).thenAnswer(inv -> {
            LeaveImportLog l = inv.getArgument(0);
            savedStatuses.add(l.getStatus());
            if (l.getId() == null) {
                l.setId(UUID.randomUUID());
            }
            return l;
        });

        when(documentService.open(documentId)).thenThrow(new RuntimeException("Storage unavailable"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.importLeaves(tenantId, documentId, "2026", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to open import document");

        verify(allocationHelper, times(2)).saveLog(any(LeaveImportLog.class));
        assertThat(savedStatuses).containsExactly(ImportStatus.PENDING, ImportStatus.FAILED);
    }

    @Test
    @DisplayName("write failure classifies duplicate key as DUPLICATE_ALLOCATION and other error as WRITE_FAILED (F-9)")
    void writeFailureClassifiedAsDuplicateVsWriteFailed() throws Exception {
        String csv =
                """
                employee_number,leave_type_code,days
                EMP-1,SL,10.00
                EMP-2,SL,10.00
                """;

        when(documentService.open(documentId)).thenReturn(mockCsvContent(csv));

        Employee emp1 = mock(Employee.class);
        when(emp1.getId()).thenReturn(UUID.randomUUID());
        Employee emp2 = mock(Employee.class);
        when(emp2.getId()).thenReturn(UUID.randomUUID());
        LeaveType lt = mock(LeaveType.class);
        when(lt.getId()).thenReturn(UUID.randomUUID());

        LeaveImportRow row1 = new LeaveImportRow(2, "EMP-1", "SL", "10.00");
        LeaveImportRow row2 = new LeaveImportRow(3, "EMP-2", "SL", "10.00");

        var valids = List.of(
                new LeaveImportRowValidator.ValidatedRow(row1, emp1, lt, new BigDecimal("10.00")),
                new LeaveImportRowValidator.ValidatedRow(row2, emp2, lt, new BigDecimal("10.00")));

        when(rowValidator.validate(eq(tenantId), any()))
                .thenReturn(new LeaveImportRowValidator.ValidationResult(valids, List.of()));

        // Row 1 throws duplicate key
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "duplicate key value violates unique constraint uk_leave_allocation"))
                .when(allocationHelper)
                .createOneAllocation(eq(tenantId), org.mockito.ArgumentMatchers.argThat(r -> r.employeeId()
                        .equals(emp1.getId())));

        // Row 2 throws general error
        org.mockito.Mockito.doThrow(new RuntimeException("DB connection dropped"))
                .when(allocationHelper)
                .createOneAllocation(eq(tenantId), org.mockito.ArgumentMatchers.argThat(r -> r.employeeId()
                        .equals(emp2.getId())));

        LeaveImportResultResponse resp = service.importLeaves(tenantId, documentId, "2026", false);

        assertThat(resp.status()).isEqualTo(ImportStatus.COMPLETED_WITH_ERRORS);
        assertThat(resp.rowsImported()).isEqualTo(0);
        assertThat(resp.rowsFailed()).isEqualTo(2);

        org.mockito.ArgumentCaptor<InputStream> streamCaptor = org.mockito.ArgumentCaptor.forClass(InputStream.class);
        verify(documentService, times(1)).store(eq(DocumentKind.EXPORT), any(), any(), streamCaptor.capture());

        String errorCsv = new String(streamCaptor.getValue().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(errorCsv).contains("2,EMP-1,SL,10.00,DUPLICATE_ALLOCATION");
        assertThat(errorCsv).contains("3,EMP-2,SL,10.00,WRITE_FAILED");
    }

    @Test
    @DisplayName("year dates use tenant's leave_year_start_month when configured (F-10)")
    void datesResolvedWithCustomTenantStartMonth() {
        when(allocationHelper.getTenantLeaveYearStartMonth(tenantId)).thenReturn(1);

        String csv =
                """
                employee_number,leave_type_code,days
                EMP-1,SL,10.00
                """;

        when(documentService.open(documentId)).thenReturn(mockCsvContent(csv));

        Employee emp1 = mock(Employee.class);
        when(emp1.getId()).thenReturn(UUID.randomUUID());
        LeaveType lt = mock(LeaveType.class);
        when(lt.getId()).thenReturn(UUID.randomUUID());
        LeaveImportRow row1 = new LeaveImportRow(2, "EMP-1", "SL", "10.00");
        var valids = List.of(new LeaveImportRowValidator.ValidatedRow(row1, emp1, lt, new BigDecimal("10.00")));
        when(rowValidator.validate(eq(tenantId), any()))
                .thenReturn(new LeaveImportRowValidator.ValidationResult(valids, List.of()));

        service.importLeaves(tenantId, documentId, "2026", false);

        org.mockito.ArgumentCaptor<LeaveAllocationRequest> reqCaptor =
                org.mockito.ArgumentCaptor.forClass(LeaveAllocationRequest.class);
        verify(allocationHelper, times(1)).createOneAllocation(eq(tenantId), reqCaptor.capture());
        LeaveAllocationRequest captured = reqCaptor.getValue();
        // Start date should be 2026-01-01 and end date 2026-12-31 (start month 1)
        assertThat(captured.yearStartDate()).isEqualTo(java.time.LocalDate.of(2026, 1, 1));
        assertThat(captured.yearEndDate()).isEqualTo(java.time.LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("January-start tenant rejects non-YYYY leave year format (W-16.4b)")
    void januaryTenantRejectsMultiYearFormat() {
        when(allocationHelper.getTenantLeaveYearStartMonth(tenantId)).thenReturn(1);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.importLeaves(tenantId, documentId, "2026-27", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant starting in January requires YYYY");
    }

    @Test
    @DisplayName("April-start tenant rejects non-YYYY-YY leave year format (W-16.4b)")
    void aprilTenantRejectsSingleYearFormat() {
        when(allocationHelper.getTenantLeaveYearStartMonth(tenantId)).thenReturn(4);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.importLeaves(tenantId, documentId, "2026", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires YYYY-YY");
    }

    @Test
    @DisplayName("Unreadable tenant start month fails loudly (W-16.4b)")
    void unreadableTenantStartMonthFailsLoudly() {
        when(allocationHelper.getTenantLeaveYearStartMonth(tenantId))
                .thenThrow(new IllegalStateException("Tenant leave year start month could not be determined"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.importLeaves(tenantId, documentId, "2026-27", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant leave year start month could not be determined");
    }

    @Test
    @DisplayName("error report cannot be stored: the log still records the counts and the import completes")
    void errorReportStoreFailureStillRecordsCounts() {
        String csv =
                """
                employee_number,leave_type_code,days
                EMP-1,SL,10.00
                EMP-BAD1,SL,10.00
                """;
        when(documentService.open(documentId)).thenReturn(mockCsvContent(csv));
        when(documentService.store(eq(DocumentKind.EXPORT), any(), any(), any(InputStream.class)))
                .thenThrow(new IllegalStateException("blob store unavailable"));

        Employee emp = mock(Employee.class);
        when(emp.getId()).thenReturn(UUID.randomUUID());
        LeaveType lt = mock(LeaveType.class);
        when(lt.getId()).thenReturn(UUID.randomUUID());
        LeaveImportRow row = new LeaveImportRow(2, "EMP-1", "SL", "10.00");
        when(rowValidator.validate(eq(tenantId), any()))
                .thenReturn(new LeaveImportRowValidator.ValidationResult(
                        List.of(new LeaveImportRowValidator.ValidatedRow(row, emp, lt, new BigDecimal("10.00"))),
                        List.of(new LeaveImportError(3, "EMP-BAD1", "SL", "10.00", "EMPLOYEE_NOT_FOUND"))));

        LeaveImportResultResponse resp = service.importLeaves(tenantId, documentId, "2026", false);

        assertThat(resp.status()).isEqualTo(ImportStatus.COMPLETED_WITH_ERRORS);
        assertThat(resp.rowsImported()).isEqualTo(1);
        assertThat(resp.rowsFailed()).isEqualTo(1);
        assertThat(resp.errorDocumentId()).isNull();
    }

    @Test
    @DisplayName("failure after parsing marks the log FAILED instead of leaving it PENDING, and rethrows")
    void failureAfterParsingMarksLogFailed() {
        List<ImportStatus> savedStatuses = new ArrayList<>();
        when(allocationHelper.saveLog(any(LeaveImportLog.class))).thenAnswer(inv -> {
            LeaveImportLog l = inv.getArgument(0);
            savedStatuses.add(l.getStatus());
            if (l.getId() == null) {
                l.setId(UUID.randomUUID());
            }
            return l;
        });
        when(documentService.open(documentId))
                .thenReturn(mockCsvContent("employee_number,leave_type_code,days\nEMP-1,SL,10.00\n"));
        when(rowValidator.validate(eq(tenantId), any())).thenThrow(new IllegalStateException("database unavailable"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.importLeaves(tenantId, documentId, "2026", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database unavailable");

        assertThat(savedStatuses).last().isEqualTo(ImportStatus.FAILED);
        assertThat(savedStatuses).doesNotContain(ImportStatus.COMPLETED, ImportStatus.COMPLETED_WITH_ERRORS);
    }

    @Test
    @DisplayName("a cell that a spreadsheet would run as a formula is written to the error report as text")
    void errorReportNeutralisesFormulaCells() throws Exception {
        when(documentService.open(documentId))
                .thenReturn(
                        mockCsvContent("employee_number,leave_type_code,days\n=HYPERLINK(\"http://x\"),SL,10.00\n"));
        when(rowValidator.validate(eq(tenantId), any()))
                .thenReturn(new LeaveImportRowValidator.ValidationResult(
                        List.of(),
                        List.of(new LeaveImportError(
                                2, "=HYPERLINK(\"http://x\")", "SL", "10.00", "EMPLOYEE_NOT_FOUND"))));

        service.importLeaves(tenantId, documentId, "2026", true);

        org.mockito.ArgumentCaptor<InputStream> stream = org.mockito.ArgumentCaptor.forClass(InputStream.class);
        verify(documentService).store(eq(DocumentKind.EXPORT), any(), any(), stream.capture());
        String report = new String(stream.getValue().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(report).contains("\"'=HYPERLINK(");
        assertThat(report).doesNotContain("\n=HYPERLINK(").doesNotContain(",=HYPERLINK(");
    }
}
