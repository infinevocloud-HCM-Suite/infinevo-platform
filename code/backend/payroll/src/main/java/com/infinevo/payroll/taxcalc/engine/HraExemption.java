package com.infinevo.payroll.taxcalc.engine;

import com.infinevo.payroll.taxcalc.reader.model.HraRule;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHouseRent;
import com.infinevo.shared.money.Money;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure engine for Section 10(13A) House Rent Allowance exemption calculation (W-33.2 spec § 3 step 2).
 *
 * <p>Exemption is evaluated month by month over projected salary months and rent periods:
 * <pre>
 *   exemption = min(hraMonth, max(0, rentMonth − basicMonth × threshold%), basicMonth × city%)
 * </pre>
 * Zero when {@code isStayingInRentedHouse} is false or no HRA was received.
 */
public final class HraExemption {

    private HraExemption() {}

    /**
     * Line detail for a single month's HRA exemption calculation.
     */
    public record HraMonthLine(
            YearMonth month, Money basicAmount, Money hraAmount, Money rentAmount, boolean isMetro, Money exemption) {

        public HraMonthLine {
            Objects.requireNonNull(month, "month must not be null");
            Objects.requireNonNull(basicAmount, "basicAmount must not be null");
            Objects.requireNonNull(hraAmount, "hraAmount must not be null");
            Objects.requireNonNull(rentAmount, "rentAmount must not be null");
            Objects.requireNonNull(exemption, "exemption must not be null");
        }
    }

    /**
     * Monthly salary components relevant for HRA calculation.
     */
    public record HraMonthSalary(YearMonth month, Money basic, Money hra) {
        public HraMonthSalary {
            Objects.requireNonNull(month, "month must not be null");
            Objects.requireNonNull(basic, "basic must not be null");
            Objects.requireNonNull(hra, "hra must not be null");
        }
    }

    /**
     * Declared rent period for HRA exemption.
     */
    public record RentPeriod(YearMonth fromMonth, YearMonth toMonth, Money monthlyRent, boolean isMetro) {
        public RentPeriod {
            Objects.requireNonNull(fromMonth, "fromMonth must not be null");
            Objects.requireNonNull(toMonth, "toMonth must not be null");
            Objects.requireNonNull(monthlyRent, "monthlyRent must not be null");
        }

        public static RentPeriod from(EmployeeInvHouseRent rent) {
            Objects.requireNonNull(rent, "rent must not be null");
            return new RentPeriod(
                    YearMonth.from(rent.getFromMonth()),
                    YearMonth.from(rent.getToMonth()),
                    Money.of(rent.getAmountPerMonth()),
                    rent.isMetro());
        }
    }

    /**
     * Result of HRA exemption calculation across all months.
     */
    public record HraExemptionResult(Money totalExemption, List<HraMonthLine> monthLines) {
        public HraExemptionResult {
            Objects.requireNonNull(totalExemption, "totalExemption must not be null");
            monthLines = monthLines == null ? List.of() : List.copyOf(monthLines);
        }
    }

    /**
     * Calculates Section 10(13A) HRA exemption across projected months.
     */
    public static HraExemptionResult calculate(
            boolean isStayingInRentedHouse,
            HraRule rule,
            List<HraMonthSalary> monthlySalaries,
            List<RentPeriod> rentPeriods) {

        Objects.requireNonNull(rule, "rule must not be null");
        if (!isStayingInRentedHouse || monthlySalaries == null || monthlySalaries.isEmpty()) {
            return new HraExemptionResult(Money.ZERO, List.of());
        }

        List<RentPeriod> periods = rentPeriods == null ? List.of() : rentPeriods;

        Money totalHraReceived = Money.ZERO;
        for (HraMonthSalary m : monthlySalaries) {
            totalHraReceived = totalHraReceived.add(m.hra());
        }

        if (totalHraReceived.isZero()) {
            return new HraExemptionResult(Money.ZERO, List.of());
        }

        List<HraMonthLine> monthLines = new ArrayList<>();
        Money totalExemption = Money.ZERO;

        for (HraMonthSalary m : monthlySalaries) {
            YearMonth currentYm = m.month();

            RentPeriod matchingPeriod = null;
            for (RentPeriod p : periods) {
                if (!currentYm.isBefore(p.fromMonth()) && !currentYm.isAfter(p.toMonth())) {
                    matchingPeriod = p;
                    break;
                }
            }

            Money rentAmount = matchingPeriod != null ? matchingPeriod.monthlyRent() : Money.ZERO;
            boolean isMetro = matchingPeriod != null && matchingPeriod.isMetro();

            Money monthExemption = Money.ZERO;
            if (!rentAmount.isZero() && !m.hra().isZero()) {
                Money cap1 = m.hra();
                Money basicThreshold = m.basic().multiply(rule.thresholdFraction());
                Money cap2 =
                        rentAmount.compareTo(basicThreshold) > 0 ? rentAmount.subtract(basicThreshold) : Money.ZERO;
                Money cap3 = m.basic().multiply(rule.cityFraction(isMetro));

                Money minCap = cap1.compareTo(cap2) < 0 ? cap1 : cap2;
                monthExemption = minCap.compareTo(cap3) < 0 ? minCap : cap3;
            }

            monthLines.add(new HraMonthLine(currentYm, m.basic(), m.hra(), rentAmount, isMetro, monthExemption));
            totalExemption = totalExemption.add(monthExemption);
        }

        if (totalExemption.compareTo(totalHraReceived) > 0) {
            totalExemption = totalHraReceived;
        }

        return new HraExemptionResult(totalExemption, monthLines);
    }
}
