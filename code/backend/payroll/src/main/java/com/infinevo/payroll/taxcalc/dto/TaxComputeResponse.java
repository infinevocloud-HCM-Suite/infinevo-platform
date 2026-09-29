package com.infinevo.payroll.taxcalc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.payroll.taxcalc.model.TaxComputation;
import java.util.Map;

/**
 * Composite response for computing all regimes known to the engine (W-33.1 spec § 3, § 4).
 * Always includes the {@code OLD} key as {@code null} until W-33.2 adds OldRegimeCalculator.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record TaxComputeResponse(
        @JsonProperty("NEW") TaxComputationResponse newRegime, @JsonProperty("OLD") TaxComputationResponse oldRegime) {

    public static TaxComputeResponse of(Map<TaxRegime, TaxComputation> computations) {
        if (computations == null) {
            return new TaxComputeResponse(null, null);
        }
        return new TaxComputeResponse(
                TaxComputationResponse.from(computations.get(TaxRegime.NEW)),
                TaxComputationResponse.from(computations.get(TaxRegime.OLD)));
    }
}
