package com.infinevo.core.approval;

import java.util.Optional;
import java.util.UUID;

/**
 * Extension contract for resolving approvers for kinds that require external resolution (W-15.1, spec section 4).
 * Beans implementing this are keyed by {@link #kind()}.
 */
public interface ApproverResolver {

    ApproverKind kind();

    /**
     * Resolves the approver of one step.
     *
     * <p>{@code contextRef} depends on the kind. For {@link ApproverKind#PROJECT_MANAGER} it is the step's item
     * reference (W-42.3: the project id as a UUID string); a step with no item reference passes {@code null}. For
     * {@link ApproverKind#NAMED_EMPLOYEE} and {@link ApproverKind#ROLE} it is the step definition's {@code assignee}.
     */
    Optional<UUID> resolve(UUID tenantId, UUID employeeId, String contextRef);
}
