package com.infinevo.core.ai.action;

import java.util.UUID;

/**
 * The AI helper's proposed actions (W-114.8). Generic: it knows nothing about leave; an {@link AiActionExecutor}
 * carries out each action. Confirm, cancel and read are called only by {@link AiActionController}, as the signed-in
 * user; the model can only propose.
 */
public interface AiActionService extends AiActionProposals {

    /**
     * Runs the caller's intent once, under its row lock, after checking it again (spec "Confirm re-checks"). A second
     * confirm of a confirmed intent runs nothing and answers the first result.
     *
     * @throws AiActionNotFoundException not the caller's intent, or none
     * @throws AiActionExpiredException past its time; it is then {@code EXPIRED}
     * @throws AiActionCancelledException cancelled
     * @throws AiActionFailedException refused by the service, now or at an earlier confirm
     * @throws com.infinevo.core.ai.AiDisabledException the helper is off for the customer
     * @throws org.springframework.security.access.AccessDeniedException the permission, module or employee record is
     *     gone
     */
    AiActionOutcome confirm(UUID intentId);

    /**
     * Cancels the caller's intent. Cancelling twice answers the cancelled intent again.
     *
     * @throws AiActionNotFoundException not the caller's intent, or none
     * @throws AiActionConfirmedException already confirmed
     * @throws AiActionExpiredException past its time
     * @throws AiActionFailedException refused at an earlier confirm
     */
    AiActionOutcome cancel(UUID intentId);

    /**
     * The caller's intent with its card, built again from the stored payload. An intent past its time becomes
     * {@code EXPIRED} here, the first time it is read.
     *
     * @throws AiActionNotFoundException not the caller's intent, or none
     */
    AiActionView get(UUID intentId);
}
