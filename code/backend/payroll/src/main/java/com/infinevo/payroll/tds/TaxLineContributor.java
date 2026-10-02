package com.infinevo.payroll.tds;

import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.LineKind;
import com.infinevo.payroll.payrun.LineSource;
import com.infinevo.payroll.payrun.PayLine;
import com.infinevo.payroll.payrun.PayLineContributor;
import com.infinevo.payroll.payrun.PayRunEmployeeContext;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.Month;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The {@code TAX} contributor (W-36.1 §3), after {@code STATUTORY} (400):
 *
 * <ul>
 *   <li>One {@link LineKind#DEDUCTION} line, {@code source = TAX}, {@code component_code = TDS}</li>
 *   <li>Reads the settled annual tax from the active {@link EmployeeTds} record</li>
 *   <li>Subtracts YTD tax already deducted on other computed/approved/paid runs in the FY</li>
 *   <li>Spreads remaining tax evenly over remaining months to March</li>
 *   <li>Never computes tax on the fly</li>
 * </ul>
 */
@Component
@Order(500)
public class TaxLineContributor implements PayLineContributor {

    static final String COMPONENT_CODE = "TDS";
    static final String COMPONENT_NAME = "Tax deducted at source";

    private final EmployeeTdsService employeeTdsService;
    private final EmployeePayRunLineRepository employeePayRunLineRepository;

    public TaxLineContributor(
            EmployeeTdsService employeeTdsService, EmployeePayRunLineRepository employeePayRunLineRepository) {
        this.employeeTdsService = Objects.requireNonNull(employeeTdsService, "employeeTdsService must not be null");
        this.employeePayRunLineRepository =
                Objects.requireNonNull(employeePayRunLineRepository, "employeePayRunLineRepository must not be null");
    }

    @Override
    public List<PayLine> contribute(PayRunEmployeeContext ctx) {
        if (ctx == null || ctx.period() == null || ctx.employee() == null) {
            return List.of();
        }

        FinancialYear fy = FinancialYear.of(ctx.period().atDay(1));
        Optional<EmployeeTds> recordOpt =
                employeeTdsService.activeEntity(ctx.tenantId(), ctx.employee().id(), fy.label());
        if (recordOpt.isEmpty()) {
            return List.of();
        }

        EmployeeTds record = recordOpt.get();
        String runPeriodStr = ctx.period().toString();
        if (runPeriodStr.compareTo(record.getEffectiveFromPeriod()) < 0) {
            return List.of();
        }

        String periodFrom = fy.startYear() + "-04";
        String periodTo = runPeriodStr;
        BigDecimal ytd = employeePayRunLineRepository.sumTaxLines(
                ctx.tenantId(), ctx.employee().id(), periodFrom, periodTo, ctx.payrunId());
        if (ytd == null) {
            ytd = BigDecimal.ZERO;
        }

        BigDecimal remaining = record.getAnnualTax().subtract(ytd);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        YearMonth effectiveYm = YearMonth.parse(record.getEffectiveFromPeriod());
        YearMonth startYm = ctx.period().isAfter(effectiveYm) ? ctx.period() : effectiveYm;
        YearMonth marchYm = YearMonth.of(fy.endYear(), Month.MARCH);
        long months = ChronoUnit.MONTHS.between(startYm, marchYm) + 1;
        if (months <= 0) {
            return List.of();
        }

        Money monthlyAmount = Money.of(remaining).divide(BigDecimal.valueOf(months));
        if (!monthlyAmount.isPositive()) {
            return List.of();
        }

        return List.of(new PayLine(
                LineKind.DEDUCTION, LineSource.TAX, null, COMPONENT_CODE, COMPONENT_NAME, monthlyAmount, false));
    }
}
