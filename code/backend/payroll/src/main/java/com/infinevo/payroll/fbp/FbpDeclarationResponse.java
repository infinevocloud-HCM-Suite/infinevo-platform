package com.infinevo.payroll.fbp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full FBP declaration response for an employee (W-27.2).
 */
public record FbpDeclarationResponse(
        UUID ctcStructureId,
        UUID employeeId,
        boolean windowOpen,
        Instant declaredAt,
        String declaredBy,
        FbpSummaryResponse summary,
        List<FbpDeclarationLineResponse> lines) {}
