package com.infinevo.core.ai.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.shared.entitlement.PlatformModule;

/**
 * Carries out one kind of action the AI helper may propose (W-114.8). One bean per action; {@link AiActionService}
 * picks it by {@link #actionCode()}. An executor is never a tool: it runs only from {@link AiActionService#confirm},
 * which a signed-in user calls from the browser.
 */
public interface AiActionExecutor {

    /** The code stored on the intent, for example {@code core.leave.apply}. */
    String actionCode();

    /** The action the caller must still hold when they confirm. */
    String requiredAction();

    /** Whether the caller must have an employee record to confirm. True unless the action is not about oneself. */
    default boolean requiresEmployeeProfile() {
        return true;
    }

    /**
     * The module the customer must hold to confirm, or {@code null} for a {@code core} action. The module check on a
     * controller is not reached by an executor, so {@link AiActionService} checks this one itself.
     */
    default PlatformModule requiredModule() {
        return null;
    }

    /**
     * The card for this payload (rules C1, C2): a summary and one field for every value the action will send, free
     * text in full. Built from the payload and the database only, never from text the model wrote.
     */
    AiActionDescription describe(AiToolContext ctx, JsonNode payload);

    /**
     * Runs the stored payload as the caller and answers what was written, for {@code result}.
     *
     * @throws IllegalArgumentException or {@link IllegalStateException} when the service refuses it; the message is
     *     the reason shown to the user
     */
    JsonNode execute(AiToolContext ctx, JsonNode payload);
}
