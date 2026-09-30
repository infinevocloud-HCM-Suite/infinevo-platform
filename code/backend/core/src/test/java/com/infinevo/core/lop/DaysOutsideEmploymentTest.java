package com.infinevo.core.lop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.holiday.HolidayQueryService;
import com.infinevo.core.holiday.HolidayResponse;
import com.infinevo.core.org.WorkLocation;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-18.2 — a joiner's or leaver's days outside the employment window, counted the way the policy counts
 * its divisor. July 2026 starts on a Wednesday: 31 days, 23 of them Monday to Friday.
 */
class DaysOutsideEmploymentTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final Set<DayOfWeek> MON_FRI =
            Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();

    private LopPolicyService policyService;
    private HolidayQueryService holidayQueryService;
    private WorkingDayBasisCalculator calculator;

    @BeforeEach
    void setUp() {
        policyService = mock(LopPolicyService.class);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        holidayQueryService = mock(HolidayQueryService.class);
        Employee employee = mock(Employee.class);
        WorkLocation location = mock(WorkLocation.class);
        when(location.getId()).thenReturn(locationId);
        when(employee.getWorkLocation()).thenReturn(location);
        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId))
                .thenReturn(Optional.of(employee));
        calculator = new WorkingDayBasisCalculator(policyService, employeeRepository, holidayQueryService);
        calculator.setWorkingWeekSource((t, e) -> MON_FRI);
    }

    @Test
    @DisplayName("Merge-commit example: ORG_DAYS Mon–Fri, joiner on 16 July — 11 working days outside, paid 12 of 23")
    void joinerUnderWorkingDaysIsOutsideForWorkingDaysOnly() {
        policy(WorkingDayBasis.ORG_DAYS, null, true, true);

        BigDecimal outside = outside(LocalDate.of(2026, 7, 16), null);

        assertThat(outside).isEqualByComparingTo("11.00");
        BigDecimal divisor = calculator.basisFor(tenantId, JULY, employeeId).divisor();
        assertThat(divisor).isEqualByComparingTo("23.00");
        // 42,500 × (23 − 11) ÷ 23 = 22,173.91 — calendar days gave 42,500 × (23 − 15) ÷ 23 = 14,782.61.
        assertThat(new BigDecimal("42500")
                        .multiply(divisor.subtract(outside))
                        .divide(divisor, 2, java.math.RoundingMode.HALF_UP))
                .isEqualByComparingTo("22173.91");
    }

    @Test
    @DisplayName("Leaver on 10 July under ACTUAL_DAYS, weekends unpaid: 15 working days after, of 23")
    void leaverUnderActualDaysWithWeekendsUnpaid() {
        policy(WorkingDayBasis.ACTUAL_DAYS, null, false, true);

        // 13–17, 20–24, 27–31 July.
        assertThat(outside(null, LocalDate.of(2026, 7, 10))).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("A holiday in the gap is not a working day outside: holidays unpaid take it off the count")
    void holidayInTheGapIsNotCounted() {
        policy(WorkingDayBasis.ORG_DAYS, null, true, false);
        when(holidayQueryService.holidaysBetween(eq(locationId), any(), any()))
                .thenReturn(List.of(new HolidayResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Holiday",
                        LocalDate.of(2026, 7, 6),
                        LocalDate.of(2026, 7, 6),
                        false,
                        null)));

        assertThat(outside(LocalDate.of(2026, 7, 16), null)).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("FIXED_30: the gap's share of the month — 15 of 31 calendar days × 30 = 14.52")
    void fixedBasisTakesTheGapsShare() {
        policy(WorkingDayBasis.FIXED_30, null, true, true);

        assertThat(outside(LocalDate.of(2026, 7, 16), null)).isEqualByComparingTo("14.52");
        // A joiner on the last day is outside 30 of 31 days, not all 30 paid days: 29.03.
        assertThat(outside(LocalDate.of(2026, 7, 31), null)).isEqualByComparingTo("29.03");
    }

    @Test
    @DisplayName("ORG_DAYS with 26 configured: 15 of 31 calendar days × 26 = 12.58")
    void configuredOrgDaysTakesTheGapsShare() {
        policy(WorkingDayBasis.ORG_DAYS, new BigDecimal("26"), true, true);

        assertThat(outside(LocalDate.of(2026, 7, 16), null)).isEqualByComparingTo("12.58");
    }

    @Test
    @DisplayName("ACTUAL_DAYS with everything payable: calendar days, as before — 15")
    void actualDaysAllPayableCountsCalendarDays() {
        policy(WorkingDayBasis.ACTUAL_DAYS, null, true, true);

        assertThat(outside(LocalDate.of(2026, 7, 16), null)).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("Employed all month: zero, without reading a policy")
    void employedAllMonthIsZero() {
        assertThat(outside(LocalDate.of(2020, 1, 1), null)).isEqualByComparingTo("0");
        assertThat(outside(null, LocalDate.of(2026, 8, 1))).isEqualByComparingTo("0");
        verify(policyService, never()).findPolicyInForceEntity(any(), any());
    }

    @Test
    @DisplayName("No policy in force: refused, never a calendar-day default")
    void noPolicyIsRefused() {
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> outside(LocalDate.of(2026, 7, 16), null)).isInstanceOf(NoLopPolicyException.class);
    }

    private BigDecimal outside(LocalDate joined, LocalDate terminated) {
        return calculator.daysOutsideEmployment(tenantId, JULY, employeeId, joined, terminated);
    }

    private void policy(
            WorkingDayBasis basis, BigDecimal configured, boolean weekendsPayable, boolean holidaysPayable) {
        LopPolicy policy = new LopPolicy(
                tenantId,
                basis,
                configured,
                weekendsPayable,
                holidaysPayable,
                LopRounding.HALF_UP_2,
                LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));
    }
}
