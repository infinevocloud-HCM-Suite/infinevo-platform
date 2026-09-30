package com.infinevo.core.leave;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates parsed leave import rows before allocation creation (W-16.4b, spec section 2 &amp; 4).
 */
@Component
@Transactional(readOnly = true)
public class LeaveImportRowValidator {

    public record ValidatedRow(LeaveImportRow row, Employee employee, LeaveType leaveType, BigDecimal days) {}

    public record ValidationResult(List<ValidatedRow> validRows, List<LeaveImportError> errors) {}

    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveImportRowValidator(EmployeeRepository employeeRepository, LeaveTypeRepository leaveTypeRepository) {
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
        this.leaveTypeRepository = Objects.requireNonNull(leaveTypeRepository, "leaveTypeRepository must not be null");
    }

    public ValidationResult validate(UUID tenantId, List<LeaveImportRow> rows) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(rows, "rows must not be null");

        List<LeaveType> tenantLeaveTypes = leaveTypeRepository.findByTenantIdOrderByCodeAsc(tenantId);
        Map<String, LeaveType> leaveTypeMap = new HashMap<>();
        for (LeaveType lt : tenantLeaveTypes) {
            leaveTypeMap.put(lt.getCode().toUpperCase(Locale.ROOT), lt);
        }

        Map<String, Optional<Employee>> employeeCache = new HashMap<>();
        Set<String> seenEmployeeTypePairs = new HashSet<>();

        List<ValidatedRow> validRows = new ArrayList<>();
        List<LeaveImportError> errors = new ArrayList<>();

        for (LeaveImportRow row : rows) {
            String empNum = row.employeeNumber() != null ? row.employeeNumber().trim() : "";
            String code = row.leaveTypeCode() != null ? row.leaveTypeCode().trim() : "";
            String daysStr = row.days() != null ? row.days().trim() : "";

            // Check employee
            if (empNum.isEmpty()) {
                errors.add(new LeaveImportError(
                        row.lineNumber(), row.employeeNumber(), row.leaveTypeCode(), row.days(), "EMPLOYEE_NOT_FOUND"));
                continue;
            }
            Optional<Employee> empOpt = employeeCache.computeIfAbsent(
                    empNum, num -> employeeRepository.findByTenantIdAndEmployeeNumberAndDeletedFalse(tenantId, num));
            if (empOpt.isEmpty()) {
                errors.add(new LeaveImportError(
                        row.lineNumber(), row.employeeNumber(), row.leaveTypeCode(), row.days(), "EMPLOYEE_NOT_FOUND"));
                continue;
            }

            // Check leave type
            if (code.isEmpty()) {
                errors.add(new LeaveImportError(
                        row.lineNumber(),
                        row.employeeNumber(),
                        row.leaveTypeCode(),
                        row.days(),
                        "LEAVE_TYPE_NOT_FOUND"));
                continue;
            }
            LeaveType leaveType = leaveTypeMap.get(code.toUpperCase(Locale.ROOT));
            if (leaveType == null) {
                errors.add(new LeaveImportError(
                        row.lineNumber(),
                        row.employeeNumber(),
                        row.leaveTypeCode(),
                        row.days(),
                        "LEAVE_TYPE_NOT_FOUND"));
                continue;
            }
            if (!leaveType.isActive()) {
                errors.add(new LeaveImportError(
                        row.lineNumber(),
                        row.employeeNumber(),
                        row.leaveTypeCode(),
                        row.days(),
                        "LEAVE_TYPE_INACTIVE"));
                continue;
            }

            // Check days
            BigDecimal parsedDays;
            try {
                parsedDays = new BigDecimal(daysStr).setScale(2, RoundingMode.HALF_UP);
                if (parsedDays.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add(new LeaveImportError(
                            row.lineNumber(), row.employeeNumber(), row.leaveTypeCode(), row.days(), "INVALID_DAYS"));
                    continue;
                }
            } catch (Exception e) {
                errors.add(new LeaveImportError(
                        row.lineNumber(), row.employeeNumber(), row.leaveTypeCode(), row.days(), "INVALID_DAYS"));
                continue;
            }

            // Check duplicate in file
            String pairKey = empNum.toUpperCase(Locale.ROOT) + "::" + code.toUpperCase(Locale.ROOT);
            if (!seenEmployeeTypePairs.add(pairKey)) {
                errors.add(new LeaveImportError(
                        row.lineNumber(), row.employeeNumber(), row.leaveTypeCode(), row.days(), "DUPLICATE_IN_FILE"));
                continue;
            }

            validRows.add(new ValidatedRow(row, empOpt.get(), leaveType, parsedDays));
        }

        return new ValidationResult(validRows, errors);
    }
}
