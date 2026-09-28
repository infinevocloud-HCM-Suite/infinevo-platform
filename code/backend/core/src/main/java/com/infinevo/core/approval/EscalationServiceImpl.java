package com.infinevo.core.approval;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.org.ReportingLineService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production implementation of {@link EscalationService} (W-15.3, spec section 4).
 */
@Service
@Transactional
public class EscalationServiceImpl implements EscalationService {

    private final ApprovalInstanceRepository instanceRepository;
    private final ApprovalDefinitionRepository definitionRepository;
    private final ApprovalStepRepository stepRepository;
    private final EmployeeRepository employeeRepository;
    private final ReportingLineService reportingLineService;
    private final HolidayQueryService holidayQueryService;

    @Autowired
    public EscalationServiceImpl(
            ApprovalInstanceRepository instanceRepository,
            ApprovalDefinitionRepository definitionRepository,
            ApprovalStepRepository stepRepository,
            EmployeeRepository employeeRepository,
            ReportingLineService reportingLineService,
            @Autowired(required = false) HolidayQueryService holidayQueryService) {
        this.instanceRepository = Objects.requireNonNull(instanceRepository, "instanceRepository must not be null");
        this.definitionRepository =
                Objects.requireNonNull(definitionRepository, "definitionRepository must not be null");
        this.stepRepository = Objects.requireNonNull(stepRepository, "stepRepository must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
        this.reportingLineService =
                Objects.requireNonNull(reportingLineService, "reportingLineService must not be null");
        this.holidayQueryService = holidayQueryService != null ? holidayQueryService : new DefaultHolidayQueryService();
    }

    @Override
    public int escalateOverdueSteps(UUID tenantId, LocalDate asOf) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        LocalDate evaluationDate = asOf != null ? asOf : LocalDate.now(ZoneOffset.UTC);

        boolean clearTenant = false;
        if (!TenantContext.isBound()) {
            TenantContext.set(tenantId);
            clearTenant = true;
        }

        try {
            List<ApprovalStep> pendingSteps =
                    stepRepository.findByTenantIdAndDecisionIsNullOrderByCreatedAtAsc(tenantId);
            int escalatedCount = 0;

            for (ApprovalStep step : pendingSteps) {
                UUID assigneeId = step.getAssigneeEmployeeId();
                if (assigneeId == null) {
                    // Already unassignable, avoid cycling
                    continue;
                }

                Optional<ApprovalInstance> instanceOpt =
                        instanceRepository.findByTenantIdAndId(tenantId, step.getInstanceId());
                if (instanceOpt.isEmpty()) {
                    continue;
                }
                ApprovalInstance instance = instanceOpt.get();
                if (instance.getStatus() != InstanceStatus.PENDING) {
                    continue;
                }

                int threshold = ApprovalStepDefinition.DEFAULT_ESCALATE_AFTER_DAYS;
                Optional<ApprovalDefinition> defOpt = definitionRepository.findById(instance.getDefinitionId());
                if (defOpt.isPresent()) {
                    List<ApprovalStepDefinition> stepDefs = defOpt.get().getSteps();
                    if (step.getStepIndex() >= 0 && step.getStepIndex() < stepDefs.size()) {
                        Integer days = stepDefs.get(step.getStepIndex()).getEscalateAfterDays();
                        if (days != null) {
                            threshold = days;
                        }
                    }
                }

                Instant baseline =
                        (step.getEscalatedFromEmployeeId() != null || step.getReassignedFromEmployeeId() != null)
                                        && step.getUpdatedAt() != null
                                ? step.getUpdatedAt()
                                : (step.getCreatedAt() != null ? step.getCreatedAt() : step.getUpdatedAt());
                if (baseline == null) {
                    baseline = Instant.now();
                }
                LocalDate fromDate = baseline.atZone(ZoneOffset.UTC).toLocalDate();

                UUID workLocationId = null;
                Optional<Employee> empOpt = employeeRepository.findById(assigneeId);
                if (empOpt.isPresent() && empOpt.get().getWorkLocation() != null) {
                    workLocationId = empOpt.get().getWorkLocation().getId();
                }
                List<LocalDate> holidays =
                        holidayQueryService.holidaysBetween(workLocationId, fromDate, evaluationDate);

                int workingDays = countWorkingDays(fromDate, evaluationDate, holidays);
                if (workingDays > threshold) {
                    // Overdue! Escalate one level up via ReportingLineService
                    List<Employee> chain = reportingLineService.chainAbove(assigneeId, evaluationDate);
                    step.setEscalatedFromEmployeeId(assigneeId);
                    if (chain != null && !chain.isEmpty() && chain.get(0) != null) {
                        step.setAssigneeEmployeeId(chain.get(0).getId());
                    } else {
                        // An approver with no manager above yields unassignable rather than looping
                        step.setAssigneeEmployeeId(null);
                    }
                    step.setUpdatedAt(Instant.now());
                    stepRepository.save(step);
                    escalatedCount++;
                }
            }
            return escalatedCount;
        } finally {
            if (clearTenant) {
                TenantContext.clear();
            }
        }
    }

    @Override
    public ApprovalStepResponse reassign(UUID tenantId, UUID instanceId, ApprovalReassignRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(instanceId, "instanceId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        UUID targetEmployeeId = Objects.requireNonNull(request.employeeId(), "employeeId must not be null");

        Employee target = employeeRepository
                .findById(targetEmployeeId)
                .filter(e -> e.getTenantId().equals(tenantId) && !e.isDeleted())
                .orElseThrow(
                        () -> new IllegalArgumentException("Target employee not found in tenant: " + targetEmployeeId));

        ApprovalInstance instance = instanceRepository
                .findByTenantIdAndId(tenantId, instanceId)
                .orElseThrow(() -> new NoSuchElementException("Approval instance not found: " + instanceId));

        if (instance.getStatus() != InstanceStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot reassign step for non-pending instance: status is " + instance.getStatus());
        }

        if (targetEmployeeId.equals(instance.getSubjectEmployeeId())) {
            throw new IllegalArgumentException("Target cannot be the requester");
        }

        List<ApprovalStep> steps = stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instanceId);
        ApprovalStep pendingStep = steps.stream()
                .filter(s -> s.getDecision() == null)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No pending step found for instance " + instanceId));

        pendingStep.setReassignedFromEmployeeId(pendingStep.getAssigneeEmployeeId());
        pendingStep.setAssigneeEmployeeId(targetEmployeeId);
        pendingStep.setReassignReason(request.reason());
        pendingStep.setUpdatedAt(Instant.now());

        ApprovalStep saved = stepRepository.save(pendingStep);
        return ApprovalStepResponse.from(saved);
    }

    /**
     * Counts working days between {@code from} and {@code to}, excluding weekends (Saturday/Sunday) and holidays.
     * Starts evaluation from the day after {@code from} up to {@code to} inclusive.
     */
    public static int countWorkingDays(LocalDate from, LocalDate to, List<LocalDate> holidays) {
        if (from == null || to == null || from.isAfter(to) || from.isEqual(to)) {
            return 0;
        }
        Set<LocalDate> holidaySet = holidays != null ? new HashSet<>(holidays) : Collections.emptySet();
        int count = 0;
        LocalDate current = from.plusDays(1);
        while (!current.isAfter(to)) {
            DayOfWeek dow = current.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !holidaySet.contains(current)) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
    }
}
