package com.infinevo.core.leave;

import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for leave request lifecycle and business rules (W-16.3).
 */
public interface LeaveRequestService {

    LeaveRequestResponse createRequest(UUID tenantId, UUID employeeId, LeaveApplyRequest request);

    /**
     * Runs every check {@link #createRequest} runs before it saves, and saves nothing (W-114.8). {@code createRequest}
     * calls it first, so a request this accepts is one the Leave screen would accept at this moment.
     *
     * @throws IllegalArgumentException or {@link IllegalStateException} with the reason, when the request is refused
     */
    LeaveApplyCheck validateApply(UUID tenantId, UUID employeeId, LeaveApplyRequest request);

    LeaveRequestResponse createOnBehalf(UUID tenantId, LeaveOnBehalfRequest request);

    LeaveRequestResponse submit(UUID tenantId, UUID requestId, UUID callerEmployeeId);

    LeaveRequestResponse withdraw(UUID tenantId, UUID requestId, UUID callerEmployeeId, String reason);

    LeaveRequestResponse cancel(UUID tenantId, UUID requestId, UUID callerEmployeeId, String reason);

    LeaveRequestResponse getRequest(UUID tenantId, UUID requestId);

    Page<LeaveRequestResponse> searchRequests(
            UUID tenantId,
            UUID employeeId,
            LeaveRequestStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable);

    Page<LeaveRequestResponse> searchRequestsForEmployees(
            UUID tenantId,
            Collection<UUID> employeeIds,
            LeaveRequestStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable);
}
