package com.infinevo.core.leave;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a bulk leave allocation import execution run (W-16.4b, spec section 4).
 */
public record LeaveImportResultResponse(
        UUID id,
        ImportStatus status,
        boolean isDryRun,
        String leaveYear,
        int rowsTotal,
        int rowsImported,
        int rowsFailed,
        UUID sourceDocumentId,
        UUID errorDocumentId,
        Instant startedAt,
        Instant finishedAt) {

    public static LeaveImportResultResponse from(LeaveImportLog log) {
        if (log == null) {
            return null;
        }
        return new LeaveImportResultResponse(
                log.getId(),
                log.getStatus(),
                log.isDryRun(),
                log.getLeaveYear(),
                log.getRowsTotal(),
                log.getRowsImported(),
                log.getRowsFailed(),
                log.getSourceDocumentId(),
                log.getErrorDocumentId(),
                log.getStartedAt(),
                log.getFinishedAt());
    }
}
