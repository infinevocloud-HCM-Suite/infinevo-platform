package com.infinevo.payroll.component;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for managing {@link Earning} salary components (W-26.1).
 */
public interface EarningService {

    EarningResponse create(EarningRequest request);

    EarningResponse get(UUID id);

    List<EarningResponse> list(boolean activeOnly);

    EarningResponse update(UUID id, EarningRequest request);

    EarningResponse updateActive(UUID id, boolean active);

    void delete(UUID id);
}
