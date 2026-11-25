package com.infinevo.hrms.hiring;

import java.util.List;
import java.util.UUID;

/**
 * Service managing candidate profiles and talent directory (W-112).
 */
public interface CandidateService {

    CandidateResponse createCandidate(UUID tenantId, CandidateRequest request, String createdBy);

    CandidateResponse getCandidate(UUID tenantId, UUID id);

    List<CandidateResponse> listCandidates(UUID tenantId, String query);
}
