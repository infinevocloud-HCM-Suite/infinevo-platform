package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LeaveImportRowValidatorTest {

    private EmployeeRepository employeeRepository;
    private LeaveTypeRepository leaveTypeRepository;
    private LeaveImportRowValidator validator;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID empId = UUID.randomUUID();
    private final UUID typeSlId = UUID.randomUUID();
    private final UUID typeInactiveId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        employeeRepository = mock(EmployeeRepository.class);
        leaveTypeRepository = mock(LeaveTypeRepository.class);
        validator = new LeaveImportRowValidator(employeeRepository, leaveTypeRepository);

        Employee emp = mock(Employee.class);
        when(emp.getId()).thenReturn(empId);
        when(emp.getTenantId()).thenReturn(tenantId);
        when(emp.getEmployeeNumber()).thenReturn("EMP-001");
        when(emp.getDateOfJoining()).thenReturn(LocalDate.of(2025, 1, 1));

        when(employeeRepository.findByTenantIdAndEmployeeNumberAndDeletedFalse(tenantId, "EMP-001"))
                .thenReturn(Optional.of(emp));
        when(employeeRepository.findByTenantIdAndEmployeeNumberAndDeletedFalse(tenantId, "EMP-UNKNOWN"))
                .thenReturn(Optional.empty());

        LeaveType activeSl = new LeaveType(
                tenantId, "SL", "Sick Leave", true, LeaveUnit.DAYS, false, LocalDate.of(2025, 1, 1), null, true);
        activeSl.setId(typeSlId);

        LeaveType inactiveType = new LeaveType(
                tenantId, "OLD", "Old Leave", false, LeaveUnit.DAYS, false, LocalDate.of(2025, 1, 1), null, false);
        inactiveType.setId(typeInactiveId);

        when(leaveTypeRepository.findByTenantIdOrderByCodeAsc(tenantId)).thenReturn(List.of(activeSl, inactiveType));
    }

    @Test
    @DisplayName("unknown employee number is rejected with EMPLOYEE_NOT_FOUND")
    void unknownEmployee() {
        LeaveImportRow row = new LeaveImportRow(2, "EMP-UNKNOWN", "SL", "10.00");
        var result = validator.validate(tenantId, List.of(row));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        LeaveImportError err = result.errors().get(0);
        assertThat(err.lineNumber()).isEqualTo(2);
        assertThat(err.employeeNumber()).isEqualTo("EMP-UNKNOWN");
        assertThat(err.reason()).isEqualTo("EMPLOYEE_NOT_FOUND");
    }

    @Test
    @DisplayName("unknown leave type code is rejected with LEAVE_TYPE_NOT_FOUND")
    void unknownLeaveType() {
        LeaveImportRow row = new LeaveImportRow(3, "EMP-001", "NONEXISTENT", "10.00");
        var result = validator.validate(tenantId, List.of(row));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        LeaveImportError err = result.errors().get(0);
        assertThat(err.lineNumber()).isEqualTo(3);
        assertThat(err.leaveTypeCode()).isEqualTo("NONEXISTENT");
        assertThat(err.reason()).isEqualTo("LEAVE_TYPE_NOT_FOUND");
    }

    @Test
    @DisplayName("inactive leave type is rejected with LEAVE_TYPE_INACTIVE")
    void inactiveLeaveType() {
        LeaveImportRow row = new LeaveImportRow(4, "EMP-001", "OLD", "10.00");
        var result = validator.validate(tenantId, List.of(row));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        LeaveImportError err = result.errors().get(0);
        assertThat(err.lineNumber()).isEqualTo(4);
        assertThat(err.leaveTypeCode()).isEqualTo("OLD");
        assertThat(err.reason()).isEqualTo("LEAVE_TYPE_INACTIVE");
    }

    @Test
    @DisplayName("unparseable days string is rejected with INVALID_DAYS")
    void unparseableDays() {
        LeaveImportRow row = new LeaveImportRow(5, "EMP-001", "SL", "abc");
        var result = validator.validate(tenantId, List.of(row));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        LeaveImportError err = result.errors().get(0);
        assertThat(err.lineNumber()).isEqualTo(5);
        assertThat(err.days()).isEqualTo("abc");
        assertThat(err.reason()).isEqualTo("INVALID_DAYS");
    }

    @Test
    @DisplayName("zero or negative days is rejected with INVALID_DAYS")
    void zeroOrNegativeDays() {
        LeaveImportRow rowZero = new LeaveImportRow(6, "EMP-001", "SL", "0");
        LeaveImportRow rowNegative = new LeaveImportRow(7, "EMP-001", "SL", "-5.00");
        var result = validator.validate(tenantId, List.of(rowZero, rowNegative));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0).reason()).isEqualTo("INVALID_DAYS");
        assertThat(result.errors().get(1).reason()).isEqualTo("INVALID_DAYS");
    }

    @Test
    @DisplayName("days with more than two decimals is rejected, not rounded")
    void tooPreciseDays() {
        LeaveImportRow row = new LeaveImportRow(8, "EMP-001", "SL", "12.555");
        var result = validator.validate(tenantId, List.of(row));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).singleElement().satisfies(err -> assertThat(err.reason())
                .isEqualTo("INVALID_DAYS"));
    }

    @Test
    @DisplayName("duplicate employee and leave type in the same file is rejected with DUPLICATE_IN_FILE")
    void duplicateInFile() {
        LeaveImportRow row1 = new LeaveImportRow(2, "EMP-001", "SL", "10.00");
        LeaveImportRow row2 = new LeaveImportRow(3, "EMP-001", "SL", "5.00");
        var result = validator.validate(tenantId, List.of(row1, row2));

        assertThat(result.validRows()).hasSize(1);
        assertThat(result.validRows().get(0).days()).isEqualByComparingTo("10.00");
        assertThat(result.errors()).hasSize(1);
        LeaveImportError err = result.errors().get(0);
        assertThat(err.lineNumber()).isEqualTo(3);
        assertThat(err.employeeNumber()).isEqualTo("EMP-001");
        assertThat(err.leaveTypeCode()).isEqualTo("SL");
        assertThat(err.reason()).isEqualTo("DUPLICATE_IN_FILE");
    }

    @Test
    @DisplayName("valid rows pass and are returned in validRows list")
    void validRowPasses() {
        LeaveImportRow row = new LeaveImportRow(2, "EMP-001", "SL", "12.50");
        var result = validator.validate(tenantId, List.of(row));

        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows()).hasSize(1);
        var valid = result.validRows().get(0);
        assertThat(valid.employee().getId()).isEqualTo(empId);
        assertThat(valid.leaveType().getId()).isEqualTo(typeSlId);
        assertThat(valid.days()).isEqualByComparingTo("12.50");
    }
}
