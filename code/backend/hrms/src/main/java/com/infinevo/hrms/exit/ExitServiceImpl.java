package com.infinevo.hrms.exit;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link ExitService} (W-103).
 */
@Service
@Transactional
public class ExitServiceImpl implements ExitService {

    private static final int MAX_ACTOR_LEN = 100;
    private static final Set<ExitStatus> TERMINAL_STATUSES =
            EnumSet.of(ExitStatus.SETTLED, ExitStatus.REJECTED, ExitStatus.WITHDRAWN);

    private static final Map<ClearanceDepartment, List<String>> DEFAULT_CLEARANCE_ITEMS = Map.of(
            ClearanceDepartment.IT, List.of("Laptop & Hardware Return", "Access Revocation & Email Archiving"),
            ClearanceDepartment.FINANCE, List.of("Travel Advances Cleared", "Corporate Credit Card Cancelled"),
            ClearanceDepartment.ADMIN, List.of("ID Card & Access Badge Collected", "Parking Tag Collected"),
            ClearanceDepartment.HR, List.of("Exit Interview Conducted", "Knowledge Transfer Sign-off"));

    private final ExitRequestRepository exitRequestRepository;
    private final ExitClearanceItemRepository clearanceItemRepository;
    private final EmployeeService employeeService;
    private final ApplicationEventPublisher eventPublisher;

