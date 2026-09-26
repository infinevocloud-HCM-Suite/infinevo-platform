package com.infinevo.core.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HolidayQueryServiceTest {

    private HolidayCalendarRepository calendarRepository;
    private HolidayRepository holidayRepository;
    private HolidayCalendarLocationRepository locationRepository;
    private HolidayQueryService queryService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID locationA = UUID.randomUUID();
    private final UUID locationB = UUID.randomUUID();
    private final UUID calendarA = UUID.randomUUID();
    private final UUID defaultCalendar = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        calendarRepository = mock(HolidayCalendarRepository.class);
        holidayRepository = mock(HolidayRepository.class);
        locationRepository = mock(HolidayCalendarLocationRepository.class);

        queryService = new HolidayQueryService(calendarRepository, holidayRepository, locationRepository);
        TenantContext.set(tenantId);

        // Location A is mapped to Calendar A
        when(locationRepository.findByTenantIdAndWorkLocationId(tenantId, locationA))
                .thenReturn(Optional.of(new HolidayCalendarLocation(tenantId, calendarA, locationA)));

        // Location B is unmapped; tenant has a default calendar
        when(locationRepository.findByTenantIdAndWorkLocationId(tenantId, locationB))
                .thenReturn(Optional.empty());

        HolidayCalendar defaultCal = new HolidayCalendar(tenantId, "Default Calendar", true);
        try {
            java.lang.reflect.Field idField = HolidayCalendar.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(defaultCal, defaultCalendar);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(calendarRepository.findByTenantIdAndIsDefaultTrue(tenantId)).thenReturn(Optional.of(defaultCal));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a date inside a range is a holiday")
    void dateInsideRangeIsHoliday() {
        LocalDate date = LocalDate.of(2026, 8, 15);
        when(holidayRepository.isHoliday(tenantId, calendarA, date)).thenReturn(true);

        assertThat(queryService.isHoliday(locationA, date)).isTrue();
    }

    @Test
    @DisplayName("boundaries are inclusive")
    void boundariesAreInclusive() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 3);
        LocalDate before = LocalDate.of(2026, 9, 30);
        LocalDate after = LocalDate.of(2026, 10, 4);

        when(holidayRepository.isHoliday(tenantId, calendarA, start)).thenReturn(true);
        when(holidayRepository.isHoliday(tenantId, calendarA, end)).thenReturn(true);
        when(holidayRepository.isHoliday(tenantId, calendarA, before)).thenReturn(false);
        when(holidayRepository.isHoliday(tenantId, calendarA, after)).thenReturn(false);

        assertThat(queryService.isHoliday(locationA, start)).isTrue();
        assertThat(queryService.isHoliday(locationA, end)).isTrue();
        assertThat(queryService.isHoliday(locationA, before)).isFalse();
        assertThat(queryService.isHoliday(locationA, after)).isFalse();
    }

    @Test
    @DisplayName("restricted holidays are returned but flagged")
    void restrictedHolidaysAreReturnedButFlagged() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);

        Holiday restrictedHoliday = new Holiday(
                tenantId,
                calendarA,
                "Optional Festival",
                LocalDate.of(2026, 3, 15),
                LocalDate.of(2026, 3, 15),
                true,
                "Optional religious holiday");

        when(holidayRepository.findHolidaysBetween(tenantId, calendarA, from, to))
                .thenReturn(List.of(restrictedHoliday));

        List<HolidayResponse> results = queryService.holidaysBetween(locationA, from, to);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("Optional Festival");
        assertThat(results.get(0).restricted()).isTrue();
    }

    @Test
    @DisplayName("holidaysBetween returns a holiday that straddles either end of the range")
    void holidaysBetweenStraddlesEitherEnd() {
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 31);

        Holiday straddleStart = new Holiday(
                tenantId,
                calendarA,
                "Early May Holiday",
                LocalDate.of(2026, 4, 29),
                LocalDate.of(2026, 5, 2),
                false,
                null);

        Holiday straddleEnd = new Holiday(
                tenantId,
                calendarA,
                "Late May Holiday",
                LocalDate.of(2026, 5, 30),
                LocalDate.of(2026, 6, 2),
                false,
                null);

        when(holidayRepository.findHolidaysBetween(tenantId, calendarA, from, to))
                .thenReturn(List.of(straddleStart, straddleEnd));

        List<HolidayResponse> results = queryService.holidaysBetween(locationA, from, to);
        assertThat(results).hasSize(2);
        assertThat(results).extracting(HolidayResponse::name).containsExactly("Early May Holiday", "Late May Holiday");
    }

    @Test
    @DisplayName("holidaysBetween returns an empty list, not null, when there are none")
    void holidaysBetweenReturnsEmptyListWhenNone() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);

        when(holidayRepository.findHolidaysBetween(tenantId, calendarA, from, to))
                .thenReturn(List.of());

        List<HolidayResponse> results = queryService.holidaysBetween(locationA, from, to);
        assertThat(results).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("a location with no calendar falls to the default")
    void locationWithNoCalendarFallsToDefault() {
        LocalDate date = LocalDate.of(2026, 1, 26);
        when(holidayRepository.isHoliday(tenantId, defaultCalendar, date)).thenReturn(true);

        // Location B is unassigned, should use default calendar
        boolean isHol = queryService.isHoliday(locationB, date);
        assertThat(isHol).isTrue();
    }
}
