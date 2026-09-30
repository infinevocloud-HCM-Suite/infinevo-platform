package com.infinevo.payroll.payrun;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * What a {@link PayLineContributor} is given for one included employee (W-29.2 §4): the run's
 * identity and dates, the employee, and the salary version in force at the period's end.
 */
public record PayRunEmployeeContext(
        UUID tenantId,
        UUID payrunId,
        UUID employeePayrunId,
        YearMonth period,
        LocalDate periodStart,
        LocalDate periodEnd,
        EmployeeResponse employee,
        SalaryVersionResponse version) {

    public PayRunEmployeeContext {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(payrunId, "payrunId must not be null");
        Objects.requireNonNull(employeePayrunId, "employeePayrunId must not be null");
        Objects.requireNonNull(period, "period must not be null");
        Objects.requireNonNull(periodStart, "periodStart must not be null");
        Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        Objects.requireNonNull(employee, "employee must not be null");
        Objects.requireNonNull(version, "version must not be null");
    }
}
