package com.infinevo.core.approval;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for configuring, validating, and querying approval definitions (W-15.1, spec section 4).
 */
@Service
public class ApprovalDefinitionService {

    private final ApprovalDefinitionRepository repository;
    private final List<ApproverResolver> approverResolvers;

    @Autowired
    public ApprovalDefinitionService(
            ApprovalDefinitionRepository repository,
            @Autowired(required = false) List<ApproverResolver> approverResolvers) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.approverResolvers = approverResolvers != null ? approverResolvers : Collections.emptyList();
    }

    /**
     * Validates and saves an approval definition for a given flow type under the current tenant.
     */
    @Transactional
    public ApprovalDefinitionResponse saveDefinition(
            UUID tenantId, ApprovalFlowType flowType, ApprovalDefinitionRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(flowType, "flowType must not be null");
        Objects.requireNonNull(request, "request must not be null");

        validate(request);

        LocalDate effectiveFrom = request.effectiveFrom() != null ? request.effectiveFrom() : LocalDate.now();
        StepOrdering ordering = request.stepOrdering() != null ? request.stepOrdering() : StepOrdering.SEQUENTIAL;
        CommentScope commentScope = request.commentScope() != null ? request.commentScope() : CommentScope.PER_STEP;
        boolean isActive = request.isActive() == null || request.isActive();
        String actor = currentActor();

        List<ApprovalStepDefinition> normalizedSteps = normalizeSteps(request.steps());

        Optional<ApprovalDefinition> existing =
                repository.findByTenantIdAndFlowTypeAndEffectiveFrom(tenantId, flowType, effectiveFrom);

        ApprovalDefinition entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setStepOrdering(ordering);
            entity.setCommentScope(commentScope);
            entity.setActive(isActive);
            entity.setSteps(normalizedSteps);
            entity.setUpdatedBy(actor);
        } else {
            entity = new ApprovalDefinition(
                    tenantId, flowType, ordering, normalizedSteps, commentScope, isActive, effectiveFrom, actor);
        }

        ApprovalDefinition saved = repository.save(entity);
        return ApprovalDefinitionResponse.from(saved);
    }

    /**
     * Retrieves all approval definitions for the given tenant.
     */
    @Transactional(readOnly = true)
    public List<ApprovalDefinitionResponse> getAllDefinitions(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return repository.findByTenantId(tenantId).stream()
                .map(ApprovalDefinitionResponse::from)
                .toList();
    }

    /**
     * Retrieves all definitions for a specific flow type under the given tenant.
     */
    @Transactional(readOnly = true)
    public List<ApprovalDefinitionResponse> getDefinitionsByFlowType(UUID tenantId, ApprovalFlowType flowType) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(flowType, "flowType must not be null");
        return repository.findByTenantIdAndFlowType(tenantId, flowType).stream()
                .map(ApprovalDefinitionResponse::from)
                .toList();
    }

    /**
     * Finds the active definition in force for a given flow type on a specific date.
     */
    @Transactional(readOnly = true)
    public Optional<ApprovalDefinitionResponse> getEffectiveDefinition(
            UUID tenantId, ApprovalFlowType flowType, LocalDate asOf) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(flowType, "flowType must not be null");
        LocalDate effectiveDate = asOf != null ? asOf : LocalDate.now();
        return repository.findEffective(tenantId, flowType, effectiveDate).map(ApprovalDefinitionResponse::from);
    }

    /**
     * Returns the raw entity in force for engine consumers (W-15.2).
     */
    @Transactional(readOnly = true)
    public Optional<ApprovalDefinition> definitionFor(UUID tenantId, ApprovalFlowType flowType, LocalDate asOf) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(flowType, "flowType must not be null");
        LocalDate effectiveDate = asOf != null ? asOf : LocalDate.now();
        return repository.findEffective(tenantId, flowType, effectiveDate);
    }

    /**
     * Validates an approval definition request.
     */
    public void validate(ApprovalDefinitionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Approval definition request must not be null");
        }
        if (request.steps() == null || request.steps().isEmpty()) {
            throw new IllegalArgumentException("Approval definition must contain at least one step");
        }

        for (int i = 0; i < request.steps().size(); i++) {
            ApprovalStepDefinition step = request.steps().get(i);
            if (step == null) {
                throw new IllegalArgumentException("Step at index " + i + " must not be null");
            }
            if (step.getKind() == null) {
                throw new IllegalArgumentException("Step at index " + i + " must have a valid approver kind");
            }

            if (step.getKind() == ApproverKind.NAMED_EMPLOYEE) {
                if (step.getAssignee() == null || step.getAssignee().isBlank()) {
                    throw new IllegalArgumentException(
                            "Step at index " + i + ": NAMED_EMPLOYEE requires a valid employee assignee");
                }
            }

            if (step.getKind() == ApproverKind.PROJECT_MANAGER) {
                boolean hasResolver = approverResolvers.stream()
                        .anyMatch(resolver -> resolver.kind() == ApproverKind.PROJECT_MANAGER);
                if (!hasResolver) {
                    throw new IllegalArgumentException(
                            "Step at index " + i + ": PROJECT_MANAGER requires an ApproverResolver bean registered");
                }
            }
        }
    }

    private List<ApprovalStepDefinition> normalizeSteps(List<ApprovalStepDefinition> steps) {
        List<ApprovalStepDefinition> normalized = new ArrayList<>();
        for (ApprovalStepDefinition step : steps) {
            ApprovalStepDefinition copy = new ApprovalStepDefinition(
                    step.getKind(),
                    step.getAssignee(),
                    step.getEscalateAfterDays(), // defaults to 3 if null
                    step.getPerItem() // defaults to false if null
                    );
            normalized.add(copy);
        }
        return normalized;
    }

    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            String name = auth.getName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return "system";
    }
}
