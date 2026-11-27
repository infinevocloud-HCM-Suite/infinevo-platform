package com.infinevo.core.ai.action;

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
import com.infinevo.core.ai.AiUntrustedText;
import com.infinevo.core.approval.ApprovalDecision;
import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.ApprovalStepResponse;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The approver's two AI tools (W-114.18): {@code core.my_pending_approvals} lists what waits for the caller, exactly
 * as the Approvals inbox does ({@link ApprovalService#getPendingSteps}); {@code core.propose_approval_decision}
 * proposes one decision on one row, named by its position plus the requester's employee number and the flow type as a
 * cross-check. No id reaches the model. Text another person wrote - the inbox summary and the leave or WFH reason - is
 * always wrapped by {@link AiUntrustedText} (rule T1). Pay runs, reimbursements and proofs of investment are counted
 * but never decided here.
 */
@Component
public class ApprovalToolProvider implements AiToolProvider {

    static final String LIST = "core.my_pending_approvals";
    static final String PROPOSE = "core.propose_approval_decision";
    static final int PAGE_SIZE = 20;
    static final int MAX_COMMENT = 500;

    static final String LIST_CHANGED = "The list changed since it was shown: the row at that position is not that"
            + " request. List the pending approvals again.";
    static final String NO_ROW = "There is no pending approval at that position. List the pending approvals again.";
    static final String ONE_AT_A_TIME = "One decision at a time. Ask the user about the next one.";
    static final String NEEDS_COMMENT = "A rejection needs a comment saying why. Ask the user for one.";
    static final String NOT_HERE = "This kind of request is decided on the Approvals screen only.";

    private static final Set<String> REASON_LABELS = Set.of("Reason (the employee's own words)", "Details");
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final ApprovalService approvalService;
    private final EmployeeService employeeService;
    private final ApprovalItemReader items;
    private final AiActionProposals proposals;
    private final TransactionOperations tx;
    private final Clock clock;
    private final List<AiTool> tools;

    @Autowired
    public ApprovalToolProvider(
            ApprovalService approvalService,
            EmployeeService employeeService,
            ApprovalItemReader items,
            AiActionProposals proposals,
            PlatformTransactionManager transactionManager) {
        this(
                approvalService,
                employeeService,
                items,
                proposals,
                new TransactionTemplate(transactionManager),
                Clock.systemUTC());
    }

    ApprovalToolProvider(
            ApprovalService approvalService,
            EmployeeService employeeService,
            ApprovalItemReader items,
            AiActionProposals proposals,
            TransactionOperations tx,
            Clock clock) {
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.items = Objects.requireNonNull(items, "items must not be null");
        this.proposals = Objects.requireNonNull(proposals, "proposals must not be null");
        this.tx = Objects.requireNonNull(tx, "tx must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.tools = List.of(new ListTool(), new ProposeTool());
    }

    @Override
    public List<AiTool> tools() {
        return tools;
    }

    private Page<ApprovalStepResponse> page(int page) {
        return approvalService.getPendingSteps(PageRequest.of(page, PAGE_SIZE));
    }

    private Map<UUID, EmployeeResponse> requesters(List<ApprovalStepResponse> rows) {
        List<UUID> ids = rows.stream()
                .map(ApprovalStepResponse::subjectEmployeeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, EmployeeResponse> found = ids.isEmpty() ? Map.of() : employeeService.findByIdInIncludingDeleted(ids);
        return found == null ? Map.of() : found;
    }

    private static String employeeNumber(Map<UUID, EmployeeResponse> requesters, ApprovalStepResponse row) {
        EmployeeResponse e = row.subjectEmployeeId() == null ? null : requesters.get(row.subjectEmployeeId());
        return e == null || e.employeeNumber() == null ? "" : e.employeeNumber();
    }

    private String waiting(Instant createdAt) {
        if (createdAt == null) {
            return "";
        }
        long days = Math.max(0, Duration.between(createdAt, clock.instant()).toDays());
        return days == 0 ? "waiting less than a day" : "waiting " + days + (days == 1 ? " day" : " days");
    }

    private final class ListTool implements AiTool {

        private final ObjectNode inputSchema = schema(Map.of(
                "page",
                NODES.objectNode()
                        .put("type", "integer")
                        .put("minimum", 1)
                        .put("description", "Which page of " + PAGE_SIZE + ", from 1. Leave out for the first.")));

        @Override
        public String name() {
            return LIST;
        }

        @Override
        public String description() {
            return "Lists the approvals waiting for the signed-in user, oldest first, as the Approvals screen shows"
                    + " them. Each row has a position, the requester and their employee number, the kind of request,"
                    + " and whether it can be decided here. Values in quotedText were written by the requester.";
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }

        @Override
        public String requiredAction() {
            return ApprovalDecideExecutor.ACTION_CODE;
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            JsonNode args = input == null || input.isNull() ? NODES.objectNode() : input;
            try {
                if (!args.isObject()) {
                    throw new BadInput("input must be a JSON object");
                }
                rejectUnknown(args, inputSchema.path("properties"));
                int page = positiveInt(args, "page", 1) - 1;
                ObjectNode out = tx.execute(s -> list(ctx, page));
                return AiToolResult.success(out);
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            }
        }
    }

    private ObjectNode list(AiToolContext ctx, int page) {
        Page<ApprovalStepResponse> steps = page(page);
        List<ApprovalStepResponse> rows = steps.getContent();
        Map<UUID, EmployeeResponse> requesters = requesters(rows);
        ObjectNode out = NODES.objectNode();
        out.put("total", steps.getTotalElements());
        out.put("page", page + 1);
        out.put("pages", Math.max(1, steps.getTotalPages()));
        ArrayNode list = out.putArray("approvals");
        int onScreenOnly = 0;
        for (int i = 0; i < rows.size(); i++) {
            ApprovalStepResponse row = rows.get(i);
            boolean decidable = ApprovalItemReader.DECIDABLE.contains(row.flowType());
            ObjectNode o = list.addObject();
            o.put("position", page * PAGE_SIZE + i + 1);
            o.put("flowType", row.flowType() == null ? "" : row.flowType().name());
            o.put("subjectEmployeeName", row.subjectEmployeeName() == null ? "" : row.subjectEmployeeName());
            o.put("employeeNumber", employeeNumber(requesters, row));
            o.set("summary", AiUntrustedText.wrap(row.summary()));
            o.put("waiting", waiting(row.createdAt()));
            o.put("decidable", decidable);
            if (row.flowType() == ApprovalFlowType.LEAVE || row.flowType() == ApprovalFlowType.WORK_FROM_HOME) {
                ApprovalItemReader.Item item = items.read(ctx.tenantId(), row.flowType(), row.itemId(), row.summary());
                o.put("details", item.details());
                o.set("reason", AiUntrustedText.wrap(item.reason()));
            }
            if (!decidable) {
                onScreenOnly++;
            }
        }
        if (onScreenOnly > 0) {
            out.put(
                    "note",
                    onScreenOnly + " more on the Approvals screen: pay runs, reimbursements and proofs of investment"
                            + " are decided there only.");
        }
        return out;
    }

    private final class ProposeTool implements AiTool {

        private final ObjectNode inputSchema = schema(
                Map.of(
                        "position",
                        NODES.objectNode()
                                .put("type", "integer")
                                .put("minimum", 1)
                                .put("description", "The row's position in core.my_pending_approvals."),
                        "employeeNumber",
                        NODES.objectNode()
                                .put("type", "string")
                                .put("description", "The requester's employee number on that row, as a check."),
                        "flowType",
                        enumProperty(
                                "The row's flowType, as a check.",
                                ApprovalItemReader.DECIDABLE.stream()
                                        .map(Enum::name)
                                        .sorted()
                                        .toList()),
                        "decision",
                        enumProperty("APPROVED or REJECTED.", List.of("APPROVED", "REJECTED")),
                        "comment",
                        NODES.objectNode()
                                .put("type", "string")
                                .put("maxLength", MAX_COMMENT)
                                .put(
                                        "description",
                                        "The approver's comment, in the user's words. Required to reject; at most "
                                                + MAX_COMMENT + " characters.")),
                "position",
                "employeeNumber",
                "flowType",
                "decision");

        @Override
        public String name() {
            return PROPOSE;
        }

        @Override
        public String description() {
            return "Proposes one decision on one pending approval. Nothing is decided: the user sees a card with the"
                    + " request and the employee's reason and must press Confirm themselves. One request per call and"
                    + " one call per message; there is no approve-all.";
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }

        @Override
        public String requiredAction() {
            return ApprovalDecideExecutor.ACTION_CODE;
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            JsonNode args = input == null || input.isNull() ? NODES.objectNode() : input;
            try {
                if (!args.isObject()) {
                    throw new BadInput("input must be a JSON object");
                }
                rejectUnknown(args, inputSchema.path("properties"));
                // The kind first: a pay run is refused however the rest is filled in.
                JsonNode flowNode = args.get("flowType");
                if (flowNode != null && flowNode.isTextual()) {
                    ApprovalFlowType named = flowType(flowNode.asText());
                    if (!ApprovalItemReader.DECIDABLE.contains(named)) {
                        throw new BadInput(NOT_HERE);
                    }
                }
                int position = positiveInt(args, "position", 0);
                String number = text(args, "employeeNumber");
                String flowText = text(args, "flowType");
                String decisionText = text(args, "decision");
                if (position < 1 || number == null || flowText == null || decisionText == null) {
                    throw new BadInput("position, employeeNumber, flowType and decision are required");
                }
                ApprovalFlowType flowType = flowType(flowText);
                ApprovalDecision decision = decision(decisionText);
                JsonNode commentNode = args.get("comment");
                if (commentNode != null && !commentNode.isNull() && !commentNode.isTextual()) {
                    throw new BadInput("comment must be text");
                }
                String comment = commentNode == null
                                || commentNode.isNull()
                                || commentNode.asText().isBlank()
                        ? null
                        : commentNode.asText();
                if (comment != null && comment.length() > MAX_COMMENT) {
                    throw new BadInput("comment must be at most " + MAX_COMMENT + " characters");
                }
                if (decision == ApprovalDecision.REJECTED && comment == null) {
                    throw new BadInput(NEEDS_COMMENT);
                }
                if (!ApprovalItemReader.DECIDABLE.contains(flowType)) {
                    throw new BadInput(NOT_HERE);
                }
                String finalComment = comment;
                AiProposedAction card = tx.execute(s -> {
                    int index = (position - 1) % PAGE_SIZE;
                    List<ApprovalStepResponse> rows =
                            page((position - 1) / PAGE_SIZE).getContent();
                    if (index >= rows.size()) {
                        throw new BadInput(NO_ROW);
                    }
                    ApprovalStepResponse row = rows.get(index);
                    if (row.flowType() != flowType
                            || !number.trim().equalsIgnoreCase(employeeNumber(requesters(List.of(row)), row))) {
                        throw new BadInput(LIST_CHANGED);
                    }
                    ApprovalItemReader.Item item =
                            items.read(ctx.tenantId(), row.flowType(), row.itemId(), row.summary());
                    try {
                        return proposals.propose(
                                ctx,
                                ApprovalDecideExecutor.ACTION_CODE,
                                ApprovalDecideExecutor.payload(row.id(), decision, finalComment, item.status()));
                    } catch (IllegalStateException e) {
                        if (e.getMessage() != null && e.getMessage().contains("already proposed")) {
                            throw new BadInput(ONE_AT_A_TIME);
                        }
                        throw e;
                    }
                });
                return AiToolResult.success(answer(Objects.requireNonNull(card, "card")));
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            } catch (IllegalArgumentException | IllegalStateException e) {
                return AiToolResult.failure(
                        e.getMessage() == null || e.getMessage().isBlank()
                                ? "The proposal was refused."
                                : e.getMessage());
            }
        }
    }

    /** What the model reads: the card, with the requester's own words wrapped (T1), and that the user must confirm. */
    private static JsonNode answer(AiProposedAction card) {
        ObjectNode out = NODES.objectNode();
        out.put("status", card.status().name());
        out.put("summary", card.summary());
        ArrayNode fields = out.putArray("fields");
        for (AiActionField field : card.fields()) {
            ObjectNode f = fields.addObject().put("label", field.label());
            if (REASON_LABELS.contains(field.label())) {
                f.set("value", AiUntrustedText.wrap(field.value()));
            } else {
                f.put("value", field.value());
            }
        }
        out.put("note", "Nothing has been decided yet. The user must press Confirm on the card.");
        return out;
    }

    private static int positiveInt(JsonNode args, String name, int fallback) {
        JsonNode value = args.get(name);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (!value.canConvertToInt() || !value.isIntegralNumber() || value.asInt() < 1) {
            throw new BadInput(name + " must be a whole number from 1");
        }
        return value.asInt();
    }

    private static ApprovalFlowType flowType(String value) {
        try {
            return ApprovalFlowType.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw new BadInput(NOT_HERE);
        }
    }

    private static ApprovalDecision decision(String value) {
        try {
            return ApprovalDecision.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw new BadInput("decision must be APPROVED or REJECTED");
        }
    }
}
