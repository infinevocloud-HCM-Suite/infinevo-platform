package com.infinevo.payroll.payrun;

import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.salary.SalaryComponentItemResponse;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The {@code STRUCTURE} contributor (W-29.2 §3): full-month amounts from the salary version in force,
 * replacing the legacy {@code mapToPayRunDTO} ({@code EmployeePayRunServiceImpl.java:240-300},
 * {@code :372-390}). Scaling by payable days is W-29.3's; statutory lines are W-31's; tax is W-36's.
 *
 * <table>
 *   <tr><th>Version line</th><th>Lines written</th></tr>
 *   <tr><td>earning, fixed</td><td>{@code EARNING}, the monthly amount, taxable as the catalogue says</td></tr>
 *   <tr><td>earning, variable</td><td>{@code EARNING} by {@link PeriodicEarningRule} — counted in gross</td></tr>
 *   <tr><td>earning, FBP</td><td>declared monthly, non-taxable; the unallocated remainder, taxable</td></tr>
 *   <tr><td>benefit</td><td>{@code BENEFIT}, reported, never paid</td></tr>
 *   <tr><td>reimbursement</td><td>{@code REIMBURSEMENT}; an FBP one split as an earning is</td></tr>
 * </table>
 *
 * <p>A disabled line, and a zero amount, write nothing. A component the catalogue no longer holds
 * (deleted after the version was written) is an error for this employee, never a silent zero.
 */
@Component
@Order(100)
public class StructureLineContributor implements PayLineContributor {

    /** What {@code EmployeeSalaryServiceImpl.toResponse} names a component the catalogue has lost. */
    static final String UNKNOWN_CODE = "UNKNOWN";

    private final EarningRepository earningRepository;

    public StructureLineContributor(EarningRepository earningRepository) {
        this.earningRepository = Objects.requireNonNull(earningRepository, "earningRepository must not be null");
    }

    @Override
    public List<PayLine> contribute(PayRunEmployeeContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        SalaryVersionResponse version = ctx.version();
        Map<UUID, Earning> catalogue = new HashMap<>();
        for (Earning earning : earningRepository.findAllByTenantIdAndDeletedFalse(ctx.tenantId())) {
            catalogue.put(earning.getId(), earning);
        }

        List<PayLine> lines = new ArrayList<>();
        for (SalaryComponentItemResponse item : nullSafe(version.earnings())) {
            if (item.enabled()) {
                earningLines(item, catalogue.get(item.componentId()), ctx, lines);
            }
        }
        for (SalaryComponentItemResponse item : nullSafe(version.benefits())) {
            if (item.enabled()) {
                requireKnown(item, "Benefit");
                add(lines, LineKind.BENEFIT, item, money(item.monthlyAmount()), false);
            }
        }
        for (SalaryComponentItemResponse item : nullSafe(version.reimbursements())) {
            if (item.enabled()) {
                requireKnown(item, "Reimbursement");
                if (item.isFbp()) {
                    fbpSplit(lines, LineKind.REIMBURSEMENT, item);
                } else {
                    add(lines, LineKind.REIMBURSEMENT, item, money(item.monthlyAmount()), false);
                }
            }
        }
        return lines;
    }

    private static void earningLines(
            SalaryComponentItemResponse item, Earning definition, PayRunEmployeeContext ctx, List<PayLine> lines) {
        if (definition == null || UNKNOWN_CODE.equals(item.componentCode())) {
            throw new IllegalStateException("Earning component " + item.componentId()
                    + " is no longer in the catalogue; restore it or revise the salary version");
        }
        if (item.isFbp()) {
            fbpSplit(lines, LineKind.EARNING, item);
            return;
        }
        if (definition.isVariable()) {
            String text = item.earningFrequency() != null ? item.earningFrequency() : definition.getEarningFrequency();
            EarningFrequency frequency = EarningFrequency.parse(text)
                    .orElseThrow(() -> new IllegalStateException("Variable earning " + item.componentCode()
                            + " has no recognisable earning frequency ('" + text + "')"));
            Optional<Money> amount = PeriodicEarningRule.amountFor(
                    frequency,
                    money(item.monthlyAmount()),
                    money(item.annualAmount()),
                    ctx.period(),
                    ctx.employee().dateOfJoining());
            amount.ifPresent(a -> add(lines, LineKind.EARNING, item, a, definition.isTaxable()));
            return;
        }
        add(lines, LineKind.EARNING, item, money(item.monthlyAmount()), definition.isTaxable());
    }

    /**
     * W-27.2 §13 decision 3: the declared monthly amount is paid non-taxable, the rest of the pool
     * taxable. A declaration above the pool is an error, not a negative remainder.
     */
    private static void fbpSplit(List<PayLine> lines, LineKind kind, SalaryComponentItemResponse item) {
        Money pool = money(item.monthlyAmount());
        Money declared = money(item.declaredMonthlyAmount());
        Money remainder = pool.subtract(declared);
        if (remainder.isNegative()) {
            throw new IllegalStateException("FBP component " + item.componentCode() + " is declared at " + declared
                    + " a month, above its pool of " + pool);
        }
        add(lines, kind, item, declared, false);
        add(lines, kind, item, remainder, true);
    }

    private static void requireKnown(SalaryComponentItemResponse item, String what) {
        if (UNKNOWN_CODE.equals(item.componentCode())) {
            throw new IllegalStateException(what + " component " + item.componentId()
                    + " is no longer in the catalogue; restore it or revise the salary version");
        }
    }

    private static void add(
            List<PayLine> lines, LineKind kind, SalaryComponentItemResponse item, Money amount, boolean taxable) {
        if (amount.isZero()) {
            return;
        }
        lines.add(new PayLine(
                kind,
                LineSource.STRUCTURE,
                item.componentId(),
                item.componentCode(),
                item.componentName(),
                amount,
                taxable));
    }

    private static Money money(BigDecimal value) {
        return value == null ? Money.ZERO : Money.of(value);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
