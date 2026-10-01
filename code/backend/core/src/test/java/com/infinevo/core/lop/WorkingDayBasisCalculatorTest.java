package com.infinevo.core.lop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
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
 * Unit tests for {@link WorkingDayBasisCalculator} (W-18.1 §7).
 */
class WorkingDayBasisCalculatorTest {

    private LopPolicyService policyService;
    private EmployeeRepository employeeRepository;
    private HolidayQueryService holidayQueryService;
    private WorkingDayBasisCalculator calculator;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();

    private final YearMonth feb2026 = YearMonth.of(2026, 2); // 28 days
    private final YearMonth jul2026 = YearMonth.of(2026, 7); // 31 days

    @BeforeEach
    void setUp() {
        policyService = mock(LopPolicyService.class);
        employeeRepository = mock(EmployeeRepository.class);
        holidayQueryService = mock(HolidayQueryService.class);

        Employee employee = mock(Employee.class);
        WorkLocation location = mock(WorkLocation.class);
        when(location.getId()).thenReturn(locationId);
        when(employee.getWorkLocation()).thenReturn(location);
        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId))
                .thenReturn(Optional.of(employee));

        calculator = new WorkingDayBasisCalculator(policyService, employeeRepository, holidayQueryService);
    }

    @Test
    @DisplayName("FIXED_30 basis produces 30 days for February and July regardless of month length")
    void fixed30_februaryAndJuly_produces30() {
        LopPolicy policy = new LopPolicy(
                tenantId, WorkingDayBasis.FIXED_30, null, true, true, LopRounding.HALF_UP_2, LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));

        WorkingDayBasisResponse febResponse = calculator.basisFor(tenantId, feb2026, employeeId);
        WorkingDayBasisResponse julResponse = calculator.basisFor(tenantId, jul2026, employeeId);

        assertThat(febResponse.payableDays()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(febResponse.divisor()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(febResponse.policyId()).isEqualTo(policy.getId());

        assertThat(julResponse.payableDays()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(julResponse.divisor()).isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("ACTUAL_DAYS basis with weekends and holidays payable produces period calendar days")
    void actualDays_allPayable_producesMonthLength() {
        LopPolicy policy = new LopPolicy(
                tenantId,
                WorkingDayBasis.ACTUAL_DAYS,
                null,
                true,
                true,
                LopRounding.HALF_UP_2,
                LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));

        WorkingDayBasisResponse febResponse = calculator.basisFor(tenantId, feb2026, employeeId);
        WorkingDayBasisResponse julResponse = calculator.basisFor(tenantId, jul2026, employeeId);

        // February 2026 has 28 days, July 2026 has 31 days
        assertThat(febResponse.payableDays()).isEqualByComparingTo(new BigDecimal("28.00"));
        assertThat(julResponse.payableDays()).isEqualByComparingTo(new BigDecimal("31.00"));
    }

    @Test
    @DisplayName("ACTUAL_DAYS with weekends_payable=false subtracts non-working days using WorkingWeekSource")
    void actualDays_weekendsNotPayable_subtractsWeekends() {
        LopPolicy policy = new LopPolicy(
                tenantId,
                WorkingDayBasis.ACTUAL_DAYS,
                null,
                false,
                true,
                LopRounding.HALF_UP_2,
                LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));

        // Mon-Fri working days
        WorkingWeekSource monFri = (t, e) ->
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        calculator.setWorkingWeekSource(monFri);

        WorkingDayBasisResponse febResponse = calculator.basisFor(tenantId, feb2026, employeeId);
        WorkingDayBasisResponse julResponse = calculator.basisFor(tenantId, jul2026, employeeId);

        // Feb 2026 has 20 weekdays; July 2026 has 23 weekdays
        assertThat(febResponse.payableDays()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(julResponse.payableDays()).isEqualByComparingTo(new BigDecimal("23.00"));
    }

    @Test
    @DisplayName("ACTUAL_DAYS with holidays_payable=false subtracts holidays from HolidayQueryService")
    void actualDays_holidaysNotPayable_subtractsHolidays() {
        LopPolicy policy = new LopPolicy(
                tenantId,
                WorkingDayBasis.ACTUAL_DAYS,
                null,
                true,
                false,
                LopRounding.HALF_UP_2,
                LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));

        // Mock 2 holiday days in July 2026 (July 15 and July 16)
        when(holidayQueryService.holidaysBetween(eq(locationId), any(), any()))
                .thenReturn(List.of(new HolidayResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Summer Break",
                        LocalDate.of(2026, 7, 15),
                        LocalDate.of(2026, 7, 16),
                        false,
                        null)));

        WorkingDayBasisResponse julResponse = calculator.basisFor(tenantId, jul2026, employeeId);

        // 31 - 2 = 29
        assertThat(julResponse.payableDays()).isEqualByComparingTo(new BigDecimal("29.00"));
    }

    @Test
    @DisplayName("ACTUAL_DAYS with both weekends and holidays non-payable does not double-count weekend holidays")
    void actualDays_bothNonPayable_doesNotDoubleCount() {
        LopPolicy policy = new LopPolicy(
                tenantId,
                WorkingDayBasis.ACTUAL_DAYS,
                null,
                false,
                false,
                LopRounding.HALF_UP_2,
                LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));

        // Mon-Fri working days
        WorkingWeekSource monFri = (t, e) ->
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        calculator.setWorkingWeekSource(monFri);

        // July 4, 2026 is a Saturday (already non-working weekend)
        when(holidayQueryService.holidaysBetween(eq(locationId), any(), any()))
                .thenReturn(List.of(new HolidayResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Saturday Holiday",
                        LocalDate.of(2026, 7, 4),
                        LocalDate.of(2026, 7, 4),
                        false,
                        null)));

        WorkingDayBasisResponse julResponse = calculator.basisFor(tenantId, jul2026, employeeId);

        // July 2026 has 23 weekdays. Since July 4 is Saturday, weekday count remains 23.
        assertThat(julResponse.payableDays()).isEqualByComparingTo(new BigDecimal("23.00"));
    }

    @Test
    @DisplayName("ORG_DAYS with configured_days_per_month returns fixed n")
    void orgDays_configuredN_returnsN() {
        BigDecimal configuredN = new BigDecimal("26.50");
        LopPolicy policy = new LopPolicy(
                tenantId,
                WorkingDayBasis.ORG_DAYS,
                configuredN,
                true,
                true,
                LopRounding.HALF_UP_2,
                LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));

        WorkingDayBasisResponse febResponse = calculator.basisFor(tenantId, feb2026, employeeId);
        WorkingDayBasisResponse julResponse = calculator.basisFor(tenantId, jul2026, employeeId);

        assertThat(febResponse.payableDays()).isEqualByComparingTo(new BigDecimal("26.50"));
        assertThat(julResponse.payableDays()).isEqualByComparingTo(new BigDecimal("26.50"));
    }

    @Test
    @DisplayName("ORG_DAYS without n counts from WorkingWeekSource: Mon-Fri gives 23 and Mon-Sat gives 27 for July")
    void orgDays_withoutN_countsWorkingWeekdays() {
        LopPolicy policy = new LopPolicy(
                tenantId, WorkingDayBasis.ORG_DAYS, null, true, true, LopRounding.HALF_UP_2, LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));

        // 1. Mon-Fri
        WorkingWeekSource monFri = (t, e) ->
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        calculator.setWorkingWeekSource(monFri);

        WorkingDayBasisResponse julMonFri = calculator.basisFor(tenantId, jul2026, employeeId);
        assertThat(julMonFri.payableDays()).isEqualByComparingTo(new BigDecimal("23.00"));

        // 2. Mon-Sat
        WorkingWeekSource monSat = (t, e) -> Set.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY);
        calculator.setWorkingWeekSource(monSat);

        WorkingDayBasisResponse julMonSat = calculator.basisFor(tenantId, jul2026, employeeId);
        assertThat(julMonSat.payableDays()).isEqualByComparingTo(new BigDecimal("27.00"));
    }

    @Test
    @DisplayName("ORG_DAYS without n subtracts holidays when holidays_payable=false")
    void orgDays_withoutN_holidaysNotPayable_subtractsHolidays() {
        LopPolicy policy = new LopPolicy(
                tenantId, WorkingDayBasis.ORG_DAYS, null, true, false, LopRounding.HALF_UP_2, LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));

        WorkingWeekSource monFri = (t, e) ->
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        calculator.setWorkingWeekSource(monFri);

        // July 15, 2026 is a Wednesday (working day)
        when(holidayQueryService.holidaysBetween(eq(locationId), any(), any()))
                .thenReturn(List.of(new HolidayResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Holiday",
                        LocalDate.of(2026, 7, 15),
                        LocalDate.of(2026, 7, 15),
                        false,
                        null)));

        WorkingDayBasisResponse julResponse = calculator.basisFor(tenantId, jul2026, employeeId);

        // 23 weekdays - 1 holiday = 22
        assertThat(julResponse.payableDays()).isEqualByComparingTo(new BigDecimal("22.00"));
    }

    @Test
    @DisplayName(
            "W-18.1 (Issue #14): Configured days per month preserves 26.50 as divisor and does not round to integer")
    void configuredDays_preservesFractionalDays() {
        LopPolicy policyHalfUp0 = new LopPolicy(
                tenantId,
                WorkingDayBasis.ORG_DAYS,
                new BigDecimal("26.50"),
                true,
                true,
                LopRounding.HALF_UP_0,
                LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policyHalfUp0));

        WorkingDayBasisResponse response0 = calculator.basisFor(tenantId, jul2026, employeeId);
        // The divisor must stay 26.50, never rounded to 27
        assertThat(response0.payableDays()).isEqualByComparingTo(new BigDecimal("26.50"));
        assertThat(response0.divisor()).isEqualByComparingTo(new BigDecimal("26.50"));
    }

    @Test
    @DisplayName(
            "W-18.1 (Issue #15): When holidays cannot be looked up, throws NoLopPolicyException instead of assuming zero")
    void holidayLookupFailure_throwsNoLopPolicyException() {
        LopPolicy policy = new LopPolicy(
                tenantId,
                WorkingDayBasis.ACTUAL_DAYS,
                null,
                true,
                false, // holidays NOT payable
                LopRounding.HALF_UP_2,
                LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));

        // When HolidayQueryService fails
        when(holidayQueryService.holidaysBetween(any(), any(), any()))
                .thenThrow(new RuntimeException("Holiday service unavailable"));

        assertThatThrownBy(() -> calculator.basisFor(tenantId, jul2026, employeeId))
                .isInstanceOf(NoLopPolicyException.class)
                .hasMessageContaining("Could not resolve holidays");

        // When HolidayQueryService bean is absent
        WorkingDayBasisCalculator calcWithoutHoliday =
                new WorkingDayBasisCalculator(policyService, employeeRepository, null);
        assertThatThrownBy(() -> calcWithoutHoliday.basisFor(tenantId, jul2026, employeeId))
                .isInstanceOf(NoLopPolicyException.class)
                .hasMessageContaining("HolidayQueryService bean is required");
    }

    @Test
    @DisplayName("Tenant with no policy throws NoLopPolicyException — there is no silent default")
    void noPolicy_throwsNoLopPolicyException() {
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> calculator.basisFor(tenantId, jul2026, employeeId))
                .isInstanceOf(NoLopPolicyException.class)
                .hasMessageContaining("No loss-of-pay policy in force");
    }

    @Test
    @DisplayName("F-4: a WorkingWeekSource that throws surfaces as NoLopPolicyException, for ORG_DAYS and ACTUAL_DAYS")
    void workingWeekSourceFailure_throwsNoLopPolicyException() {
        calculator.setWorkingWeekSource((t, e) -> {
            throw new IllegalStateException("schedule store unavailable");
        });

        LopPolicy orgDays = new LopPolicy(
                tenantId, WorkingDayBasis.ORG_DAYS, null, true, true, LopRounding.HALF_UP_2, LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(orgDays));
        assertThatThrownBy(() -> calculator.basisFor(tenantId, jul2026, employeeId))
                .isInstanceOf(NoLopPolicyException.class)
                .hasMessageContaining("Could not resolve the working week")
                .hasCauseInstanceOf(IllegalStateException.class);

        LopPolicy actualDays = new LopPolicy(
                tenantId,
                WorkingDayBasis.ACTUAL_DAYS,
                null,
                false,
                true,
                LopRounding.HALF_UP_2,
                LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(actualDays));
        assertThatThrownBy(() -> calculator.basisFor(tenantId, jul2026, employeeId))
                .isInstanceOf(NoLopPolicyException.class)
                .hasMessageContaining("Could not resolve the working week");
    }

    @Test
    @DisplayName("F-4: a NoLopPolicyException subtype from the source (e.g. no pay schedule) passes through as is")
    void workingWeekSourceNoLopPolicySubtype_passesThrough() {
        NoLopPolicyException fromSource = new NoLopPolicyException("No pay schedule configured") {};
        calculator.setWorkingWeekSource((t, e) -> {
            throw fromSource;
        });
        LopPolicy orgDays = new LopPolicy(
                tenantId, WorkingDayBasis.ORG_DAYS, null, true, true, LopRounding.HALF_UP_2, LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(orgDays));

        assertThatThrownBy(() -> calculator.basisFor(tenantId, jul2026, employeeId))
                .isSameAs(fromSource);
    }

    @Test
    @DisplayName("F-5: the response carries the policy's lopRounding")
    void response_carriesPolicyRounding() {
        LopPolicy policy = new LopPolicy(
                tenantId, WorkingDayBasis.FIXED_30, null, true, true, LopRounding.HALF_UP_0, LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));

        assertThat(calculator.basisFor(tenantId, jul2026, employeeId).lopRounding())
                .isEqualTo(LopRounding.HALF_UP_0);
    }

    @Test
    @DisplayName("F-5: HALF_UP_2 when the policy says nothing")
    void rounding_defaultsToHalfUp2_whenPolicySaysNothing() {
        LopPolicy policy =
                new LopPolicy(tenantId, WorkingDayBasis.ACTUAL_DAYS, null, true, true, null, LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));

        assertThat(calculator.basisFor(tenantId, jul2026, employeeId).lopRounding())
                .isEqualTo(LopRounding.HALF_UP_2);
    }

    @Test
    @DisplayName("F-6: an unknown, deleted or other-tenant employee is refused, never given the default calendar")
    void unknownEmployee_isRefused() {
        LopPolicy policy = new LopPolicy(
                tenantId,
                WorkingDayBasis.ACTUAL_DAYS,
                null,
                true,
                false,
                LopRounding.HALF_UP_2,
                LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));
        UUID unknown = UUID.randomUUID();
        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(unknown, tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> calculator.basisFor(tenantId, jul2026, unknown))
                .isInstanceOf(NoLopPolicyException.class)
                .hasMessageContaining("not found in tenant");
    }

    @Test
    @DisplayName("W-18.2: an employee with no work location is refused when holidays count, never given the default")
    void employeeWithoutWorkLocation_isRefusedWhenHolidaysCount() {
        LopPolicy policy = new LopPolicy(
                tenantId, WorkingDayBasis.ORG_DAYS, null, true, false, LopRounding.HALF_UP_2, LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));
        calculator.setWorkingWeekSource((t, e) ->
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        UUID nowhere = UUID.randomUUID();
        Employee withoutLocation = mock(Employee.class);
        when(withoutLocation.getWorkLocation()).thenReturn(null);
        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(nowhere, tenantId))
                .thenReturn(Optional.of(withoutLocation));

        assertThatThrownBy(() -> calculator.basisFor(tenantId, jul2026, nowhere))
                .isInstanceOf(NoLopPolicyException.class)
                .hasMessageContaining("has no work location");

        // Holidays paid: the calendar is never needed, so the missing location does not matter.
        LopPolicy holidaysPaid = new LopPolicy(
                tenantId, WorkingDayBasis.ORG_DAYS, null, true, true, LopRounding.HALF_UP_2, LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(holidaysPaid));
        assertThat(calculator.basisFor(tenantId, jul2026, nowhere).divisor()).isEqualByComparingTo("23.00");
    }

    @Test
    @DisplayName("F-6: with no employee the tenant's default calendar is used (location null)")
    void noEmployee_usesDefaultCalendar() {
        LopPolicy policy = new LopPolicy(
                tenantId,
                WorkingDayBasis.ACTUAL_DAYS,
                null,
                true,
                false,
                LopRounding.HALF_UP_2,
                LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));
        when(holidayQueryService.holidaysBetween(isNull(), any(), any()))
                .thenReturn(List.of(holiday(LocalDate.of(2026, 7, 15), false)));

        assertThat(calculator.basisFor(tenantId, jul2026, null).divisor())
                .isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    @DisplayName("F-7: a restricted holiday does not change payable days or the divisor")
    void restrictedHoliday_doesNotChangeDivisor() {
        LopPolicy policy = new LopPolicy(
                tenantId, WorkingDayBasis.ORG_DAYS, null, true, false, LopRounding.HALF_UP_2, LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));
        calculator.setWorkingWeekSource((t, e) ->
                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY));
        // July 15 2026 is a Wednesday; July 16 a Thursday
        when(holidayQueryService.holidaysBetween(eq(locationId), any(), any()))
                .thenReturn(
                        List.of(holiday(LocalDate.of(2026, 7, 15), true), holiday(LocalDate.of(2026, 7, 16), false)));

        WorkingDayBasisResponse response = calculator.basisFor(tenantId, jul2026, employeeId);

        // 23 weekdays - 1 non-restricted holiday = 22; the restricted one is not subtracted
        assertThat(response.payableDays()).isEqualByComparingTo(new BigDecimal("22.00"));
        assertThat(response.divisor()).isEqualByComparingTo(new BigDecimal("22.00"));
    }

    @Test
    @DisplayName("F-8: a period with no payable days is refused rather than returning divisor 0")
    void zeroPayableDays_isRefused() {
        LopPolicy policy = new LopPolicy(
                tenantId, WorkingDayBasis.ORG_DAYS, null, true, true, LopRounding.HALF_UP_2, LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));
        calculator.setWorkingWeekSource((t, e) -> Set.of());

        assertThatThrownBy(() -> calculator.basisFor(tenantId, jul2026, employeeId))
                .isInstanceOf(NoLopPolicyException.class)
                .hasMessageContaining("No payable days in period");
    }

    @Test
    @DisplayName("F-8: ACTUAL_DAYS where every working day is a holiday is refused too")
    void zeroPayableDays_actualDays_isRefused() {
        LopPolicy policy = new LopPolicy(
                tenantId,
                WorkingDayBasis.ACTUAL_DAYS,
                null,
                false,
                false,
                LopRounding.HALF_UP_2,
                LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));
        calculator.setWorkingWeekSource((t, e) -> Set.of(DayOfWeek.MONDAY));
        when(holidayQueryService.holidaysBetween(eq(locationId), any(), any()))
                .thenReturn(List.of(new HolidayResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Shutdown",
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 31),
                        false,
                        null)));

        assertThatThrownBy(() -> calculator.basisFor(tenantId, jul2026, employeeId))
                .isInstanceOf(NoLopPolicyException.class)
                .hasMessageContaining("No payable days in period");
    }

    private static HolidayResponse holiday(LocalDate date, boolean restricted) {
        return new HolidayResponse(UUID.randomUUID(), UUID.randomUUID(), "Holiday", date, date, restricted, null);
    }

    @Test
    @DisplayName("Basis requiring weekday set throws NoLopPolicyException when WorkingWeekSource bean is absent")
    void missingWorkingWeekSource_throwsNoLopPolicyException() {
        LopPolicy policy = new LopPolicy(
                tenantId, WorkingDayBasis.ORG_DAYS, null, true, true, LopRounding.HALF_UP_2, LocalDate.of(2026, 1, 1));
        when(policyService.findPolicyInForceEntity(eq(tenantId), any())).thenReturn(Optional.of(policy));

        // WorkingWeekSource is null
        calculator.setWorkingWeekSource(null);

        assertThatThrownBy(() -> calculator.basisFor(tenantId, jul2026, employeeId))
                .isInstanceOf(NoLopPolicyException.class)
                .hasMessageContaining("WorkingWeekSource bean is required");
    }
}
