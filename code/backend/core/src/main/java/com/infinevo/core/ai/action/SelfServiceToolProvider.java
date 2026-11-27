package com.infinevo.core.ai.action;

import static com.infinevo.core.ai.AiToolInputs.date;
import static com.infinevo.core.ai.AiToolInputs.dateProperty;
import static com.infinevo.core.ai.AiToolInputs.enumProperty;
import static com.infinevo.core.ai.AiToolInputs.rejectUnknown;
import static com.infinevo.core.ai.AiToolInputs.schema;
import static com.infinevo.core.ai.AiToolInputs.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiProposedAction;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolInputs.BadInput;
import com.infinevo.core.ai.AiToolProvider;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.leave.LeaveRequestResponse;
import com.infinevo.core.leave.LeaveRequestService;
import com.infinevo.core.leave.LeaveRequestStatus;
import com.infinevo.core.wfh.ApplyWfhRequest;
import com.infinevo.core.wfh.WfhApplyCheck;
import com.infinevo.core.wfh.WfhRequestResponse;
import com.infinevo.core.wfh.WfhRequestService;
import com.infinevo.core.wfh.WfhRequestStatus;
import com.infinevo.shared.authz.PermissionService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The AI helper's self-service tools (W-114.17): the caller's open leave and WFH requests, and proposals to apply for
 * work from home, or to withdraw or cancel one of the caller's own leave or WFH requests. They only propose: nothing
 * changes until the user presses Confirm on the card, an endpoint, not a tool.
 *
 * <p>A request is named by its start date. One employee's active requests of one kind cannot overlap, so a date picks
 * at most one; the model never sees a database id. Only the caller's own requests are searched. A refusal by the rule
 * the service will apply (wrong status, start date reached) goes back as the tool's error and writes no intent; no
 * request on that date answers the caller's open requests so the helper can ask which.
 */
@Component
public class SelfServiceToolProvider implements AiToolProvider {

    static final String OPEN = "core.my_open_requests";
    static final String WFH_REQUEST = "core.propose_wfh_request";
    static final String WFH_WITHDRAW = "core.propose_wfh_withdraw";
    static final String WFH_CANCEL = "core.propose_wfh_cancel";
    static final String LEAVE_WITHDRAW = "core.propose_leave_withdraw";
    static final String LEAVE_CANCEL = "core.propose_leave_cancel";

    static final int MAX_LISTED = 25;
    static final int SEARCH_PAGE = 100;
    static final int MAX_REASON = 500;
    static final String NO_EMPLOYEE = "Not available: the signed-in user has no employee record";
    static final String NOTE = "Nothing has been changed yet. The user must press Confirm on the card.";

    private static final Logger log = LoggerFactory.getLogger(SelfServiceToolProvider.class);
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final LeaveRequestService leaveService;
    private final WfhRequestService wfhService;
    private final AiActionProposals proposals;
    private final PermissionService permissions;
    private final TransactionOperations tx;
    private final Clock clock;
    private final List<AiTool> tools;

    @Autowired
    public SelfServiceToolProvider(
            LeaveRequestService leaveService,
            WfhRequestService wfhService,
            AiActionProposals proposals,
            PermissionService permissions,
            PlatformTransactionManager transactionManager) {
        this(
                leaveService,
                wfhService,
                proposals,
                permissions,
                new TransactionTemplate(transactionManager),
                Clock.systemUTC());
    }

