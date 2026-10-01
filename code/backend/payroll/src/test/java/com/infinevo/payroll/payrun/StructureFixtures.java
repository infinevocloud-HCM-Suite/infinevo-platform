package com.infinevo.payroll.payrun;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.lop.LopRounding;
import com.infinevo.core.lop.WorkingDayBasisResponse;
import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.salary.SalaryComponentItemResponse;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Builders for the W-29.2 unit tests, and the §8 worked example: one employee, July, annual CTC
 * 6,00,000 with a {@code YEARLY} variable earning — net pay 47,000.00.
 */
final class StructureFixtures {

    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final YearMonth JULY = YearMonth.of(2026, 7);

    private StructureFixtures() {}

    static SalaryComponentItemResponse item(
            UUID componentId,
            String code,
            String name,
            String monthly,
            String annual,
            boolean enabled,
            String frequency,
            boolean fbp,
            String declaredMonthly) {
        return new SalaryComponentItemResponse(
                UUID.randomUUID(),
                componentId,
                code,
                name,
                CalculationType.FLAT,
                new BigDecimal(monthly),
                null,
                new BigDecimal(monthly),
                new BigDecimal(annual),
                enabled,
                true,
                frequency,
                null,
                fbp,
                declaredMonthly == null ? null : new BigDecimal(declaredMonthly).multiply(BigDecimal.valueOf(12)),
                declaredMonthly == null ? null : new BigDecimal(declaredMonthly));
    }

    static SalaryComponentItemResponse fixed(UUID componentId, String code, String name, String monthly) {
        return item(
                componentId,
                code,
                name,
                monthly,
                new BigDecimal(monthly).multiply(BigDecimal.valueOf(12)).toPlainString(),
                true,
                null,
                false,
                null);
    }

    static Earning earning(UUID id, boolean variable, boolean taxable) {
        Earning earning = mock(Earning.class);
        when(earning.getId()).thenReturn(id);
        when(earning.isVariable()).thenReturn(variable);
        when(earning.isTaxable()).thenReturn(taxable);
        return earning;
    }

    static SalaryVersionResponse version(
            List<SalaryComponentItemResponse> earnings,
            List<SalaryComponentItemResponse> benefits,
            List<SalaryComponentItemResponse> reimbursements) {
        return new SalaryVersionResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2025, 1, 1),
                new BigDecimal("600000.0000"),
                new BigDecimal("50000.0000"),
                false,
                null,
                null,
                null,
                earnings,
                benefits,
                reimbursements,
                List.of());
    }

    static PayRunEmployeeContext context(SalaryVersionResponse version, YearMonth period, LocalDate joined) {
        EmployeeResponse employee = new EmployeeResponse(
                version.employeeId(),
                TENANT,
                "E-1",
                "First",
                null,
                "Last",
                "MALE",
                joined,
                null,
                EmploymentStatus.ACTIVE,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
        return new PayRunEmployeeContext(
                TENANT,
                UUID.randomUUID(),
                UUID.randomUUID(),
                period,
                period.atDay(1),
                period.atEndOfMonth(),
                employee,
                version,
                new WorkingDayBasisResponse(
                        BigDecimal.valueOf(period.lengthOfMonth()).setScale(2),
                        BigDecimal.valueOf(period.lengthOfMonth()).setScale(2),
                        UUID.randomUUID(),
                        LopRounding.HALF_UP_2),
                LopRounding.HALF_UP_2,
                List.of(),
                PayRunDays.of(
                        BigDecimal.valueOf(period.lengthOfMonth()),
                        BigDecimal.ZERO,
                        LopFixtures.outside(period, BigDecimal.valueOf(period.lengthOfMonth()), joined, null)),
                Set.of(),
                List.of());
    }

    /** The §8 worked example: its version and the earning catalogue it reads. */
    record WorkedExample(SalaryVersionResponse version, List<Earning> catalogue) {}

    static WorkedExample workedExample() {
        UUID basic = UUID.randomUUID();
        UUID hra = UUID.randomUUID();
        UUID special = UUID.randomUUID();
        UUID meal = UUID.randomUUID();
        UUID bonus = UUID.randomUUID();
        SalaryVersionResponse version = version(
                List.of(
                        fixed(basic, "BASIC", "Basic", "25000.0000"),
                        fixed(hra, "HRA", "House rent allowance", "10000.0000"),
                        fixed(special, "SPECIAL", "Special allowance", "7500.0000"),
                        item(meal, "MEAL", "Meal card", "2500.0000", "30000.0000", true, null, true, "1500.0000"),
                        item(bonus, "BONUS", "Annual bonus", "5000.0000", "60000.0000", true, "YEARLY", false, null)),
                List.of(fixed(UUID.randomUUID(), "EMPLOYER_PF", "Employer PF", "1800.0000")),
                List.of(fixed(UUID.randomUUID(), "FUEL", "Fuel reimbursement", "2000.0000")));
        List<Earning> catalogue = List.of(
                earning(basic, false, true),
                earning(hra, false, true),
                earning(special, false, true),
                earning(meal, false, true),
                earning(bonus, true, true));
        return new WorkedExample(version, catalogue);
    }
}
