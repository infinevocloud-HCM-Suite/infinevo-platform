package com.infinevo.core.wfh;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.SubjectRef;
import com.infinevo.core.attendance.AttendanceRepository;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.leave.LeaveRequestRepository;
import com.infinevo.core.leave.LeaveRequestStatus;
import com.infinevo.core.leave.WorkingDayCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WfhRequestServiceImpl implements WfhRequestService {

    private static final Logger log = LoggerFactory.getLogger(WfhRequestServiceImpl.class);

    private final WfhRequestRepository requestRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final WfhBalanceService balanceService;
    private final WfhPolicyService policyService;
    private final WfhConsumptionRepository consumptionRepository;
    private final EmployeeRepository employeeRepository;
    private final WorkingDayCalculator workingDayCalculator;
    private final AttendanceRepository attendanceRepository;
    private final ApprovalService approvalService;

    public WfhRequestServiceImpl(
            WfhRequestRepository requestRepository,
            LeaveRequestRepository leaveRequestRepository,
            WfhBalanceService balanceService,
            WfhPolicyService policyService,
            WfhConsumptionRepository consumptionRepository,
            EmployeeRepository employeeRepository,
            WorkingDayCalculator workingDayCalculator,
            AttendanceRepository attendanceRepository,
            @Autowired(required = false) ApprovalService approvalService) {
        this.requestRepository = Objects.requireNonNull(requestRepository);
        this.leaveRequestRepository = Objects.requireNonNull(leaveRequestRepository);
        this.balanceService = Objects.requireNonNull(balanceService);
        this.policyService = Objects.requireNonNull(policyService);
        this.consumptionRepository = Objects.requireNonNull(consumptionRepository);
        this.employeeRepository = Objects.requireNonNull(employeeRepository);
        this.workingDayCalculator = Objects.requireNonNull(workingDayCalculator);
        this.attendanceRepository = Objects.requireNonNull(attendanceRepository);
        this.approvalService = approvalService;
    }

    @Override
    public WfhRequestResponse apply(UUID tenantId, UUID employeeId, ApplyWfhRequest request) {
        return submitRequest(tenantId, employeeId, request, false);
    }

    @Override
    public WfhRequestResponse applyOnBehalf(UUID tenantId, UUID employeeId, ApplyWfhRequest request) {
        UUID targetEmployeeId = request.employeeId() != null ? request.employeeId() : employeeId;
        return submitRequest(tenantId, targetEmployeeId, request, true);
    }

    private WfhRequestResponse submitRequest(
            UUID tenantId, UUID employeeId, ApplyWfhRequest request, boolean onBehalf) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        Employee employee = employeeRepository
                .findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Employee not found: " + employeeId));

        LocalDate fromDate = Objects.requireNonNull(request.fromDate(), "fromDate must not be null");
        LocalDate toDate = Objects.requireNonNull(request.toDate(), "toDate must not be null");

        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate cannot be after toDate");
        }

        BigDecimal workingDays;
        String halfDayPeriod = null;

        if (request.halfDay()) {
            if (!fromDate.equals(toDate)) {
                throw new IllegalArgumentException("Half-day WFH request must have identical fromDate and toDate");
            }
            if (request.halfDayPeriod() == null
                    || (!request.halfDayPeriod().equalsIgnoreCase("first")
                            && !request.halfDayPeriod().equalsIgnoreCase("second"))) {
                throw new IllegalArgumentException("halfDayPeriod must be 'first' or 'second'");
            }
            halfDayPeriod = request.halfDayPeriod().toLowerCase();
            workingDays = new BigDecimal("0.50");
        } else {
            UUID workLocationId = employee.getWorkLocation() != null
                    ? employee.getWorkLocation().getId()
                    : null;
            workingDays =
                    workingDayCalculator.calculateWorkingDays(fromDate, toDate, false, false, false, workLocationId);

            if (workingDays.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Selected date range contains no working days");
            }
        }

        // Mutual exclusion: Check overlap in WFH
        if (requestRepository.existsOverlappingRequest(
                tenantId, employeeId, List.of(WfhRequestStatus.PENDING, WfhRequestStatus.APPROVED), fromDate, toDate)) {
            throw new IllegalArgumentException(
                    "Employee already has an active or approved WFH request overlapping these dates.");
        }

        // Mutual exclusion: Check overlap in Leaves
        if (!leaveRequestRepository
                .findOverlapping(
                        tenantId,
                        employeeId,
                        fromDate,
                        toDate,
                        List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED),
                        null)
                .isEmpty()) {
            throw new IllegalArgumentException(
                    "Employee already has a pending or approved leave request overlapping these dates.");
        }

        // Check available balance
        BigDecimal availableDays = balanceService.getAvailableDays(tenantId, employeeId, fromDate);
        if (availableDays.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "No active WFH quota allocated for this employee. Please contact HR to assign quota.");
        }
        if (availableDays.compareTo(workingDays) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient WFH balance. Available: " + availableDays + ", requested: " + workingDays);
        }

        // Check monthly velocity cap
        if (!balanceService.isWithinMonthlyLimit(tenantId, employeeId, fromDate, workingDays)) {
            throw new IllegalArgumentException("Request exceeds maximum allowable WFH days for the month.");
        }

        WfhRequest entity = new WfhRequest();
        entity.setTenantId(tenantId);
        entity.setEmployeeId(employeeId);
        entity.setFromDate(fromDate);
        entity.setToDate(toDate);
        entity.setHalfDay(request.halfDay());
        entity.setHalfDayPeriod(halfDayPeriod);
        entity.setWorkingDays(workingDays);
        entity.setReason(request.reason() != null ? request.reason().trim() : "");
        entity.setStatus(WfhRequestStatus.PENDING);
        entity.setOnBehalf(onBehalf);

        entity = requestRepository.save(entity);

        if (approvalService != null) {
            try {
                UUID instanceId = approvalService.start(
                        ApprovalFlowType.WORK_FROM_HOME, new SubjectRef("wfh_request", entity.getId()), employeeId);
                entity.setApprovalInstanceId(instanceId);
                entity = requestRepository.save(entity);
            } catch (Exception ex) {
                log.warn("Could not initiate approval flow for WFH request {}: {}", entity.getId(), ex.getMessage());
            }
        }

        String employeeName =
                (employee.getFirstName() + " " + (employee.getLastName() != null ? employee.getLastName() : "")).trim();
        return WfhRequestResponse.from(entity, employeeName, employee.getEmployeeNumber());
    }

    @Override
    @Transactional(readOnly = true)
    public WfhRequestResponse getRequest(UUID tenantId, UUID requestId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");

        WfhRequest entity = requestRepository
                .findByTenantIdAndId(tenantId, requestId)
                .orElseThrow(() -> new NoSuchElementException("WFH request not found: " + requestId));

        Employee emp = employeeRepository
                .findByIdAndTenantIdAndDeletedFalse(entity.getEmployeeId(), tenantId)
                .orElse(null);
        String name = emp != null
                ? (emp.getFirstName() + " " + (emp.getLastName() != null ? emp.getLastName() : "")).trim()
                : "Unknown";
        String number = emp != null ? emp.getEmployeeNumber() : "";
        return WfhRequestResponse.from(entity, name, number);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WfhRequestResponse> listEmployeeRequests(UUID tenantId, UUID employeeId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");

        Employee emp = employeeRepository
                .findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId)
                .orElse(null);
        String name = emp != null
                ? (emp.getFirstName() + " " + (emp.getLastName() != null ? emp.getLastName() : "")).trim()
                : "Unknown";
        String number = emp != null ? emp.getEmployeeNumber() : "";

        return requestRepository.findByTenantIdAndEmployeeIdOrderByFromDateDesc(tenantId, employeeId).stream()
                .map(r -> WfhRequestResponse.from(r, name, number))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WfhRequestResponse> listRequests(UUID tenantId, WfhRequestStatus status) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        List<WfhRequest> list = requestRepository.findAll().stream()
                .filter(r -> r.getTenantId().equals(tenantId))
                .filter(r -> status == null || r.getStatus() == status)
                .toList();

        return list.stream()
                .map(r -> {
                    Employee emp = employeeRepository
                            .findByIdAndTenantIdAndDeletedFalse(r.getEmployeeId(), tenantId)
                            .orElse(null);
                    String name = emp != null
                            ? (emp.getFirstName() + " " + (emp.getLastName() != null ? emp.getLastName() : "")).trim()
                            : "Unknown";
                    String number = emp != null ? emp.getEmployeeNumber() : "";
                    return WfhRequestResponse.from(r, name, number);
                })
                .toList();
    }

    @Override
    public WfhRequestResponse withdraw(UUID tenantId, UUID requestId, UUID callerEmployeeId, String reason) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");

        WfhRequest req = requestRepository
                .findByTenantIdAndId(tenantId, requestId)
                .orElseThrow(() -> new NoSuchElementException("WFH request not found: " + requestId));

        if (req.getStatus() != WfhRequestStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING requests can be withdrawn; current status: " + req.getStatus());
        }

        if (req.getApprovalInstanceId() != null && approvalService != null) {
            approvalService.cancelInstance(tenantId, req.getApprovalInstanceId(), reason);
        }

        req.setStatus(WfhRequestStatus.WITHDRAWN);
        req.setDecidedAt(Instant.now());
        if (reason != null && !reason.isBlank()) {
            req.setReason(
                    req.getReason() != null ? req.getReason() + " | Withdrawn: " + reason : "Withdrawn: " + reason);
        }
        req = requestRepository.save(req);

        Employee emp = employeeRepository
                .findByIdAndTenantIdAndDeletedFalse(req.getEmployeeId(), tenantId)
                .orElse(null);
        String name = emp != null
                ? (emp.getFirstName() + " " + (emp.getLastName() != null ? emp.getLastName() : "")).trim()
                : "Unknown";
        String number = emp != null ? emp.getEmployeeNumber() : "";
        return WfhRequestResponse.from(req, name, number);
    }

    @Override
    public WfhRequestResponse cancel(UUID tenantId, UUID requestId, UUID callerEmployeeId, String reason) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");

        WfhRequest req = requestRepository
                .findByTenantIdAndId(tenantId, requestId)
                .orElseThrow(() -> new NoSuchElementException("WFH request not found: " + requestId));

        if (req.getStatus() != WfhRequestStatus.APPROVED) {
            throw new IllegalStateException(
                    "Only APPROVED requests can be cancelled; current status: " + req.getStatus());
        }

        if (!LocalDate.now(ZoneOffset.UTC).isBefore(req.getFromDate())) {
            throw new IllegalStateException(
                    "WFH request cannot be cancelled on or after start date: " + req.getFromDate());
        }

        req.setStatus(WfhRequestStatus.CANCELLED);
        req.setDecidedAt(Instant.now());
        if (reason != null && !reason.isBlank()) {
            req.setReason(
                    req.getReason() != null ? req.getReason() + " | Cancelled: " + reason : "Cancelled: " + reason);
        }
        req = requestRepository.save(req);

        final UUID targetReqId = req.getId();
        // Reversal row in wfh_consumption
        consumptionRepository
                .findByTenantIdAndWfhRequestIdAndReversesIdIsNull(tenantId, targetReqId)
                .ifPresent(orig -> {
                    WfhConsumption rev = new WfhConsumption();
                    rev.setTenantId(tenantId);
                    rev.setEmployeeId(orig.getEmployeeId());
                    rev.setAllocationId(orig.getAllocationId());
                    rev.setWfhRequestId(targetReqId);
                    rev.setConsumedDays(orig.getConsumedDays().negate().setScale(2, RoundingMode.HALF_UP));
                    rev.setConsumedOn(LocalDate.now(ZoneOffset.UTC));
                    rev.setPeriod(orig.getPeriod());
                    rev.setReversesId(orig.getId());
                    rev.setReason(reason != null ? "Cancellation: " + reason : "Cancelled");
                    consumptionRepository.save(rev);
                });

        // Clear attendance marked as WFH
        for (LocalDate d = req.getFromDate(); !d.isAfter(req.getToDate()); d = d.plusDays(1)) {
            attendanceRepository
                    .findByTenantIdAndEmployeeIdAndAttendanceDate(tenantId, req.getEmployeeId(), d)
                    .ifPresent(att -> {
                        if ("Work From Home".equals(att.getRemarks())) {
                            attendanceRepository.delete(att);
                        }
                    });
        }

        Employee emp = employeeRepository
                .findByIdAndTenantIdAndDeletedFalse(req.getEmployeeId(), tenantId)
                .orElse(null);
        String name = emp != null
                ? (emp.getFirstName() + " " + (emp.getLastName() != null ? emp.getLastName() : "")).trim()
                : "Unknown";
        String number = emp != null ? emp.getEmployeeNumber() : "";
        return WfhRequestResponse.from(req, name, number);
    }
}
