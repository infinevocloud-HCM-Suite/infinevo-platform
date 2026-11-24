package com.infinevo.payroll.taxcalc.recalc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/** An open tax recalculation failure as the payroll officer sees it (D-147). */
public record TaxRecalcFailureResponse(
        UUID id,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("financial_year") String financialYear,
        TaxTrigger trigger,
        @JsonProperty("error_message") String errorMessage,
        int attempts,
        @JsonProperty("first_failed_at") Instant firstFailedAt,
        @JsonProperty("last_failed_at") Instant lastFailedAt) {

    static TaxRecalcFailureResponse of(TaxRecalcFailure f) {
        return new TaxRecalcFailureResponse(
                f.getId(),
                f.getEmployeeId(),
                f.getFinancialYear(),
                f.getTrigger(),
                f.getErrorMessage(),
                f.getAttempts(),
                f.getFirstFailedAt(),
                f.getLastFailedAt());
    }
}
