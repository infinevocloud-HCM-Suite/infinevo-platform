package com.infinevo.payroll.form16;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Annual tax statement (Form 16 Part B) record (W-36.4).
 */
public record Form16Statement(
        String financialYear,
        String assessmentYear,
        @JsonProperty("final") boolean isFinal,
        DeductorDetails deductor,
        EmployeeDetails employee,
        String regime,
        List<QuarterTax> quarters,
        BigDecimal totalDeducted,
        BigDecimal annualTax,
        BigDecimal balance,
        TaxBreakdown breakdown,
        String breakdownNote,
        Instant generatedAt) {

    public Form16Statement {
        Objects.requireNonNull(financialYear, "financialYear must not be null");
        Objects.requireNonNull(assessmentYear, "assessmentYear must not be null");
        Objects.requireNonNull(deductor, "deductor must not be null");
        Objects.requireNonNull(employee, "employee must not be null");
        Objects.requireNonNull(regime, "regime must not be null");
        Objects.requireNonNull(quarters, "quarters must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");

        totalDeducted = totalDeducted != null
                ? totalDeducted.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        annualTax = annualTax != null
                ? annualTax.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        balance = balance != null
                ? balance.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
