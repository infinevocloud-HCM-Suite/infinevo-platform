package com.infinevo.payroll.tds.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.payroll.tds.EmployeeTds;
import com.infinevo.payroll.tds.TdsSpread;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response payload for employee TDS queries and updates (W-36.1 §4).
 *
 * <p>Carries the record's figures alongside the dynamically aggregated {@code year_to_date} and
 * {@code remaining} tax balance. D-145 adds what the next regular pay run deducts:
 * {@code deducted_so_far} (equal to {@code year_to_date}), {@code months_left}, {@code monthly_share} and
 * {@code next_pay_month} (the month the next regular run covers, {@code YYYY-MM}; null when none is left),
 * computed by {@link TdsSpread} as the pay run line is.
 *
 * <p>D-151: {@code suggestion} is what an automatic recalculation computed while the officer's figure is
 * active, for the officer to apply or clear; null when there is none, and never on the employee's own view.
 */
public record EmployeeTdsResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("financial_year") String financialYear,
        @JsonProperty("regime") String regime,
        @JsonProperty("source") String source,
        @JsonProperty("declaration_id") UUID declarationId,
        @JsonProperty("annual_gross") BigDecimal annualGross,
        @JsonProperty("annual_taxable_income") BigDecimal annualTaxableIncome,
        @JsonProperty("annual_tax") BigDecimal annualTax,
        @JsonProperty("effective_from_period") String effectiveFromPeriod,
        @JsonProperty("is_active") boolean isActive,
        @JsonProperty("superseded_at") Instant supersededAt,
        @JsonProperty("note") String note,
        @JsonProperty("year_to_date") BigDecimal yearToDate,
        @JsonProperty("remaining") BigDecimal remaining,
        @JsonProperty("deducted_so_far") BigDecimal deductedSoFar,
        @JsonProperty("months_left") int monthsLeft,
        @JsonProperty("monthly_share") BigDecimal monthlyShare,
        @JsonProperty("next_pay_month") String nextPayMonth,
        @JsonProperty("suggestion") Suggestion suggestion,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("created_by") String createdBy,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("updated_by") String updatedBy) {

    /** D-151: the calculator's figure kept beside an officer's active figure. */
    public record Suggestion(
            @JsonProperty("regime") String regime,
            @JsonProperty("annual_gross") BigDecimal annualGross,
            @JsonProperty("annual_taxable_income") BigDecimal annualTaxableIncome,
            @JsonProperty("annual_tax") BigDecimal annualTax,
            @JsonProperty("note") String note,
            @JsonProperty("suggested_at") Instant suggestedAt) {

        static Suggestion of(EmployeeTds entity) {
            if (!entity.hasSuggestion()) {
                return null;
            }
            return new Suggestion(
                    entity.getSuggestedRegime().name(),
                    entity.getSuggestedAnnualGross(),
                    entity.getSuggestedAnnualTaxableIncome(),
                    entity.getSuggestedAnnualTax(),
                    entity.getSuggestedNote(),
                    entity.getSuggestedAt());
        }
    }

    /** The same response without the suggestion: the employee's own view (D-151). */
    public EmployeeTdsResponse withoutSuggestion() {
        return new EmployeeTdsResponse(
                id,
                employeeId,
                financialYear,
                regime,
                source,
                declarationId,
                annualGross,
                annualTaxableIncome,
                annualTax,
                effectiveFromPeriod,
                isActive,
                supersededAt,
                note,
                yearToDate,
                remaining,
                deductedSoFar,
                monthsLeft,
                monthlyShare,
                nextPayMonth,
                null,
                createdAt,
                createdBy,
                updatedAt,
                updatedBy);
    }

    /** The record with its spread; the monthly share is rounded here, once, for display. */
    public static EmployeeTdsResponse from(EmployeeTds entity, TdsSpread spread) {
        return new EmployeeTdsResponse(
                entity.getId(),
                entity.getEmployeeId(),
                entity.getFinancialYear(),
                entity.getRegime().name(),
                entity.getSource().name(),
                entity.getDeclarationId(),
                entity.getAnnualGross(),
                entity.getAnnualTaxableIncome(),
                entity.getAnnualTax(),
                entity.getEffectiveFromPeriod(),
                entity.isActive(),
                entity.getSupersededAt(),
                entity.getNote(),
                spread.deductedSoFar(),
                spread.remaining(),
                spread.deductedSoFar(),
                spread.monthsLeft(),
                spread.monthlyShare().toAmount(),
                spread.nextPayMonth() != null ? spread.nextPayMonth().toString() : null,
                entity.isActive() ? Suggestion.of(entity) : null,
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }
}
