package com.infinevo.payroll.settlement;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.leave.LeaveBalanceResponse;
import com.infinevo.core.leave.LeaveBalanceService;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryComponentItemResponse;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link LeaveEncashmentService} (W-90.1).
 * Calculates leave encashment value using the Basic (+ DA) / Divisor daily rate,
 * enforces Section 10(10AA) statutory 4-limit exemption rules, and records settlements.
 */
@Service
public class LeaveEncashmentServiceImpl implements LeaveEncashmentService {

    private static final Logger log = LoggerFactory.getLogger(LeaveEncashmentServiceImpl.class);

    public static final BigDecimal STATUTORY_CEILING = BigDecimal.valueOf(2500000.00);
    public static final int DEFAULT_DAILY_DIVISOR = 30;

    private final LeaveEncashmentRepository repository;
    private final LeaveBalanceService leaveBalanceService;
    private final EmployeeSalaryService salaryService;
    private final EmployeeService employeeService;

    public LeaveEncashmentServiceImpl(
            LeaveEncashmentRepository repository,
            ObjectProvider<LeaveBalanceService> leaveBalanceServiceProvider,
            EmployeeSalaryService salaryService,
            EmployeeService employeeService) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.leaveBalanceService = Objects.requireNonNull(leaveBalanceServiceProvider, "leaveBalanceServiceProvider")
                .getIfAvailable();
        this.salaryService = Objects.requireNonNull(salaryService, "salaryService");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService");
    }

    public LeaveEncashmentServiceImpl(
            LeaveEncashmentRepository repository,
            LeaveBalanceService leaveBalanceService,
            EmployeeSalaryService salaryService,
            EmployeeService employeeService) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.leaveBalanceService = leaveBalanceService;
        this.salaryService = Objects.requireNonNull(salaryService, "salaryService");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService");
    }

    @Override
    @Transactional(readOnly = true)
    public LeaveEncashmentResponse estimateEncashment(LeaveEncashmentEstimateRequest request) {
        UUID tenantId = TenantContext.require();
        return computeEncashment(
                tenantId,
                request.employeeId(),
                request.exitDate(),
                request.leaveTypeId(),
                request.customDays(),
                request.dailyDivisor(),
                null,
                "ESTIMATE");
    }

    @Override
    @Transactional
    public LeaveEncashmentResponse recordSettlement(RecordLeaveEncashmentRequest request) {
        UUID tenantId = TenantContext.require();

        // Check if an active settlement already exists for this employee and leave type
        repository
                .findByTenantIdAndEmployeeIdAndLeaveTypeIdAndStatusNot(
                        tenantId, request.employeeId(), request.leaveTypeId(), "CANCELLED")
                .ifPresent(existing -> {
                    throw new IllegalStateException("An active leave encashment settlement already exists for employee "
                            + request.employeeId()
                            + " and leave type "
                            + request.leaveTypeId());
                });

        LeaveEncashmentResponse computed = computeEncashment(
                tenantId,
                request.employeeId(),
                request.exitDate(),
                request.leaveTypeId(),
                request.customDays(),
                request.dailyDivisor(),
                null,
                request.status());

        LeaveEncashmentSettlement settlement = new LeaveEncashmentSettlement(
                tenantId,
                request.employeeId(),
                request.leaveTypeId(),
                computed.encashableDays(),
                computed.perDayRate(),
                computed.grossEncashmentAmount(),
                computed.taxExemptAmount(),
                computed.taxableAmount(),
                STATUTORY_CEILING,
                request.status(),
                "system");

        LeaveEncashmentSettlement saved = repository.save(settlement);
        log.info(
                "Recorded leave encashment settlement id={} employee={} gross={} exempt={} taxable={}",
                saved.getId(),
                saved.getEmployeeId(),
                saved.getGrossEncashmentAmount(),
                saved.getTaxExemptAmount(),
                saved.getTaxableAmount());

        return toResponse(
                saved,
                computed.leaveTypeCode(),
                computed.leaveTypeName(),
                request.exitDate(),
                computed.monthlyBasicAndDa(),
                computed.dailyDivisor());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveEncashmentResponse> getSettlements(UUID employeeId) {
        UUID tenantId = TenantContext.require();
        List<LeaveEncashmentSettlement> settlements = repository.findByTenantIdAndEmployeeId(tenantId, employeeId);

        return settlements.stream()
                .map(s -> toResponse(s, "EL", "Earned Leave", LocalDate.now(), BigDecimal.ZERO, DEFAULT_DAILY_DIVISOR))
                .toList();
    }

    private LeaveEncashmentResponse computeEncashment(
            UUID tenantId,
            UUID employeeId,
            LocalDate exitDate,
            UUID targetLeaveTypeId,
            BigDecimal customDays,
            Integer divisor,
            UUID existingId,
            String status) {

        int dailyDivisor = (divisor != null && divisor > 0) ? divisor : DEFAULT_DAILY_DIVISOR;

        // 1. Fetch salary structure in force as of exitDate to calculate per-day rate
        BigDecimal monthlyBasicAndDa = resolveMonthlyBasicAndDa(employeeId, exitDate);
        BigDecimal perDayRate = monthlyBasicAndDa.divide(BigDecimal.valueOf(dailyDivisor), 2, RoundingMode.HALF_UP);

        // 2. Fetch leave balance and verify encashability
        if (leaveBalanceService == null) {
            throw new IllegalStateException("LeaveBalanceService is not available in the current context");
        }
        List<LeaveBalanceResponse> balances =
                leaveBalanceService.getBalancesForEmployee(tenantId, employeeId, exitDate);

        LeaveBalanceResponse targetBalance;
        if (targetLeaveTypeId != null) {
            targetBalance = balances.stream()
                    .filter(b -> targetLeaveTypeId.equals(b.leaveTypeId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Leave balance not found for leave type " + targetLeaveTypeId));

            if (!isEncashable(targetBalance)) {
                throw new IllegalArgumentException(
                        "Leave type '" + targetBalance.leaveTypeCode()
                                + "' is not encashable. Only Privilege/Earned leave (PL/EL) is eligible for encashment on exit.");
            }
        } else {
            targetBalance = balances.stream()
                    .filter(this::isEncashable)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No encashable Privilege/Earned leave (PL/EL) balance found for employee"));
        }

        // 3. Determine encashable days
        BigDecimal encashableDays;
        if (customDays != null) {
            if (customDays.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Encashable days cannot be negative: " + customDays);
            }
            encashableDays = customDays.setScale(2, RoundingMode.HALF_UP);
        } else {
            BigDecimal available = targetBalance.availableDays() != null
                    ? targetBalance.availableDays()
                    : (targetBalance.remainingDays() != null ? targetBalance.remainingDays() : BigDecimal.ZERO);
            encashableDays = available.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }

        // 4. Compute gross amount and statutory exemption under Section 10(10AA)
        // Non-government employees exempt least of 4 limits:
        // (1) Actual leave encashment received
        // (2) Statutory ceiling of ₹25,00,000
        // (3) 10 months' average salary (Basic + DA) preceding exit
        // (4) Cash equivalent of earned leave (max 30 days per completed year of service)
        BigDecimal grossAmount = encashableDays.multiply(perDayRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal limit1 = grossAmount;
        BigDecimal limit2 = STATUTORY_CEILING;
        BigDecimal limit3 = resolveTenMonthsAverageSalary(employeeId, exitDate);
        BigDecimal limit4 = resolveEarnedLeaveServiceCap(employeeId, exitDate, perDayRate, encashableDays);

        BigDecimal taxExemptAmount = limit1.min(limit2).min(limit3).min(limit4).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxableAmount =
                grossAmount.subtract(taxExemptAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        return new LeaveEncashmentResponse(
                existingId,
                employeeId,
                targetBalance.leaveTypeId(),
                targetBalance.leaveTypeCode(),
                targetBalance.leaveTypeName(),
                exitDate,
                encashableDays,
                perDayRate,
                monthlyBasicAndDa,
                dailyDivisor,
                grossAmount,
                taxExemptAmount,
                taxableAmount,
                STATUTORY_CEILING,
                status);
    }

    private BigDecimal resolveTenMonthsAverageSalary(UUID employeeId, LocalDate exitDate) {
        BigDecimal sumBasicAndDa = BigDecimal.ZERO;
        int evaluatedMonths = 0;
        for (int i = 1; i <= 10; i++) {
            LocalDate asOf = exitDate.minusMonths(i);
            try {
                BigDecimal monthly = resolveMonthlyBasicAndDa(employeeId, asOf);
                sumBasicAndDa = sumBasicAndDa.add(monthly);
                evaluatedMonths++;
            } catch (Exception ignored) {
                // If date precedes salary records, continue
            }
        }
        if (evaluatedMonths == 0) {
            BigDecimal current = resolveMonthlyBasicAndDa(employeeId, exitDate);
            return current.multiply(BigDecimal.TEN).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal avgMonthly = sumBasicAndDa.divide(BigDecimal.valueOf(evaluatedMonths), 2, RoundingMode.HALF_UP);
        return avgMonthly.multiply(BigDecimal.TEN).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveEarnedLeaveServiceCap(
            UUID employeeId, LocalDate exitDate, BigDecimal perDayRate, BigDecimal encashableDays) {
        long completedYears = 0;
        try {
            EmployeeResponse employee = employeeService.get(employeeId);
            if (employee != null && employee.dateOfJoining() != null && !exitDate.isBefore(employee.dateOfJoining())) {
                completedYears = ChronoUnit.YEARS.between(employee.dateOfJoining(), exitDate);
            }
        } catch (Exception e) {
            log.warn("Could not retrieve date of joining for employee {}: {}", employeeId, e.getMessage());
        }

        BigDecimal maxDays = BigDecimal.valueOf(Math.max(0, completedYears) * 30L);
        BigDecimal qualifyingDays = encashableDays.min(maxDays);
        return qualifyingDays.multiply(perDayRate).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveMonthlyBasicAndDa(UUID employeeId, LocalDate date) {
        SalaryVersionResponse salaryVersion = salaryService.getAsOf(employeeId, date);
        if (salaryVersion == null
                || salaryVersion.earnings() == null
                || salaryVersion.earnings().isEmpty()) {
            // Attempt to find any version
            List<SalaryVersionResponse> versions = salaryService.listVersions(employeeId);
            if (!versions.isEmpty()) {
                salaryVersion = versions.get(0);
            } else {
                throw new IllegalStateException("No salary structure version found for employee " + employeeId);
            }
        }

        BigDecimal basicAndDa = BigDecimal.ZERO;
        for (SalaryComponentItemResponse item : salaryVersion.earnings()) {
            if (item.enabled() && isBasicOrDa(item)) {
                if (item.monthlyAmount() != null) {
                    basicAndDa = basicAndDa.add(item.monthlyAmount());
                }
            }
        }

        if (basicAndDa.compareTo(BigDecimal.ZERO) <= 0) {
            // If explicit basic/da not labelled, use the first earning or monthly CTC fallback
            if (!salaryVersion.earnings().isEmpty()
                    && salaryVersion.earnings().get(0).monthlyAmount() != null) {
                basicAndDa = salaryVersion.earnings().get(0).monthlyAmount();
            } else if (salaryVersion.monthlyCtc() != null) {
                basicAndDa = salaryVersion.monthlyCtc();
            } else {
                throw new IllegalStateException("Basic salary could not be resolved from employee salary structure");
            }
        }

        return basicAndDa.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isBasicOrDa(SalaryComponentItemResponse item) {
        String code = item.componentCode() != null ? item.componentCode().toUpperCase() : "";
        String name = item.componentName() != null ? item.componentName().toUpperCase() : "";

        return code.equals("BASIC")
                || code.equals("BASIC_SALARY")
                || code.equals("DA")
                || code.equals("DEARNESS")
                || name.contains("BASIC")
                || name.contains("DEARNESS");
    }

    private boolean isEncashable(LeaveBalanceResponse balance) {
        String code = balance.leaveTypeCode() != null ? balance.leaveTypeCode().toUpperCase() : "";
        String name = balance.leaveTypeName() != null ? balance.leaveTypeName().toUpperCase() : "";

        // Under Indian law, Earned Leave (EL) or Privilege Leave (PL) is encashable
        return code.equals("EL") || code.equals("PL") || name.contains("EARNED") || name.contains("PRIVILEGE");
    }

    private LeaveEncashmentResponse toResponse(
            LeaveEncashmentSettlement s,
            String leaveTypeCode,
            String leaveTypeName,
            LocalDate exitDate,
            BigDecimal monthlyBasicAndDa,
            int dailyDivisor) {
        return new LeaveEncashmentResponse(
                s.getId(),
                s.getEmployeeId(),
                s.getLeaveTypeId(),
                leaveTypeCode,
                leaveTypeName,
                exitDate,
                s.getEncashableDays(),
                s.getPerDayRate(),
                monthlyBasicAndDa,
                dailyDivisor,
                s.getGrossEncashmentAmount(),
                s.getTaxExemptAmount(),
                s.getTaxableAmount(),
                s.getStatutoryCeiling(),
                s.getStatus());
    }
}
