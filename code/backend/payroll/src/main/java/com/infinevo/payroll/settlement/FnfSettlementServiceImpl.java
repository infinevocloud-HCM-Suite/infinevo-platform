package com.infinevo.payroll.settlement;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.payroll.salary.EmployeeSalaryService;
import com.infinevo.payroll.salary.SalaryComponentItemResponse;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link FnfSettlementService} (W-90.3).
 * Consolidates final exit salary, leave encashment dues, gratuity, notice buyout/shortfall,
 * statutory deductions, and controls the settlement lifecycle.
 */
@Service
public class FnfSettlementServiceImpl implements FnfSettlementService {

    private static final Logger log = LoggerFactory.getLogger(FnfSettlementServiceImpl.class);

    private final FnfSettlementRepository repository;
    private final LeaveEncashmentRepository leaveEncashmentRepository;
    private final NoticePeriodRepository noticePeriodRepository;
    private final EmployeeSalaryService salaryService;
    private final EmployeeRepository employeeRepository;

    public FnfSettlementServiceImpl(
            FnfSettlementRepository repository,
            LeaveEncashmentRepository leaveEncashmentRepository,
            NoticePeriodRepository noticePeriodRepository,
            EmployeeSalaryService salaryService,
            EmployeeRepository employeeRepository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.leaveEncashmentRepository = Objects.requireNonNull(leaveEncashmentRepository, "leaveEncashmentRepository");
        this.noticePeriodRepository = Objects.requireNonNull(noticePeriodRepository, "noticePeriodRepository");
        this.salaryService = Objects.requireNonNull(salaryService, "salaryService");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository");
    }

