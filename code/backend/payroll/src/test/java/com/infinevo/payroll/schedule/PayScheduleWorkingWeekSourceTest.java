package com.infinevo.payroll.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PayScheduleWorkingWeekSource} (W-28 §7).
 */
class PayScheduleWorkingWeekSourceTest {

    private PayScheduleRepository repository;
    private PayScheduleWorkingWeekSource source;
    private final UUID tenantId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private final UUID employeeId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @BeforeEach
    void setUp() {
        repository = mock(PayScheduleRepository.class);
        source = new PayScheduleWorkingWeekSource(repository);
    }

    @Test
    @DisplayName("{1,2,3,4,5} → MONDAY through FRIDAY (Mon-Fri work week)")
    void monFriMapping() {
        givenWorkingDays(new Short[] {1, 2, 3, 4, 5});
        Set<DayOfWeek> days = source.weekdaysFor(tenantId, employeeId);
        assertThat(days)
                .containsExactlyInAnyOrder(
                        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        assertThat(days).doesNotContain(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    }

    @Test
    @DisplayName("{1,2,3,4,5,6} → MONDAY through SATURDAY (Mon-Sat work week)")
    void monSatMapping() {
        givenWorkingDays(new Short[] {1, 2, 3, 4, 5, 6});
        Set<DayOfWeek> days = source.weekdaysFor(tenantId, employeeId);
        assertThat(days)
                .containsExactlyInAnyOrder(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY,
                        DayOfWeek.SATURDAY);
        assertThat(days).doesNotContain(DayOfWeek.SUNDAY);
    }

    @Test
    @DisplayName("No pay schedule → NoPayScheduleException (409)")
    void noSchedule_throwsNoPayScheduleException() {
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> source.weekdaysFor(tenantId, employeeId))
                .isInstanceOf(NoPayScheduleException.class)
                // core's calculator and /basis map NoLopPolicyException to 409 (W-28 F-1)
                .isInstanceOf(com.infinevo.core.lop.NoLopPolicyException.class)
                .hasMessageContaining(tenantId.toString());
    }

    @Test
    @DisplayName("employeeId is ignored — tenant-level schedule applies")
    void employeeIdIsIgnored() {
        givenWorkingDays(new Short[] {1, 2, 3, 4, 5});
        UUID anotherEmployee = UUID.randomUUID();
        // should not throw, should return the tenant schedule regardless of employeeId
        Set<DayOfWeek> days = source.weekdaysFor(tenantId, anotherEmployee);
        assertThat(days).hasSize(5);
    }

    private void givenWorkingDays(Short[] days) {
        PaySchedule schedule = new PaySchedule(
                tenantId, days, PayDayRule.LAST_WORKING_DAY, null, (short) 25, LocalDate.of(2026, 1, 1));
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(schedule));
    }
}
