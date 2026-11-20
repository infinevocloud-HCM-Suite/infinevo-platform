package com.infinevo.payroll.settlement;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link NoticePeriodService} (W-90.2).
 * Computes calendar day delta between resignation and exit dates, derives daily rate on Gross or Basic,
 * determines recovery vs payout vs waiver, and records settlements.
 */
@Service
public class NoticePeriodServiceImpl implements NoticePeriodService {

    private static final Logger log = LoggerFactory.getLogger(NoticePeriodServiceImpl.class);

    private final NoticePeriodRepository repository;
    private final EmployeeSalaryService salaryService;

    public NoticePeriodServiceImpl(NoticePeriodRepository repository, EmployeeSalaryService salaryService) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.salaryService = Objects.requireNonNull(salaryService, "salaryService");
    }

    @Override
    @Transactional(readOnly = true)
    public NoticePeriodResponse calculateNoticePeriod(NoticePeriodCalculateRequest request) {
        return computeSettlement(
                request.employeeId(),
                request.resignationDate(),
                request.actualExitDate(),
                request.requiredNoticeDays(),
                request.rateBasis(),
                request.dailyDivisor(),
                request.isWaived(),
                request.waiverReason(),
                null,
                null,
                "ESTIMATE");
    }

    @Override
    @Transactional
    public NoticePeriodResponse recordSettlement(RecordNoticeSettlementRequest request) {
        UUID tenantId = TenantContext.require();

        // Check if an active settlement already exists for this employee
        repository
                .findByTenantIdAndEmployeeIdAndStatusNot(tenantId, request.employeeId(), "CANCELLED")
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "An active notice period settlement already exists for employee " + request.employeeId());
                });

        NoticePeriodResponse computed = computeSettlement(
                request.employeeId(),
                request.resignationDate(),
                request.actualExitDate(),
                request.requiredNoticeDays(),
                request.rateBasis(),
                request.dailyDivisor(),
                request.isWaived(),
                request.waiverReason(),
                request.approvedBy(),
                null,
                request.status());

        NoticePeriodSettlement settlement = new NoticePeriodSettlement(
                tenantId,
                request.employeeId(),
                request.resignationDate(),
                computed.expectedExitDate(),
                request.actualExitDate(),
                computed.requiredNoticeDays(),
                computed.servedNoticeDays(),
                computed.shortfallDays(),
                computed.dailyRate(),
                computed.rateBasis(),
                computed.settlementType(),
                computed.settlementAmount(),
                request.waiverReason(),
                request.approvedBy(),
                request.status(),
                "system");

        NoticePeriodSettlement saved = repository.save(settlement);
        log.info(
                "Recorded notice period settlement id={} employee={} type={} amount={} shortfallDays={}",
                saved.getId(),
                saved.getEmployeeId(),
                saved.getSettlementType(),
                saved.getSettlementAmount(),
                saved.getShortfallDays());

        return toResponse(saved, computed.baseSalaryAmount(), computed.dailyDivisor());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NoticePeriodResponse> getSettlements(UUID employeeId) {
        UUID tenantId = TenantContext.require();
        List<NoticePeriodSettlement> settlements = repository.findByTenantIdAndEmployeeId(tenantId, employeeId);
        return settlements.stream().map(s -> toResponse(s, BigDecimal.ZERO, 30)).toList();
    }

    private NoticePeriodResponse computeSettlement(
            UUID employeeId,
            LocalDate resignationDate,
            LocalDate actualExitDate,
            int requiredDays,
            String rateBasis,
            int divisor,
            boolean isWaived,
            String waiverReason,
            String approvedBy,
            UUID existingId,
            String status) {

        if (actualExitDate.isBefore(resignationDate)) {
            throw new IllegalArgumentException("Actual exit date (" + actualExitDate
                    + ") cannot be before resignation date (" + resignationDate + ")");
        }

        LocalDate expectedExitDate = resignationDate.plusDays(requiredDays);
        int servedDays = (int) Math.max(0, ChronoUnit.DAYS.between(resignationDate, actualExitDate));
        int shortfall = requiredDays - servedDays;

        String basis = (rateBasis != null && rateBasis.equalsIgnoreCase("BASIC")) ? "BASIC" : "GROSS";
        BigDecimal baseSalary = resolveSalaryBase(employeeId, actualExitDate, basis);
        BigDecimal dailyRate = baseSalary.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);

        String settlementType;
        BigDecimal settlementAmount;

        if (isWaived) {
            settlementType = "WAIVED";
            settlementAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else if (shortfall > 0) {
            settlementType = "RECOVERY";
            settlementAmount = dailyRate.multiply(BigDecimal.valueOf(shortfall)).setScale(2, RoundingMode.HALF_UP);
        } else if (shortfall < 0) {
            settlementType = "PAYOUT";
            settlementAmount =
                    dailyRate.multiply(BigDecimal.valueOf(Math.abs(shortfall))).setScale(2, RoundingMode.HALF_UP);
        } else {
            settlementType = "WAIVED";
            settlementAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        int absoluteShortfallDays = Math.abs(shortfall);

        return new NoticePeriodResponse(
                existingId,
                employeeId,
                resignationDate,
                expectedExitDate,
                actualExitDate,
                requiredDays,
                servedDays,
                absoluteShortfallDays,
                dailyRate,
                basis,
                divisor,
                baseSalary,
                settlementType,
                settlementAmount,
                waiverReason,
                approvedBy,
                status);
    }

    private BigDecimal resolveSalaryBase(UUID employeeId, LocalDate date, String basis) {
        SalaryVersionResponse salaryVersion = salaryService.getAsOf(employeeId, date);
        if (salaryVersion == null
                || salaryVersion.earnings() == null
                || salaryVersion.earnings().isEmpty()) {
            List<SalaryVersionResponse> versions = salaryService.listVersions(employeeId);
            if (!versions.isEmpty()) {
                salaryVersion = versions.get(0);
            } else {
                throw new IllegalStateException("No salary structure version found for employee " + employeeId);
            }
        }

        BigDecimal total = BigDecimal.ZERO;
        if (basis.equals("BASIC")) {
            for (SalaryComponentItemResponse item : salaryVersion.earnings()) {
                if (item.enabled() && isBasicOrDa(item)) {
                    if (item.monthlyAmount() != null) {
                        total = total.add(item.monthlyAmount());
                    }
                }
            }
        } else {
            // GROSS: sum of all enabled earnings
            for (SalaryComponentItemResponse item : salaryVersion.earnings()) {
                if (item.enabled() && item.monthlyAmount() != null) {
                    total = total.add(item.monthlyAmount());
                }
            }
        }

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            if (!salaryVersion.earnings().isEmpty()
                    && salaryVersion.earnings().get(0).monthlyAmount() != null) {
                total = salaryVersion.earnings().get(0).monthlyAmount();
            } else if (salaryVersion.monthlyCtc() != null) {
                total = salaryVersion.monthlyCtc();
            } else {
                throw new IllegalStateException("Base salary could not be resolved from employee salary structure");
            }
        }

        return total.setScale(2, RoundingMode.HALF_UP);
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

    private NoticePeriodResponse toResponse(NoticePeriodSettlement s, BigDecimal baseSalary, int dailyDivisor) {
        return new NoticePeriodResponse(
                s.getId(),
                s.getEmployeeId(),
                s.getResignationDate(),
                s.getExpectedExitDate(),
                s.getActualExitDate(),
                s.getRequiredNoticeDays(),
                s.getServedNoticeDays(),
                s.getShortfallDays(),
                s.getDailyRate(),
                s.getRateBasis(),
                dailyDivisor,
                baseSalary,
                s.getSettlementType(),
                s.getSettlementAmount(),
                s.getWaiverReason(),
                s.getApprovedBy(),
                s.getStatus());
    }
}
