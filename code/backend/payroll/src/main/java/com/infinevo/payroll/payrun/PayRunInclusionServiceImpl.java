package com.infinevo.payroll.payrun;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.employee.detail.EmployeeBankService;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryNotFoundException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * W-29.1 §3. Salary first, then bank: an employee with neither is {@code NO_SALARY}. The personal
 * section test of the legacy filter ({@code EmployeePayRunServiceImpl.java:190}) is not ported —
 * nothing in the pay calculation reads it.
 *
 * <p>Deliberately not {@code @Transactional}: {@code versionInForce} signals "no salary" by throwing,
 * and a throw through a participating transaction would mark the caller's transaction
 * rollback-only. Each seam call runs in its own read-only transaction instead.
 */
@Service
public class PayRunInclusionServiceImpl implements PayRunInclusionService {

    private final EmployeeSalaryService salaryService;
    private final EmployeeBankService bankService;

    public PayRunInclusionServiceImpl(EmployeeSalaryService salaryService, EmployeeBankService bankService) {
        this.salaryService = Objects.requireNonNull(salaryService, "salaryService must not be null");
        this.bankService = Objects.requireNonNull(bankService, "bankService must not be null");
    }

    @Override
    public List<InclusionDecision> decide(
            UUID tenantId, List<EmployeeResponse> employees, LocalDate periodStart, LocalDate periodEnd) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(employees, "employees must not be null");
        List<InclusionDecision> decisions = new ArrayList<>(employees.size());
        for (EmployeeResponse employee : employees) {
            if (!isConsidered(employee, periodStart, periodEnd)) {
                continue;
            }
            decisions.add(decideOne(tenantId, employee.id(), periodEnd));
        }
        return decisions;
    }

    private InclusionDecision decideOne(UUID tenantId, UUID employeeId, LocalDate periodEnd) {
        UUID salaryVersionId;
        try {
            salaryVersionId = salaryService
                    .versionInForce(tenantId, employeeId, periodEnd)
                    .id();
        } catch (SalaryNotFoundException e) {
            return InclusionDecision.skipped(employeeId, SkipReason.NO_SALARY);
        }
        if (bankService.find(employeeId).isEmpty()) {
            return InclusionDecision.skipped(employeeId, SkipReason.NO_BANK_DETAILS);
        }
        return InclusionDecision.included(employeeId, salaryVersionId);
    }

    /**
     * Employed on any day of the period: joined on or before its end, and either {@code ACTIVE} or
     * {@code TERMINATED} on or after its start. {@code SUSPENDED} is out. The same rule as
     * {@code EmployeeRepository.findEmployedBetween}, held here too so the decision never depends on
     * the caller having filtered.
     */
    static boolean isConsidered(EmployeeResponse employee, LocalDate periodStart, LocalDate periodEnd) {
        Objects.requireNonNull(employee, "employee must not be null");
        Objects.requireNonNull(periodStart, "periodStart must not be null");
        Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        if (employee.dateOfJoining() == null || employee.dateOfJoining().isAfter(periodEnd)) {
            return false;
        }
        if (employee.status() == EmploymentStatus.ACTIVE) {
            return true;
        }
        return employee.status() == EmploymentStatus.TERMINATED
                && employee.terminationDate() != null
                && !employee.terminationDate().isBefore(periodStart);
    }
}
