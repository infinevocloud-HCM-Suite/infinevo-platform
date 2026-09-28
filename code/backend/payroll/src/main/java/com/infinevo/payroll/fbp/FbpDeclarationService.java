package com.infinevo.payroll.fbp;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service contract for managing employee Flexible Benefit Plan declarations (W-27.2).
 */
public interface FbpDeclarationService {

    FbpDeclarationResponse readOwn();

    FbpDeclarationResponse declareOwn(FbpDeclarationRequest request);

    FbpDeclarationResponse read(UUID employeeId, LocalDate asOf);

    FbpDeclarationResponse set(UUID employeeId, FbpDeclarationRequest request);

    void carryForward(UUID oldVersionId, UUID newVersionId, UUID tenantId, UUID employeeId);

    void recapAfterSalaryUpdate(UUID ctcStructureId, UUID tenantId);

    FbpSummaryResponse summary(UUID ctcStructureId, UUID tenantId);
}
