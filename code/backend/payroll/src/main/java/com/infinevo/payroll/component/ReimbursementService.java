package com.infinevo.payroll.component;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for managing {@link Reimbursement} salary components (W-26.1).
 */
public interface ReimbursementService {

    ReimbursementResponse create(ReimbursementRequest request);

    ReimbursementResponse get(UUID id);

    List<ReimbursementResponse> list(boolean activeOnly);

    ReimbursementResponse update(UUID id, ReimbursementRequest request);

    ReimbursementResponse updateActive(UUID id, boolean active);

    void delete(UUID id);
}
