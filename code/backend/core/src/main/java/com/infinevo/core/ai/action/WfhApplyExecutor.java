package com.infinevo.core.ai.action;

import static com.infinevo.core.ai.action.SelfServiceCards.NO_REASON;
import static com.infinevo.core.ai.action.SelfServiceCards.REASON;
import static com.infinevo.core.ai.action.SelfServiceCards.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.wfh.ApplyWfhRequest;
import com.infinevo.core.wfh.WfhApplyCheck;
import com.infinevo.core.wfh.WfhRequestResponse;
import com.infinevo.core.wfh.WfhRequestService;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Applies the caller's own work from home (W-114.17): action {@code core.wfh.apply}. Calls
 * {@link WfhRequestService#apply} - what the portal's {@code MyWfhController} calls - so every WFH check runs again at
 * confirm. The payload holds the request as it is sent and {@code workingDays}, counted at proposal for the card only.
 */
@Component
public class WfhApplyExecutor implements AiActionExecutor {

    public static final String ACTION_CODE = "core.wfh.apply";

    static final String FROM_DATE = "fromDate";
    static final String TO_DATE = "toDate";
    static final String HALF_DAY = "halfDay";
    static final String HALF_DAY_PERIOD = "halfDayPeriod";
    static final String WORKING_DAYS = "workingDays";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final WfhRequestService wfhRequestService;

    public WfhApplyExecutor(WfhRequestService wfhRequestService) {
        this.wfhRequestService = Objects.requireNonNull(wfhRequestService, "wfhRequestService must not be null");
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String requiredAction() {
        return "core.wfh.apply";
    }

    static ObjectNode payload(ApplyWfhRequest request, WfhApplyCheck check) {
        ObjectNode payload = NODES.objectNode();
        payload.put(FROM_DATE, request.fromDate().toString());
        payload.put(TO_DATE, request.toDate().toString());
        payload.put(HALF_DAY, request.halfDay());
        if (check.halfDayPeriod() == null) {
            payload.putNull(HALF_DAY_PERIOD);
        } else {
            payload.put(HALF_DAY_PERIOD, check.halfDayPeriod());
        }
        if (request.reason() == null) {
            payload.putNull(REASON);
        } else {
            payload.put(REASON, request.reason());
        }
        payload.put(WORKING_DAYS, SelfServiceCards.days(check.workingDays()));
        return payload;
    }

    /** The request the payload sends: always the caller's own, never on behalf. */
    static ApplyWfhRequest request(JsonNode payload) {
        return new ApplyWfhRequest(
                null,
                LocalDate.parse(payload.path(FROM_DATE).asText()),
                LocalDate.parse(payload.path(TO_DATE).asText()),
                payload.path(HALF_DAY).asBoolean(false),
                text(payload, HALF_DAY_PERIOD),
                text(payload, REASON));
    }

    @Override
    public AiActionDescription describe(AiToolContext ctx, JsonNode payload) {
        String from = payload.path(FROM_DATE).asText();
        String to = payload.path(TO_DATE).asText();
        boolean halfDay = payload.path(HALF_DAY).asBoolean(false);
        String period = text(payload, HALF_DAY_PERIOD);
        String days = text(payload, WORKING_DAYS);
        String reason = text(payload, REASON);
        return new AiActionDescription(
                "Work from home " + SelfServiceCards.span(from, to) + (halfDay ? " (half day)" : ""),
                List.of(
                        new AiActionField("From", from),
                        new AiActionField("To", to),
                        new AiActionField("Half day", SelfServiceCards.halfDay(halfDay, period)),
                        new AiActionField("Working days", days == null ? "" : days),
                        new AiActionField("Reason", reason == null ? NO_REASON : reason)));
    }

    @Override
    public JsonNode execute(AiToolContext ctx, JsonNode payload) {
        WfhRequestResponse created = wfhRequestService.apply(ctx.tenantId(), ctx.employeeId(), request(payload));
        ObjectNode result = NODES.objectNode();
        result.put("wfhRequestId", created.id() == null ? null : created.id().toString());
        result.put("status", created.status() == null ? null : created.status().name());
        return result;
    }
}
