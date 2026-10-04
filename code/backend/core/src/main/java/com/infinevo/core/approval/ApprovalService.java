package com.infinevo.core.approval;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service orchestrating the approval instance lifecycle, routing, and decisions (W-15.2).
 */
@Service
public class ApprovalService {

    private final ApprovalDefinitionRepository definitionRepository;
    private final ApprovalInstanceRepository instanceRepository;
    private final ApprovalStepRepository stepRepository;
    private final CoreApproverResolver approverResolver;
    private final EmployeeService employeeService;
    private final OutcomeDispatcher outcomeDispatcher;
    private final DelegationService delegationService;
    private final PermissionService permissionService;

    public ApprovalService(
            ApprovalDefinitionRepository definitionRepository,
            ApprovalInstanceRepository instanceRepository,
            ApprovalStepRepository stepRepository,
            CoreApproverResolver approverResolver,
            EmployeeService employeeService,
            OutcomeDispatcher outcomeDispatcher) {
        this(
                definitionRepository,
                instanceRepository,
                stepRepository,
                approverResolver,
                employeeService,
                outcomeDispatcher,
                null,
                null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ApprovalService(
            ApprovalDefinitionRepository definitionRepository,
            ApprovalInstanceRepository instanceRepository,
            ApprovalStepRepository stepRepository,
            CoreApproverResolver approverResolver,
            EmployeeService employeeService,
            OutcomeDispatcher outcomeDispatcher,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    DelegationService delegationService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    PermissionService permissionService) {
        this.definitionRepository =
                Objects.requireNonNull(definitionRepository, "definitionRepository must not be null");
        this.instanceRepository = Objects.requireNonNull(instanceRepository, "instanceRepository must not be null");
        this.stepRepository = Objects.requireNonNull(stepRepository, "stepRepository must not be null");
        this.approverResolver = Objects.requireNonNull(approverResolver, "approverResolver must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.outcomeDispatcher = Objects.requireNonNull(outcomeDispatcher, "outcomeDispatcher must not be null");
        this.delegationService = delegationService;
        this.permissionService = permissionService;
    }

    /**
     * Starts an approval instance for the given flow type and polymorphic subject (12-core-contracts.md §5 row 7).
     */
    @Transactional
    public UUID start(ApprovalFlowType flowType, SubjectRef subject, UUID subjectEmployeeId) {
        return start(flowType, subject, subjectEmployeeId, Collections.emptyList());
    }

    /**
     * Starts an approval instance with optional per-item references for item-level review.
     */
    @Transactional
    public UUID start(ApprovalFlowType flowType, SubjectRef subject, UUID subjectEmployeeId, List<String> itemRefs) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(flowType, "flowType must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(subjectEmployeeId, "subjectEmployeeId must not be null");

        List<ApprovalDefinition> activeDefs =
                definitionRepository.findEffectiveDefinitions(tenantId, flowType, LocalDate.now());
        if (activeDefs.isEmpty()) {
            throw new IllegalStateException("No active approval definition found for flow " + flowType);
        }
        ApprovalDefinition definition = activeDefs.get(0);

        ApprovalInstance instance =
                new ApprovalInstance(tenantId, flowType, definition.getId(), subject, subjectEmployeeId);
        instance = instanceRepository.save(instance);

        List<ApprovalStepDefinition> stepDefs = definition.getSteps();
        int initialStepsCount = definition.getStepOrdering() == StepOrdering.SEQUENTIAL ? 1 : stepDefs.size();
        for (int i = 0; i < initialStepsCount && i < stepDefs.size(); i++) {
            createStepsForIndex(tenantId, instance, flowType, subjectEmployeeId, stepDefs.get(i), i, itemRefs);
        }

        return instance.getId();
    }

    private void createStepsForIndex(
            UUID tenantId,
            ApprovalInstance instance,
            ApprovalFlowType flowType,
            UUID subjectEmployeeId,
            ApprovalStepDefinition stepDef,
            int stepIndex,
            List<String> itemRefs) {
        if (stepDef.perItem() && itemRefs != null && !itemRefs.isEmpty()) {
            for (String itemRef : itemRefs) {
                createSingleStep(tenantId, instance, flowType, subjectEmployeeId, stepDef, stepIndex, itemRef);
            }
        } else {
            createSingleStep(tenantId, instance, flowType, subjectEmployeeId, stepDef, stepIndex, null);
        }
    }

    /**
     * What the approver resolver is told about a step (W-42.2). A {@code PROJECT_MANAGER} step is per item, and its item
     * is the project, so it resolves by the step's {@code itemRef}. Every other kind keeps the definition's
     * {@code assignee}: a per-item {@code ROLE} step, as proof of investment's, still needs its role code, not its item.
     */
    private static String resolverContextRef(ApprovalStepDefinition stepDef, String itemRef) {
        return stepDef.kind() == ApproverKind.PROJECT_MANAGER && itemRef != null ? itemRef : stepDef.assignee();
    }

    private void createSingleStep(
            UUID tenantId,
            ApprovalInstance instance,
            ApprovalFlowType flowType,
            UUID subjectEmployeeId,
            ApprovalStepDefinition stepDef,
            int stepIndex,
            String itemRef) {
        Optional<UUID> assignee = approverResolver.resolve(
                tenantId, subjectEmployeeId, stepDef.kind(), resolverContextRef(stepDef, itemRef));
        UUID assignedId = assignee.orElse(null);
        UUID delegatedFromId = null;
        if (delegationService != null && assignedId != null) {
            Optional<UUID> delegate =
                    delegationService.resolveDelegate(tenantId, assignedId, flowType, LocalDate.now());
            if (delegate.isPresent()) {
                delegatedFromId = assignedId;
                assignedId = delegate.get();
            }
        }
        ApprovalStep step =
                new ApprovalStep(tenantId, instance.getId(), stepIndex, itemRef, stepDef.kind(), assignedId);
        step.setDelegatedFromEmployeeId(delegatedFromId);
        step.setCreatedAt(Instant.now());
        stepRepository.save(step);
    }

    /**
     * Records a decision on an individual approval step.
     */
    @Transactional
    public void decide(UUID stepId, ApprovalDecideRequest request) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(stepId, "stepId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        ApprovalStep step = stepRepository
                .findByTenantIdAndId(tenantId, stepId)
                .orElseThrow(() -> new NoSuchElementException("No approval step found with id " + stepId));

        if (step.getDecision() != null) {
            throw new IllegalStateException("Step " + stepId + " has already been decided");
        }

        // Verify caller is the assigned employee or admin holding core.approval.manage for unassigned step
        Optional<EmployeeResponse> currentEmployee = employeeService.currentEmployee();
        boolean hasManage = permissionService != null && permissionService.holds("core.approval.manage");

        if (step.getAssigneeEmployeeId() == null) {
            if (!hasManage) {
                throw new AccessDeniedException(
                        "Step has no assigned approver and caller does not hold core.approval.manage");
            }
            if (currentEmployee.isPresent()) {
                step.setAssigneeEmployeeId(currentEmployee.get().id());
            }
        } else {
            if (currentEmployee.isEmpty()
                    || !step.getAssigneeEmployeeId()
                            .equals(currentEmployee.get().id())) {
                throw new AccessDeniedException("Caller is not the assigned approver for step " + stepId);
            }
        }

        // Verify step ordering
        ApprovalInstance instance = instanceRepository
                .findByTenantIdAndId(tenantId, step.getInstanceId())
                .orElseThrow(() -> new NoSuchElementException("Approval instance not found: " + step.getInstanceId()));

        ApprovalDefinition definition = definitionRepository
                .findById(instance.getDefinitionId())
                .orElseThrow(() ->
                        new IllegalStateException("Approval definition not found: " + instance.getDefinitionId()));

        if (definition.getStepOrdering() == StepOrdering.SEQUENTIAL) {
            List<ApprovalStep> allSteps =
                    stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instance.getId());
            for (ApprovalStep s : allSteps) {
                if (s.getStepIndex() < step.getStepIndex()) {
                    if (s.getDecision() == null || s.getDecision() != ApprovalDecision.APPROVED) {
                        throw new IllegalStateException(
                                "Sequential ordering requires step " + s.getStepIndex() + " to be approved first");
                    }
                }
            }
        }

        // Validate approved amount
        if (request.approvedAmount() != null) {
            if (request.approvedAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("approvedAmount must not be negative");
            }
            step.setApprovedAmount(request.approvedAmount());
        }

        step.setDecision(request.decision());
        step.setComment(request.comment());
        step.setDecidedAt(Instant.now());
        stepRepository.save(step);

        if (request.decision() == ApprovalDecision.REJECTED) {
            instance.setStatus(InstanceStatus.REJECTED);
            instance.setCompletedAt(Instant.now());
            instanceRepository.save(instance);

            // Cancel any remaining undecided steps for this instance so none survive rejection
            List<ApprovalStep> allSteps =
                    stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instance.getId());
            for (ApprovalStep s : allSteps) {
                if (s.getDecision() == null && !s.getId().equals(step.getId())) {
                    s.setDecision(ApprovalDecision.REJECTED);
                    s.setComment("Instance rejected");
                    s.setDecidedAt(Instant.now());
                    stepRepository.save(s);
                }
            }
            outcomeDispatcher.dispatchAfterCommit(instance.getId());
        } else {
            // Decision is APPROVED
            List<ApprovalStepDefinition> stepDefs = definition.getSteps();
            if (definition.getStepOrdering() == StepOrdering.SEQUENTIAL) {
                // Check if all steps for the current stepIndex are approved
                List<ApprovalStep> currentLevelSteps = stepRepository.findByTenantIdAndInstanceIdAndStepIndex(
                        tenantId, instance.getId(), step.getStepIndex());
                boolean currentLevelAllApproved =
                        currentLevelSteps.stream().allMatch(s -> s.getDecision() == ApprovalDecision.APPROVED);

                if (currentLevelAllApproved) {
                    int nextIndex = step.getStepIndex() + 1;
                    if (nextIndex < stepDefs.size()) {
                        // Activate next sequential step
                        List<ApprovalStep> existingNext = stepRepository.findByTenantIdAndInstanceIdAndStepIndex(
                                tenantId, instance.getId(), nextIndex);
                        if (existingNext.isEmpty()) {
                            List<String> nextItemRefs = null;
                            if (stepDefs.get(nextIndex).perItem()) {
                                nextItemRefs = currentLevelSteps.stream()
                                        .map(ApprovalStep::getItemRef)
                                        .filter(Objects::nonNull)
                                        .toList();
                            }
                            createStepsForIndex(
                                    tenantId,
                                    instance,
                                    instance.getFlowType(),
                                    instance.getSubjectEmployeeId(),
                                    stepDefs.get(nextIndex),
                                    nextIndex,
                                    nextItemRefs);
                        } else {
                            // If next steps already existed (e.g. from tests), activate their clocks
                            for (ApprovalStep nextStep : existingNext) {
                                if (nextStep.getAssigneeEmployeeId() == null) {
                                    Optional<UUID> assignee = approverResolver.resolve(
                                            tenantId,
                                            instance.getSubjectEmployeeId(),
                                            stepDefs.get(nextIndex).kind(),
                                            resolverContextRef(stepDefs.get(nextIndex), nextStep.getItemRef()));
                                    assignee.ifPresent(nextStep::setAssigneeEmployeeId);
                                }
                                nextStep.setCreatedAt(Instant.now());
                                stepRepository.save(nextStep);
                            }
                        }
                    } else {
                        // No more steps: instance is APPROVED
                        instance.setStatus(InstanceStatus.APPROVED);
                        instance.setCompletedAt(Instant.now());
                        instanceRepository.save(instance);
                        outcomeDispatcher.dispatchAfterCommit(instance.getId());
                    }
                }
            } else {
                // ANY_ORDER
                List<ApprovalStep> allSteps =
                        stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instance.getId());
                boolean allApproved = allSteps.size() >= stepDefs.size()
                        && allSteps.stream().allMatch(s -> s.getDecision() == ApprovalDecision.APPROVED);
                if (allApproved) {
                    instance.setStatus(InstanceStatus.APPROVED);
                    instance.setCompletedAt(Instant.now());
                    instanceRepository.save(instance);
                    outcomeDispatcher.dispatchAfterCommit(instance.getId());
                }
            }
        }
    }