    @Override
    @Transactional
    public FnfSettlementResponse initiateSettlement(FnfInitiateRequest request) {
        UUID tenantId = TenantContext.require();

        if (request.exitDate() == null) {
            throw new IllegalArgumentException("Exit date is required");
        }
        if (request.employeeId() == null) {
            throw new IllegalArgumentException("Employee ID is required");
        }

        // Check if an active F&F settlement already exists
        Optional<FnfSettlement> existing =
                repository.findByTenantIdAndEmployeeIdAndStatusNot(tenantId, request.employeeId(), "CANCELLED");
        if (existing.isPresent()) {
            throw new IllegalStateException("An active F&F settlement already exists for employee "
                    + request.employeeId() + " (status: " + existing.get().getStatus() + ")");
        }

        LocalDate resignationDate = request.resignationDate() != null
                ? request.resignationDate()
                : request.exitDate().minusDays(30);

        if (request.exitDate().isBefore(resignationDate)) {
            throw new IllegalArgumentException("Exit date (" + request.exitDate()
                    + ") cannot be before resignation date (" + resignationDate + ")");
        }

        // 1. Pro-rated Exit Month Salary
        int daysInExitMonth = request.exitDate().lengthOfMonth();
        int workingDays = request.workingDaysInExitMonth() != null
                ? request.workingDaysInExitMonth()
                : request.exitDate().getDayOfMonth();

        BigDecimal monthlyGross = resolveSalary(request.employeeId(), request.exitDate(), false);
        BigDecimal monthlyBasic = resolveSalary(request.employeeId(), request.exitDate(), true);

        BigDecimal dailyGross = monthlyGross.divide(BigDecimal.valueOf(daysInExitMonth), 4, RoundingMode.HALF_UP);
        BigDecimal exitMonthSalary =
                dailyGross.multiply(BigDecimal.valueOf(workingDays)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal dailyBasic = monthlyBasic.divide(BigDecimal.valueOf(daysInExitMonth), 4, RoundingMode.HALF_UP);
        BigDecimal proRatedBasic =
                dailyBasic.multiply(BigDecimal.valueOf(workingDays)).setScale(2, RoundingMode.HALF_UP);

        // 2. Leave Encashment dues (W-90.1)
        BigDecimal leaveEncashmentAmount =
                leaveEncashmentRepository
                        .findByTenantIdAndEmployeeIdAndStatusNot(tenantId, request.employeeId(), "CANCELLED")
                        .stream()
                        .map(LeaveEncashmentSettlement::getGrossEncashmentAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. Gratuity (Payment of Gratuity Act 1972: 15 * Basic * years / 26 after 5 years service)
        BigDecimal gratuityAmount = BigDecimal.ZERO;
        Optional<Employee> empOpt =
                employeeRepository.findByIdAndTenantIdAndDeletedFalse(request.employeeId(), tenantId);
        if (empOpt.isPresent() && empOpt.get().getDateOfJoining() != null) {
            LocalDate doj = empOpt.get().getDateOfJoining();
            long serviceDays = ChronoUnit.DAYS.between(doj, request.exitDate());
            long completeYears = serviceDays / 365;
            if (completeYears >= 5) {
                gratuityAmount = monthlyBasic
                        .multiply(BigDecimal.valueOf(15))
                        .multiply(BigDecimal.valueOf(completeYears))
                        .divide(BigDecimal.valueOf(26), 2, RoundingMode.HALF_UP);
            }
        }

        // 4. Notice Period Payout or Recovery (W-90.2)
        BigDecimal noticePayAmount = BigDecimal.ZERO;
        BigDecimal noticeRecoveryAmount = BigDecimal.ZERO;
        Optional<NoticePeriodSettlement> noticeSettlement =
                noticePeriodRepository.findByTenantIdAndEmployeeIdAndStatusNot(
                        tenantId, request.employeeId(), "CANCELLED");
        if (noticeSettlement.isPresent()) {
            NoticePeriodSettlement nps = noticeSettlement.get();
            if ("PAYOUT".equalsIgnoreCase(nps.getSettlementType())) {
                noticePayAmount = nps.getSettlementAmount();
            } else if ("RECOVERY".equalsIgnoreCase(nps.getSettlementType())) {
                noticeRecoveryAmount = nps.getSettlementAmount();
            }
        }

        BigDecimal otherEarnings = request.otherEarnings() != null
                ? request.otherEarnings().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal grossEarnings = exitMonthSalary
                .add(leaveEncashmentAmount)
                .add(gratuityAmount)
                .add(noticePayAmount)
                .add(otherEarnings)
                .setScale(2, RoundingMode.HALF_UP);

        // 5. Statutory Deductions on Exit Salary
        // EPF: 12% on basic (capped at 15000 standard statutory ceiling)
        BigDecimal epfEligibleBasic = proRatedBasic.min(BigDecimal.valueOf(15000));
        BigDecimal epfDeduction =
                epfEligibleBasic.multiply(BigDecimal.valueOf(0.12)).setScale(2, RoundingMode.HALF_UP);

        // ESI: 0.75% if monthly gross <= 21000
        BigDecimal esiDeduction = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (monthlyGross.compareTo(BigDecimal.valueOf(21000)) <= 0) {
            esiDeduction = exitMonthSalary.multiply(BigDecimal.valueOf(0.0075)).setScale(2, RoundingMode.HALF_UP);
        }

        // PT: standard flat Professional Tax (e.g. ₹200 if monthly gross > 15000)
        BigDecimal ptDeduction = exitMonthSalary.compareTo(BigDecimal.valueOf(15000)) > 0
                ? BigDecimal.valueOf(200.00).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal tdsDeduction = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal otherRecoveries = request.otherRecoveries() != null
                ? request.otherRecoveries().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal grossDeductions = noticeRecoveryAmount
                .add(epfDeduction)
                .add(esiDeduction)
                .add(ptDeduction)
                .add(tdsDeduction)
                .add(otherRecoveries)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal netPayable = grossEarnings.subtract(grossDeductions).setScale(2, RoundingMode.HALF_UP);

        FnfSettlement settlement = new FnfSettlement();
        settlement.setTenantId(tenantId);
        settlement.setEmployeeId(request.employeeId());
        settlement.setResignationDate(resignationDate);
        settlement.setExitDate(request.exitDate());
        settlement.setExitReason(request.exitReason() != null ? request.exitReason() : "RESIGNATION");
        settlement.setWorkingDaysInExitMonth(workingDays);
        settlement.setExitMonthSalary(exitMonthSalary);
        settlement.setLeaveEncashmentAmount(leaveEncashmentAmount);
        settlement.setGratuityAmount(gratuityAmount);
        settlement.setNoticePayAmount(noticePayAmount);
        settlement.setOtherEarnings(otherEarnings);
        settlement.setGrossEarnings(grossEarnings);
        settlement.setNoticeRecoveryAmount(noticeRecoveryAmount);
        settlement.setEpfDeduction(epfDeduction);
        settlement.setEsiDeduction(esiDeduction);
        settlement.setPtDeduction(ptDeduction);
        settlement.setTdsDeduction(tdsDeduction);
        settlement.setOtherRecoveries(otherRecoveries);
        settlement.setGrossDeductions(grossDeductions);
        settlement.setNetPayable(netPayable);
        settlement.setStatus("DRAFT");
        settlement.setRemarks(request.remarks());

        FnfSettlement saved = repository.save(settlement);
        log.info(
                "Initiated F&F settlement id={} employee={} grossEarnings={} grossDeductions={} netPayable={}",
                saved.getId(),
                saved.getEmployeeId(),
                saved.getGrossEarnings(),
                saved.getGrossDeductions(),
                saved.getNetPayable());

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public FnfSettlementResponse getSettlement(UUID id) {
        UUID tenantId = TenantContext.require();
        FnfSettlement settlement = repository
                .findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("F&F settlement not found with ID: " + id));
        return toResponse(settlement);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FnfSettlementResponse> getSettlementsByEmployee(UUID employeeId) {
        UUID tenantId = TenantContext.require();
        return repository.findByTenantIdAndEmployeeId(tenantId, employeeId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FnfSettlementResponse> listSettlements() {
        UUID tenantId = TenantContext.require();
        return repository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public FnfSettlementResponse approveSettlement(UUID id, String approver) {
        UUID tenantId = TenantContext.require();
        FnfSettlement settlement = repository
                .findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("F&F settlement not found with ID: " + id));

        if (!"DRAFT".equals(settlement.getStatus()) && !"UNDER_REVIEW".equals(settlement.getStatus())) {
            throw new IllegalStateException("Only DRAFT or UNDER_REVIEW settlements can be approved. Current status: "
                    + settlement.getStatus());
        }

        settlement.setStatus("APPROVED");
        settlement.setApprovedBy(approver != null ? approver : "system");
        settlement.setApprovedAt(Instant.now());
        settlement.setUpdatedAt(Instant.now());
        settlement.setUpdatedBy(approver != null ? approver : "system");

        FnfSettlement saved = repository.save(settlement);
        log.info("Approved F&F settlement id={} employee={} by={}", saved.getId(), saved.getEmployeeId(), approver);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public FnfSettlementResponse paySettlement(UUID id) {
        UUID tenantId = TenantContext.require();
        FnfSettlement settlement = repository
                .findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("F&F settlement not found with ID: " + id));

        if (!"APPROVED".equals(settlement.getStatus())) {
            throw new IllegalStateException(
                    "Only APPROVED settlements can be marked as PAID. Current status: " + settlement.getStatus());
        }

        settlement.setStatus("PAID");
        settlement.setPaidAt(Instant.now());
        settlement.setUpdatedAt(Instant.now());

        FnfSettlement saved = repository.save(settlement);
        log.info("Marked F&F settlement as PAID id={} employee={}", saved.getId(), saved.getEmployeeId());
        return toResponse(saved);
    }

    private BigDecimal resolveSalary(UUID employeeId, LocalDate date, boolean basicOnly) {
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
        if (basicOnly) {
            for (SalaryComponentItemResponse item : salaryVersion.earnings()) {
                if (item.enabled() && isBasicOrDa(item) && item.monthlyAmount() != null) {
                    total = total.add(item.monthlyAmount());
                }
            }
        } else {
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
                throw new IllegalStateException("Salary could not be resolved from employee salary structure");
            }
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isBasicOrDa(SalaryComponentItemResponse item) {
        String code = item.componentCode() != null ? item.componentCode().toUpperCase() : "";
        return code.contains("BASIC") || code.contains("DA") || code.equals("BASE");
    }

    private FnfSettlementResponse toResponse(FnfSettlement s) {
        return new FnfSettlementResponse(
                s.getId(),
                s.getEmployeeId(),
                s.getResignationDate(),
                s.getExitDate(),
                s.getExitReason(),
                s.getWorkingDaysInExitMonth(),
                s.getExitMonthSalary(),
                s.getLeaveEncashmentAmount(),
                s.getGratuityAmount(),
                s.getNoticePayAmount(),
                s.getOtherEarnings(),
                s.getGrossEarnings(),
                s.getNoticeRecoveryAmount(),
                s.getEpfDeduction(),
                s.getEsiDeduction(),
                s.getPtDeduction(),
                s.getTdsDeduction(),
                s.getOtherRecoveries(),
                s.getGrossDeductions(),
                s.getNetPayable(),
                s.getStatus(),
                s.getApprovedBy(),
                s.getApprovedAt(),
                s.getPaidAt(),
                s.getRemarks(),
                s.getCreatedAt());
    }
}
