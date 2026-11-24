package com.infinevo.payroll.taxcalc.recalc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

/**
 * An open tax recalculation failure as the payroll officer sees it (D-147). D-151 adds
 * {@code employee_name}, so the officer's screen says who it is without {@code core.employee.read}.
 */
public record TaxRecalcFailureResponse(
        UUID id,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("financial_year") String financialYear,
        TaxTrigger trigger,
        @JsonProperty("error_message") String errorMessage,
        int attempts,
        @JsonProperty("first_failed_at") Instant firstFailedAt,
        @JsonProperty("last_failed_at") Instant lastFailedAt,
        @JsonProperty("employee_name") String employeeName) {

    /** Without a name; the controller adds it ({@link #withEmployeeName}). */
    public TaxRecalcFailureResponse(
            UUID id,
            UUID employeeId,
            String financialYear,
            TaxTrigger trigger,
            String errorMessage,
            int attempts,
            Instant firstFailedAt,
            Instant lastFailedAt) {
        this(id, employeeId, financialYear, trigger, errorMessage, attempts, firstFailedAt, lastFailedAt, null);
    }

    public TaxRecalcFailureResponse withEmployeeName(String name) {
        return new TaxRecalcFailureResponse(
                id, employeeId, financialYear, trigger, errorMessage, attempts, firstFailedAt, lastFailedAt, name);
    }

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
