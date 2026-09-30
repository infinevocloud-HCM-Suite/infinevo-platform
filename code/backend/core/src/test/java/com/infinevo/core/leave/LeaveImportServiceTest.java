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

        when(documentService.store(eq(DocumentKind.EXPORT), any(), any(), any(InputStream.class)))
                .thenReturn(errorDocId);
    }

    private DocumentService.DocumentContent mockCsvContent(String csv) {
        DocumentResponse meta = new DocumentResponse(
                documentId,
                null,
                DocumentKind.EXPORT,
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
}
