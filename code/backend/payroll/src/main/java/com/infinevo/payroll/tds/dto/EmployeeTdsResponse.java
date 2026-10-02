package com.infinevo.payroll.tds.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.payroll.tds.EmployeeTds;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response payload for employee TDS queries and updates (W-36.1 §4).
 *
 * <p>Carries the record's figures alongside the dynamically aggregated {@code year_to_date} and
 * {@code remaining} tax balance.
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
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("created_by") String createdBy,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("updated_by") String updatedBy) {

    public static EmployeeTdsResponse from(EmployeeTds entity, BigDecimal yearToDate, BigDecimal remaining) {
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
                yearToDate,
                remaining,
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }
}
