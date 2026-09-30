package com.infinevo.payroll.payrun;

import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * One employee's totals, summed from their lines (W-29.2 §3). Everything is carried at scale 4;
 * {@link #netPay} is the only value rounded to 2, once, here.
 *
 * <pre>
 * net_pay = (gross_earnings + total_reimbursements − total_deductions).toAmount()
 * </pre>
 *
 * <p>{@code total_benefits} is the employer's side of the structure: reported, never in net (BUG-013).
 */
public record PayRunTotals(
        Money grossEarnings, Money totalReimbursements, Money totalBenefits, Money totalDeductions, BigDecimal netPay) {

    public PayRunTotals {
        Objects.requireNonNull(grossEarnings, "grossEarnings must not be null");
        Objects.requireNonNull(totalReimbursements, "totalReimbursements must not be null");
        Objects.requireNonNull(totalBenefits, "totalBenefits must not be null");
        Objects.requireNonNull(totalDeductions, "totalDeductions must not be null");
        Objects.requireNonNull(netPay, "netPay must not be null");
    }

    public static PayRunTotals of(List<PayLine> lines) {
        Objects.requireNonNull(lines, "lines must not be null");
        Money gross = Money.ZERO;
        Money reimbursements = Money.ZERO;
        Money benefits = Money.ZERO;
        Money deductions = Money.ZERO;
        for (PayLine line : lines) {
            switch (line.kind()) {
                case EARNING -> gross = gross.add(line.amount());
                case REIMBURSEMENT -> reimbursements = reimbursements.add(line.amount());
                case BENEFIT -> benefits = benefits.add(line.amount());
                case DEDUCTION -> deductions = deductions.add(line.amount());
            }
        }
        BigDecimal net = gross.add(reimbursements).subtract(deductions).toAmount();
        return new PayRunTotals(gross, reimbursements, benefits, deductions, net);
    }
}