    SelfServiceToolProvider(
            LeaveRequestService leaveService,
            WfhRequestService wfhService,
            AiActionProposals proposals,
            PermissionService permissions,
            TransactionOperations tx,
            Clock clock) {
        this.leaveService = Objects.requireNonNull(leaveService, "leaveService must not be null");
        this.wfhService = Objects.requireNonNull(wfhService, "wfhService must not be null");
        this.proposals = Objects.requireNonNull(proposals, "proposals must not be null");
        this.permissions = Objects.requireNonNull(permissions, "permissions must not be null");
        this.tx = Objects.requireNonNull(tx, "tx must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.tools = List.of(
                new Tool(
                        OPEN,
                        "The signed-in user's own open leave and work-from-home requests: pending or approved, starting"
                                + " today or later, with whether each can be withdrawn (pending) or cancelled (approved,"
                                + " not started). Use it before proposing a withdraw or cancel.",
                        schema(Map.of()),
                        "core.leave.read_own",
                        List.of("core.wfh.read_own"),
                        (ctx, args) -> AiToolResult.success(open(ctx))),
                new Tool(
                        WFH_REQUEST,
                        "Proposes a work-from-home request for the signed-in user. Nothing is applied: the user sees a"
                                + " card and must press Confirm. Use exact dates (YYYY-MM-DD). It is checked first; if"
                                + " it is refused, tell the user why.",
                        schema(
                                Map.of(
                                        "fromDate", dateProperty("First day, YYYY-MM-DD."),
                                        "toDate",
                                                dateProperty("Last day, YYYY-MM-DD. The same as fromDate for one day."),
                                        "halfDay",
                                                NODES.objectNode()
                                                        .put("type", "boolean")
                                                        .put(
                                                                "description",
                                                                "True for a half day; the dates are then the same."),
                                        "halfDayPeriod",
                                                enumProperty(
                                                        "Which half, when halfDay is true.",
                                                        List.of("first", "second")),
                                        "reason", reasonProperty()),
                                "fromDate",
                                "toDate"),
                        "core.wfh.apply",
                        List.of(),
                        this::proposeWfh),
                change(
                        WFH_WITHDRAW,
                        "Proposes withdrawing one of the signed-in user's own pending work-from-home requests, named by"
                                + " its start date. Nothing changes until the user presses Confirm.",
                        "core.wfh.apply",
                        (ctx, args) -> proposeWfhChange(ctx, args, false)),
                change(
                        WFH_CANCEL,
                        "Proposes cancelling one of the signed-in user's own approved work-from-home requests that has"
                                + " not started, named by its start date. Nothing changes until the user presses Confirm.",
                        "core.wfh.apply",
                        (ctx, args) -> proposeWfhChange(ctx, args, true)),
                change(
                        LEAVE_WITHDRAW,
                        "Proposes withdrawing one of the signed-in user's own pending leave requests, named by its"
                                + " start date. Nothing changes until the user presses Confirm.",
                        "core.leave.apply",
                        (ctx, args) -> proposeLeaveChange(ctx, args, false)),
                change(
                        LEAVE_CANCEL,
                        "Proposes cancelling one of the signed-in user's own approved leave requests that has not"
                                + " started, named by its start date; the days go back to the balance. Nothing changes"
                                + " until the user presses Confirm.",
                        "core.leave.apply",
                        (ctx, args) -> proposeLeaveChange(ctx, args, true)));
    }

    @Override
    public List<AiTool> tools() {
        return tools;
    }

    private LocalDate today() {
        // The services' own rule reads the day in UTC (LeaveRequestServiceImpl, WfhRequestServiceImpl cancel).
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    // ---- core.my_open_requests ----

    private ObjectNode open(AiToolContext ctx) {
        return tx.execute(s -> {
            ObjectNode out = NODES.objectNode();
            if (permissions.holds("core.leave.read_own")) {
                ArrayNode leave = out.putArray("leave");
                openLeave(ctx).forEach(r -> leave.add(leaveRow(r)));
            }
            if (permissions.holds("core.wfh.read_own")) {
                ArrayNode wfh = out.putArray("workFromHome");
                openWfh(ctx).forEach(r -> wfh.add(wfhRow(r)));
            }
            return out;
        });
    }

    private List<LeaveRequestResponse> openLeave(AiToolContext ctx) {
        LocalDate today = today();
        return ownLeave(ctx).stream()
                .filter(r -> !r.fromDate().isBefore(today))
                .limit(MAX_LISTED)
                .toList();
    }

    /**
     * Every pending or approved leave request of the caller's, oldest start first, whatever the date: a pending
     * request whose start has passed can still be withdrawn, so withdraw and cancel look here, not in the list.
     */
    private List<LeaveRequestResponse> ownLeave(AiToolContext ctx) {
        List<LeaveRequestResponse> found = new ArrayList<>();
        for (LeaveRequestStatus status : List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED)) {
            found.addAll(leaveService
                    .searchRequests(
                            ctx.tenantId(),
                            ctx.employeeId(),
                            status,
                            // No dates: the search's "date IS NULL OR ..." filter cannot type a bound date in
                            // PostgreSQL (42P18). Newest start first, so the upcoming ones lead; filtered below.
                            null,
                            null,
                            PageRequest.of(0, SEARCH_PAGE))
                    .getContent());
        }
        return found.stream()
                .filter(r -> ctx.employeeId().equals(r.employeeId()))
                .filter(r -> r.fromDate() != null)
                .sorted(Comparator.comparing(LeaveRequestResponse::fromDate))
                .toList();
    }

    private List<WfhRequestResponse> openWfh(AiToolContext ctx) {
        LocalDate today = today();
        return ownWfh(ctx).stream()
                .filter(r -> !r.fromDate().isBefore(today))
                .limit(MAX_LISTED)
                .toList();
    }

