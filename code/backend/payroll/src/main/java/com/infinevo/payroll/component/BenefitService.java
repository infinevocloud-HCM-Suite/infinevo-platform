package com.infinevo.payroll.component;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for managing {@link Benefit} salary components (W-26.1).
 */
public interface BenefitService {

    BenefitResponse create(BenefitRequest request);

    BenefitResponse get(UUID id);

    List<BenefitResponse> list(boolean activeOnly);

    BenefitResponse update(UUID id, BenefitRequest request);

    BenefitResponse updateActive(UUID id, boolean active);

    void delete(UUID id);
}
