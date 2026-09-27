package com.infinevo.payroll.component;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for managing {@link Deduction} salary components (W-26.1).
 */
public interface DeductionService {

    DeductionResponse create(DeductionRequest request);

    DeductionResponse get(UUID id);

    List<DeductionResponse> list(boolean activeOnly);

    DeductionResponse update(UUID id, DeductionRequest request);

    DeductionResponse updateActive(UUID id, boolean active);

    void delete(UUID id);
}
