package com.infinevo.core.ai;

/**
 * The one check every AI helper request passes (W-114.1): may the bound tenant make an AI request now.
 *
 * <p>Later parts call {@link #consume()} once per chat message a user sends, before the model is called. Tool calls
 * inside a message are not counted; a message that then fails at the model is still counted.
 */
public interface AiAccessGate {

    /** The bound tenant's switch, cap and this month's usage. A tenant with no subscription row is off. */
    AiStatus status();

    /**
     * Counts one request for the bound tenant.
     *
     * @return the month's count including this request
     * @throws AiDisabledException when the AI helper is off for the tenant, or it has no subscription row
     * @throws AiCapReachedException when the month's count has reached the cap; nothing is counted
     */
    int consume();
}
