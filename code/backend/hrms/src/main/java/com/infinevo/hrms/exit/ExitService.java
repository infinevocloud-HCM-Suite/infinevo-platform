package com.infinevo.hrms.exit;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for employee resignation, exit clearances, and Payroll hand-off (W-103).
 */
public interface ExitService {

    ExitRequestResponse submitResignation(SubmitResignationRequest request);

    List<ExitRequestResponse> getMyExits();

    ExitRequestResponse withdrawMyExit(UUID exitId);

    List<ExitRequestResponse> listAllExits(ExitStatus status);

    ExitRequestResponse getExit(UUID exitId);

    ExitRequestResponse approveExit(UUID exitId, ApproveExitRequest request);

    ExitRequestResponse rejectExit(UUID exitId, RejectExitRequest request);

    ExitRequestResponse signOffDepartmentClearance(
            UUID exitId, ClearanceDepartment department, UpdateClearanceRequest request);

    ExitRequestResponse updateClearanceItem(UUID exitId, UUID itemId, UpdateClearanceRequest request);

    PayrollHandOffPayload handOffToPayroll(UUID exitId);
}
