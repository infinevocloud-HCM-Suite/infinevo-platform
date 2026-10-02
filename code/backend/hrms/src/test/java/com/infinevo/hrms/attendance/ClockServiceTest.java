package com.infinevo.hrms.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.attendance.AttendanceStatus;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

/**
 * Unit test for {@link ClockServiceImpl} (W-40.3 §7).
 */
class ClockServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID EMPLOYEE = UUID.randomUUID();
    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

    private ClockSessionRepository clockSessions;
    private ClockDayService clockDayService;
    private EmployeeService employeeService;
    private TenantClock tenantClock;
    private MutableClock clock;
    private ClockServiceImpl service;
    private EmployeeResponse currentEmployee;

    @BeforeEach
    void setUp() {
        clockSessions = mock(ClockSessionRepository.class);
        clockDayService = mock(ClockDayService.class);
        employeeService = mock(EmployeeService.class);
        tenantClock = mock(TenantClock.class);

        // Fixed clock start at 2026-04-15 09:00:00 UTC (14:30 IST)
        clock = new MutableClock(Instant.parse("2026-04-15T09:00:00Z"));

        service = new ClockServiceImpl(clockSessions, clockDayService, employeeService, tenantClock, clock);
        TenantContext.set(TENANT);

        currentEmployee = mock(EmployeeResponse.class);
        when(currentEmployee.id()).thenReturn(EMPLOYEE);
        when(currentEmployee.tenantId()).thenReturn(TENANT);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(currentEmployee));

        when(tenantClock.dateOf(any())).thenAnswer(inv -> {
            Instant inst = inv.getArgument(0);
            return inst.atZone(KOLKATA).toLocalDate();
        });
        when(tenantClock.today())
                .thenAnswer(inv -> clock.instant().atZone(KOLKATA).toLocalDate());
        when(clockSessions.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Clock-in opens a new session for today")
    void clockIn_opensNewSession() {
        when(clockSessions.findByTenantIdAndEmployeeIdAndClockOutAtIsNullAndVoidedAtIsNull(TENANT, EMPLOYEE))
                .thenReturn(Optional.empty());

        ClockSessionResponse response = service.clockIn();

        assertThat(response.employeeId()).isEqualTo(EMPLOYEE);
        assertThat(response.attendanceDate()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(response.clockInAt()).isEqualTo(clock.instant());
        assertThat(response.clockOutAt()).isNull();
        assertThat(response.origin()).isEqualTo(SessionOrigin.CLOCK);
        assertThat(response.voidedAt()).isNull();

        ArgumentCaptor<ClockSession> captor = ArgumentCaptor.forClass(ClockSession.class);
        verify(clockSessions).save(captor.capture());
        ClockSession saved = captor.getValue();
        assertThat(saved.getAttendanceDate()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(saved.getClockOutAt()).isNull();
    }

    @Test
    @DisplayName("Second clock-in while a session from today is open throws 409 Conflict")
    void secondClockInSameDay_throwsConflict() {
        ClockSession openToday = new ClockSession(
                TENANT, EMPLOYEE, LocalDate.of(2026, 4, 15), clock.instant(), SessionOrigin.CLOCK, "test");
        when(clockSessions.findByTenantIdAndEmployeeIdAndClockOutAtIsNullAndVoidedAtIsNull(TENANT, EMPLOYEE))
                .thenReturn(Optional.of(openToday));

        assertThatThrownBy(() -> service.clockIn())
                .isInstanceOf(ClockService.ClockConflictException.class)
                .hasMessageContaining("already open for today");

        verify(clockSessions, never()).save(any());
    }

    @Test
    @DisplayName("Clock-in on day 2 voids open session from day 1 with NOT_CLOCKED_OUT and opens day 2 session")
    void clockInNextDay_voidsStaleSession_andOpensNew() {
        Instant day1In = Instant.parse("2026-04-14T04:00:00Z"); // Day 1
        ClockSession staleSession =
                new ClockSession(TENANT, EMPLOYEE, LocalDate.of(2026, 4, 14), day1In, SessionOrigin.CLOCK, "test");

        when(clockSessions.findByTenantIdAndEmployeeIdAndClockOutAtIsNullAndVoidedAtIsNull(TENANT, EMPLOYEE))
                .thenReturn(Optional.of(staleSession));

        // Now is day 2 (2026-04-15)
        ClockSessionResponse response = service.clockIn();

        // Stale session was voided
        assertThat(staleSession.getVoidReason()).isEqualTo(VoidReason.NOT_CLOCKED_OUT);
        assertThat(staleSession.getVoidedAt()).isEqualTo(clock.instant());

        // New session for day 2 opened
        assertThat(response.attendanceDate()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(response.clockInAt()).isEqualTo(clock.instant());
        assertThat(response.voidedAt()).isNull();
    }

    @Test
    @DisplayName("Clock-out with no open session throws 409 Conflict")
    void clockOut_noOpenSession_throwsConflict() {
        when(clockSessions.findByTenantIdAndEmployeeIdAndClockOutAtIsNullAndVoidedAtIsNull(TENANT, EMPLOYEE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.clockOut())
                .isInstanceOf(ClockService.ClockConflictException.class)
                .hasMessageContaining("No active session open");

        verify(clockDayService, never()).rederive(any(), any());
    }

    @Test
    @DisplayName("Clock-out > 24h after clock-in throws 409 Conflict and leaves session open")
    void clockOut_over24Hours_throwsConflict_leavesOpen() {
        Instant inTime = clock.instant().minus(Duration.ofHours(24).plusSeconds(1));
        ClockSession openOver24h =
                new ClockSession(TENANT, EMPLOYEE, LocalDate.of(2026, 4, 14), inTime, SessionOrigin.CLOCK, "test");
        when(clockSessions.findByTenantIdAndEmployeeIdAndClockOutAtIsNullAndVoidedAtIsNull(TENANT, EMPLOYEE))
                .thenReturn(Optional.of(openOver24h));

        assertThatThrownBy(() -> service.clockOut())
                .isInstanceOf(ClockService.ClockConflictException.class)
                .hasMessageContaining("24 hours");

        assertThat(openOver24h.getClockOutAt()).isNull();
        verify(clockSessions, never()).save(any());
        verify(clockDayService, never()).rederive(any(), any());
    }

    @Test
    @DisplayName("Clock-out closes open session and calls ClockDayService.rederive")
    void clockOut_closesSession_andRederivesDay() {
        Instant inTime = clock.instant().minus(Duration.ofHours(4));
        ClockSession open =
                new ClockSession(TENANT, EMPLOYEE, LocalDate.of(2026, 4, 15), inTime, SessionOrigin.CLOCK, "test");
        when(clockSessions.findByTenantIdAndEmployeeIdAndClockOutAtIsNullAndVoidedAtIsNull(TENANT, EMPLOYEE))
                .thenReturn(Optional.of(open));

        when(clockDayService.rederive(EMPLOYEE, LocalDate.of(2026, 4, 15)))
                .thenReturn(new ClockDayResponse(LocalDate.of(2026, 4, 15), 240, AttendanceStatus.HALF_DAY, true));

        ClockOutResponse response = service.clockOut();

        assertThat(response.session().clockOutAt()).isEqualTo(clock.instant());
        assertThat(response.session().workedMinutes()).isEqualTo(240);
        assertThat(response.date()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(response.workedMinutes()).isEqualTo(240);
        assertThat(response.status()).isEqualTo(AttendanceStatus.HALF_DAY);
        assertThat(response.writtenToAttendance()).isTrue();

        verify(clockDayService).rederive(EMPLOYEE, LocalDate.of(2026, 4, 15));
    }

    @Test
    @DisplayName("A 23:30 clock-in in Asia/Kolkata closed at 06:00 next day belongs to the first date")
    void lateShiftAcrossMidnight_belongsToFirstDate() {
        // 23:30 IST on 2026-04-15 is 18:00 UTC on 2026-04-15
        clock.set(Instant.parse("2026-04-15T18:00:00Z"));
        when(clockSessions.findByTenantIdAndEmployeeIdAndClockOutAtIsNullAndVoidedAtIsNull(TENANT, EMPLOYEE))
                .thenReturn(Optional.empty());

        ClockSessionResponse inResp = service.clockIn();
        assertThat(inResp.attendanceDate()).isEqualTo(LocalDate.of(2026, 4, 15));

        // Advance clock to 06:00 IST on 2026-04-16 (00:30 UTC on 2026-04-16)
        clock.set(Instant.parse("2026-04-16T00:30:00Z"));

        ClockSession openSession = new ClockSession(
                TENANT,
                EMPLOYEE,
                LocalDate.of(2026, 4, 15),
                Instant.parse("2026-04-15T18:00:00Z"),
                SessionOrigin.CLOCK,
                "test");
        when(clockSessions.findByTenantIdAndEmployeeIdAndClockOutAtIsNullAndVoidedAtIsNull(TENANT, EMPLOYEE))
                .thenReturn(Optional.of(openSession));

        when(clockDayService.rederive(EMPLOYEE, LocalDate.of(2026, 4, 15)))
                .thenReturn(new ClockDayResponse(LocalDate.of(2026, 4, 15), 390, AttendanceStatus.HALF_DAY, true));

        ClockOutResponse outResp = service.clockOut();
        assertThat(outResp.date()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(outResp.session().attendanceDate()).isEqualTo(LocalDate.of(2026, 4, 15));
        verify(clockDayService).rederive(EMPLOYEE, LocalDate.of(2026, 4, 15));
    }

    @Test
    @DisplayName("Date range validations refuse from after to and span > 93 days")
    void dateRangeValidations() {
        LocalDate d1 = LocalDate.of(2026, 4, 15);
        LocalDate d2 = LocalDate.of(2026, 4, 10);
        assertThatThrownBy(() -> service.mySessions(d1, d2))
                .isInstanceOf(ClockService.ValidationException.class)
                .hasMessageContaining("from date must not be after to date");

        LocalDate dFar = LocalDate.of(2026, 1, 1);
        assertThatThrownBy(() -> service.mySessions(dFar, d1))
                .isInstanceOf(ClockService.ValidationException.class)
                .hasMessageContaining("must not exceed 93 days");
    }

    @Test
    @DisplayName("Caller not linked to employee profile throws AccessDeniedException (403)")
    void unlinkedUser_throwsAccessDeniedException() {
        when(employeeService.currentEmployee()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.clockIn())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No employee profile");
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