    /** Every pending or approved WFH request of the caller's, oldest start first, whatever the date. */
    private List<WfhRequestResponse> ownWfh(AiToolContext ctx) {
        return wfhService.listEmployeeRequests(ctx.tenantId(), ctx.employeeId()).stream()
                .filter(r -> ctx.employeeId().equals(r.employeeId()))
                .filter(r -> r.status() == WfhRequestStatus.PENDING || r.status() == WfhRequestStatus.APPROVED)
                .filter(r -> r.fromDate() != null)
                .sorted(Comparator.comparing(WfhRequestResponse::fromDate))
                .toList();
    }

    private ObjectNode leaveRow(LeaveRequestResponse r) {
        ObjectNode row = NODES.objectNode();
        row.put("leaveType", r.leaveTypeName());
        row.put("from", String.valueOf(r.fromDate()));
        row.put("to", String.valueOf(r.toDate()));
        row.put("days", SelfServiceCards.days(r.workingDays()));
        row.put("status", r.status().name());
        row.put("canWithdraw", r.status() == LeaveRequestStatus.PENDING);
        row.put("canCancel", r.status() == LeaveRequestStatus.APPROVED && today().isBefore(r.fromDate()));
        return row;
    }

    private ObjectNode wfhRow(WfhRequestResponse r) {
        ObjectNode row = NODES.objectNode();
        row.put("from", String.valueOf(r.fromDate()));
        row.put("to", String.valueOf(r.toDate()));
        row.put("halfDay", SelfServiceCards.halfDay(r.halfDay(), r.halfDayPeriod()));
        row.put("status", r.status().name());
        row.put("canWithdraw", r.status() == WfhRequestStatus.PENDING);
        row.put("canCancel", r.status() == WfhRequestStatus.APPROVED && today().isBefore(r.fromDate()));
        return row;
    }

    // ---- core.propose_wfh_request ----

    private AiToolResult proposeWfh(AiToolContext ctx, JsonNode args) {
        LocalDate from = date(args, "fromDate");
        LocalDate to = date(args, "toDate");
        if (from == null || to == null) {
            throw new BadInput("fromDate and toDate are required");
        }
        boolean halfDay = bool(args, "halfDay");
        String period = text(args, "halfDayPeriod");
        String reason = reason(args);
        ApplyWfhRequest request = new ApplyWfhRequest(null, from, to, halfDay, period, reason);
        AiProposedAction card = tx.execute(s -> {
            WfhApplyCheck check = wfhService.validateApply(ctx.tenantId(), ctx.employeeId(), request);
            return proposals.propose(ctx, WfhApplyExecutor.ACTION_CODE, WfhApplyExecutor.payload(request, check));
        });
        return AiToolResult.success(answer(Objects.requireNonNull(card, "card")));
    }

    // ---- withdraw and cancel ----

    private AiToolResult proposeLeaveChange(AiToolContext ctx, JsonNode args, boolean cancel) {
        LocalDate from = startDate(args);
        String reason = reason(args);
        return Objects.requireNonNull(tx.execute(s -> {
            LeaveRequestStatus wanted = cancel ? LeaveRequestStatus.APPROVED : LeaveRequestStatus.PENDING;
            List<LeaveRequestResponse> matches = ownLeave(ctx).stream()
                    .filter(r -> from.equals(r.fromDate()) && r.status() == wanted)
                    .toList();
            if (matches.size() != 1) {
                return notFound(
                        ctx,
                        matches.isEmpty()
                                ? "You have no " + wanted.name().toLowerCase(java.util.Locale.ROOT)
                                        + " leave request starting on " + from + "."
                                : "More than one leave request starts on " + from + ".");
            }
            LeaveRequestResponse r = matches.get(0);
            if (cancel && !today().isBefore(r.fromDate())) {
                // The leave service's own wording (LeaveRequestServiceImpl cancel).
                return AiToolResult.failure(
                        "Leave request cannot be cancelled on or after start date: " + r.fromDate());
            }
            String code = cancel ? LeaveCancelExecutor.ACTION_CODE : LeaveWithdrawExecutor.ACTION_CODE;
            return AiToolResult.success(answer(proposals.propose(ctx, code, LeaveStatusExecutor.payload(r, reason))));
        }));
    }

