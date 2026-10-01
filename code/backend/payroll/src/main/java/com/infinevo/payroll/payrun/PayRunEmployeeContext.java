package com.infinevo.payroll.payrun;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.lop.LopRounding;
import com.infinevo.core.lop.WorkingDayBasisResponse;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * What a {@link PayLineContributor} is given for one included employee (W-29.2 §4, W-29.3 §4): the
 * run's identity and dates; the employee, with joining and termination dates; the salary version in
 * force at the period's end; the working-day basis and the policy's rounding (W-18.1); the employee's
 * slice of the period's pay inputs (W-19), read once per run; the day figures; the ids of the
 * catalogue components flagged {@code is_pro_rata}; and the lines earlier contributors produced.
 */
public record PayRunEmployeeContext(
        UUID tenantId,
        UUID payrunId,
        UUID employeePayrunId,
        YearMonth period,
        LocalDate periodStart,
        LocalDate periodEnd,
        EmployeeResponse employee,
        SalaryVersionResponse version,
        WorkingDayBasisResponse basis,
        LopRounding lopRounding,
        List<PayInputResponse> payInputs,
        PayRunDays days,
        Set<UUID> proRataComponentIds,
        List<PayLine> priorLines) {

    public PayRunEmployeeContext {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(payrunId, "payrunId must not be null");
        Objects.requireNonNull(employeePayrunId, "employeePayrunId must not be null");
        Objects.requireNonNull(period, "period must not be null");
        Objects.requireNonNull(periodStart, "periodStart must not be null");
        Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        Objects.requireNonNull(employee, "employee must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(basis, "basis must not be null");
        Objects.requireNonNull(days, "days must not be null");
        payInputs = payInputs == null ? List.of() : List.copyOf(payInputs);
        proRataComponentIds = proRataComponentIds == null ? Set.of() : Set.copyOf(proRataComponentIds);
        priorLines = priorLines == null ? List.of() : List.copyOf(priorLines);
    }

    /** The same context, carrying the lines the contributors before this one produced. */
    public PayRunEmployeeContext withPriorLines(List<PayLine> lines) {
        return new PayRunEmployeeContext(
                tenantId,
                payrunId,
                employeePayrunId,
                period,
                periodStart,
                periodEnd,
                employee,
                version,
                basis,
                lopRounding,
                payInputs,
                days,
                proRataComponentIds,
                lines);
    }
}
