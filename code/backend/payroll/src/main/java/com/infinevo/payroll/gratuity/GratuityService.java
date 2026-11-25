package com.infinevo.payroll.gratuity;

import java.util.UUID;

/**
 * Service interface for gratuity estimation and settlement persistence (W-91).
 */
public interface GratuityService {

    GratuityEstimateResponse estimateGratuity(GratuityEstimateRequest request);

    GratuitySettlementResponse createSettlement(GratuitySettlementCreateRequest request);

    GratuitySettlementResponse getSettlement(UUID id);
}
