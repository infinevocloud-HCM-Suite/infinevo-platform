package com.infinevo.payroll.taxcalc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.payroll.taxcalc.engine.HousePropertyIncome.HousePropertyResult;
import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Detailed breakdown response for a tax computation result (W-33.1 spec § 4, W-33.2 spec § 4).
 * All money figures are exposed at scale 2 for client rendering.
 *
 * <p>The old-regime working ({@code exemption_under_section10} onwards) is zero for the new regime,
 * where none of those exemptions or deductions apply.
 */
public record TaxComputationResponse(
        @JsonProperty("regime") String regime,
        @JsonProperty("financial_year") String financialYear,
        @JsonProperty("income_from_salary") BigDecimal incomeFromSalary,
        @JsonProperty("prev_employment_income") BigDecimal prevEmploymentIncome,
        @JsonProperty("standard_deduction") BigDecimal standardDeduction,
        @JsonProperty("gross_total_income") BigDecimal grossTotalIncome,
        @JsonProperty("taxable_income") BigDecimal taxableIncome,
        @JsonProperty("tax_before_rebate") BigDecimal taxBeforeRebate,
        @JsonProperty("rebate") BigDecimal rebate,
        @JsonProperty("surcharge") BigDecimal surcharge,
        @JsonProperty("marginal_relief") BigDecimal marginalRelief,
        @JsonProperty("cess") BigDecimal cess,
        @JsonProperty("prev_employment_tds") BigDecimal prevEmploymentTds,
        @JsonProperty("annual_tax") BigDecimal annualTax,
        @JsonProperty("slab_lines") List<SlabLineResponse> slabLines,
        @JsonProperty("assumptions") List<String> assumptions,
        @JsonProperty("exemption_under_section10") BigDecimal exemptionUnderSection10,
        @JsonProperty("professional_tax") BigDecimal professionalTax,
        @JsonProperty("house_property") HousePropertyResponse houseProperty,
        @JsonProperty("other_income") BigDecimal otherIncome,
        @JsonProperty("chapter_via_deductions") BigDecimal chapterViaDeductions,
        @JsonProperty("chapter_via_lines") List<ChapterViaLineResponse> chapterViaLines,
        @JsonProperty("interest_deduction") BigDecimal interestDeduction,
        @JsonProperty("additional_home_loan_interest") BigDecimal additionalHomeLoanInterest,
        @JsonProperty("exemption_under_section6a") BigDecimal exemptionUnderSection6a) {

    public static TaxComputationResponse from(TaxComputation computation) {
        if (computation == null) {
            return null;
        }
        List<SlabLineResponse> slabLines = computation.slabLines() == null
                ? List.of()
                : computation.slabLines().stream()
                        .map(s -> new SlabLineResponse(
                                scale2(s.fromAmount()),
                                s.toAmount() != null ? scale2(s.toAmount()) : null,
                                s.taxRatePercent(),
                                scale2(s.taxableAmount()),
                                scale2(s.taxAmount())))
                        .toList();

        HousePropertyResult hp = computation.houseProperty();
        HousePropertyResponse houseProperty = new HousePropertyResponse(
                scale2(hp.housePropertyIncome()),
                scale2(hp.selfOccupiedInterest()),
                scale2(hp.letOutNet()),
                hp.lossCapApplied());

        List<ChapterViaLineResponse> chapterViaLines = computation.chapterViaLines().stream()
                .map(l -> new ChapterViaLineResponse(
                        l.sectionCode(),
                        l.description(),
                        scale2(l.declaredAmount()),
                        scale2(l.allowedAmount()),
                        l.cap() != null ? scale2(l.cap()) : null))
                .toList();

        // Same sum TaxSummaryFiguresMapper writes to exemption_under_section6a (W-33.2 spec § 4 mapper row).
        Money exemptionUnderSection6a = computation
                .chapterViaDeductions()
                .add(computation.interestDeduction())
                .add(computation.additionalHomeLoanInterest());

        return new TaxComputationResponse(
                computation.regime().name(),
                computation.financialYear(),
                scale2(computation.incomeFromSalary()),
                scale2(computation.prevEmploymentIncome()),
                scale2(computation.standardDeduction()),
                scale2(computation.grossTotalIncome()),
                scale2(computation.taxableIncome()),
                scale2(computation.taxBeforeRebate()),
                scale2(computation.rebate()),
                scale2(computation.surcharge()),
                scale2(computation.marginalRelief()),
                scale2(computation.cess()),
                scale2(computation.prevEmploymentTds()),
                scale2(computation.annualTax()),
                slabLines,
                computation.assumptions() != null ? computation.assumptions() : List.of(),
                scale2(computation.hraExemption()),
                scale2(computation.professionalTax()),
                houseProperty,
                scale2(computation.otherIncome()),
                scale2(computation.chapterViaDeductions()),
                chapterViaLines,
                scale2(computation.interestDeduction()),
                scale2(computation.additionalHomeLoanInterest()),
                scale2(exemptionUnderSection6a));
    }

    private static BigDecimal scale2(Money money) {
        return money != null ? money.raw().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }
}