    /**
     * Lists pending steps assigned to the current employee, or unassigned steps if the caller holds core.approval.manage.
     */
    @Transactional(readOnly = true)
    public Page<ApprovalStepResponse> getPendingSteps(Pageable pageable) {
        UUID tenantId = TenantContext.require();
        Optional<EmployeeResponse> currentEmployee = employeeService.currentEmployee();
        boolean hasManage = permissionService != null && permissionService.holds("core.approval.manage");

        Page<ApprovalStep> stepPage;
        if (currentEmployee.isPresent()) {
            UUID empId = currentEmployee.get().id();
            if (hasManage) {
                stepPage = stepRepository.findPendingForAssigneeOrUnassigned(tenantId, empId, pageable);
            } else {
                stepPage = stepRepository.findByTenantIdAndAssigneeEmployeeIdAndDecisionIsNullOrderByCreatedAtAsc(
                        tenantId, empId, pageable);
            }
        } else if (hasManage) {
            stepPage = stepRepository.findUnassignedPendingSteps(tenantId, pageable);
        } else {
            return Page.empty(pageable);
        }

        List<UUID> instanceIds = stepPage.getContent().stream()
                .map(ApprovalStep::getInstanceId)
                .distinct()
                .toList();

        Map<UUID, ApprovalInstance> instanceMap = instanceIds.isEmpty()
                ? Collections.emptyMap()
                : instanceRepository.findAllById(instanceIds).stream()
                        .filter(inst -> inst.getTenantId().equals(tenantId))
                        .collect(Collectors.toMap(ApprovalInstance::getId, Function.identity()));

        List<UUID> definitionIds = instanceMap.values().stream()
                .map(ApprovalInstance::getDefinitionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, Integer> totalStepsMap = definitionIds.isEmpty()
                ? Collections.emptyMap()
                : definitionRepository.findAllById(definitionIds).stream()
                        .filter(def -> def.getTenantId().equals(tenantId))
                        .collect(Collectors.toMap(
                                ApprovalDefinition::getId,
                                def -> def.getSteps() != null ? def.getSteps().size() : 1));

        // One lookup for the page, not one per row: the inbox names the subject of each request.
        List<UUID> subjectIds = instanceMap.values().stream()
                .map(ApprovalInstance::getSubjectEmployeeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, String> subjectNames =
                subjectIds.isEmpty() ? Collections.emptyMap() : employeeService.displayNames(subjectIds);

        return stepPage.map(step -> {
            ApprovalInstance inst = instanceMap.get(step.getInstanceId());
            Integer totalSteps =
                    (inst != null && inst.getDefinitionId() != null) ? totalStepsMap.get(inst.getDefinitionId()) : null;
            String subjectName =
                    (inst != null && subjectNames != null) ? subjectNames.get(inst.getSubjectEmployeeId()) : null;
            return ApprovalStepResponse.from(step, inst, totalSteps, subjectName);
        });
    }

    /**
     * Fetches details of an approval instance and all its steps.
     */
    @Transactional(readOnly = true)
    public ApprovalInstanceDetailResponse getInstance(UUID instanceId) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(instanceId, "instanceId must not be null");
        ApprovalInstance instance = instanceRepository
                .findByTenantIdAndId(tenantId, instanceId)
                .orElseThrow(() -> new NoSuchElementException("Approval instance not found: " + instanceId));
        return toDetail(tenantId, instance);
    }

    /**
     * The approval instance for a subject in the bound tenant, with its steps (W-48.5 §4) — lets a
     * module that holds no instance id (hrms reading an overtime request) show who must approve.
     * The repository returns instances unordered; when a subject has more than one, the newest by
     * {@code created_at} wins, ties broken by the higher id. No permission check: the caller decides
     * visibility.
     */
    @Transactional(readOnly = true)
    public Optional<ApprovalInstanceDetailResponse> findInstanceBySubject(SubjectRef subject) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(subject, "subject must not be null");
        return instanceRepository
                .findByTenantIdAndSubjectTableAndSubjectId(tenantId, subject.table(), subject.id())
                .stream()
                .max(Comparator.comparing(
                                ApprovalInstance::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ApprovalInstance::getId))
                .map(instance -> toDetail(tenantId, instance));
    }

