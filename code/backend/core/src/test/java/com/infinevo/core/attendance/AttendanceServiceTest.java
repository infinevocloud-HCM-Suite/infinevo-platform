package com.infinevo.core.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AttendanceServiceImpl} verifying all business rules from spec section 4 (W-39.1).
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    private AttendanceServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        service = new AttendanceServiceImpl(attendanceRepository, employeeRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Empty or null entries list is refused")
    void emptyOrNullEntriesRefused() {
        assertThatThrownBy(() -> service.upsert(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entries must not be empty");

        assertThatThrownBy(() -> service.upsert(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entries must not be empty");
    }

    @Test
    @DisplayName("Batch size exceeding 1000 items is refused")
    void batchExceeding1000Refused() {
        List<AttendanceEntry> entries = new ArrayList<>(1001);
        UUID empId = UUID.randomUUID();
        LocalDate baseDate = LocalDate.now().minusDays(1005);
        for (int i = 0; i < 1001; i++) {
            entries.add(new AttendanceEntry(empId, baseDate.plusDays(i), AttendanceStatus.PRESENT, null));
        }

        assertThatThrownBy(() -> service.upsert(entries))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entries cannot exceed 1000 items");
    }

    @Test
    @DisplayName("Date in the future is refused")
    void futureDateRefused() {
        UUID empId = UUID.randomUUID();
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<AttendanceEntry> entries =
                List.of(new AttendanceEntry(empId, tomorrow, AttendanceStatus.PRESENT, "Future day"));

        assertThatThrownBy(() -> service.upsert(entries))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Attendance date cannot be in the future");

        verify(attendanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Same (employeeId, date) twice in one request is refused")
    void duplicateEntryInRequestRefused() {
        UUID empId = UUID.randomUUID();
        LocalDate date = LocalDate.now().minusDays(1);
        List<AttendanceEntry> entries = List.of(
                new AttendanceEntry(empId, date, AttendanceStatus.PRESENT, "Morning"),
                new AttendanceEntry(empId, date, AttendanceStatus.HALF_DAY, "Afternoon"));

        assertThatThrownBy(() -> service.upsert(entries))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate entry for employee");

        verify(attendanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Employee not found in tenant or deleted is refused, naming the entry")
    void employeeNotFoundOrDeletedRefused() {
        UUID empId = UUID.randomUUID();
        LocalDate date = LocalDate.now().minusDays(2);
        List<AttendanceEntry> entries = List.of(new AttendanceEntry(empId, date, AttendanceStatus.PRESENT, "Present"));

        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(empId, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsert(entries))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(empId.toString());

        verify(attendanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("One bad entry rejects whole request, nothing saved (atomic)")
    void oneBadEntryRejectsWholeRequest() {
        UUID validEmpId = UUID.randomUUID();
        UUID invalidEmpId = UUID.randomUUID();
        LocalDate date1 = LocalDate.now().minusDays(1);
        LocalDate date2 = LocalDate.now().plusDays(1); // Future date!

        List<AttendanceEntry> entries = List.of(
                new AttendanceEntry(validEmpId, date1, AttendanceStatus.PRESENT, "Good"),
                new AttendanceEntry(invalidEmpId, date2, AttendanceStatus.ABSENT, "Bad"));

        assertThatThrownBy(() -> service.upsert(entries))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Attendance date cannot be in the future");

        verify(employeeRepository, never()).findByIdAndTenantIdAndDeletedFalse(any(), any());
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Existing row for (employeeId, date) is updated, not duplicated (idempotent)")
    void existingRowIsUpdatedIdempotently() {
        UUID empId = UUID.randomUUID();
        LocalDate date = LocalDate.now().minusDays(1);
        Employee employee = mockEmployee(empId);

        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(empId, TENANT_ID))
                .thenReturn(Optional.of(employee));

        Attendance existing = new Attendance(
                TENANT_ID, employee, date, AttendanceStatus.PRESENT, AttendanceSource.ADMIN, "Original", "actor");
        when(attendanceRepository.findByTenantIdAndEmployeeIdAndAttendanceDate(TENANT_ID, empId, date))
                .thenReturn(Optional.of(existing));
        when(attendanceRepository.save(existing)).thenAnswer(inv -> inv.getArgument(0));

        List<AttendanceEntry> entries =
                List.of(new AttendanceEntry(empId, date, AttendanceStatus.HALF_DAY, "Updated remarks"));

        List<AttendanceResponse> result = service.upsert(entries);

        assertThat(result).hasSize(1);
        AttendanceResponse resp = result.get(0);
        assertThat(resp.status()).isEqualTo(AttendanceStatus.HALF_DAY);
        assertThat(resp.remarks()).isEqualTo("Updated remarks");
        assertThat(existing.getStatus()).isEqualTo(AttendanceStatus.HALF_DAY);
        assertThat(existing.getRemarks()).isEqualTo("Updated remarks");
    }

    @Test
    @DisplayName("List with 'from' after 'to' is refused")
    void listFromAfterToRefused() {
        LocalDate from = LocalDate.of(2026, 3, 10);
        LocalDate to = LocalDate.of(2026, 3, 5);

        assertThatThrownBy(() -> service.list(from, to, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from date must not be after to date");
    }

    @Test
    @DisplayName("List with date span over 93 days is refused")
    void listSpanOver93DaysRefused() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = from.plusDays(94);

        assertThatThrownBy(() -> service.list(from, to, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Date range must not exceed 93 days");
    }

    @Test
    @DisplayName("List with null dates is refused")
    void listNullDatesRefused() {
        assertThatThrownBy(() -> service.list(null, LocalDate.now(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from and to dates are required");

        assertThatThrownBy(() -> service.list(LocalDate.now(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from and to dates are required");
    }

    @Test
    @DisplayName("Delete by non-existent id throws NotFoundException")
    void deleteNotFoundThrows() {
        UUID id = UUID.randomUUID();
        when(attendanceRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(AttendanceService.NotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    @DisplayName("Delete by valid id invokes repository delete and flush")
    void deleteValidIdSuccess() {
        UUID id = UUID.randomUUID();
        Employee employee = mockEmployee(UUID.randomUUID());
        Attendance attendance = new Attendance(
                TENANT_ID,
                employee,
                LocalDate.now().minusDays(1),
                AttendanceStatus.PRESENT,
                AttendanceSource.ADMIN,
                null,
                "user");

        when(attendanceRepository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(attendance));

        service.delete(id);

        verify(attendanceRepository).delete(attendance);
        verify(attendanceRepository).flush();
    }

    @Test
    @DisplayName("AttendanceQuery.days returns mapped records")
    void queryDaysReturnsMappedDays() {
        UUID empId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 5);
        Employee employee = mockEmployee(empId);

        Attendance a1 = new Attendance(
                TENANT_ID, employee, from, AttendanceStatus.PRESENT, AttendanceSource.ADMIN, "Present", "admin");
        Attendance a2 = new Attendance(
                TENANT_ID, employee, to, AttendanceStatus.ABSENT, AttendanceSource.ADMIN, "Sick", "admin");

        when(attendanceRepository.findByTenantIdAndEmployeeIdAndDateRangeAsc(TENANT_ID, empId, from, to))
                .thenReturn(List.of(a1, a2));

        List<AttendanceDay> days = service.days(empId, from, to);

        assertThat(days).hasSize(2);
        assertThat(days.get(0).date()).isEqualTo(from);
        assertThat(days.get(0).status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(days.get(1).date()).isEqualTo(to);
        assertThat(days.get(1).status()).isEqualTo(AttendanceStatus.ABSENT);
    }

    private static Employee mockEmployee(UUID id) {
        Employee employee = org.mockito.Mockito.mock(Employee.class);
        org.mockito.Mockito.lenient().when(employee.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(employee.getTenantId()).thenReturn(TENANT_ID);
        return employee;
    }
}