    private AiToolResult proposeWfhChange(AiToolContext ctx, JsonNode args, boolean cancel) {
        LocalDate from = startDate(args);
        String reason = reason(args);
        return Objects.requireNonNull(tx.execute(s -> {
            WfhRequestStatus wanted = cancel ? WfhRequestStatus.APPROVED : WfhRequestStatus.PENDING;
            List<WfhRequestResponse> matches = ownWfh(ctx).stream()
                    .filter(r -> from.equals(r.fromDate()) && r.status() == wanted)
                    .toList();
            if (matches.size() != 1) {
                return notFound(
                        ctx,
                        matches.isEmpty()
                                ? "You have no " + wanted.name().toLowerCase(java.util.Locale.ROOT)
                                        + " work-from-home request starting on " + from + "."
                                : "More than one work-from-home request starts on " + from + ".");
            }
            WfhRequestResponse r = matches.get(0);
            if (cancel && !today().isBefore(r.fromDate())) {
                // The WFH service's own wording (WfhRequestServiceImpl cancel).
                return AiToolResult.failure("WFH request cannot be cancelled on or after start date: " + r.fromDate());
            }
            String code = cancel ? WfhCancelExecutor.ACTION_CODE : WfhWithdrawExecutor.ACTION_CODE;
            return AiToolResult.success(answer(proposals.propose(ctx, code, WfhStatusExecutor.payload(r, reason))));
        }));
    }

    /** No request, or more than one, on that date: the open requests, so the helper can ask which. No intent. */
    private AiToolResult notFound(AiToolContext ctx, String message) {
        ObjectNode out = NODES.objectNode();
        out.put("found", false);
        out.put("message", message);
        out.set("openRequests", open(ctx));
        return AiToolResult.success(out);
    }

    /** What the model reads: the card, and that the user must confirm. No ids (W-114.4 rule 1). */
    private static JsonNode answer(AiProposedAction card) {
        ObjectNode out = NODES.objectNode();
        out.put("status", card.status().name());
        out.put("summary", card.summary());
        ArrayNode fields = out.putArray("fields");
        for (AiActionField field : card.fields()) {
            fields.addObject().put("label", field.label()).put("value", field.value());
        }
        out.put("note", NOTE);
        return out;
    }

    // ---- inputs ----

    private static ObjectNode reasonProperty() {
        return NODES.objectNode()
                .put("type", "string")
                .put("maxLength", MAX_REASON)
                .put("description", "Why, in the user's words; at most " + MAX_REASON + " characters. Optional.");
    }

    private Tool change(
            String name, String description, String action, BiFunction<AiToolContext, JsonNode, AiToolResult> body) {
        return new Tool(
                name,
                description,
                schema(
                        Map.of(
                                "fromDate", dateProperty("The start date of the request, YYYY-MM-DD."),
                                "reason", reasonProperty()),
                        "fromDate"),
                action,
                List.of(),
                body);
    }

    private static LocalDate startDate(JsonNode args) {
        LocalDate from = date(args, "fromDate");
        if (from == null) {
            throw new BadInput("fromDate is required");
        }
        return from;
    }

    private static String reason(JsonNode args) {
        String reason = text(args, "reason");
        if (reason != null && reason.length() > MAX_REASON) {
            throw new BadInput("reason must be at most " + MAX_REASON + " characters");
        }
        return reason;
    }

    private static boolean bool(JsonNode args, String name) {
        JsonNode value = args.get(name);
        if (value == null || value.isNull()) {
            return false;
        }
        if (!value.isBoolean()) {
            throw new BadInput(name + " must be true or false");
        }
        return value.asBoolean();
    }

    /** One tool: the shared input check, the employee check and the refusal handling around its body. */
    private static final class Tool implements AiTool {

        private final String name;
        private final String description;
        private final ObjectNode inputSchema;
        private final String requiredAction;
        private final List<String> anyOf;
        private final BiFunction<AiToolContext, JsonNode, AiToolResult> body;

        Tool(
                String name,
                String description,
                ObjectNode inputSchema,
                String requiredAction,
                List<String> anyOf,
                BiFunction<AiToolContext, JsonNode, AiToolResult> body) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
            this.requiredAction = requiredAction;
            this.anyOf = List.copyOf(anyOf);
            this.body = body;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }

        @Override
        public String requiredAction() {
            return requiredAction;
        }

        @Override
        public List<String> requiredAnyOf() {
            return anyOf;
        }

        @Override
        public boolean needsEmployee() {
            return true;
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            if (ctx == null || ctx.employeeId() == null) {
                return AiToolResult.failure(NO_EMPLOYEE);
            }
            JsonNode args = input == null || input.isNull() ? NODES.objectNode() : input;
            try {
                if (!args.isObject()) {
                    throw new BadInput("input must be a JSON object");
                }
                rejectUnknown(args, inputSchema.path("properties"));
                return body.apply(ctx, args);
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            } catch (IllegalArgumentException | IllegalStateException e) {
                // The service's refusal, in its own words, so the helper can tell the user why. No intent written.
                log.info("AI self-service proposal {} refused by the service checks", name);
                return AiToolResult.failure(
                        e.getMessage() == null || e.getMessage().isBlank()
                                ? "The request was refused."
                                : e.getMessage());
            }
        }
    }
}
