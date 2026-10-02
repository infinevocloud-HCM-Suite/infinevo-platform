package com.infinevo.payroll.tds;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.payroll.taxcalc.TaxRegime;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response payload for annual TDS record with YTD and remaining figures (W-36.1 §4).
 */
public record EmployeeTdsResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("financial_year") String financialYear,
        @JsonProperty("regime") TaxRegime regime,
        @JsonProperty("source") TdsSource source,
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

    public static EmployeeTdsResponse from(EmployeeTds entity, BigDecimal ytd) {
        BigDecimal ytdAmount = ytd != null ? ytd : BigDecimal.ZERO.setScale(4);
        BigDecimal remaining =
                entity.getAnnualTax() != null ? entity.getAnnualTax().subtract(ytdAmount) : BigDecimal.ZERO.setScale(4);
        return new EmployeeTdsResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmployeeId(),
                entity.getFinancialYear(),
                entity.getRegime(),
                entity.getSource(),
                entity.getDeclarationId(),
                entity.getAnnualGross(),
                entity.getAnnualTaxableIncome(),
                entity.getAnnualTax(),
                entity.getEffectiveFromPeriod(),
                entity.isActive(),
                entity.getSupersededAt(),
                entity.getNote(),
                ytdAmount,
                remaining,
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }
}
