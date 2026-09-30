package com.infinevo.payroll.taxcalc.recalc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO response representing a tax recalculation history entry (W-33.3).
 */
public record TaxComputationRecordResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("declaration_id") UUID declarationId,
        @JsonProperty("financial_year") String financialYear,
        @JsonProperty("regime") String regime,
        @JsonProperty("trigger") TaxTrigger trigger,
        @JsonProperty("gross_total_income") BigDecimal grossTotalIncome,
        @JsonProperty("hra_exemption") BigDecimal hraExemption,
        @JsonProperty("standard_deduction") BigDecimal standardDeduction,
        @JsonProperty("professional_tax") BigDecimal professionalTax,
        @JsonProperty("house_property_income") BigDecimal housePropertyIncome,
        @JsonProperty("other_income") BigDecimal otherIncome,
        @JsonProperty("chapter_via") BigDecimal chapterVia,
        @JsonProperty("taxable_income") BigDecimal taxableIncome,
        @JsonProperty("tax_before_rebate") BigDecimal taxBeforeRebate,
        @JsonProperty("rebate") BigDecimal rebate,
        @JsonProperty("surcharge") BigDecimal surcharge,
        @JsonProperty("cess") BigDecimal cess,
        @JsonProperty("prev_employer_tds") BigDecimal prevEmployerTds,
        @JsonProperty("annual_tax") BigDecimal annualTax,
        @JsonProperty("working") Object working,
        @JsonProperty("computed_at") Instant computedAt,
        @JsonProperty("computed_by") UUID computedBy) {}
