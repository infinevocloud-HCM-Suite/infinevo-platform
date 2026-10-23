package com.infinevo.core.wfh;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalOutcomeHandler;
import com.infinevo.core.approval.StepDecision;
import com.infinevo.core.attendance.Attendance;
import com.infinevo.core.attendance.AttendanceRepository;
import com.infinevo.core.attendance.AttendanceSource;
import com.infinevo.core.attendance.AttendanceStatus;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles approval engine outcomes for Work From Home requests (W-74).
 * Transitions PENDING requests to APPROVED or REJECTED when the approval flow concludes,
 * records quota consumption, and automatically generates attendance records.
 */
@Component
public class WfhApprovalOutcomeHandler implements ApprovalOutcomeHandler {

    private static final Logger log = LoggerFactory.getLogger(WfhApprovalOutcomeHandler.class);

    private final ApprovalInstanceRepository instanceRepository;
    private final WfhRequestRepository requestRepository;
    private final WfhAllocationRepository allocationRepository;
    private final WfhConsumptionRepository consumptionRepository;
    private final WfhPolicyService policyService;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;

    public WfhApprovalOutcomeHandler(
            ApprovalInstanceRepository instanceRepository,
            WfhRequestRepository requestRepository,
            WfhAllocationRepository allocationRepository,
            WfhConsumptionRepository consumptionRepository,
            WfhPolicyService policyService,
            EmployeeRepository employeeRepository,
            AttendanceRepository attendanceRepository) {
        this.instanceRepository = Objects.requireNonNull(instanceRepository);
        this.requestRepository = Objects.requireNonNull(requestRepository);
        this.allocationRepository = Objects.requireNonNull(allocationRepository);
        this.consumptionRepository = Objects.requireNonNull(consumptionRepository);
        this.policyService = Objects.requireNonNull(policyService);
        this.employeeRepository = Objects.requireNonNull(employeeRepository);
        this.attendanceRepository = Objects.requireNonNull(attendanceRepository);
    }

    @Override
    public ApprovalFlowType flowType() {
        return ApprovalFlowType.WORK_FROM_HOME;
    }

