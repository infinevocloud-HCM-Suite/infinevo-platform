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
import com.infinevo.core.leave.HalfDayPeriod;
import com.infinevo.core.leave.LeaveApplyCheck;
import com.infinevo.core.leave.LeaveApplyRequest;
import com.infinevo.core.leave.LeaveRequestService;
import com.infinevo.core.leave.LeaveType;
import com.infinevo.core.leave.LeaveTypeRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The AI helper's first action tool (W-114.8): {@code core.propose_leave_request}. It only proposes - nothing is
 * written to {@code core.leave_request} until the user presses Confirm, which is an endpoint, not a tool.
 *
 * <p>The leave type is matched by name, ignoring case, among the tenant's active types. The request is checked by
 * {@link LeaveRequestService#validateApply}, the same checks the Leave screen runs; a refusal goes back to the model
 * as the tool's error and writes no intent. A type whose policy needs a document is refused: the chat cannot attach
 * one. On success the payload is stored through {@link AiActionProposals} and the card's fields are returned.
 *
 * <p>The tool depends on {@link AiActionProposals} only, never on {@link AiActionService}, so it cannot confirm.
 */
@Component
public class LeaveActionToolProvider implements AiToolProvider {

    static final String NAME = "core.propose_leave_request";
    static final int MAX_REASON = 500;

    static final String NEEDS_DOCUMENT = "This leave type needs a supporting document, which the chat cannot attach;"
            + " please apply on the Leave screen.";

    private static final Logger log = LoggerFactory.getLogger(LeaveActionToolProvider.class);
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final LeaveRequestService leaveRequestService;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AiActionProposals proposals;
    private final TransactionOperations tx;
    private final List<AiTool> tools;

    @Autowired
    public LeaveActionToolProvider(
            LeaveRequestService leaveRequestService,
            LeaveTypeRepository leaveTypeRepository,
            AiActionProposals proposals,
            PlatformTransactionManager transactionManager) {
        this(leaveRequestService, leaveTypeRepository, proposals, new TransactionTemplate(transactionManager));
    }

    LeaveActionToolProvider(
            LeaveRequestService leaveRequestService,
            LeaveTypeRepository leaveTypeRepository,
            AiActionProposals proposals,
            TransactionOperations tx) {
        this.leaveRequestService = Objects.requireNonNull(leaveRequestService, "leaveRequestService must not be null");
        this.leaveTypeRepository = Objects.requireNonNull(leaveTypeRepository, "leaveTypeRepository must not be null");
        this.proposals = Objects.requireNonNull(proposals, "proposals must not be null");
        this.tx = Objects.requireNonNull(tx, "tx must not be null");
        this.tools = List.of(new ProposeLeaveTool());
    }

    @Override
    public List<AiTool> tools() {
        return tools;
    }

    private final class ProposeLeaveTool implements AiTool {

        private final ObjectNode inputSchema = schema(
                Map.of(
                        "leaveType",
                        NODES.objectNode()
                                .put("type", "string")
                                .put("description", "The leave type by its name, for example Casual Leave."),
                        "fromDate",
                        dateProperty("First day of leave, YYYY-MM-DD."),
                        "toDate",
                        dateProperty("Last day of leave, YYYY-MM-DD. The same as fromDate for one day."),
                        "halfDay",
                        NODES.objectNode()
                                .put("type", "boolean")
                                .put("description", "True for a half day; fromDate and toDate are then the same."),
                        "halfDayPeriod",
                        enumProperty("Which half, when halfDay is true.", List.of("first", "second")),
                        "reason",
                        NODES.objectNode()
                                .put("type", "string")
                                .put("maxLength", MAX_REASON)
                                .put(
                                        "description",
                                        "Why, in the user's words; at most " + MAX_REASON + " characters.")),
                "leaveType",
                "fromDate",
                "toDate");

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public String description() {
            return "Proposes a leave request for the signed-in user. Nothing is applied: the user sees a card with"
                    + " the details and must press Confirm themselves. Use exact dates (YYYY-MM-DD). The leave"
                    + " request is checked first; if it is refused, tell the user why.";
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }

        @Override
        public String requiredAction() {
            return "core.leave.apply";
        }

        @Override
        public boolean needsEmployee() {
            return true;
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            if (ctx == null || ctx.employeeId() == null) {
                return AiToolResult.failure("Not available: the signed-in user has no employee record");
            }
            JsonNode args = input == null || input.isNull() ? NODES.objectNode() : input;
            try {
                if (!args.isObject()) {
                    throw new BadInput("input must be a JSON object");
                }
                rejectUnknown(args, inputSchema.path("properties"));
                String typeName = text(args, "leaveType");
                LocalDate from = date(args, "fromDate");
                LocalDate to = date(args, "toDate");
                if (typeName == null || from == null || to == null) {
                    throw new BadInput("leaveType, fromDate and toDate are required");
                }
                boolean halfDay = bool(args, "halfDay");
                String periodText = text(args, "halfDayPeriod");
                HalfDayPeriod period = periodText == null ? null : period(periodText);
                String reason = text(args, "reason");
                if (reason != null && reason.length() > MAX_REASON) {
                    throw new BadInput("reason must be at most " + MAX_REASON + " characters");
                }

                AiProposedAction card = tx.execute(s -> {
                    LeaveType type = leaveType(ctx, typeName);
                    LeaveApplyRequest request =
                            new LeaveApplyRequest(type.getId(), from, to, halfDay, period, reason, null, true);
                    LeaveApplyCheck check =
                            leaveRequestService.validateApply(ctx.tenantId(), ctx.employeeId(), request);
                    return proposals.propose(
                            ctx, LeaveApplyExecutor.ACTION_CODE, LeaveApplyExecutor.payload(request, check));
                });
                return AiToolResult.success(answer(Objects.requireNonNull(card, "card")));
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            } catch (IllegalArgumentException | IllegalStateException e) {
                // The leave service's refusal, in its own words, so the helper can tell the user why.
                log.info("AI leave proposal refused by the leave checks");
                return AiToolResult.failure(refusal(e.getMessage()));
            }
        }
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
        out.put("note", "Nothing has been applied yet. The user must press Confirm on the card.");
        return out;
    }

    private LeaveType leaveType(AiToolContext ctx, String name) {
        List<LeaveType> active = leaveTypeRepository.findByTenantIdAndIsActiveTrueOrderByCodeAsc(ctx.tenantId());
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        List<LeaveType> matches = active.stream()
                .filter(t -> t.getName() != null
                        && t.getName().trim().toLowerCase(Locale.ROOT).equals(wanted))
                .toList();
        if (matches.size() == 1) {
            return matches.get(0);
        }
        String valid = active.isEmpty()
                ? "there are no active leave types"
                : "the valid names are "
                        + String.join(
                                ", ", active.stream().map(LeaveType::getName).toList());
        throw new BadInput(
                (matches.isEmpty() ? "No active leave type is named '" : "More than one leave type is named '")
                        + name.trim() + "'; " + valid + ".");
    }

    private static String refusal(String message) {
        if (message != null && message.contains("requires supporting documents")) {
            return NEEDS_DOCUMENT;
        }
        return message == null || message.isBlank() ? "The leave request was refused." : message;
    }

    private static HalfDayPeriod period(String value) {
        try {
            return HalfDayPeriod.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadInput("halfDayPeriod must be first or second");
        }
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
}
