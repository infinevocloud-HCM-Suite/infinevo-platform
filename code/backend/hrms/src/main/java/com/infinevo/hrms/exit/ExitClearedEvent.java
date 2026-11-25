package com.infinevo.hrms.exit;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain event published when all departmental exit clearances are resolved and the exit moves to
 * {@link ExitStatus#READY_FOR_FNF} (W-103).
 */
public record ExitClearedEvent(
        UUID tenantId,
        UUID exitRequestId,
        UUID employeeId,
        LocalDate resignationDate,
        LocalDate exitDate,
        String exitReason) {}
