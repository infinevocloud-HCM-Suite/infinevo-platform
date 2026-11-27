package com.infinevo.core.ai.action;

import static com.infinevo.core.ai.action.SelfServiceCards.NO_REASON;
import static com.infinevo.core.ai.action.SelfServiceCards.REASON;
import static com.infinevo.core.ai.action.SelfServiceCards.WFH_GONE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.wfh.WfhRequestResponse;
import com.infinevo.core.wfh.WfhRequestService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Withdraws or cancels the caller's own work-from-home request (W-114.17). Neither the WFH service nor this path's
 * caller compares the request's owner with the caller ({@code WfhRequestServiceImpl.java:249-330}), so this check is
 * the only one here. The service then runs its own rule again at confirm.
 */
abstract class WfhStatusExecutor implements AiActionExecutor {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    final WfhRequestService wfhRequestService;

    WfhStatusExecutor(WfhRequestService wfhRequestService) {
        this.wfhRequestService = Objects.requireNonNull(wfhRequestService, "wfhRequestService must not be null");
    }

    abstract String verb();

    abstract WfhRequestResponse change(AiToolContext ctx, WfhRequestResponse request, String reason);

    @Override
    public String requiredAction() {
        return "core.wfh.apply";
    }

    static ObjectNode payload(WfhRequestResponse request, String reason) {
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
        Optional<WfhRequestResponse> found = SelfServiceCards.ownWfh(wfhRequestService, ctx, payload);
        String reason = SelfServiceCards.text(payload, REASON);
        if (found.isEmpty()) {
            return new AiActionDescription(
                    WFH_GONE, List.of(new AiActionField("Reason", reason == null ? NO_REASON : reason)));
        }
        WfhRequestResponse r = found.get();
        return new AiActionDescription(
                verb() + " your work from home " + SelfServiceCards.span(r.fromDate(), r.toDate()),
                List.of(
                        new AiActionField("From", String.valueOf(r.fromDate())),
                        new AiActionField("To", String.valueOf(r.toDate())),
                        new AiActionField("Half day", SelfServiceCards.halfDay(r.halfDay(), r.halfDayPeriod())),
                        new AiActionField(
                                "Status", r.status() == null ? "" : r.status().name()),
                        new AiActionField("Reason", reason == null ? NO_REASON : reason)));
    }

    @Override
    public JsonNode execute(AiToolContext ctx, JsonNode payload) {
        WfhRequestResponse own = SelfServiceCards.ownWfh(wfhRequestService, ctx, payload)
                .orElseThrow(() -> new IllegalStateException(WFH_GONE));
        WfhRequestResponse changed = change(ctx, own, SelfServiceCards.text(payload, REASON));
        ObjectNode result = NODES.objectNode();
        result.put("wfhRequestId", changed.id() == null ? null : changed.id().toString());
        result.put("status", changed.status() == null ? null : changed.status().name());
        return result;
    }
}
