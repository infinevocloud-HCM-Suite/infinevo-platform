package com.infinevo.payroll.priorpayroll;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request payload for initiating a prior payroll bulk import (W-38.1 §4).
 */
public record PriorPayrollImportRequest(
        @NotNull(message = "documentId must not be null") UUID documentId,
        @NotNull(message = "financialYear must not be null") String financialYear,
        boolean dryRun) {}