    @Override
    @Transactional
    public void onApproved(UUID instanceId, List<StepDecision> decisions) {
        ApprovalInstance instance = instanceRepository.findById(instanceId).orElse(null);
        if (instance == null) {
            log.warn("Approval instance {} not found during WFH approval outcome", instanceId);
            return;
        }

        UUID requestId = instance.getSubjectId();
        UUID tenantId = instance.getTenantId();
        boolean clearTenant = false;
        if (!TenantContext.isBound()) {
            TenantContext.set(tenantId);
            clearTenant = true;
        }

        try {
            requestRepository.findByTenantIdAndId(tenantId, requestId).ifPresent(req -> {
                if (req.getStatus() == WfhRequestStatus.PENDING) {
                    boolean overlaps = requestRepository.existsOverlappingRequestExcluding(
                            tenantId,
                            req.getEmployeeId(),
                            List.of(WfhRequestStatus.APPROVED),
                            req.getFromDate(),
                            req.getToDate(),
                            req.getId());

                    if (overlaps) {
                        log.warn("WFH request {} overlaps with already approved request; auto-rejecting", requestId);
                        req.setStatus(WfhRequestStatus.REJECTED);
                        req.setDecidedAt(Instant.now());
                        req.setReason(
                                req.getReason() != null
                                        ? req.getReason() + " | Auto-rejected: overlaps with approved WFH request"
                                        : "Auto-rejected: overlaps with approved WFH request");
                        requestRepository.save(req);
                        return;
                    }

                    // 1. Transition to APPROVED
                    req.setStatus(WfhRequestStatus.APPROVED);
                    req.setDecidedAt(Instant.now());
                    requestRepository.save(req);

                    // 2. Ledger consumption
                    WfhAllocation allocation = allocationRepository
                            .findFirstByTenantIdAndEmployeeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                    tenantId, req.getEmployeeId(), req.getFromDate(), req.getFromDate())
                            .orElseGet(() -> {
                                WfhPolicy defaultPolicy = policyService.getOrCreateDefaultPolicy(tenantId);
                                WfhAllocation def = new WfhAllocation();
                                def.setTenantId(tenantId);
                                def.setEmployeeId(req.getEmployeeId());
                                def.setPolicyId(defaultPolicy.getId());
                                def.setWfhYear(String.valueOf(req.getFromDate().getYear()));
                                def.setYearStartDate(
                                        LocalDate.of(req.getFromDate().getYear(), 1, 1));
                                def.setYearEndDate(
                                        LocalDate.of(req.getFromDate().getYear(), 12, 31));
                                def.setEntitlementDays(defaultPolicy.getAnnualDays());
                                def.setProRateFactor(BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP));
                                return allocationRepository.save(def);
                            });

                    WfhConsumption consumption = new WfhConsumption();
                    consumption.setTenantId(tenantId);
                    consumption.setEmployeeId(req.getEmployeeId());
                    consumption.setAllocationId(allocation.getId());
                    consumption.setWfhRequestId(req.getId());
                    consumption.setConsumedDays(req.getWorkingDays().setScale(2, RoundingMode.HALF_UP));
                    consumption.setConsumedOn(LocalDate.now(ZoneOffset.UTC));
                    consumption.setPeriod(YearMonth.from(req.getFromDate()).toString());
                    consumption.setReason(req.getReason());
                    consumptionRepository.save(consumption);

                    // 3. Auto-generate Attendance
                    Employee emp = employeeRepository
                            .findByIdAndTenantIdAndDeletedFalse(req.getEmployeeId(), tenantId)
                            .orElse(null);
                    if (emp != null) {
                        AttendanceStatus targetStatus =
                                req.isHalfDay() ? AttendanceStatus.HALF_DAY : AttendanceStatus.PRESENT;

                        for (LocalDate d = req.getFromDate(); !d.isAfter(req.getToDate()); d = d.plusDays(1)) {
                            DayOfWeek dow = d.getDayOfWeek();
                            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                                continue;
                            }

                            final LocalDate attDate = d;
                            attendanceRepository
                                    .findByTenantIdAndEmployeeIdAndAttendanceDate(tenantId, emp.getId(), attDate)
                                    .ifPresentOrElse(
                                            existing -> {
                                                existing.update(
                                                        targetStatus,
                                                        AttendanceSource.ADMIN,
                                                        "Work From Home",
                                                        "system");
                                                attendanceRepository.save(existing);
                                            },
                                            () -> {
                                                Attendance newAtt = new Attendance(
                                                        tenantId,
                                                        emp,
                                                        attDate,
                                                        targetStatus,
                                                        AttendanceSource.ADMIN,
                                                        "Work From Home",
                                                        "system");
                                                attendanceRepository.save(newAtt);
                                            });
                        }
                    }

                    log.info("WFH request {} approved successfully via instance {}", requestId, instanceId);
                }
            });
        } finally {
            if (clearTenant) {
                TenantContext.clear();
            }
        }
    }

    @Override
    @Transactional
    public void onRejected(UUID instanceId, List<StepDecision> decisions) {
        ApprovalInstance instance = instanceRepository.findById(instanceId).orElse(null);
        if (instance == null) {
            log.warn("Approval instance {} not found during WFH rejection outcome", instanceId);
            return;
        }

        UUID requestId = instance.getSubjectId();
        UUID tenantId = instance.getTenantId();
        boolean clearTenant = false;
        if (!TenantContext.isBound()) {
            TenantContext.set(tenantId);
            clearTenant = true;
        }

        try {
            requestRepository.findByTenantIdAndId(tenantId, requestId).ifPresent(req -> {
                if (req.getStatus() == WfhRequestStatus.PENDING) {
                    req.setStatus(WfhRequestStatus.REJECTED);
                    req.setDecidedAt(Instant.now());
                    requestRepository.save(req);
                    log.info("WFH request {} rejected via approval instance {}", requestId, instanceId);
                }
            });
        } finally {
            if (clearTenant) {
                TenantContext.clear();
            }
        }
    }
}
