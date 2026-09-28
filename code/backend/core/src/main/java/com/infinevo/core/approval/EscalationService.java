package com.infinevo.core.approval;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service for approval escalation and manual administrative reassignment (W-15.3, spec section 4).
 */
public interface EscalationService {

    /**
     * Escalates pending steps in the given tenant that have exceeded their definition's
     * {@code escalate_after_days} working days threshold (spec section 3).
     *
     * @param tenantId the tenant ID
     * @param asOf the date of evaluation
     * @return the number of steps escalated
     */
    int escalateOverdueSteps(UUID tenantId, LocalDate asOf);

    /**
     * Administratively reassigns a pending approval step on an instance to a named employee (W-15.3, decision 2).
     *
     * @param tenantId the tenant ID
     * @param instanceId the approval instance ID
     * @param request the reassignment request containing target employee ID and reason
     * @return the updated approval step response
     */
    ApprovalStepResponse reassign(UUID tenantId, UUID instanceId, ApprovalReassignRequest request);
}
