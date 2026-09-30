package com.infinevo.core.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.org.WorkLocation;
import com.infinevo.core.org.WorkLocationRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HolidayCalendarServiceTest {

    private HolidayCalendarRepository calendarRepository;
    private HolidayRepository holidayRepository;
    private HolidayCalendarLocationRepository locationRepository;
    private WorkLocationRepository workLocationRepository;
    private HolidayCalendarService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID calendarId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();
    private final UUID holidayId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        calendarRepository = mock(HolidayCalendarRepository.class);
        holidayRepository = mock(HolidayRepository.class);
        locationRepository = mock(HolidayCalendarLocationRepository.class);
        workLocationRepository = mock(WorkLocationRepository.class);

        service = new HolidayCalendarService(
                calendarRepository, holidayRepository, locationRepository, workLocationRepository);
        TenantContext.set(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static void setId(Object entity, UUID id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("createCalendar saves calendar and assigns valid locations")
    void createCalendar_success() {
        HolidayCalendarRequest req = new HolidayCalendarRequest("Main Calendar", false, Set.of(locationId));

        WorkLocation loc = mock(WorkLocation.class);
        when(workLocationRepository.findByIdAndTenantId(locationId, tenantId)).thenReturn(Optional.of(loc));
        when(locationRepository.existsByTenantIdAndWorkLocationIdAndCalendarIdNot(tenantId, locationId, null))
                .thenReturn(false);

        when(calendarRepository.save(any(HolidayCalendar.class))).thenAnswer(invocation -> {
            HolidayCalendar cal = invocation.getArgument(0);
            setId(cal, calendarId);
            return cal;
        });

        when(locationRepository.findByTenantIdAndCalendarId(tenantId, calendarId))
                .thenReturn(List.of(new HolidayCalendarLocation(tenantId, calendarId, locationId)));
        when(holidayRepository.findByTenantIdAndCalendarIdOrderByFromDateAsc(tenantId, calendarId))
                .thenReturn(List.of());

        HolidayCalendarResponse res = service.createCalendar(req);
        assertThat(res.id()).isEqualTo(calendarId);
        assertThat(res.name()).isEqualTo("Main Calendar");
        assertThat(res.workLocationIds()).contains(locationId);
    }

    @Test
    @DisplayName("createCalendar throws IllegalStateException when default calendar already exists")
    void createCalendar_duplicateDefault_throws() {
        HolidayCalendarRequest req = new HolidayCalendarRequest("Default Cal", true, Set.of());
        when(calendarRepository.findByTenantIdAndIsDefaultTrue(tenantId))
                .thenReturn(Optional.of(new HolidayCalendar(tenantId, "Existing Default", true)));

        assertThatThrownBy(() -> service.createCalendar(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("A default holiday calendar already exists");
    }

    @Test
    @DisplayName("createCalendar validates blank or overly long names")
    void createCalendar_invalidName_throws() {
        assertThatThrownBy(() -> service.createCalendar(new HolidayCalendarRequest("  ", false, Set.of())))
                .isInstanceOf(IllegalArgumentException.class);

        String longName = "A".repeat(129);
        assertThatThrownBy(() -> service.createCalendar(new HolidayCalendarRequest(longName, false, Set.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getCalendar throws HolidayCalendarNotFoundException when not found")
    void getCalendar_notFound_throws() {
        when(calendarRepository.findByTenantIdAndId(tenantId, calendarId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCalendar(calendarId))
                .isInstanceOf(HolidayCalendarService.HolidayCalendarNotFoundException.class)
                .hasMessageContaining("Holiday calendar not found");
    }

    @Test
    @DisplayName("updateCalendar flushes location delete before re-assigning")
    void updateCalendar_flushesLocationDelete() {
        HolidayCalendar existing = new HolidayCalendar(tenantId, "Old Name", false);
        setId(existing, calendarId);
        when(calendarRepository.findByTenantIdAndId(tenantId, calendarId)).thenReturn(Optional.of(existing));

        WorkLocation loc = mock(WorkLocation.class);
        when(workLocationRepository.findByIdAndTenantId(locationId, tenantId)).thenReturn(Optional.of(loc));
        when(locationRepository.existsByTenantIdAndWorkLocationIdAndCalendarIdNot(tenantId, locationId, calendarId))
                .thenReturn(false);

        when(calendarRepository.save(any(HolidayCalendar.class))).thenReturn(existing);
        when(locationRepository.findByTenantIdAndCalendarId(tenantId, calendarId))
                .thenReturn(List.of(new HolidayCalendarLocation(tenantId, calendarId, locationId)));
        when(holidayRepository.findByTenantIdAndCalendarIdOrderByFromDateAsc(tenantId, calendarId))
                .thenReturn(List.of());

        HolidayCalendarRequest updateReq = new HolidayCalendarRequest("New Name", false, Set.of(locationId));
        HolidayCalendarResponse res = service.updateCalendar(calendarId, updateReq);

        verify(locationRepository).deleteByTenantIdAndCalendarId(tenantId, calendarId);
        verify(locationRepository).flush();
        assertThat(res.name()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("updateCalendar on default calendar preserves isDefault when omitted or null")
    void updateCalendar_defaultCalendar_preservesDefaultWhenNull() {
        HolidayCalendar defaultCal = new HolidayCalendar(tenantId, "HQ Calendar", true);
        setId(defaultCal, calendarId);
        when(calendarRepository.findByTenantIdAndId(tenantId, calendarId)).thenReturn(Optional.of(defaultCal));
        when(calendarRepository.save(any(HolidayCalendar.class))).thenReturn(defaultCal);
        when(locationRepository.findByTenantIdAndCalendarId(tenantId, calendarId))
                .thenReturn(List.of());
        when(holidayRepository.findByTenantIdAndCalendarIdOrderByFromDateAsc(tenantId, calendarId))
                .thenReturn(List.of());

        // Request with isDefault = null (simulates rename or location add without isDefault in payload)
        HolidayCalendarRequest updateReq = new HolidayCalendarRequest("Renamed HQ Calendar", null, Set.of());
        HolidayCalendarResponse res = service.updateCalendar(calendarId, updateReq);

        assertThat(res.name()).isEqualTo("Renamed HQ Calendar");
        assertThat(res.isDefault()).isTrue();
    }

    @Test
    @DisplayName("updateCalendar on default calendar throws when explicitly attempting to demote isDefault to false")
    void updateCalendar_defaultCalendar_throwsWhenDemotedToFalse() {
        HolidayCalendar defaultCal = new HolidayCalendar(tenantId, "HQ Calendar", true);
        setId(defaultCal, calendarId);
        when(calendarRepository.findByTenantIdAndId(tenantId, calendarId)).thenReturn(Optional.of(defaultCal));

        HolidayCalendarRequest updateReq = new HolidayCalendarRequest("Renamed HQ Calendar", false, Set.of());

        assertThatThrownBy(() -> service.updateCalendar(calendarId, updateReq))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot remove default status from the default holiday calendar");
    }

    @Test
    @DisplayName("updateCalendar with isDefault true promotes calendar and demotes previous default")
    void updateCalendar_promoteToDefault_demotesPreviousDefault() {
        HolidayCalendar previousDefault = new HolidayCalendar(tenantId, "Old Default", true);
        UUID oldId = UUID.randomUUID();
        setId(previousDefault, oldId);

        HolidayCalendar targetCal = new HolidayCalendar(tenantId, "New Default", false);
        setId(targetCal, calendarId);

        when(calendarRepository.findByTenantIdAndId(tenantId, calendarId)).thenReturn(Optional.of(targetCal));
        when(calendarRepository.findByTenantIdAndIsDefaultTrue(tenantId)).thenReturn(Optional.of(previousDefault));
        when(calendarRepository.save(any(HolidayCalendar.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(locationRepository.findByTenantIdAndCalendarId(tenantId, calendarId))
                .thenReturn(List.of());
        when(holidayRepository.findByTenantIdAndCalendarIdOrderByFromDateAsc(tenantId, calendarId))
                .thenReturn(List.of());

        HolidayCalendarRequest updateReq = new HolidayCalendarRequest("New Default", true, Set.of());
        HolidayCalendarResponse res = service.updateCalendar(calendarId, updateReq);

        assertThat(previousDefault.isDefault()).isFalse();
        verify(calendarRepository).save(previousDefault);
        assertThat(res.isDefault()).isTrue();
    }

    @Test
    @DisplayName("F-2: the first calendar a tenant creates becomes its default even when not asked")
    void createCalendar_firstCalendar_becomesDefault() {
        when(calendarRepository.findByTenantIdAndIsDefaultTrue(tenantId)).thenReturn(Optional.empty());
        when(calendarRepository.save(any(HolidayCalendar.class))).thenAnswer(invocation -> {
            HolidayCalendar cal = invocation.getArgument(0);
            setId(cal, calendarId);
            return cal;
        });

        HolidayCalendarResponse res = service.createCalendar(new HolidayCalendarRequest("First", false, Set.of()));

        assertThat(res.isDefault()).isTrue();
    }

    @Test
    @DisplayName("F-2: a later calendar is not the default when the tenant already has one")
    void createCalendar_defaultExists_newCalendarIsNotDefault() {
        when(calendarRepository.findByTenantIdAndIsDefaultTrue(tenantId))
                .thenReturn(Optional.of(new HolidayCalendar(tenantId, "Existing Default", true)));
        when(calendarRepository.save(any(HolidayCalendar.class))).thenAnswer(invocation -> {
            HolidayCalendar cal = invocation.getArgument(0);
            setId(cal, calendarId);
            return cal;
        });

        HolidayCalendarResponse res = service.createCalendar(new HolidayCalendarRequest("Second", false, Set.of()));

        assertThat(res.isDefault()).isFalse();
    }

    @Test
    @DisplayName("F-4: updateCalendar with workLocationIds null leaves the location links unchanged")
    void updateCalendar_nullLocations_leavesLinksUnchanged() {
        HolidayCalendar existing = new HolidayCalendar(tenantId, "Old Name", false);
        setId(existing, calendarId);
        when(calendarRepository.findByTenantIdAndId(tenantId, calendarId)).thenReturn(Optional.of(existing));
        when(calendarRepository.save(any(HolidayCalendar.class))).thenReturn(existing);
        when(locationRepository.findByTenantIdAndCalendarId(tenantId, calendarId))
                .thenReturn(List.of(new HolidayCalendarLocation(tenantId, calendarId, locationId)));

        HolidayCalendarResponse res =
                service.updateCalendar(calendarId, new HolidayCalendarRequest("New Name", (Boolean) null, null));

        verify(locationRepository, never()).deleteByTenantIdAndCalendarId(any(), any());
        verify(locationRepository, never()).save(any());
        assertThat(res.workLocationIds()).containsExactly(locationId);
    }

    @Test
    @DisplayName("F-4: updateCalendar with an empty workLocationIds set clears every link")
    void updateCalendar_emptyLocations_clearsLinks() {
        HolidayCalendar existing = new HolidayCalendar(tenantId, "Old Name", false);
        setId(existing, calendarId);
        when(calendarRepository.findByTenantIdAndId(tenantId, calendarId)).thenReturn(Optional.of(existing));
        when(calendarRepository.save(any(HolidayCalendar.class))).thenReturn(existing);

        service.updateCalendar(calendarId, new HolidayCalendarRequest("New Name", (Boolean) null, Set.of()));

        verify(locationRepository).deleteByTenantIdAndCalendarId(tenantId, calendarId);
        verify(locationRepository, never()).save(any());
    }

    @Test
    @DisplayName("addHoliday validates date range and adds holiday")
    void addHoliday_success() {
        HolidayCalendar cal = new HolidayCalendar(tenantId, "Calendar", false);
        setId(cal, calendarId);
        when(calendarRepository.findByTenantIdAndId(tenantId, calendarId)).thenReturn(Optional.of(cal));

        LocalDate from = LocalDate.of(2026, 8, 15);
        LocalDate to = LocalDate.of(2026, 8, 15);
        HolidayRequest req = new HolidayRequest("Independence Day", from, to, false, "National holiday");

        when(holidayRepository.save(any(Holiday.class))).thenAnswer(invocation -> {
            Holiday h = invocation.getArgument(0);
            setId(h, holidayId);
            return h;
        });

        HolidayResponse res = service.addHoliday(calendarId, req);
        assertThat(res.id()).isEqualTo(holidayId);
        assertThat(res.name()).isEqualTo("Independence Day");
    }

    @Test
    @DisplayName("addHoliday throws IllegalArgumentException when to date is before from date")
    void addHoliday_invertedDates_throws() {
        HolidayCalendar cal = new HolidayCalendar(tenantId, "Calendar", false);
        setId(cal, calendarId);
        when(calendarRepository.findByTenantIdAndId(tenantId, calendarId)).thenReturn(Optional.of(cal));

        LocalDate from = LocalDate.of(2026, 8, 15);
        LocalDate to = LocalDate.of(2026, 8, 14);
        HolidayRequest req = new HolidayRequest("Invalid Holiday", from, to, false, null);

        assertThatThrownBy(() -> service.addHoliday(calendarId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("to date must be on or after from date");
    }

    @Test
    @DisplayName("deleteHoliday throws HolidayNotFoundException when holiday does not exist")
    void deleteHoliday_holidayNotFound_throws() {
        HolidayCalendar cal = new HolidayCalendar(tenantId, "Calendar", false);
        setId(cal, calendarId);
        when(calendarRepository.findByTenantIdAndId(tenantId, calendarId)).thenReturn(Optional.of(cal));
        when(holidayRepository.findByTenantIdAndCalendarIdAndId(tenantId, calendarId, holidayId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteHoliday(calendarId, holidayId))
                .isInstanceOf(HolidayCalendarService.HolidayNotFoundException.class)
                .hasMessageContaining("Holiday not found");
    }

    @Test
    @DisplayName("deleteHoliday deletes existing holiday successfully")
    void deleteHoliday_success() {
        HolidayCalendar cal = new HolidayCalendar(tenantId, "Calendar", false);
        setId(cal, calendarId);
        Holiday hol = new Holiday(
                tenantId,
                calendarId,
                "Republic Day",
                LocalDate.of(2026, 1, 26),
                LocalDate.of(2026, 1, 26),
                false,
                null);
        setId(hol, holidayId);

        when(calendarRepository.findByTenantIdAndId(tenantId, calendarId)).thenReturn(Optional.of(cal));
        when(holidayRepository.findByTenantIdAndCalendarIdAndId(tenantId, calendarId, holidayId))
                .thenReturn(Optional.of(hol));

        service.deleteHoliday(calendarId, holidayId);
        verify(holidayRepository).delete(hol);
    }
}
