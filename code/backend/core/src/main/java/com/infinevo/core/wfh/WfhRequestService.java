package com.infinevo.core.wfh;

import java.util.List;
import java.util.UUID;

public interface WfhRequestService {

    WfhRequestResponse apply(UUID tenantId, UUID employeeId, ApplyWfhRequest request);

    WfhRequestResponse applyOnBehalf(UUID tenantId, UUID employeeId, ApplyWfhRequest request);

    WfhRequestResponse getRequest(UUID tenantId, UUID requestId);

    List<WfhRequestResponse> listEmployeeRequests(UUID tenantId, UUID employeeId);

    List<WfhRequestResponse> listRequests(UUID tenantId, WfhRequestStatus status);

    WfhRequestResponse withdraw(UUID tenantId, UUID requestId, UUID callerEmployeeId, String reason);

    WfhRequestResponse cancel(UUID tenantId, UUID requestId, UUID callerEmployeeId, String reason);
}
