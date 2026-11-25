package com.infinevo.core.ai.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.leave.HalfDayPeriod;
import com.infinevo.core.leave.LeaveApplyCheck;
import com.infinevo.core.leave.LeaveApplyRequest;
import com.infinevo.core.leave.LeaveRequestResponse;
import com.infinevo.core.leave.LeaveRequestService;
import com.infinevo.core.leave.LeaveType;
import com.infinevo.core.leave.LeaveTypeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Applies the caller's own leave (W-114.8): action {@code core.leave.apply}. Builds a {@link LeaveApplyRequest} from
 * the stored payload and calls {@link LeaveRequestService#createRequest} with {@code submit = true}, the method the
 * Leave screen's {@code POST /api/v1/leave-requests} calls, so every leave check runs again at confirm.
 *
 * <p>The payload holds the request exactly as it is sent - {@code leaveTypeId}, {@code fromDate}, {@code toDate},
 * {@code isHalfDay}, {@code halfDayPeriod}, {@code reason}, {@code submit} - and {@code workingDays}, the days the leave
 * service counted at proposal, shown on the card only. The service counts them again when it saves.
 */
@Component
public class LeaveApplyExecutor implements AiActionExecutor {

    public static final String ACTION_CODE = "core.leave.apply";

    static final String LEAVE_TYPE_ID = "leaveTypeId";
    static final String FROM_DATE = "fromDate";
    static final String TO_DATE = "toDate";
    static final String IS_HALF_DAY = "isHalfDay";
    static final String HALF_DAY_PERIOD = "halfDayPeriod";
    static final String REASON = "reason";
    static final String SUBMIT = "submit";
    static final String WORKING_DAYS = "workingDays";

    static final String UNKNOWN_TYPE = "(leave type no longer exists)";
    static final String NO_REASON = "(none)";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final LeaveRequestService leaveRequestService;
    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveApplyExecutor(LeaveRequestService leaveRequestService, LeaveTypeRepository leaveTypeRepository) {
        this.leaveRequestService = Objects.requireNonNull(leaveRequestService, "leaveRequestService must not be null");
        this.leaveTypeRepository = Objects.requireNonNull(leaveTypeRepository, "leaveTypeRepository must not be null");
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String requiredAction() {
        return "core.leave.apply";
    }

    /** The payload for an accepted request: what will be sent, and the days counted now for the card. */
    static ObjectNode payload(LeaveApplyRequest request, LeaveApplyCheck check) {
        ObjectNode payload = NODES.objectNode();
        payload.put(LEAVE_TYPE_ID, request.leaveTypeId().toString());
        payload.put(FROM_DATE, request.fromDate().toString());
        payload.put(TO_DATE, request.toDate().toString());
        payload.put(IS_HALF_DAY, Boolean.TRUE.equals(request.isHalfDay()));
        if (request.halfDayPeriod() == null) {
            payload.putNull(HALF_DAY_PERIOD);
        } else {
            payload.put(HALF_DAY_PERIOD, request.halfDayPeriod().getDbValue());
        }
        if (request.reason() == null) {
            payload.putNull(REASON);
        } else {
            payload.put(REASON, request.reason());
        }
        payload.put(SUBMIT, true);
        payload.put(WORKING_DAYS, check.workingDays().toPlainString());
        return payload;
    }

    /** The request the payload sends: always the caller's own, always submitted, never with documents. */
    static LeaveApplyRequest request(JsonNode payload) {
        String period = text(payload, HALF_DAY_PERIOD);
        return new LeaveApplyRequest(
                UUID.fromString(payload.path(LEAVE_TYPE_ID).asText()),
                LocalDate.parse(payload.path(FROM_DATE).asText()),
                LocalDate.parse(payload.path(TO_DATE).asText()),
                payload.path(IS_HALF_DAY).asBoolean(false),
                period == null ? null : HalfDayPeriod.fromString(period),
                text(payload, REASON),
                null,
                true);
    }

    @Override
    public AiActionDescription describe(AiToolContext ctx, JsonNode payload) {
        String typeName =
                leaveTypeName(ctx.tenantId(), payload.path(LEAVE_TYPE_ID).asText(null));
        String from = payload.path(FROM_DATE).asText();
        String to = payload.path(TO_DATE).asText();
        boolean halfDay = payload.path(IS_HALF_DAY).asBoolean(false);
        String period = text(payload, HALF_DAY_PERIOD);
        String days = text(payload, WORKING_DAYS);
        String reason = text(payload, REASON);

        List<AiActionField> fields = new ArrayList<>();
        fields.add(new AiActionField("Leave type", typeName));
        fields.add(new AiActionField("From", from));
        fields.add(new AiActionField("To", to));
        fields.add(new AiActionField("Half day", halfDayText(halfDay, period)));
        fields.add(new AiActionField("Days", days == null ? "" : days));
        fields.add(new AiActionField("Reason", reason == null ? NO_REASON : reason));
        fields.add(new AiActionField("Send for approval", payload.path(SUBMIT).asBoolean(true) ? "Yes" : "No"));

        String when = from.equals(to) ? "on " + from : "from " + from + " to " + to;
        String summary = "Apply for " + typeName + " " + when
                + (halfDay ? " (half day, " + (period == null ? "" : period) + " half)" : "")
                + (days == null ? "" : ", " + days + " " + ("1".equals(stripped(days)) ? "day" : "days"));
        return new AiActionDescription(summary, fields);
    }

    @Override
    public JsonNode execute(AiToolContext ctx, JsonNode payload) {
        LeaveRequestResponse created =
                leaveRequestService.createRequest(ctx.tenantId(), ctx.employeeId(), request(payload));
        ObjectNode result = NODES.objectNode();
        result.put("leaveRequestId", created.id() == null ? null : created.id().toString());
        result.put("status", created.status() == null ? null : created.status().name());
        return result;
    }

    private String leaveTypeName(UUID tenantId, String leaveTypeId) {
        if (leaveTypeId == null) {
            return UNKNOWN_TYPE;
        }
        try {
            return leaveTypeRepository
                    .findByTenantIdAndId(tenantId, UUID.fromString(leaveTypeId))
                    .map(LeaveType::getName)
                    .orElse(UNKNOWN_TYPE);
        } catch (IllegalArgumentException e) {
            return UNKNOWN_TYPE;
        }
    }

    private static String halfDayText(boolean halfDay, String period) {
        if (!halfDay) {
            return "No";
        }
        return period == null ? "Yes" : "Yes, " + period + " half";
    }

    private static String stripped(String days) {
        try {
            return new BigDecimal(days).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return days;
        }
    }

    private static String text(JsonNode payload, String name) {
        JsonNode value = payload.get(name);
        return value == null || value.isNull() ? null : value.asText();
    }
}
