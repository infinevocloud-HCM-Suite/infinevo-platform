package com.infinevo.payroll.payrun;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Explains one employee's pay figure from its stamp (W-18.2 §3). Anyone holding {@code payroll.run.read}
 * reads any row; an employee holding only {@code payroll.payslip.read_own} reads their own and is refused
 * a colleague's (§13 decision 2). The controller's guard admits either code; ownership is decided here.
 */
@Service
public class PayFigureExplanationService {

    static final String READ_ANY = "payroll.run.read";
    static final String READ_OWN = "payroll.payslip.read_own";

    private final PayRunRepository payRuns;
    private final EmployeePayRunRepository employeePayRuns;
    private final EmployeePayRunLineRepository lines;
    private final EmployeeService employeeService;
    private final PermissionService permissionService;

    public PayFigureExplanationService(
            PayRunRepository payRuns,
            EmployeePayRunRepository employeePayRuns,
            EmployeePayRunLineRepository lines,
            EmployeeService employeeService,
            PermissionService permissionService) {
        this.payRuns = Objects.requireNonNull(payRuns, "payRuns must not be null");
        this.employeePayRuns = Objects.requireNonNull(employeePayRuns, "employeePayRuns must not be null");
        this.lines = Objects.requireNonNull(lines, "lines must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
    }

    /**
     * @throws PermissionDeniedException if the caller holds only {@code read_own} and {@code employeeId}
     *     is not theirs
     * @throws PayRunNotFoundException if the run, or the employee's row in it, is not in the bound tenant
     * @throws PayFigureNotComputedException if the row has no computed figure
     */
    @Transactional(readOnly = true)
    public PayFigureExplanationResponse explain(UUID payrunId, UUID employeeId) {
        Objects.requireNonNull(payrunId, "payrunId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        requireMayRead(employeeId);
        UUID tenantId = TenantContext.require();
        PayRun run = payRuns.findByIdAndTenantId(payrunId, tenantId)
                .orElseThrow(() -> new PayRunNotFoundException(payrunId));
        EmployeePayRun row = employeePayRuns
                .findByTenantIdAndPayrunIdAndEmployeeId(tenantId, payrunId, employeeId)
                .orElseThrow(() -> new PayRunNotFoundException(payrunId));
        // W-30.2: an off-cycle figure prices no days and carries no stamp; its five stamp fields read null.
        boolean unstamped = run.getRunType() == PayRunType.OFF_CYCLE
                && row.getComputedAt() != null
                && row.getComputationError() == null;
        PolicyStamp stamp = unstamped
                ? null
                : row.getStamp()
                        .filter(s -> row.getComputationError() == null)
                        .orElseThrow(() ->
                                new PayFigureNotComputedException(payrunId, employeeId, row.getComputationError()));

        BigDecimal lop = BigDecimal.ZERO.setScale(4);
        BigDecimal benefitLop = BigDecimal.ZERO.setScale(4);
        BigDecimal reversal = BigDecimal.ZERO.setScale(4);
        for (EmployeePayRunLine line :
                lines.findByTenantIdAndEmployeePayrunIdOrderBySortOrderAsc(tenantId, row.getId())) {
            if (line.getSource() != LineSource.LOP) {
                continue;
            }
            if (line.getLineKind() == LineKind.DEDUCTION) {
                lop = lop.add(line.getAmount());
            } else if (line.getLineKind() == LineKind.EARNING) {
                reversal = reversal.add(line.getAmount());
            } else if (line.getLineKind() == LineKind.BENEFIT
                    && line.getComponentCode().equals(LopLineContributor.LOP_BENEFIT_CODE)) {
                benefitLop = benefitLop.add(line.getAmount());
            }
        }

        return new PayFigureExplanationResponse(
                run.getId(),
                run.getPeriod().toString(),
                employeeId,
                row.getGrossEarnings(),
                row.getTotalDeductions(),
                row.getNetPay(),
                lop,
                benefitLop,
                reversal,
                row.getLopDays(),
                row.getUnpaidDays(),
                row.getPaidDays(),
                stamp == null ? null : stamp.policyId(),
                stamp == null ? null : stamp.workingDayBasis(),
                stamp == null ? null : stamp.divisor(),
                stamp == null ? null : stamp.payableDays(),
                stamp == null ? null : stamp.lopRounding(),
                row.getComputedAt());
    }

    private void requireMayRead(UUID employeeId) {
        if (permissionService.holds(READ_ANY)) {
            return;
        }
        EmployeeResponse caller =
                employeeService.currentEmployee().orElseThrow(() -> new PermissionDeniedException(READ_OWN));
        if (!caller.id().equals(employeeId)) {
            throw new PermissionDeniedException(READ_ANY);
        }
    }
}
