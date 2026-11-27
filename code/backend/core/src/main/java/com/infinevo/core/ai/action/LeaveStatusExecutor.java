package com.infinevo.core.ai.action;

import static com.infinevo.core.ai.action.SelfServiceCards.LEAVE_GONE;
import static com.infinevo.core.ai.action.SelfServiceCards.NO_REASON;
import static com.infinevo.core.ai.action.SelfServiceCards.REASON;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.leave.LeaveRequestResponse;
import com.infinevo.core.leave.LeaveRequestService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Withdraws or cancels the caller's own leave request (W-114.17). The executor checks the request is the caller's
 * before it calls the leave service, because the service does not and this path does not go through the controller
 * ({@code LeaveRequestController.java:181-188}). The service then runs its own rule again, so a request approved,
 * withdrawn or started in the meantime is refused with the service's message.
 */
abstract class LeaveStatusExecutor implements AiActionExecutor {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    final LeaveRequestService leaveRequestService;

    LeaveStatusExecutor(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = Objects.requireNonNull(leaveRequestService, "leaveRequestService must not be null");
    }

    /** "Withdraw" or "Cancel", the card's verb. */
    abstract String verb();

    /** Calls the service's own withdraw or cancel. */
    abstract LeaveRequestResponse change(AiToolContext ctx, LeaveRequestResponse request, String reason);

    @Override
    public String requiredAction() {
        return "core.leave.apply";
    }

    /** The payload: the request and the reason, nothing else. */
    static ObjectNode payload(LeaveRequestResponse request, String reason) {
        ObjectNode payload = NODES.objectNode();
        payload.put(SelfServiceCards.REQUEST_ID, request.id().toString());
        if (reason == null) {
            payload.putNull(REASON);
        } else {
            payload.put(REASON, reason);
        }
        return payload;
    }

    @Override
    public AiActionDescription describe(AiToolContext ctx, JsonNode payload) {
        Optional<LeaveRequestResponse> found = SelfServiceCards.ownLeave(leaveRequestService, ctx, payload);
        String reason = SelfServiceCards.text(payload, REASON);
        if (found.isEmpty()) {
            return new AiActionDescription(
                    LEAVE_GONE, List.of(new AiActionField("Reason", reason == null ? NO_REASON : reason)));
        }
        LeaveRequestResponse r = found.get();
        String type = r.leaveTypeName() == null ? "leave" : r.leaveTypeName();
        return new AiActionDescription(
                verb() + " your " + type + " " + SelfServiceCards.span(r.fromDate(), r.toDate()),
                List.of(
                        new AiActionField("Leave type", type),
                        new AiActionField("From", String.valueOf(r.fromDate())),
                        new AiActionField("To", String.valueOf(r.toDate())),
                        new AiActionField("Days", SelfServiceCards.days(r.workingDays())),
                        new AiActionField(
                                "Status", r.status() == null ? "" : r.status().name()),
                        new AiActionField("Reason", reason == null ? NO_REASON : reason)));
    }

    @Override
    public JsonNode execute(AiToolContext ctx, JsonNode payload) {
        LeaveRequestResponse own = SelfServiceCards.ownLeave(leaveRequestService, ctx, payload)
                .orElseThrow(() -> new IllegalStateException(LEAVE_GONE));
        LeaveRequestResponse changed = change(ctx, own, SelfServiceCards.text(payload, REASON));
        ObjectNode result = NODES.objectNode();
        result.put("leaveRequestId", changed.id() == null ? null : changed.id().toString());
        result.put("status", changed.status() == null ? null : changed.status().name());
        return result;
    }
}