    public ExitServiceImpl(
            ExitRequestRepository exitRequestRepository,
            ExitClearanceItemRepository clearanceItemRepository,
            EmployeeService employeeService,
            ApplicationEventPublisher eventPublisher) {
        this.exitRequestRepository =
                Objects.requireNonNull(exitRequestRepository, "exitRequestRepository must not be null");
        this.clearanceItemRepository =
                Objects.requireNonNull(clearanceItemRepository, "clearanceItemRepository must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    @Override
    public ExitRequestResponse submitResignation(SubmitResignationRequest request) {
        UUID tenantId = TenantContext.require();
        if (request == null) {
            throw new ExitValidationException("Request body must not be null", Map.of("request", "required"));
        }
        validateSubmission(request);

        EmployeeResponse employee = requireCurrentEmployee();
        if (exitRequestRepository.existsByTenantIdAndEmployeeIdAndStatusNotIn(
                tenantId, employee.id(), TERMINAL_STATUSES)) {
            throw new ExitConflictException("An active exit request already exists for this employee");
        }

        String actor = currentActor();
        ExitRequest entity = new ExitRequest();
        entity.setTenantId(tenantId);
        entity.setEmployeeId(employee.id());
        entity.setResignationDate(request.resignationDate());
        entity.setRequestedLwd(request.requestedLwd());
        entity.setExitType(
                request.exitType() == null || request.exitType().isBlank()
                        ? "RESIGNATION"
                        : request.exitType().trim().toUpperCase());
        entity.setReasonCategory(request.reasonCategory().trim());
        entity.setReasonNotes(request.reasonNotes());
        entity.setStatus(ExitStatus.SUBMITTED);
        entity.setCreatedBy(actor);
        entity.setUpdatedBy(actor);

        ExitRequest saved = exitRequestRepository.saveAndFlush(entity);
        return toResponse(saved, fullName(employee), List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExitRequestResponse> getMyExits() {
        UUID tenantId = TenantContext.require();
        EmployeeResponse employee = requireCurrentEmployee();
        String name = fullName(employee);
        return exitRequestRepository.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(tenantId, employee.id()).stream()
                .map(exit -> toResponse(exit, name, loadItems(exit.getId(), tenantId)))
                .toList();
    }

    @Override
    public ExitRequestResponse withdrawMyExit(UUID exitId) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse employee = requireCurrentEmployee();
        ExitRequest exit = requireExit(exitId, tenantId);
        if (!exit.getEmployeeId().equals(employee.id())) {
            throw new ExitNotFoundException("Exit request not found: " + exitId);
        }
        if (exit.getStatus() != ExitStatus.SUBMITTED) {
            throw new ExitConflictException("Only SUBMITTED exit requests can be withdrawn");
        }
        exit.setStatus(ExitStatus.WITHDRAWN);
        exit.setUpdatedBy(currentActor());
        ExitRequest saved = exitRequestRepository.saveAndFlush(exit);
        return toResponse(saved, fullName(employee), List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExitRequestResponse> listAllExits(ExitStatus status) {
        UUID tenantId = TenantContext.require();
        List<ExitRequest> rows = status == null
                ? exitRequestRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                : exitRequestRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
        return rows.stream()
                .map(exit ->
                        toResponse(exit, resolveEmployeeName(exit.getEmployeeId()), loadItems(exit.getId(), tenantId)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ExitRequestResponse getExit(UUID exitId) {
        UUID tenantId = TenantContext.require();
        ExitRequest exit = requireExit(exitId, tenantId);
        return toResponse(exit, resolveEmployeeName(exit.getEmployeeId()), loadItems(exit.getId(), tenantId));
    }

    @Override
    public ExitRequestResponse approveExit(UUID exitId, ApproveExitRequest request) {
        UUID tenantId = TenantContext.require();
        ExitRequest exit = requireExit(exitId, tenantId);
        if (exit.getStatus() != ExitStatus.SUBMITTED) {
            throw new ExitConflictException("Only SUBMITTED exit requests can be approved");
        }

        LocalDate lwd =
                request != null && request.approvedLwd() != null ? request.approvedLwd() : exit.getRequestedLwd();
        if (lwd.isBefore(exit.getResignationDate())) {
            throw new ExitValidationException(
                    "Approved last working day must not be before resignation date",
                    Map.of("approvedLwd", "must be on or after resignationDate"));
        }

        String actor = currentActor();
        exit.setApprovedLwd(lwd);
        exit.setApprovedBy(actor);
        exit.setApprovedAt(Instant.now());
        exit.setStatus(ExitStatus.CLEARANCE_IN_PROGRESS);
        exit.setUpdatedBy(actor);
        ExitRequest saved = exitRequestRepository.saveAndFlush(exit);

        List<ExitClearanceItem> items = createDefaultClearances(tenantId, saved.getId(), actor);
        return toResponse(saved, resolveEmployeeName(saved.getEmployeeId()), items);
    }

    @Override
    public ExitRequestResponse rejectExit(UUID exitId, RejectExitRequest request) {
        UUID tenantId = TenantContext.require();
        ExitRequest exit = requireExit(exitId, tenantId);
        if (exit.getStatus() != ExitStatus.SUBMITTED) {
            throw new ExitConflictException("Only SUBMITTED exit requests can be rejected");
        }

        String actor = currentActor();
        exit.setStatus(ExitStatus.REJECTED);
        if (request != null && request.remarks() != null && !request.remarks().isBlank()) {
            exit.setReasonNotes(request.remarks().trim());
        }
        exit.setUpdatedBy(actor);
        ExitRequest saved = exitRequestRepository.saveAndFlush(exit);
        return toResponse(saved, resolveEmployeeName(saved.getEmployeeId()), List.of());
    }

    @Override
    public ExitRequestResponse signOffDepartmentClearance(
            UUID exitId, ClearanceDepartment department, UpdateClearanceRequest request) {
        UUID tenantId = TenantContext.require();
        if (department == null) {
            throw new ExitValidationException("Department is required", Map.of("department", "must not be null"));
        }
        ExitRequest exit = requireExit(exitId, tenantId);
        ensureClearanceMutable(exit);

        ClearanceItemStatus targetStatus = resolveTargetStatus(request);
        String remarks = request == null ? null : request.remarks();
        List<ExitClearanceItem> deptItems =
                clearanceItemRepository.findByExitRequestIdAndTenantIdAndDepartment(exitId, tenantId, department);
        if (deptItems.isEmpty()) {
            throw new ExitNotFoundException("No clearance items found for department " + department);
        }

        String actor = currentActor();
        Instant now = Instant.now();
        for (ExitClearanceItem item : deptItems) {
            applyClearanceUpdate(item, targetStatus, remarks, actor, now);
        }
        clearanceItemRepository.saveAllAndFlush(deptItems);

        return checkAndCompleteClearance(exit, tenantId, actor);
    }

    @Override
    public ExitRequestResponse updateClearanceItem(UUID exitId, UUID itemId, UpdateClearanceRequest request) {
        UUID tenantId = TenantContext.require();
        ExitRequest exit = requireExit(exitId, tenantId);
        ensureClearanceMutable(exit);

        ExitClearanceItem item = clearanceItemRepository
                .findByIdAndExitRequestIdAndTenantId(itemId, exitId, tenantId)
                .orElseThrow(() -> new ExitNotFoundException("Clearance item not found: " + itemId));

        ClearanceItemStatus targetStatus = resolveTargetStatus(request);
        String remarks = request == null ? null : request.remarks();
        String actor = currentActor();
        applyClearanceUpdate(item, targetStatus, remarks, actor, Instant.now());
        clearanceItemRepository.saveAndFlush(item);

        return checkAndCompleteClearance(exit, tenantId, actor);
    }

    @Override
    public PayrollHandOffPayload handOffToPayroll(UUID exitId) {
        UUID tenantId = TenantContext.require();
        ExitRequest exit = requireExit(exitId, tenantId);
        if (exit.getStatus() != ExitStatus.READY_FOR_FNF) {
            throw new ExitConflictException("Exit request must be READY_FOR_FNF before handing off to Payroll");
        }
        boolean pendingExists = clearanceItemRepository.existsByExitRequestIdAndTenantIdAndStatus(
                exitId, tenantId, ClearanceItemStatus.PENDING);
        if (pendingExists) {
            throw new ExitConflictException("Cannot hand off to Payroll while clearance items remain PENDING");
        }

        LocalDate effectiveLwd = exit.getApprovedLwd() != null ? exit.getApprovedLwd() : exit.getRequestedLwd();
        exit.setStatus(ExitStatus.SETTLED);
        exit.setUpdatedBy(currentActor());
        exitRequestRepository.saveAndFlush(exit);

        return new PayrollHandOffPayload(
                exit.getEmployeeId(),
                exit.getResignationDate(),
                effectiveLwd,
                exit.getExitType(),
                countWorkingDaysInExitMonth(effectiveLwd),
                ExitStatus.SETTLED);
    }

    private ExitRequestResponse checkAndCompleteClearance(ExitRequest exit, UUID tenantId, String actor) {
        boolean hasPending = clearanceItemRepository.existsByExitRequestIdAndTenantIdAndStatus(
                exit.getId(), tenantId, ClearanceItemStatus.PENDING);
        if (!hasPending && exit.getStatus() == ExitStatus.CLEARANCE_IN_PROGRESS) {
            exit.setStatus(ExitStatus.READY_FOR_FNF);
            exit.setUpdatedBy(actor);
            exit = exitRequestRepository.saveAndFlush(exit);

            LocalDate effectiveLwd = exit.getApprovedLwd() != null ? exit.getApprovedLwd() : exit.getRequestedLwd();
            eventPublisher.publishEvent(new ExitClearedEvent(
                    tenantId,
                    exit.getId(),
                    exit.getEmployeeId(),
                    exit.getResignationDate(),
                    effectiveLwd,
                    exit.getExitType()));
        }
        return toResponse(exit, resolveEmployeeName(exit.getEmployeeId()), loadItems(exit.getId(), tenantId));
    }

    private List<ExitClearanceItem> createDefaultClearances(UUID tenantId, UUID exitRequestId, String actor) {
        List<ExitClearanceItem> created = new ArrayList<>();
        for (ClearanceDepartment dept : ClearanceDepartment.values()) {
            for (String itemName : DEFAULT_CLEARANCE_ITEMS.getOrDefault(dept, List.of())) {
                ExitClearanceItem item = new ExitClearanceItem();
                item.setTenantId(tenantId);
                item.setExitRequestId(exitRequestId);
                item.setDepartment(dept);
                item.setItemName(itemName);
                item.setStatus(ClearanceItemStatus.PENDING);
                item.setCreatedBy(actor);
                item.setUpdatedBy(actor);
                created.add(item);
            }
        }
        return clearanceItemRepository.saveAllAndFlush(created);
    }

    private List<ExitClearanceItem> loadItems(UUID exitRequestId, UUID tenantId) {
        return clearanceItemRepository.findByExitRequestIdAndTenantIdOrderByDepartmentAscItemNameAsc(
                exitRequestId, tenantId);
    }

    private ExitRequest requireExit(UUID exitId, UUID tenantId) {
        return exitRequestRepository
                .findByIdAndTenantId(exitId, tenantId)
                .orElseThrow(() -> new ExitNotFoundException("Exit request not found: " + exitId));
    }

    private EmployeeResponse requireCurrentEmployee() {
        return employeeService
                .currentEmployee()
                .orElseThrow(() -> new ExitValidationException(
                        "Authenticated user is not linked to an employee record",
                        Map.of("employee", "No employee linked to current user")));
    }

    private String resolveEmployeeName(UUID employeeId) {
        try {
            return fullName(employeeService.get(employeeId));
        } catch (RuntimeException ignored) {
            return employeeId.toString();
        }
    }

    private static String fullName(EmployeeResponse emp) {
        String first = emp.firstName() == null ? "" : emp.firstName().trim();
        String last = emp.lastName() == null ? "" : emp.lastName().trim();
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? emp.employeeNumber() : combined;
    }

    private static void validateSubmission(SubmitResignationRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (request.resignationDate() == null) {
            errors.put("resignationDate", "must not be null");
        }
        if (request.requestedLwd() == null) {
            errors.put("requestedLwd", "must not be null");
        }
        if (request.reasonCategory() == null || request.reasonCategory().isBlank()) {
            errors.put("reasonCategory", "must not be blank");
        }
        if (request.resignationDate() != null
                && request.requestedLwd() != null
                && request.requestedLwd().isBefore(request.resignationDate())) {
            errors.put("requestedLwd", "must be on or after resignationDate");
        }
        if (!errors.isEmpty()) {
            throw new ExitValidationException("Invalid resignation request", errors);
        }
    }

    private static void ensureClearanceMutable(ExitRequest exit) {
        if (exit.getStatus() != ExitStatus.CLEARANCE_IN_PROGRESS && exit.getStatus() != ExitStatus.READY_FOR_FNF) {
            throw new ExitConflictException("Clearances can only be updated while exit is in CLEARANCE_IN_PROGRESS");
        }
    }

    private static ClearanceItemStatus resolveTargetStatus(UpdateClearanceRequest request) {
        if (request == null || request.status() == null) {
            return ClearanceItemStatus.CLEARED;
        }
        return request.status();
    }

    private static void applyClearanceUpdate(
            ExitClearanceItem item, ClearanceItemStatus status, String remarks, String actor, Instant now) {
        item.setStatus(status);
        item.setRemarks(remarks);
        item.setUpdatedBy(actor);
        if (status == ClearanceItemStatus.PENDING) {
            item.setClearedBy(null);
            item.setClearedAt(null);
        } else {
            item.setClearedBy(actor);
            item.setClearedAt(now);
        }
    }

    private static int countWorkingDaysInExitMonth(LocalDate exitDate) {
        LocalDate cursor = exitDate.withDayOfMonth(1);
        int workingDays = 0;
        while (!cursor.isAfter(exitDate)) {
            DayOfWeek dow = cursor.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                workingDays++;
            }
            cursor = cursor.plusDays(1);
        }
        return workingDays;
    }

    private static ExitRequestResponse toResponse(
            ExitRequest exit, String employeeName, List<ExitClearanceItem> items) {
        LocalDate effectiveLwd = exit.getApprovedLwd() != null ? exit.getApprovedLwd() : exit.getRequestedLwd();
        long noticeDays = ChronoUnit.DAYS.between(exit.getResignationDate(), effectiveLwd);
        List<ExitClearanceItemResponse> itemResponses = items.stream()
                .map(item -> new ExitClearanceItemResponse(
                        item.getId(),
                        item.getDepartment(),
                        item.getItemName(),
                        item.getStatus(),
                        item.getClearedBy(),
                        item.getClearedAt(),
                        item.getRemarks()))
                .toList();
        return new ExitRequestResponse(
                exit.getId(),
                exit.getTenantId(),
                exit.getEmployeeId(),
                employeeName,
                exit.getResignationDate(),
                exit.getRequestedLwd(),
                exit.getApprovedLwd(),
                noticeDays,
                exit.getExitType(),
                exit.getReasonCategory(),
                exit.getReasonNotes(),
                exit.getStatus(),
                exit.getApprovedBy(),
                exit.getApprovedAt(),
                exit.getCreatedAt(),
                itemResponses);
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return "system";
        }
        String name = auth.getName();
        return name.length() <= MAX_ACTOR_LEN ? name : name.substring(0, MAX_ACTOR_LEN);
    }
}
