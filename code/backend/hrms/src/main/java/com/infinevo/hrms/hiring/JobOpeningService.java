package com.infinevo.hrms.hiring;

import java.util.List;
import java.util.UUID;

/**
 * Service managing job openings and requisitions (W-112).
 */
public interface JobOpeningService {

    JobOpeningResponse createOpening(UUID tenantId, JobOpeningRequest request, String createdBy);

    JobOpeningResponse updateOpening(UUID tenantId, UUID id, JobOpeningRequest request, String updatedBy);

    JobOpeningResponse getOpening(UUID tenantId, UUID id);

    List<JobOpeningResponse> listOpenings(UUID tenantId, JobOpeningStatus status);

    JobOpeningResponse changeStatus(UUID tenantId, UUID id, JobOpeningStatus status, String updatedBy);
}
