package com.infinevo.payroll.payrun;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.lop.LopRounding;
import com.infinevo.core.lop.WorkingDayBasisResponse;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.payroll.salary.StatutoryProfileResponse;
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
 *
 * <p>W-30.2: the run type. On an off-cycle run the pay inputs are the employee's slice of the
 * inputs tagged with the run, the day figures are zero, and {@code version} and {@code basis} may be
 * null — the structure and loss-of-pay contributors produce nothing there.
 *
 * <p>W-31.4: the statutory eligibility profile.
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
        List<PayLine> priorLines,
        PayRunType runType,
        StatutoryProfileResponse statutoryProfile) {

    /** A {@code REGULAR} run's context — the shape before W-30.2. */
    public PayRunEmployeeContext(
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
        this(
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
                priorLines,
                PayRunType.REGULAR,
                null);
    }

    /** The shape before W-31.4. */
    public PayRunEmployeeContext(
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
            List<PayLine> priorLines,
            PayRunType runType) {
        this(
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
                priorLines,
                runType,
                null);
    }

    public PayRunEmployeeContext {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(payrunId, "payrunId must not be null");
        Objects.requireNonNull(employeePayrunId, "employeePayrunId must not be null");
        Objects.requireNonNull(period, "period must not be null");
        Objects.requireNonNull(periodStart, "periodStart must not be null");
        Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        Objects.requireNonNull(employee, "employee must not be null");
        Objects.requireNonNull(days, "days must not be null");
        runType = runType == null ? PayRunType.REGULAR : runType;
        if (runType == PayRunType.REGULAR) {
            Objects.requireNonNull(version, "version must not be null");
            Objects.requireNonNull(basis, "basis must not be null");
        }
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
                lines,
                runType,
                statutoryProfile);
    }

    /** The same context with the employee's statutory profile. */
    public PayRunEmployeeContext withStatutoryProfile(StatutoryProfileResponse profile) {
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
                priorLines,
                runType,
                profile);
    }
}
