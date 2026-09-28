package com.infinevo.core.approval;

import java.time.LocalDate;
import java.util.List;

/**
 * Request payload for creating or updating an approval definition (W-15.1, spec section 4).
 */
public record ApprovalDefinitionRequest(
        StepOrdering stepOrdering,
        CommentScope commentScope,
        LocalDate effectiveFrom,
        Boolean isActive,
        List<ApprovalStepDefinition> steps) {

    public ApprovalDefinitionRequest(
            StepOrdering stepOrdering,
            CommentScope commentScope,
            LocalDate effectiveFrom,
            List<ApprovalStepDefinition> steps) {
        this(stepOrdering, commentScope, effectiveFrom, true, steps);
    }
}
