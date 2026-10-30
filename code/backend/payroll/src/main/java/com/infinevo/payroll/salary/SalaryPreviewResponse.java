package com.infinevo.payroll.salary;

import com.infinevo.payroll.statutory.lines.SalaryStatutoryItemResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response payload for a salary preview: the split a save would store, its statutory lines and a monthly
 * take-home estimate (W-75.1). Nothing behind it is saved.
 *
 * <p>{@code takeHomeMonthlyEstimate} = gross − employee EPF − employee ESI − PT. It is an estimate: no TDS and no
 * loss of pay. {@code ptEstimated} is {@code false} when PT applies but the employee has no work location to
 * resolve it from; PT is then left out of the estimate.
 */
public record SalaryPreviewResponse(
        UUID employeeId,
        LocalDate effectiveFrom,
        BigDecimal annualCtc,
        BigDecimal monthlyCtc,
        List<SalaryComponentItemResponse> earnings,
        List<SalaryComponentItemResponse> benefits,
        List<SalaryComponentItemResponse> reimbursements,
        List<SalaryStatutoryItemResponse> statutory,
        BigDecimal grossMonthly,
        List<MonthlyLine> employeeDeductionsMonthly,
        List<MonthlyLine> employerContributionsMonthly,
        BigDecimal takeHomeMonthlyEstimate,
        boolean ptEstimated,
        UUID balancingComponentId) {

    /** One monthly statutory amount, by component code ({@code EPF_EMPLOYEE}, {@code ESI_EMPLOYEE}, {@code PT}, …). */
    public record MonthlyLine(String componentCode, BigDecimal monthlyAmount, boolean includedInCtc) {}
}
