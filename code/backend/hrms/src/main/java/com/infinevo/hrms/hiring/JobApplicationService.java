package com.infinevo.hrms.hiring;

import java.util.List;
import java.util.UUID;

/**
 * Service managing candidate applications and hiring pipeline stage movements (W-112).
 */
public interface JobApplicationService {

    JobApplicationResponse apply(UUID tenantId, UUID jobOpeningId, UUID candidateId, String createdBy);

    JobApplicationResponse getApplication(UUID tenantId, UUID id);

    List<JobApplicationResponse> listApplicationsByOpening(UUID tenantId, UUID jobOpeningId);

    JobApplicationResponse changeStage(
            UUID tenantId, UUID id, JobApplicationStage stage, String rejectionReason, String updatedBy);
}
