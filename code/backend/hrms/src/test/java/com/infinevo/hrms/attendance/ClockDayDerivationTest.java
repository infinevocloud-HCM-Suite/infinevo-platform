package com.infinevo.hrms.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.attendance.AttendanceService;
import com.infinevo.core.attendance.AttendanceSource;
import com.infinevo.core.attendance.AttendanceStatus;
import com.infinevo.core.attendance.ClockDayResult;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test for day attendance derivation from clock sessions (W-40.3 §7).
 */
class ClockDayDerivationTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID EMPLOYEE = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 4, 15);

    private ClockSessionRepository clockSessions;
    private AttendancePreferenceService preferenceService;
    private AttendanceService attendanceService;
    private ClockDayServiceImpl service;

    @BeforeEach
    void setUp() {
        clockSessions = mock(ClockSessionRepository.class);
        preferenceService = mock(AttendancePreferenceService.class);
        attendanceService = mock(AttendanceService.class);
        service = new ClockDayServiceImpl(clockSessions, preferenceService, attendanceService);
        TenantContext.set(TENANT);

        when(attendanceService.recordFromClock(any(), any(), any()))
                .thenAnswer(inv -> new ClockDayResult(true, inv.getArgument(2), AttendanceSource.CLOCK));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("EVERY_SESSION sums each session duration (540m -> PRESENT)")
    void everySession_sumsEachSessionDuration_marksPresent() {
        when(preferenceService.current())
                .thenReturn(new AttendancePreferenceResponse(
                        UUID.randomUUID(),
                        TENANT,
                        HoursCalculation.EVERY_SESSION,
                        new BigDecimal("9.00"),
                        new BigDecimal("4.50"),
                        null,
                        null,
                        true,
                        false,
                        Instant.now(),
                        Instant.now()));

        ClockSession s1 = new ClockSession(
                TENANT, EMPLOYEE, DATE, Instant.parse("2026-04-15T09:00:00Z"), SessionOrigin.CLOCK, "test");
        s1.close(Instant.parse("2026-04-15T13:00:00Z"), "test"); // 4h = 240m

        ClockSession s2 = new ClockSession(
                TENANT, EMPLOYEE, DATE, Instant.parse("2026-04-15T14:00:00Z"), SessionOrigin.CLOCK, "test");
        s2.close(Instant.parse("2026-04-15T19:00:00Z"), "test"); // 5h = 300m

        when(clockSessions
                        .findByTenantIdAndEmployeeIdAndAttendanceDateAndVoidedAtIsNullAndClockOutAtIsNotNullOrderByClockInAtAsc(
                                TENANT, EMPLOYEE, DATE))
                .thenReturn(List.of(s1, s2));

        ClockDayResponse response = service.rederive(EMPLOYEE, DATE);

        assertThat(response.date()).isEqualTo(DATE);
        assertThat(response.workedMinutes()).isEqualTo(540);
        assertThat(response.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(response.writtenToAttendance()).isTrue();

        verify(attendanceService).recordFromClock(EMPLOYEE, DATE, AttendanceStatus.PRESENT);
    }

    @Test
    @DisplayName("FIRST_IN_LAST_OUT computes last clock out minus first clock in (600m -> PRESENT)")
    void firstInLastOut_spansFirstToLast_marksPresent() {
        when(preferenceService.current())
                .thenReturn(new AttendancePreferenceResponse(
                        UUID.randomUUID(),
                        TENANT,
                        HoursCalculation.FIRST_IN_LAST_OUT,
                        new BigDecimal("9.00"),
                        new BigDecimal("4.50"),
                        null,
                        null,
                        true,
                        false,
                        Instant.now(),
                        Instant.now()));

        ClockSession s1 = new ClockSession(
                TENANT, EMPLOYEE, DATE, Instant.parse("2026-04-15T09:00:00Z"), SessionOrigin.CLOCK, "test");
        s1.close(Instant.parse("2026-04-15T13:00:00Z"), "test");

        ClockSession s2 = new ClockSession(
                TENANT, EMPLOYEE, DATE, Instant.parse("2026-04-15T14:00:00Z"), SessionOrigin.CLOCK, "test");
        s2.close(Instant.parse("2026-04-15T19:00:00Z"), "test"); // 09:00 to 19:00 = 10h = 600m

        when(clockSessions
                        .findByTenantIdAndEmployeeIdAndAttendanceDateAndVoidedAtIsNullAndClockOutAtIsNotNullOrderByClockInAtAsc(
                                TENANT, EMPLOYEE, DATE))
                .thenReturn(List.of(s1, s2));

        ClockDayResponse response = service.rederive(EMPLOYEE, DATE);

        assertThat(response.workedMinutes()).isEqualTo(600);
        assertThat(response.status()).isEqualTo(AttendanceStatus.PRESENT);
        verify(attendanceService).recordFromClock(EMPLOYEE, DATE, AttendanceStatus.PRESENT);
    }

    @Test
    @DisplayName("270 minutes is HALF_DAY, 269 minutes is ABSENT")
    void halfDayThresholdBoundary() {
        when(preferenceService.current())
                .thenReturn(new AttendancePreferenceResponse(
                        UUID.randomUUID(),
                        TENANT,
                        HoursCalculation.EVERY_SESSION,
                        new BigDecimal("9.00"),
                        new BigDecimal("4.50"), // 4.5h = 270m
                        null,
                        null,
                        true,
                        false,
                        Instant.now(),
                        Instant.now()));

        // Exactly 270 minutes
        ClockSession sHalf = new ClockSession(
                TENANT, EMPLOYEE, DATE, Instant.parse("2026-04-15T09:00:00Z"), SessionOrigin.CLOCK, "test");
        sHalf.close(Instant.parse("2026-04-15T13:30:00Z"), "test"); // 4.5h = 270m
        when(clockSessions
                        .findByTenantIdAndEmployeeIdAndAttendanceDateAndVoidedAtIsNullAndClockOutAtIsNotNullOrderByClockInAtAsc(
                                TENANT, EMPLOYEE, DATE))
                .thenReturn(List.of(sHalf));

        ClockDayResponse respHalf = service.rederive(EMPLOYEE, DATE);
        assertThat(respHalf.workedMinutes()).isEqualTo(270);
        assertThat(respHalf.status()).isEqualTo(AttendanceStatus.HALF_DAY);

        // 269 minutes
        ClockSession sUnder = new ClockSession(
                TENANT, EMPLOYEE, DATE, Instant.parse("2026-04-15T09:00:00Z"), SessionOrigin.CLOCK, "test");
        sUnder.close(Instant.parse("2026-04-15T13:29:00Z"), "test"); // 269m
        when(clockSessions
                        .findByTenantIdAndEmployeeIdAndAttendanceDateAndVoidedAtIsNullAndClockOutAtIsNotNullOrderByClockInAtAsc(
                                TENANT, EMPLOYEE, DATE))
                .thenReturn(List.of(sUnder));

        ClockDayResponse respUnder = service.rederive(EMPLOYEE, DATE);
        assertThat(respUnder.workedMinutes()).isEqualTo(269);
        assertThat(respUnder.status()).isEqualTo(AttendanceStatus.ABSENT);
    }

    @Test
    @DisplayName("Thresholds come from preference, not constants")
    void thresholdsFromPreference() {
        // Custom preference: 8h full day (480m), 4h half day (240m)
        when(preferenceService.current())
                .thenReturn(new AttendancePreferenceResponse(
                        UUID.randomUUID(),
                        TENANT,
                        HoursCalculation.EVERY_SESSION,
                        new BigDecimal("8.00"),
                        new BigDecimal("4.00"),
                        null,
                        null,
                        true,
                        false,
                        Instant.now(),
                        Instant.now()));

        ClockSession s = new ClockSession(
                TENANT, EMPLOYEE, DATE, Instant.parse("2026-04-15T09:00:00Z"), SessionOrigin.CLOCK, "test");
        s.close(Instant.parse("2026-04-15T17:00:00Z"), "test"); // 8h = 480m

        when(clockSessions
                        .findByTenantIdAndEmployeeIdAndAttendanceDateAndVoidedAtIsNullAndClockOutAtIsNotNullOrderByClockInAtAsc(
                                TENANT, EMPLOYEE, DATE))
                .thenReturn(List.of(s));

        ClockDayResponse resp = service.rederive(EMPLOYEE, DATE);
        assertThat(resp.workedMinutes()).isEqualTo(480);
        assertThat(resp.status()).isEqualTo(AttendanceStatus.PRESENT);
    }
}
