package com.infinevo.core.ai.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.leave.LeaveRequestResponse;
import com.infinevo.core.leave.LeaveRequestService;
import com.infinevo.core.wfh.WfhRequestResponse;
import com.infinevo.core.wfh.WfhRequestService;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared pieces of the W-114.17 self-service actions: the payload keys, reading the caller's own request, and the
 * texts. A request of another employee reads exactly like one that does not exist (a hidden thing answers like an
 * unknown one), both when the card is drawn and at confirm.
 */
final class SelfServiceCards {

    static final String REQUEST_ID = "requestId";
    static final String REASON = "reason";
    static final String NO_REASON = "(none)";
    static final String LEAVE_GONE = "That leave request was not found.";
    static final String WFH_GONE = "That work-from-home request was not found.";

    private SelfServiceCards() {}

    /** The caller's own leave request named by the payload, or empty: missing, another employee's, or a bad id. */
    static Optional<LeaveRequestResponse> ownLeave(LeaveRequestService service, AiToolContext ctx, JsonNode payload) {
        UUID id = id(payload);
        if (id == null || ctx.employeeId() == null) {
            return Optional.empty();
        }
        try {
            LeaveRequestResponse r = service.getRequest(ctx.tenantId(), id);
            return r != null && ctx.employeeId().equals(r.employeeId()) ? Optional.of(r) : Optional.empty();
        } catch (NoSuchElementException e) {
            return Optional.empty();
        }
    }

    /** The caller's own WFH request named by the payload, or empty: missing, another employee's, or a bad id. */
    static Optional<WfhRequestResponse> ownWfh(WfhRequestService service, AiToolContext ctx, JsonNode payload) {
        UUID id = id(payload);
        if (id == null || ctx.employeeId() == null) {
            return Optional.empty();
        }
        try {
            WfhRequestResponse r = service.getRequest(ctx.tenantId(), id);
            return r != null && ctx.employeeId().equals(r.employeeId()) ? Optional.of(r) : Optional.empty();
        } catch (NoSuchElementException e) {
            return Optional.empty();
        }
    }

    static UUID id(JsonNode payload) {
        String text = text(payload, REQUEST_ID);
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String text(JsonNode payload, String name) {
        JsonNode value = payload == null ? null : payload.get(name);
        return value == null || value.isNull() ? null : value.asText();
    }

    static String days(BigDecimal days) {
        return days == null ? "" : days.stripTrailingZeros().toPlainString();
    }

    static String halfDay(boolean halfDay, String period) {
        if (!halfDay) {
            return "No";
        }
        return period == null ? "Yes" : "Yes, " + period.toLowerCase(java.util.Locale.ROOT) + " half";
    }

    static String span(Object from, Object to) {
        return String.valueOf(from).equals(String.valueOf(to)) ? "on " + from : "from " + from + " to " + to;
    }
}
