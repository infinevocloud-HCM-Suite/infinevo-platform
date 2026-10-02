package com.infinevo.payroll.priorpayroll;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link PriorPayrollService} (W-38.1 §4).
 */
@Service
public class PriorPayrollServiceImpl implements PriorPayrollService {

    private final PriorPayrollMonthRepository monthRepository;
    private final EmployeeRepository employeeRepository;
    private final PayRunRepository payRunRepository;
    private final PriorPayrollRowValidator rowValidator;

    public PriorPayrollServiceImpl(
            PriorPayrollMonthRepository monthRepository,
            EmployeeRepository employeeRepository,
            PayRunRepository payRunRepository,
            PriorPayrollRowValidator rowValidator) {
        this.monthRepository = Objects.requireNonNull(monthRepository, "monthRepository must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
        this.payRunRepository = Objects.requireNonNull(payRunRepository, "payRunRepository must not be null");
        this.rowValidator = Objects.requireNonNull(rowValidator, "rowValidator must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PriorPayrollMonthResponse> months(String financialYear, UUID employeeId, Pageable pageable) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(financialYear, "financialYear must not be null");
        Objects.requireNonNull(pageable, "pageable must not be null");

        FinancialYear fy = FinancialYear.parse(financialYear);
        String fromPeriod = String.format("%04d-04", fy.startYear());
        String toPeriod = String.format("%04d-03", fy.endYear());

        Page<PriorPayrollMonth> page;
        if (employeeId != null) {
            page = monthRepository.findByTenantIdAndEmployeeIdAndPeriodBetween(
                    tenantId, employeeId, fromPeriod, toPeriod, pageable);
        } else {
            page = monthRepository.findByTenantIdAndPeriodBetween(tenantId, fromPeriod, toPeriod, pageable);
        }

        Map<UUID, Employee> employeeCache = new HashMap<>();
        return page.map(m -> {
            Employee emp = employeeCache.computeIfAbsent(m.getEmployeeId(), eId -> employeeRepository
                    .findByIdAndTenantIdAndDeletedFalse(eId, tenantId)
                    .orElse(null));
            String empNum = emp != null ? emp.getEmployeeNumber() : "";
            String empName = "";
            if (emp != null) {
                String first = emp.getFirstName() != null ? emp.getFirstName().trim() : "";
                String last = emp.getLastName() != null ? emp.getLastName().trim() : "";
                empName = (first + (last.isEmpty() ? "" : " " + last)).trim();
            }
            return PriorPayrollMonthResponse.from(m, empNum, empName);
        });
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(id, "id must not be null");
        PriorPayrollMonth month = monthRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new PriorPayrollNotFoundException(id));
        monthRepository.delete(month);
    }

    @Override
    @Transactional(readOnly = true)
    public PriorPayrollStatusResponse status(String financialYear) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(financialYear, "financialYear must not be null");

        FinancialYear fy = FinancialYear.parse(financialYear);
        String fromPeriod = String.format("%04d-04", fy.startYear());
        String toPeriod = String.format("%04d-03", fy.endYear());

        List<String> importedPeriods = monthRepository.findDistinctPeriods(tenantId, fromPeriod, toPeriod);

        Optional<String> firstRegularRunOpt = payRunRepository.findEarliestPeriod(
                tenantId, PayRunType.REGULAR, PayRunStatus.CANCELLED, fromPeriod, toPeriod);

        YearMonth cutoffYm;
        if (firstRegularRunOpt.isPresent()) {
            cutoffYm = YearMonth.parse(firstRegularRunOpt.get());
        } else {
            LocalDate today = rowValidator.tenantToday(tenantId);
            cutoffYm = YearMonth.from(today);
        }

        List<String> missingPeriods = new ArrayList<>();
        YearMonth startYm = YearMonth.of(fy.startYear(), 4);
        for (YearMonth cur = startYm; cur.isBefore(cutoffYm); cur = cur.plusMonths(1)) {
            String pStr = cur.toString();
            if (!importedPeriods.contains(pStr)) {
                missingPeriods.add(pStr);
            }
        }

        return new PriorPayrollStatusResponse(
                financialYear, firstRegularRunOpt.orElse(null), importedPeriods, missingPeriods);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasImportedRows(String period) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(period, "period must not be null");
        return monthRepository.existsByTenantIdAndPeriod(tenantId, period);
    }
}
