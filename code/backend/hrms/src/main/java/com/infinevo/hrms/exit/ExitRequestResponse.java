package com.infinevo.hrms.exit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO representing an exit request and its departmental clearance items (W-103).
 */
public record ExitRequestResponse(
        UUID id,
        UUID tenantId,
        UUID employeeId,
        String employeeName,
        LocalDate resignationDate,
        LocalDate requestedLwd,
        LocalDate approvedLwd,
        long noticePeriodDays,
        String exitType,
        String reasonCategory,
        String reasonNotes,
        ExitStatus status,
        String approvedBy,
        Instant approvedAt,
        Instant createdAt,
        List<ExitClearanceItemResponse> clearanceItems) {}
