package com.infinevo.payroll.payslip;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.payrun.EmployeePayRun;
import com.infinevo.payroll.payrun.EmployeePayRunLine;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.EmployeePayRunRepository;
import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.LineKind;
import com.infinevo.payroll.payrun.PayRun;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link PayslipService} (W-36.2 §4).
 * Renders payslips dynamically from the pay run rows; nothing is ever written to storage.
 */
@Service
public class PayslipServiceImpl implements PayslipService {

    private final EmployeePayRunRepository employeePayRuns;
    private final PayRunRepository payRuns;
    private final EmployeePayRunLineRepository lines;
    private final EmployeeService employeeService;
    private final JdbcTemplate jdbcTemplate;

    public PayslipServiceImpl(
            EmployeePayRunRepository employeePayRuns,
            PayRunRepository payRuns,
            EmployeePayRunLineRepository lines,
            EmployeeService employeeService,
            JdbcTemplate jdbcTemplate) {
        this.employeePayRuns = Objects.requireNonNull(employeePayRuns, "employeePayRuns must not be null");
        this.payRuns = Objects.requireNonNull(payRuns, "payRuns must not be null");
        this.lines = Objects.requireNonNull(lines, "lines must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public PayslipResponse render(UUID tenantId, UUID employeePayrunId, Instant linkExpiresAt) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(employeePayrunId, "employeePayrunId must not be null");

        EmployeePayRun employeePayRun = employeePayRuns
                .findByIdAndTenantId(employeePayrunId, tenantId)
                .orElseThrow(() -> new PayslipNotFoundException("Employee pay run not found: " + employeePayrunId));

        if (employeePayRun.getInclusionStatus() == InclusionStatus.SKIPPED) {
            throw new PayslipNotFoundException("Employee was skipped in pay run");
        }

        PayRun payRun = payRuns.findByIdAndTenantId(employeePayRun.getPayrunId(), tenantId)
                .orElseThrow(() -> new PayslipNotFoundException("Pay run not found: " + employeePayRun.getPayrunId()));

        if (payRun.getStatus() != PayRunStatus.PAID) {
            throw new PayslipNotFoundException("Pay run is not paid: " + payRun.getStatus());
        }

        return buildPayslipResponse(tenantId, payRun, employeePayRun, linkExpiresAt);
    }

    @Override
    @Transactional(readOnly = true)
    public PayslipResponse forOfficer(UUID payrunId, UUID employeeId) {
        Objects.requireNonNull(payrunId, "payrunId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        UUID tenantId = TenantContext.require();

        PayRun payRun = payRuns.findByIdAndTenantId(payrunId, tenantId)
                .orElseThrow(() -> new PayslipNotFoundException("Pay run not found: " + payrunId));

        if (payRun.getStatus() == PayRunStatus.DRAFT
                || payRun.getStatus() == PayRunStatus.LOCKED
                || payRun.getStatus() == PayRunStatus.COMPUTING
                || payRun.getStatus() == PayRunStatus.FAILED) {
            throw new PayslipConflictException("Pay run " + payrunId + " is in status " + payRun.getStatus()
                    + ", payslips are only available for COMPUTED, APPROVED or PAID runs");
        }

        EmployeePayRun employeePayRun = employeePayRuns
                .findByTenantIdAndPayrunIdAndEmployeeId(tenantId, payrunId, employeeId)
                .orElseThrow(
                        () -> new PayslipNotFoundException("Employee " + employeeId + " not in pay run " + payrunId));

        if (employeePayRun.getInclusionStatus() == InclusionStatus.SKIPPED) {
            throw new PayslipNotFoundException("Employee " + employeeId + " was skipped in pay run " + payrunId);
        }

        return buildPayslipResponse(tenantId, payRun, employeePayRun, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PayslipSummaryResponse> listOwn(Pageable pageable) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse employee = employeeService
                .currentEmployee()
                .orElseThrow(() -> new PayslipForbiddenException("No employee linked to current user"));

        Page<EmployeePayRun> page = employeePayRuns.findOwnPaidRuns(tenantId, employee.id(), pageable);
        return page.map(r -> {
            PayRun pr = payRuns.findByIdAndTenantId(r.getPayrunId(), tenantId).orElse(null);
            String period = pr != null ? pr.getPeriod().toString() : null;
            LocalDate paidOn = pr != null ? pr.getPaidOn() : null;
            return new PayslipSummaryResponse(r.getPayrunId(), period, paidOn, scale2(r.getNetPay()));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PayslipResponse own(UUID payrunId) {
        Objects.requireNonNull(payrunId, "payrunId must not be null");
        UUID tenantId = TenantContext.require();
        EmployeeResponse employee = employeeService
                .currentEmployee()
                .orElseThrow(() -> new PayslipForbiddenException("No employee linked to current user"));

        PayRun payRun = payRuns.findByIdAndTenantId(payrunId, tenantId)
                .orElseThrow(() -> new PayslipNotFoundException("Pay run not found: " + payrunId));

        if (payRun.getStatus() != PayRunStatus.PAID) {
            throw new PayslipNotFoundException(
                    "Payslip is only available for PAID runs, status was " + payRun.getStatus());
        }

        EmployeePayRun employeePayRun = employeePayRuns
                .findByTenantIdAndPayrunIdAndEmployeeId(tenantId, payrunId, employee.id())
                .orElseThrow(() ->
                        new PayslipNotFoundException("Employee " + employee.id() + " not in pay run " + payrunId));

        if (employeePayRun.getInclusionStatus() == InclusionStatus.SKIPPED) {
            throw new PayslipNotFoundException("Employee was skipped in pay run " + payrunId);
        }

        return buildPayslipResponse(tenantId, payRun, employeePayRun, null);
    }

    private PayslipResponse buildPayslipResponse(
            UUID tenantId, PayRun payRun, EmployeePayRun employeePayRun, Instant linkExpiresAt) {
        EmployeeResponse employee = employeeService.get(employeePayRun.getEmployeeId());
        String departmentName = resolveDepartment(employee.departmentId());
        String designationName = resolveDesignation(employee.designationId());
        String employerName = resolveTenantName(tenantId);

        List<EmployeePayRunLine> runLines =
                lines.findByTenantIdAndEmployeePayrunIdOrderBySortOrderAsc(tenantId, employeePayRun.getId());

        List<PayslipLineResponse> earnings = new ArrayList<>();
        List<PayslipLineResponse> deductions = new ArrayList<>();
        List<PayslipLineResponse> reimbursements = new ArrayList<>();
        List<PayslipLineResponse> benefits = new ArrayList<>();

        for (EmployeePayRunLine line : runLines) {
            PayslipLineResponse lineResp = new PayslipLineResponse(
                    line.getComponentCode(),
                    line.getComponentName(),
                    scale2(line.getAmount()),
                    line.getSource() != null ? line.getSource().name() : null,
                    line.isTaxable());
            if (line.getLineKind() == LineKind.EARNING) {
                earnings.add(lineResp);
            } else if (line.getLineKind() == LineKind.DEDUCTION) {
                deductions.add(lineResp);
            } else if (line.getLineKind() == LineKind.REIMBURSEMENT) {
                reimbursements.add(lineResp);
            } else if (line.getLineKind() == LineKind.BENEFIT) {
                benefits.add(lineResp);
            }
        }

        PayslipResponse.RunSummary runSummary = new PayslipResponse.RunSummary(
                payRun.getId(),
                payRun.getRunType() != null ? payRun.getRunType().name() : "REGULAR",
                payRun.getPeriod().toString(),
                payRun.getPayDate(),
                payRun.getPaidOn(),
                payRun.getStatus() != null ? payRun.getStatus().name() : null);

        PayslipResponse.EmployerSummary employerSummary = new PayslipResponse.EmployerSummary(employerName);

        PayslipResponse.EmployeeSummary employeeSummary = new PayslipResponse.EmployeeSummary(
                employee.id(),
                employee.employeeNumber(),
                formatEmployeeName(employee),
                designationName,
                departmentName,
                employee.dateOfJoining());

        PayslipResponse.DaysSummary daysSummary = new PayslipResponse.DaysSummary(
                scale2(employeePayRun.getPayableDays()),
                scale2(employeePayRun.getPaidDays()),
                scale2(employeePayRun.getLopDays()),
                scale2(employeePayRun.getUnpaidDays()));

        PayslipResponse.TotalsSummary totalsSummary = new PayslipResponse.TotalsSummary(
                scale2(employeePayRun.getGrossEarnings()),
                scale2(employeePayRun.getTotalDeductions()),
                scale2(employeePayRun.getTotalReimbursements()),
                scale2(employeePayRun.getTotalBenefits()),
                scale2(employeePayRun.getNetPay()));

        PayslipResponse.LinkSummary linkSummary =
                linkExpiresAt != null ? new PayslipResponse.LinkSummary(linkExpiresAt) : null;

        return new PayslipResponse(
                runSummary,
                employerSummary,
                employeeSummary,
                daysSummary,
                earnings,
                deductions,
                reimbursements,
                benefits,
                totalsSummary,
                linkSummary);
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String formatEmployeeName(EmployeeResponse employee) {
        StringBuilder sb = new StringBuilder();
        if (employee.firstName() != null && !employee.firstName().isBlank()) {
            sb.append(employee.firstName().trim());
        }
        if (employee.middleName() != null && !employee.middleName().isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(employee.middleName().trim());
        }
        if (employee.lastName() != null && !employee.lastName().isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(employee.lastName().trim());
        }
        return sb.toString();
    }

    private String resolveDepartment(UUID departmentId) {
        if (departmentId == null) return null;
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT name FROM core.department WHERE id = ?", String.class, departmentId);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveDesignation(UUID designationId) {
        if (designationId == null) return null;
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT name FROM core.designation WHERE id = ?", String.class, designationId);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveTenantName(UUID tenantId) {
        if (tenantId == null) return "Infinevo";
        try {
            String name = jdbcTemplate.queryForObject(
                    "SELECT name FROM core.tenant WHERE tenant_id = ?", String.class, tenantId);
            return name != null && !name.isBlank() ? name : "Infinevo";
        } catch (Exception e) {
            return "Infinevo";
        }
    }
}
