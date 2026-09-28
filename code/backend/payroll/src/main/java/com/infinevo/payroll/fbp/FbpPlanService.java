package com.infinevo.payroll.fbp;

import java.time.LocalDate;
import java.util.List;

/**
 * Service interface for Flexible Benefit Plan configuration and components (W-27.1).
 */
public interface FbpPlanService {

    FbpPlanResponse get();

    FbpPlanResponse upsert(FbpPlanRequest request);

    FbpPlanResponse lock();

    FbpPlanResponse unlock();

    List<FbpComponentResponse> components();

    boolean isWindowOpen(LocalDate today);
}
