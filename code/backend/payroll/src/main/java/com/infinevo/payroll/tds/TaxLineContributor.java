package com.infinevo.payroll.tds;

import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.LineKind;
import com.infinevo.payroll.payrun.LineSource;
import com.infinevo.payroll.payrun.PayLine;
import com.infinevo.payroll.payrun.PayLineContributor;
import com.infinevo.payroll.payrun.PayRunEmployeeContext;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.payroll.priorpayroll.PriorPayrollTaxQuery;
import com.infinevo.payroll.salary.EmployeeStatutoryProfileService;
import com.infinevo.payroll.statutory.settings.StatutorySettingsService;
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
 *
 * <p>D-141: no line, and no "No TDS record" note, when the company switch "Deduct TDS in pay run" is off
 * or the employee is not TDS applicable. The tax record is untouched; switched back on, the next run
 * spreads what remains of the annual tax over the months left, because year-to-date counts only the
 * TDS lines actually written.
 */
@Component
@Order(500)
public class TaxLineContributor implements PayLineContributor {

    static final String COMPONENT_CODE = "TDS";
    static final String COMPONENT_NAME = "Tax deducted at source";

    private final EmployeeTdsService tdsService;
    private final EmployeePayRunLineRepository lineRepository;
    private final PriorPayrollTaxQuery priorTax;
    private final StatutorySettingsService statutorySettingsService;
    private final EmployeeStatutoryProfileService employeeStatutoryProfileService;

    public TaxLineContributor(
            EmployeeTdsService tdsService,
            EmployeePayRunLineRepository lineRepository,
            PriorPayrollTaxQuery priorTax,
            StatutorySettingsService statutorySettingsService,
            EmployeeStatutoryProfileService employeeStatutoryProfileService) {
        this.tdsService = Objects.requireNonNull(tdsService, "tdsService must not be null");
        this.lineRepository = Objects.requireNonNull(lineRepository, "lineRepository must not be null");
        this.priorTax = Objects.requireNonNull(priorTax, "priorTax must not be null");
        this.statutorySettingsService =
                Objects.requireNonNull(statutorySettingsService, "statutorySettingsService must not be null");
        this.employeeStatutoryProfileService = Objects.requireNonNull(
                employeeStatutoryProfileService, "employeeStatutoryProfileService must not be null");
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
        // D-141: read after the record so an employee with nothing to deduct costs no extra query (W-55).
        if (!deductsTds(ctx)) {
            return List.of();
        }

        String periodFrom = String.format("%04d-04", fy.startYear());
        String periodTo = ctx.period().toString();
        // W-38.2: tax deducted in months imported through W-38.1 is tax already deducted this year.
        BigDecimal ytd = lineRepository
                .sumTaxLines(ctx.tenantId(), ctx.employee().id(), periodFrom, periodTo, ctx.payrunId())
                .add(priorTax.total(ctx.tenantId(), ctx.employee().id(), fy));

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

    /**
     * A regular run with no active record for the year says so on the employee's row (§2, §3), unless
     * TDS is switched off for the company or the employee (D-141), when no record is expected.
     */
    @Override
    public String note(PayRunEmployeeContext ctx) {
        if (ctx.runType() == PayRunType.OFF_CYCLE) {
            return null;
        }
        String fy = FinancialYear.of(ctx.period().atDay(1)).label();
        if (tdsService.active(ctx.employee().id(), fy).isPresent() || !deductsTds(ctx)) {
            return null;
        }
        return "No TDS record for " + fy;
    }

    /**
     * D-141: the company switch first, then the employee flag. The profile on the context is used when the
     * run carries it; otherwise it is read, and an employee with no saved profile counts as applicable.
     */
    private boolean deductsTds(PayRunEmployeeContext ctx) {
        if (!statutorySettingsService.tds(ctx.tenantId()).enabled()) {
            return false;
        }
        if (ctx.statutoryProfile() != null) {
            return ctx.statutoryProfile().tdsApplicable();
        }
        return employeeStatutoryProfileService.isTdsApplicable(ctx.employee().id());
    }
}
