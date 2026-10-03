package com.infinevo.core.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link AttendanceService#recordFromClock} (W-40.2, spec section 7).
 *
 * <p>Covers:
 * <ul>
 *   <li>The three rows of the precedence table (no row, existing CLOCK, existing ADMIN).
 *   <li>The three refusal rules (employee not found/deleted, date in future, status null).
 * </ul>
 */
class AttendanceClockWriteTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EMPLOYEE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    private AttendanceRepository attendanceRepository;
    private EmployeeRepository employeeRepository;
    private TenantClock tenantClock;
    private AttendanceServiceImpl service;
    private Employee employee;

    @BeforeEach
    void setUp() {
        attendanceRepository = mock(AttendanceRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        tenantClock = mock(TenantClock.class);

        when(tenantClock.today()).thenReturn(TODAY);

        employee = mock(Employee.class);
        when(employee.getId()).thenReturn(EMPLOYEE_ID);
        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(EMPLOYEE_ID, TENANT_ID))
                .thenReturn(Optional.of(employee));

        TenantContext.set(TENANT_ID);
        service = new AttendanceServiceImpl(attendanceRepository, employeeRepository, tenantClock);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── Precedence Rule Tests ──

    @Test
    @DisplayName("Precedence row 1: When no row exists, inserts with source=CLOCK and written=true")
    void noRowInsertsClockRecord() {
        LocalDate date = TODAY.minusDays(1);
        when(attendanceRepository.findByTenantIdAndEmployeeIdAndAttendanceDate(TENANT_ID, EMPLOYEE_ID, date))
                .thenReturn(Optional.empty());

        ClockDayResult result = service.recordFromClock(EMPLOYEE_ID, date, AttendanceStatus.PRESENT);

        assertThat(result.written()).isTrue();
        assertThat(result.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(result.source()).isEqualTo(AttendanceSource.CLOCK);

        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        verify(attendanceRepository).save(captor.capture());
        Attendance saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.getAttendanceDate()).isEqualTo(date);
        assertThat(saved.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(saved.getSource()).isEqualTo(AttendanceSource.CLOCK);
        assertThat(saved.getRemarks()).isNull();
    }

    @Test
    @DisplayName("Precedence row 2: When row with source=CLOCK exists, updates status and returns written=true")
    void existingClockRowUpdatesStatus() {
        LocalDate date = TODAY.minusDays(1);
        Attendance existing = new Attendance(
                TENANT_ID, employee, date, AttendanceStatus.HALF_DAY, AttendanceSource.CLOCK, "morning", "system");
        when(attendanceRepository.findByTenantIdAndEmployeeIdAndAttendanceDate(TENANT_ID, EMPLOYEE_ID, date))
                .thenReturn(Optional.of(existing));

        ClockDayResult result = service.recordFromClock(EMPLOYEE_ID, date, AttendanceStatus.PRESENT);

        assertThat(result.written()).isTrue();
        assertThat(result.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(result.source()).isEqualTo(AttendanceSource.CLOCK);

        verify(attendanceRepository).save(existing);
        assertThat(existing.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(existing.getSource()).isEqualTo(AttendanceSource.CLOCK);
    }

    @Test
    @DisplayName(
            "Precedence row 3: When row with source=ADMIN exists, does nothing and returns written=false with ADMIN status")
    void existingAdminRowLeftUnchanged() {
        LocalDate date = TODAY.minusDays(1);
        Attendance existing = new Attendance(
                TENANT_ID, employee, date, AttendanceStatus.ABSENT, AttendanceSource.ADMIN, "Admin entered", "admin");
        when(attendanceRepository.findByTenantIdAndEmployeeIdAndAttendanceDate(TENANT_ID, EMPLOYEE_ID, date))
                .thenReturn(Optional.of(existing));

        ClockDayResult result = service.recordFromClock(EMPLOYEE_ID, date, AttendanceStatus.PRESENT);

        assertThat(result.written()).isFalse();
        assertThat(result.status()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(result.source()).isEqualTo(AttendanceSource.ADMIN);

        verify(attendanceRepository, never()).save(any());
        assertThat(existing.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(existing.getSource()).isEqualTo(AttendanceSource.ADMIN);
    }

    // ── Refusal Rule Tests ──

    @Test
    @DisplayName("Refusal rule 1: Employee not found in tenant or soft-deleted throws IllegalArgumentException")
    void employeeNotFoundOrDeletedThrows() {
        UUID unknownEmployee = UUID.randomUUID();
        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(unknownEmployee, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordFromClock(unknownEmployee, TODAY, AttendanceStatus.PRESENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Employee not found in tenant or is deleted: " + unknownEmployee);
    }

    @Test
    @DisplayName("Refusal rule 2: Date after TenantClock.today() throws IllegalArgumentException")
    void futureDateThrows() {
        LocalDate tomorrow = TODAY.plusDays(1);

        assertThatThrownBy(() -> service.recordFromClock(EMPLOYEE_ID, tomorrow, AttendanceStatus.PRESENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Attendance date cannot be in the future: " + tomorrow);
    }

    @Test
    @DisplayName("Refusal rule 3: Null status throws IllegalArgumentException")
    void nullStatusThrows() {
        assertThatThrownBy(() -> service.recordFromClock(EMPLOYEE_ID, TODAY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status is required");
    }

    @Test
    @DisplayName("Refusal: Null employeeId or date throws IllegalArgumentException")
    void nullEmployeeOrDateThrows() {
        assertThatThrownBy(() -> service.recordFromClock(null, TODAY, AttendanceStatus.PRESENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("employeeId is required");

        assertThatThrownBy(() -> service.recordFromClock(EMPLOYEE_ID, null, AttendanceStatus.PRESENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date is required");
    }
}
