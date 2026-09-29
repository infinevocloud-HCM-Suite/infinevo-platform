package com.infinevo.payroll.taxcalc;

import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryFigures;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Maps {@link TaxComputation} to {@link TaxSummaryFigures} (W-33.1 spec § 4).
 *
 * <p>Rule mapping:
 * <ul>
 *   <li>{@code taxable_income = grossTotalIncome}</li>
 *   <li>{@code net_taxable_income = taxableIncome}</li>
 *   <li>{@code tax_on_taxable_income = taxBeforeRebate - rebate + surcharge + cess}</li>
 *   <li>{@code tds_previous_employer = prevEmploymentTds}</li>
 *   <li>{@code tax_to_be_paid = annualTax}</li>
 *   <li>{@code remaining_months = months from current period to March}</li>
 *   <li>{@code tds_through_payroll = 0, tax_ytd_amount = 0} (until W-36.1)</li>
 *   <li>{@code tds_other_income = 0, other_sources_income = 0, exemptionUnderSection10 = 0, exemptionUnderSection6a = 0}</li>
 * </ul>
 */
@Component
public class TaxSummaryFiguresMapper {

    private final Clock clock;

    public TaxSummaryFiguresMapper(@Autowired(required = false) Clock clock) {
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    public TaxSummaryFigures toFigures(TaxComputation computation, FinancialYear fy) {
        if (computation == null) {
            throw new IllegalArgumentException("computation must not be null");
        }
        if (fy == null) {
            throw new IllegalArgumentException("financialYear must not be null");
        }

        Money taxOnTaxableIncome = computation
                .taxBeforeRebate()
                .subtract(computation.rebate())
                .add(computation.surcharge())
                .add(computation.cess());

        int remainingMonths = calculateRemainingMonths(fy);

        Money exemptionUnderSection6a = computation
                .chapterViaDeductions()
                .add(computation.interestDeduction())
                .add(computation.additionalHomeLoanInterest());

        return new TaxSummaryFigures(
                computation.grossTotalIncome().raw(),
                computation.taxableIncome().raw(),
                taxOnTaxableIncome.raw(),
                BigDecimal.ZERO.setScale(4),
                computation.annualTax().raw(),
                BigDecimal.ZERO.setScale(4),
                computation.prevEmploymentTds().raw(),
                BigDecimal.ZERO.setScale(4),
                computation.otherIncome().raw(),
                computation.hraExemption().raw(),
                exemptionUnderSection6a.raw(),
                remainingMonths);
    }

    public int calculateRemainingMonths(FinancialYear fy) {
        LocalDate today = LocalDate.now(clock);
        YearMonth current = YearMonth.from(today);
        YearMonth fyStart = YearMonth.from(fy.start());
        YearMonth fyEnd = YearMonth.from(fy.end());

        if (current.isBefore(fyStart)) {
            return 12;
        }
        if (current.isAfter(fyEnd)) {
            return 0;
        }
        return (int) ChronoUnit.MONTHS.between(current, fyEnd) + 1;
    }
}
