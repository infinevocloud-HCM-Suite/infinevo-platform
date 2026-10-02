package com.infinevo.payroll.tds;

import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.LineKind;
import com.infinevo.payroll.payrun.LineSource;
import com.infinevo.payroll.payrun.PayLine;
import com.infinevo.payroll.payrun.PayLineContributor;
import com.infinevo.payroll.payrun.PayRunEmployeeContext;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Pay line contributor for income tax deducted at source (W-36.1 §3, §4).
 *
 * <p>Produces one {@link LineKind#DEDUCTION} line with {@link LineSource#TAX} and component code
 * {@code "TDS"} per included employee with an active TDS record whose effective period has arrived.
 * The amount spreads the remaining annual tax across the remaining months of the financial year.
 * Runs at {@code @Order(500)}, after statutory lines (400).
 */
@Component
@Order(500)
public class TaxLineContributor implements PayLineContributor {

    static final String COMPONENT_CODE = "TDS";
    static final String COMPONENT_NAME = "Tax deducted at source";

    private final EmployeeTdsService tdsService;
    private final EmployeePayRunLineRepository lineRepository;

    public TaxLineContributor(EmployeeTdsService tdsService, EmployeePayRunLineRepository lineRepository) {
        this.tdsService = Objects.requireNonNull(tdsService, "tdsService must not be null");
        this.lineRepository = Objects.requireNonNull(lineRepository, "lineRepository must not be null");
    }

    @Override
    public List<PayLine> contribute(PayRunEmployeeContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        // W-30.2 leaves tax on an off-cycle payment to W-36; W-36.1 does not decide it, so an
        // off-cycle run carries no monthly TDS — as structure and loss-of-pay lines.
        if (ctx.runType() == PayRunType.OFF_CYCLE) {
            return List.of();
        }

        FinancialYear fy = FinancialYear.of(ctx.period().atDay(1));
        Optional<EmployeeTds> recordOpt = tdsService.active(ctx.employee().id(), fy.label());
        if (recordOpt.isEmpty()) {
            return List.of();
        }

        EmployeeTds record = recordOpt.get();
        YearMonth effectiveFrom = YearMonth.parse(record.getEffectiveFromPeriod());
        if (ctx.period().isBefore(effectiveFrom)) {
            return List.of();
        }

        String periodFrom = String.format("%04d-04", fy.startYear());
        String periodTo = ctx.period().toString();
        BigDecimal ytd =
                lineRepository.sumTaxLines(ctx.tenantId(), ctx.employee().id(), periodFrom, periodTo, ctx.payrunId());

        BigDecimal remaining = record.getAnnualTax().subtract(ytd);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        YearMonth startMonth = ctx.period().isAfter(effectiveFrom) ? ctx.period() : effectiveFrom;
        YearMonth march = YearMonth.of(fy.endYear(), 3);
        long months = ChronoUnit.MONTHS.between(startMonth, march) + 1;
        if (months <= 0) {
            return List.of();
        }

        Money amount = Money.of(remaining).divide(BigDecimal.valueOf(months));
        PayLine line =
                new PayLine(LineKind.DEDUCTION, LineSource.TAX, null, COMPONENT_CODE, COMPONENT_NAME, amount, false);

        return List.of(line);
    }

    /** A regular run with no active record for the year says so on the employee's row (§2, §3). */
    @Override
    public String note(PayRunEmployeeContext ctx) {
        if (ctx.runType() == PayRunType.OFF_CYCLE) {
            return null;
        }
        String fy = FinancialYear.of(ctx.period().atDay(1)).label();
        return tdsService.active(ctx.employee().id(), fy).isPresent() ? null : "No TDS record for " + fy;
    }
}
