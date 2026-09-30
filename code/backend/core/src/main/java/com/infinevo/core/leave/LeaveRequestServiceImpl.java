package com.infinevo.core.leave;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.SubjectRef;
import com.infinevo.core.document.Document;
import com.infinevo.core.document.DocumentRepository;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.org.WorkLocation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link LeaveRequestService} (W-16.3).
 */
@Service
@Transactional
public class LeaveRequestServiceImpl implements LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveRequestDocumentRepository leaveRequestDocumentRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeavePolicyRepository leavePolicyRepository;
    private final LeaveEligibilityService leaveEligibilityService;
    private final LeaveBalanceService leaveBalanceService;
    private final WorkingDayCalculator workingDayCalculator;
    private final ApprovalService approvalService;
    private final ApprovalInstanceRepository approvalInstanceRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveConsumptionService leaveConsumptionService;
    private final DocumentRepository documentRepository;
    private final EmployeeService employeeService;

    public LeaveRequestServiceImpl(
            LeaveRequestRepository leaveRequestRepository,
            LeaveRequestDocumentRepository leaveRequestDocumentRepository,
            LeaveTypeRepository leaveTypeRepository,
            LeavePolicyRepository leavePolicyRepository,
            LeaveEligibilityService leaveEligibilityService,
            LeaveBalanceService leaveBalanceService,
            WorkingDayCalculator workingDayCalculator,
            ApprovalService approvalService,
            ApprovalInstanceRepository approvalInstanceRepository,
            EmployeeRepository employeeRepository,
            LeaveConsumptionService leaveConsumptionService) {
        this(
                leaveRequestRepository,
                leaveRequestDocumentRepository,
                leaveTypeRepository,
                leavePolicyRepository,
                leaveEligibilityService,
                leaveBalanceService,
                workingDayCalculator,
                approvalService,
                approvalInstanceRepository,
                employeeRepository,
                leaveConsumptionService,
                null,
                null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LeaveRequestServiceImpl(
            LeaveRequestRepository leaveRequestRepository,
            LeaveRequestDocumentRepository leaveRequestDocumentRepository,
            LeaveTypeRepository leaveTypeRepository,
            LeavePolicyRepository leavePolicyRepository,
            LeaveEligibilityService leaveEligibilityService,
            LeaveBalanceService leaveBalanceService,
            WorkingDayCalculator workingDayCalculator,
            ApprovalService approvalService,
            ApprovalInstanceRepository approvalInstanceRepository,
            EmployeeRepository employeeRepository,
            LeaveConsumptionService leaveConsumptionService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    DocumentRepository documentRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) EmployeeService employeeService) {
        this.leaveRequestRepository = Objects.requireNonNull(leaveRequestRepository);
        this.leaveRequestDocumentRepository = Objects.requireNonNull(leaveRequestDocumentRepository);
        this.leaveTypeRepository = Objects.requireNonNull(leaveTypeRepository);
        this.leavePolicyRepository = Objects.requireNonNull(leavePolicyRepository);
        this.leaveEligibilityService = Objects.requireNonNull(leaveEligibilityService);
        this.leaveBalanceService = Objects.requireNonNull(leaveBalanceService);
        this.workingDayCalculator = Objects.requireNonNull(workingDayCalculator);
        this.approvalService = Objects.requireNonNull(approvalService);
        this.approvalInstanceRepository = Objects.requireNonNull(approvalInstanceRepository);
        this.employeeRepository = Objects.requireNonNull(employeeRepository);
        this.leaveConsumptionService = Objects.requireNonNull(leaveConsumptionService);
        this.documentRepository = documentRepository;
        this.employeeService = employeeService;
    }

    @Override
    public LeaveRequestResponse createRequest(UUID tenantId, UUID employeeId, LeaveApplyRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        LocalDate fromDate = Objects.requireNonNull(request.fromDate(), "fromDate must not be null");
        LocalDate toDate = Objects.requireNonNull(request.toDate(), "toDate must not be null");
        boolean isHalfDay = Boolean.TRUE.equals(request.isHalfDay());
        validateDatesAndHalfDay(fromDate, toDate, isHalfDay, request.halfDayPeriod());

        UUID leaveTypeId = Objects.requireNonNull(request.leaveTypeId(), "leaveTypeId must not be null");
        LeaveType leaveType = leaveTypeRepository
                .findByTenantIdAndId(tenantId, leaveTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Leave type not found: " + leaveTypeId));

        if (!leaveType.isActive()) {
            throw new IllegalArgumentException("Leave type is not active: " + leaveType.getName());
        }
        if (isHalfDay && !leaveType.isAllowHalfDay()) {
            throw new IllegalArgumentException("Half-day leave is not allowed for leave type: " + leaveType.getName());
        }

        if (!leaveEligibilityService.isEligible(tenantId, employeeId, leaveTypeId, fromDate)) {
            throw new IllegalArgumentException("Employee is not eligible for leave type: " + leaveType.getName());
        }

        Optional<LeavePolicy> policyOpt =
                leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                tenantId, leaveTypeId, fromDate);

        validatePolicyConstraints(policyOpt.orElse(null), fromDate, request.documentIds());
        validateDocuments(tenantId, employeeId, request.documentIds());

        UUID workLocationId = employeeRepository
                .findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId)
                .map(Employee::getWorkLocation)
                .map(WorkLocation::getId)
                .orElse(null);

        BigDecimal workingDays = workingDayCalculator.calculateWorkingDays(
                fromDate, toDate, isHalfDay, policyOpt.orElse(null), workLocationId);

        if (policyOpt.isPresent() && policyOpt.get().getMaxDaysPerApplication() != null) {
            BigDecimal maxDays = policyOpt.get().getMaxDaysPerApplication();
            if (workingDays.compareTo(maxDays) > 0) {
                throw new IllegalArgumentException(
                        "Working days (" + workingDays + ") exceeds max days per application (" + maxDays + ")");
            }
        }

        checkOverlap(tenantId, employeeId, fromDate, toDate, null);

        boolean submit = request.shouldSubmit();
        if (submit) {
            validateBalanceLimit(
                    policyOpt.orElse(null), tenantId, employeeId, leaveTypeId, fromDate, workingDays, null);
        }

        LeaveRequestStatus status = submit ? LeaveRequestStatus.PENDING : LeaveRequestStatus.DRAFT;
        LeaveRequest entity = new LeaveRequest(
                tenantId,
                employeeId,
                leaveTypeId,
                fromDate,
                toDate,
                isHalfDay,
                request.halfDayPeriod(),
                workingDays,
                request.reason(),
                status,
                null,
                false);
        entity = leaveRequestRepository.save(entity);

        List<UUID> docIds = request.documentIds();
        if (docIds != null && !docIds.isEmpty()) {
            for (UUID docId : docIds) {
                leaveRequestDocumentRepository.save(new LeaveRequestDocument(tenantId, entity.getId(), docId));
            }
        }

        if (submit) {
            UUID instanceId = approvalService.start(
                    ApprovalFlowType.LEAVE, new SubjectRef("leave_request", entity.getId()), employeeId);
            entity.setApprovalInstanceId(instanceId);
            entity = leaveRequestRepository.save(entity);
        }

        return LeaveRequestResponse.from(entity, docIds);
    }

    @Override
    public LeaveRequestResponse createOnBehalf(UUID tenantId, LeaveOnBehalfRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        UUID employeeId = Objects.requireNonNull(request.employeeId(), "employeeId must not be null");

        // Reject self-on-behalf (W-16.3 review item 20)
        if (employeeService != null) {
            employeeService.currentEmployee().ifPresent(caller -> {
                if (caller.id() != null && caller.id().equals(employeeId)) {
                    throw new IllegalArgumentException("Cannot create leave request on behalf of oneself");
                }
            });
        }

        LocalDate fromDate = Objects.requireNonNull(request.fromDate(), "fromDate must not be null");
        LocalDate toDate = Objects.requireNonNull(request.toDate(), "toDate must not be null");
        boolean isHalfDay = Boolean.TRUE.equals(request.isHalfDay());
        validateDatesAndHalfDay(fromDate, toDate, isHalfDay, request.halfDayPeriod());

        UUID leaveTypeId = Objects.requireNonNull(request.leaveTypeId(), "leaveTypeId must not be null");
        LeaveType leaveType = leaveTypeRepository
                .findByTenantIdAndId(tenantId, leaveTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Leave type not found: " + leaveTypeId));

        if (!leaveType.isActive()) {
            throw new IllegalArgumentException("Leave type is not active: " + leaveType.getName());
        }
        if (isHalfDay && !leaveType.isAllowHalfDay()) {
            throw new IllegalArgumentException("Half-day leave is not allowed for leave type: " + leaveType.getName());
        }

        if (!leaveEligibilityService.isEligible(tenantId, employeeId, leaveTypeId, fromDate)) {
            throw new IllegalArgumentException("Employee is not eligible for leave type: " + leaveType.getName());
        }

        Optional<LeavePolicy> policyOpt =
                leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                tenantId, leaveTypeId, fromDate);

        validatePolicyConstraints(policyOpt.orElse(null), fromDate, request.documentIds());
        validateDocuments(tenantId, employeeId, request.documentIds());

        UUID workLocationId = employeeRepository
                .findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId)
                .map(Employee::getWorkLocation)
                .map(WorkLocation::getId)
                .orElse(null);

        BigDecimal workingDays = workingDayCalculator.calculateWorkingDays(
                fromDate, toDate, isHalfDay, policyOpt.orElse(null), workLocationId);

        checkOverlap(tenantId, employeeId, fromDate, toDate, null);

        // Balance limit check on-behalf too (W-16.3 review item 20)
        validateBalanceLimit(policyOpt.orElse(null), tenantId, employeeId, leaveTypeId, fromDate, workingDays, null);

        // Administrator data entry (D-35): created APPROVED without an approval instance
        LeaveRequest entity = new LeaveRequest(
                tenantId,
                employeeId,
                leaveTypeId,
                fromDate,
                toDate,
                isHalfDay,
                request.halfDayPeriod(),
                workingDays,
                request.reason(),
                LeaveRequestStatus.APPROVED,
                null,
                true);
        entity.setDecidedAt(Instant.now());
        entity = leaveRequestRepository.save(entity);

        List<UUID> docIds = request.documentIds();
        if (docIds != null && !docIds.isEmpty()) {
            for (UUID docId : docIds) {
                leaveRequestDocumentRepository.save(new LeaveRequestDocument(tenantId, entity.getId(), docId));
            }
        }

        leaveConsumptionService.consume(entity);

        return LeaveRequestResponse.from(entity, docIds);
    }

    @Override
    public LeaveRequestResponse submit(UUID tenantId, UUID requestId, UUID callerEmployeeId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");

        LeaveRequest req = leaveRequestRepository
                .findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Leave request not found: " + requestId));

        if (!req.getStatus().canTransitionTo(LeaveRequestStatus.PENDING)) {
            throw new IllegalStateException(
                    "Only DRAFT leave requests can be submitted; current status: " + req.getStatus());
        }

        checkOverlap(tenantId, req.getEmployeeId(), req.getFromDate(), req.getToDate(), req.getId());

        Optional<LeavePolicy> policyOpt =
                leavePolicyRepository
                        .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                tenantId, req.getLeaveTypeId(), req.getFromDate());

        List<UUID> docIds =
                leaveRequestDocumentRepository.findByTenantIdAndLeaveRequestId(tenantId, req.getId()).stream()
                        .map(LeaveRequestDocument::getDocumentId)
                        .toList();

        validatePolicyConstraints(policyOpt.orElse(null), req.getFromDate(), docIds);
        validateDocuments(tenantId, req.getEmployeeId(), docIds);
        validateBalanceLimit(
                policyOpt.orElse(null),
                tenantId,
                req.getEmployeeId(),
                req.getLeaveTypeId(),
                req.getFromDate(),
                req.getWorkingDays(),
                req.getId());

        UUID instanceId = approvalService.start(
                ApprovalFlowType.LEAVE, new SubjectRef("leave_request", req.getId()), req.getEmployeeId());
        req.setStatus(LeaveRequestStatus.PENDING);
        req.setApprovalInstanceId(instanceId);
        req = leaveRequestRepository.save(req);

        return LeaveRequestResponse.from(req, docIds);
    }

    @Override
    public LeaveRequestResponse withdraw(UUID tenantId, UUID requestId, UUID callerEmployeeId, String reason) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");

        LeaveRequest req = leaveRequestRepository
                .findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Leave request not found: " + requestId));

        if (!req.getStatus().canTransitionTo(LeaveRequestStatus.WITHDRAWN)) {
            throw new IllegalStateException(
                    "Only PENDING leave requests can be withdrawn; current status: " + req.getStatus());
        }

        if (req.getApprovalInstanceId() != null) {
            approvalService.cancelInstance(tenantId, req.getApprovalInstanceId(), reason);
        }

        req.setStatus(LeaveRequestStatus.WITHDRAWN);
        if (reason != null && !reason.isBlank()) {
            req.setReason(
                    req.getReason() != null ? req.getReason() + " | Withdrawn: " + reason : "Withdrawn: " + reason);
        }
        req = leaveRequestRepository.save(req);

        List<UUID> docIds =
                leaveRequestDocumentRepository.findByTenantIdAndLeaveRequestId(tenantId, req.getId()).stream()
                        .map(LeaveRequestDocument::getDocumentId)
                        .toList();
        return LeaveRequestResponse.from(req, docIds);
    }

    @Override
    public LeaveRequestResponse cancel(UUID tenantId, UUID requestId, UUID callerEmployeeId, String reason) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");

        LeaveRequest req = leaveRequestRepository
                .findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Leave request not found: " + requestId));

        if (!req.getStatus().canTransitionTo(LeaveRequestStatus.CANCELLED)) {
            throw new IllegalStateException(
                    "Only APPROVED leave requests can be cancelled; current status: " + req.getStatus());
        }

        if (!LocalDate.now(ZoneOffset.UTC).isBefore(req.getFromDate())) {
            throw new IllegalStateException(
                    "Leave request cannot be cancelled on or after start date: " + req.getFromDate());
        }

        req.setStatus(LeaveRequestStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) {
            req.setReason(
                    req.getReason() != null ? req.getReason() + " | Cancelled: " + reason : "Cancelled: " + reason);
        }
        req = leaveRequestRepository.save(req);

        leaveConsumptionService.cancel(req, reason);

        List<UUID> docIds =
                leaveRequestDocumentRepository.findByTenantIdAndLeaveRequestId(tenantId, req.getId()).stream()
                        .map(LeaveRequestDocument::getDocumentId)
                        .toList();
        return LeaveRequestResponse.from(req, docIds);
    }

    @Override
    @Transactional(readOnly = true)
    public LeaveRequestResponse getRequest(UUID tenantId, UUID requestId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");

        LeaveRequest req = leaveRequestRepository
                .findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Leave request not found: " + requestId));

        List<UUID> docIds =
                leaveRequestDocumentRepository.findByTenantIdAndLeaveRequestId(tenantId, req.getId()).stream()
                        .map(LeaveRequestDocument::getDocumentId)
                        .toList();
        return LeaveRequestResponse.from(req, docIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> searchRequests(
            UUID tenantId,
            UUID employeeId,
            LeaveRequestStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Page<LeaveRequest> page =
                leaveRequestRepository.search(tenantId, employeeId, status, fromDate, toDate, pageable);
        return page.map(req -> {
            List<UUID> docIds =
                    leaveRequestDocumentRepository.findByTenantIdAndLeaveRequestId(tenantId, req.getId()).stream()
                            .map(LeaveRequestDocument::getDocumentId)
                            .toList();
            return LeaveRequestResponse.from(req, docIds);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> searchRequestsForEmployees(
            UUID tenantId,
            Collection<UUID> employeeIds,
            LeaveRequestStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (employeeIds == null || employeeIds.isEmpty()) {
            return Page.empty(pageable);
        }
        Page<LeaveRequest> page =
                leaveRequestRepository.searchForEmployees(tenantId, employeeIds, status, fromDate, toDate, pageable);
        return page.map(req -> {
            List<UUID> docIds =
                    leaveRequestDocumentRepository.findByTenantIdAndLeaveRequestId(tenantId, req.getId()).stream()
                            .map(LeaveRequestDocument::getDocumentId)
                            .toList();
            return LeaveRequestResponse.from(req, docIds);
        });
    }

    private void validateDatesAndHalfDay(
            LocalDate fromDate, LocalDate toDate, boolean isHalfDay, HalfDayPeriod halfDayPeriod) {
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("From date cannot be after to date: " + fromDate + " > " + toDate);
        }
        if (isHalfDay) {
            if (!fromDate.equals(toDate)) {
                throw new IllegalArgumentException("Half-day leave must be on a single date");
            }
            if (halfDayPeriod == null) {
                throw new IllegalArgumentException("halfDayPeriod is required when isHalfDay is true");
            }
        } else {
            if (halfDayPeriod != null) {
                throw new IllegalArgumentException("halfDayPeriod must be null when isHalfDay is false");
            }
        }
    }

    private void validateDocuments(UUID tenantId, UUID employeeId, List<UUID> docIds) {
        if (docIds == null || docIds.isEmpty() || documentRepository == null) {
            return;
        }
        for (UUID docId : docIds) {
            Document doc = documentRepository
                    .findByIdAndTenantIdAndDeletedFalse(docId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found in tenant: " + docId));
            if (doc.getEmployeeId() != null && !doc.getEmployeeId().equals(employeeId)) {
                throw new IllegalArgumentException("Document does not belong to employee: " + docId);
            }
        }
    }

    private void validatePolicyConstraints(LeavePolicy policy, LocalDate fromDate, List<UUID> docIds) {
        if (policy == null) {
            return;
        }
        if (policy.getFutureBookingLimitDays() != null) {
            LocalDate maxFuture = LocalDate.now(ZoneOffset.UTC).plusDays(policy.getFutureBookingLimitDays());
            if (fromDate.isAfter(maxFuture)) {
                throw new IllegalArgumentException(
                        "Request exceeds future booking limit of " + policy.getFutureBookingLimitDays() + " days");
            }
        }
        if (policy.getPastBookingLimitDays() != null) {
            LocalDate minPast = LocalDate.now(ZoneOffset.UTC).minusDays(policy.getPastBookingLimitDays());
            if (fromDate.isBefore(minPast)) {
                throw new IllegalArgumentException(
                        "Request exceeds past booking limit of " + policy.getPastBookingLimitDays() + " days");
            }
        }
        if (policy.isRequiresDocument() && (docIds == null || docIds.isEmpty())) {
            throw new IllegalArgumentException("Leave policy requires supporting documents to be attached");
        }
    }

    private void validateBalanceLimit(
            LeavePolicy policy,
            UUID tenantId,
            UUID employeeId,
            UUID leaveTypeId,
            LocalDate fromDate,
            BigDecimal workingDays,
            UUID currentRequestId) {
        if (policy == null || policy.getExceedBalanceMode() == null) {
            return;
        }
        ExceedBalanceMode mode = policy.getExceedBalanceMode();
        if (mode == ExceedBalanceMode.NO_LIMIT || mode == ExceedBalanceMode.MARK_AS_LOP) {
            // No limit never refuses; markAsLOP leaves excess to W-16.4a
            return;
        }
        if (mode == ExceedBalanceMode.YEAR_END_LIMIT) {
            BigDecimal currentRemaining = leaveBalanceService
                    .getBalance(tenantId, employeeId, leaveTypeId, fromDate)
                    .map(LeaveBalanceResponse::remainingDays)
                    .orElse(BigDecimal.ZERO);

            BigDecimal pendingDays = BigDecimal.ZERO;
            List<LeaveRequest> pendingRequests =
                    leaveRequestRepository.findByTenantIdAndEmployeeIdAndLeaveTypeIdAndStatus(
                            tenantId, employeeId, leaveTypeId, LeaveRequestStatus.PENDING);
            for (LeaveRequest pending : pendingRequests) {
                if (currentRequestId == null || !pending.getId().equals(currentRequestId)) {
                    pendingDays = pendingDays.add(pending.getWorkingDays());
                }
            }

            BigDecimal limit =
                    policy.getExceedBalanceLimitDays() != null ? policy.getExceedBalanceLimitDays() : BigDecimal.ZERO;
            BigDecimal projected = currentRemaining.subtract(pendingDays).subtract(workingDays);
            if (projected.compareTo(limit.negate()) < 0) {
                throw new IllegalStateException(
                        "Requested leave exceeds allowable negative balance limit of " + limit + " days");
            }
        }
    }

    private void checkOverlap(UUID tenantId, UUID employeeId, LocalDate fromDate, LocalDate toDate, UUID excludeId) {
        List<LeaveRequest> overlapping = leaveRequestRepository.findOverlapping(
                tenantId,
                employeeId,
                fromDate,
                toDate,
                List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED, LeaveRequestStatus.DRAFT),
                excludeId);
        if (!overlapping.isEmpty()) {
            throw new IllegalStateException("An existing active leave request overlaps with the requested dates");
        }
    }
}
