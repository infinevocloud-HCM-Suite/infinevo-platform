package com.infinevo.payroll.payrun;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.lop.LopRounding;
import com.infinevo.core.lop.WorkingDayBasisResponse;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Builders for the W-29.3 unit tests: a context with a basis, pay inputs, days and prior lines. */
final class LopFixtures {

    static final UUID EMPLOYEE = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private LopFixtures() {}

    static PayRunEmployeeContext context(
            YearMonth period,
            BigDecimal payableDays,
            BigDecimal divisor,
            LopRounding rounding,
            LocalDate joined,
            LocalDate terminated,
            List<PayInputResponse> inputs,
            Set<UUID> proRata,
            List<PayLine> priorLines) {
        PayRunDays days = PayRunDays.of(
                payableDays,
                PayInputLineContributor.netLopDays(inputs),
                outside(period, payableDays, joined, terminated));
        return new PayRunEmployeeContext(
                StructureFixtures.TENANT,
                UUID.randomUUID(),
                UUID.randomUUID(),
                period,
                period.atDay(1),
                period.atEndOfMonth(),
                employee(joined, terminated),
                version(),
                new WorkingDayBasisResponse(payableDays, divisor, UUID.randomUUID(), rounding),
                rounding,
                inputs,
                days,
                proRata,
                priorLines);
    }

    static EmployeeResponse employee(LocalDate joined, LocalDate terminated) {
        return new EmployeeResponse(
                EMPLOYEE,
                StructureFixtures.TENANT,
                "E-1",
                "First",
                null,
                "Last",
                "MALE",
                joined,
                terminated,
                terminated == null ? EmploymentStatus.ACTIVE : EmploymentStatus.TERMINATED,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
    }

    private static SalaryVersionResponse version() {
        return StructureFixtures.version(List.of(), List.of(), List.of());
    }

    static PayInputResponse input(PayInputKind kind, String quantity, String amount) {
        return input(kind, quantity, amount, null);
    }

    static PayInputResponse input(PayInputKind kind, String quantity, String amount, UUID reverses) {
        return new PayInputResponse(
                UUID.randomUUID(),
                EMPLOYEE,
                YearMonth.of(2026, 7),
                kind,
                quantity == null ? null : new BigDecimal(quantity),
                amount == null ? null : new BigDecimal(amount),
                "test",
                UUID.randomUUID().toString(),
                null,
                reverses,
                Instant.now());
    }

    static PayLine structure(LineKind kind, UUID componentId, String code, String amount) {
        return new PayLine(
                kind, LineSource.STRUCTURE, componentId, code, code, Money.of(amount), kind == LineKind.EARNING);
    }

    /**
     * Days outside the employment window as a fixed basis counts them — the gap's share of the month
     * (W-18.2). For a divisor equal to the month's length, as these fixtures use, that is calendar days;
     * the calculator's counted bases are tested in core ({@code DaysOutsideEmploymentTest}).
     */
    static BigDecimal outside(YearMonth period, BigDecimal divisor, LocalDate joined, LocalDate terminated) {
        LocalDate from = period.atDay(1);
        LocalDate to = period.atEndOfMonth();
        long gap = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if ((joined != null && d.isBefore(joined)) || (terminated != null && d.isAfter(terminated))) {
                gap++;
            }
        }
        return BigDecimal.valueOf(gap)
                .multiply(divisor)
                .divide(BigDecimal.valueOf(period.lengthOfMonth()), 10, java.math.RoundingMode.HALF_UP);
    }

    static BigDecimal days(String value) {
        return new BigDecimal(value);
    }
}