    private ApprovalInstanceDetailResponse toDetail(UUID tenantId, ApprovalInstance instance) {
        List<ApprovalStep> steps =
                stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instance.getId());
        return ApprovalInstanceDetailResponse.from(instance, steps);
    }

    /**
     * Fetches detailed history for an approval instance (W-15.3, spec section 4).
     */
    @Transactional(readOnly = true)
    public ApprovalHistoryResponse getHistory(UUID instanceId) {
        UUID tenantId = TenantContext.require();
        Objects.requireNonNull(instanceId, "instanceId must not be null");
        ApprovalInstance instance = instanceRepository
                .findByTenantIdAndId(tenantId, instanceId)
                .orElseThrow(() -> new NoSuchElementException("Approval instance not found: " + instanceId));
        List<ApprovalStep> steps = stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instanceId);
        return new ApprovalHistoryResponse(
                instance.getId(),
                instance.getFlowType(),
                instance.getStatus(),
                instance.getSubjectTable(),
                instance.getSubjectId(),
                instance.getSubjectEmployeeId(),
                instance.getCreatedAt(),
                instance.getCompletedAt(),
                steps.stream()
                        .map(ApprovalHistoryResponse.ApprovalHistoryStepResponse::from)
                        .toList());
    }

    /**
     * Cancels an active approval instance and all its pending steps (W-16.3).
     */
    @Transactional
    public void cancelInstance(UUID tenantId, UUID instanceId, String reason) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(instanceId, "instanceId must not be null");

        instanceRepository.findByTenantIdAndId(tenantId, instanceId).ifPresent(instance -> {
            if (instance.getStatus() == InstanceStatus.PENDING) {
                instance.setStatus(InstanceStatus.REJECTED);
                instance.setCompletedAt(Instant.now());
                instanceRepository.save(instance);

                List<ApprovalStep> openSteps =
                        stepRepository.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenantId, instanceId);
                for (ApprovalStep step : openSteps) {
                    if (step.getDecision() == null) {
                        step.setDecision(ApprovalDecision.REJECTED);
                        step.setComment(reason != null && !reason.isBlank() ? "Withdrawn: " + reason : "Withdrawn");
                        step.setDecidedAt(Instant.now());
                        stepRepository.save(step);
                    }
                }
            }
        });
    }

    @Transactional
    public void cancelInstance(UUID instanceId, String reason) {
        cancelInstance(TenantContext.require(), instanceId, reason);
    }
}
