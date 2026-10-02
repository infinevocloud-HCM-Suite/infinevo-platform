package com.infinevo.payroll.priorpayroll;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for prior payroll import execution run (W-38.1 §4).
 */
public record PriorPayrollImportResponse(
        UUID id,
        PriorPayrollImportStatus status,
        @JsonProperty("is_dry_run") boolean isDryRun,
        String financialYear,
        @JsonProperty("rows_total") int rowsTotal,
        @JsonProperty("rows_imported") int rowsImported,
        @JsonProperty("rows_failed") int rowsFailed,
        UUID sourceDocumentId,
        @JsonProperty("error_document_id") UUID errorDocumentId,
        Instant startedAt,
        Instant finishedAt) {

    public static PriorPayrollImportResponse from(PriorPayrollImportLog log) {
        if (log == null) {
            return null;
        }
        return new PriorPayrollImportResponse(
                log.getId(),
                log.getStatus(),
                log.isDryRun(),
                log.getFinancialYear(),
                log.getRowsTotal(),
                log.getRowsImported(),
                log.getRowsFailed(),
                log.getSourceDocumentId(),
                log.getErrorDocumentId(),
                log.getStartedAt(),
                log.getFinishedAt());
    }
}
