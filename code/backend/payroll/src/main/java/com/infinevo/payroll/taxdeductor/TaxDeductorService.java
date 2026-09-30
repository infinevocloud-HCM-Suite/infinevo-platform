package com.infinevo.payroll.taxdeductor;

import java.util.Optional;

/**
 * Service interface for managing tax deductor details (W-36.3).
 */
public interface TaxDeductorService {

    /**
     * Validates and upserts tax deductor details for the bound tenant.
     *
     * @param request the deductor details request
     * @return the saved tax deductor response
     * @throws TaxDeductorValidationException if validation fails
     */
    TaxDeductorResponse save(TaxDeductorRequest request);

    /**
     * Retrieves the tax deductor details in force for the bound tenant.
     * This is the integration seam consumed by Form 16 (W-36.4).
     *
     * @return optional containing the deductor response if configured
     */
    Optional<TaxDeductorResponse> current();

    /**
     * Retrieves the raw tax deductor entity for the bound tenant.
     *
     * @return optional containing the deductor entity if configured
     */
    Optional<TaxDeductor> currentEntity();
}
