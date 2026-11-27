package com.infinevo.core.ai.action;

import static com.infinevo.core.ai.AiToolInputs.date;
import static com.infinevo.core.ai.AiToolInputs.dateProperty;
import static com.infinevo.core.ai.AiToolInputs.rejectUnknown;
import static com.infinevo.core.ai.AiToolInputs.requireOrder;
import static com.infinevo.core.ai.AiToolInputs.schema;
import static com.infinevo.core.ai.action.OrgMasterActionToolProvider.answer;
import static com.infinevo.core.ai.action.OrgMasterActionToolProvider.given;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiProposedAction;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolInputs.BadInput;
import com.infinevo.core.ai.AiToolProvider;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.approval.ApprovalDelegationRepository;
import com.infinevo.core.employee.EmployeeQueryService;
import com.infinevo.core.employee.EmployeeSummaryResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-114.29 step 3: {@code core.propose_delegation}. The caller hands their own approvals to a colleague named by
 * employee number or full name, for a date range and the flow types the Delegations screen offers. Refuses oneself and
 * a delegation chain, as the service would, then proposes; nothing is written until Confirm. Needs
 * {@code core.approval.delegate}, what {@code POST /api/v1/approval-delegations} needs
 * ({@code approval/DelegationController.java:45-54}), and the caller's own employee record.
 */
@Component
public class DelegationToolProvider implements AiToolProvider {

    static final String TOOL = "core.propose_delegation";
    static final int MAX_FLOWS = DelegationCreateExecutor.FLOW_LABELS.size();

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final EmployeeQueryService employees;
    private final ApprovalDelegationRepository delegations;
    private final AiActionProposals proposals;
    private final TransactionOperations tx;
    private final ObjectNode inputSchema;

    @Autowired
    public DelegationToolProvider(
            EmployeeQueryService employees,
            ApprovalDelegationRepository delegations,
            AiActionProposals proposals,
            PlatformTransactionManager transactionManager) {
        this(employees, delegations, proposals, (TransactionOperations) new TransactionTemplate(transactionManager));
    }

    DelegationToolProvider(
            EmployeeQueryService employees,
            ApprovalDelegationRepository delegations,
            AiActionProposals proposals,
            TransactionOperations tx) {
        this.employees = Objects.requireNonNull(employees, "employees must not be null");
        this.delegations = Objects.requireNonNull(delegations, "delegations must not be null");
        this.proposals = Objects.requireNonNull(proposals, "proposals must not be null");
        this.tx = Objects.requireNonNull(tx, "tx must not be null");
        Map<String, ObjectNode> props = new LinkedHashMap<>();
        props.put(
                "delegate",
                NODES.objectNode()
                        .put("type", "string")
                        .put("maxLength", 128)
                        .put("description", "The colleague's employee number, or their full name."));
        props.put("from", dateProperty("The first day, YYYY-MM-DD."));
        props.put("to", dateProperty("The last day, YYYY-MM-DD."));
        ObjectNode flows = NODES.objectNode()
                .put("type", "array")
                .put("minItems", 1)
                .put("maxItems", MAX_FLOWS)
                .put(
                        "description",
                        "Which approvals to hand over: "
                                + String.join(", ", DelegationCreateExecutor.FLOW_LABELS.values())
                                + ". Ask the user if not said.");
        flows.putObject("items").put("type", "string").put("maxLength", 40);
        props.put("flowTypes", flows);
        this.inputSchema = schema(props, "delegate", "from", "to", "flowTypes");
    }

    @Override
    public List<AiTool> tools() {
        return List.of(new ProposeDelegation());
    }

    private final class ProposeDelegation implements AiTool {

        @Override
        public String name() {
            return TOOL;
        }

        @Override
        public String description() {
            return "Proposes handing the user's own approvals to a colleague for a date range, for example while on"
                    + " leave. Nothing is written: the user sees a card and must press Confirm. Ask for the colleague,"
                    + " the dates and which approvals if they are not given.";
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }

        @Override
        public String requiredAction() {
            return DelegationCreateExecutor.DELEGATE_ACTION;
        }

        @Override
        public boolean needsEmployee() {
            return true;
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            JsonNode args = input == null || input.isNull() ? NODES.objectNode() : input;
            try {
                if (!args.isObject()) {
                    throw new BadInput("input must be a JSON object");
                }
                if (ctx.employeeId() == null) {
                    throw new BadInput("Delegating approvals needs your own employee record.");
                }
                rejectUnknown(args, inputSchema.path("properties"));
                String who = given(args, "delegate");
                LocalDate from = date(args, "from");
                LocalDate to = date(args, "to");
                if (who == null || from == null || to == null) {
                    throw new BadInput("delegate, from and to are required. Ask the user for them.");
                }
                requireOrder(from, to);
                String flowTypes = flowTypes(args);
                AiProposedAction card = tx.execute(s -> {
                    EmployeeSummaryResponse delegate = NamedEmployee.one(employees, who);
                    if (delegate.id().equals(ctx.employeeId())) {
                        throw new BadInput(DelegationCreateExecutor.SELF);
                    }
                    String chain = DelegationCreateExecutor.chainProblem(
                            delegations, ctx.tenantId(), ctx.employeeId(), delegate.id(), from, to);
                    if (chain != null) {
                        throw new BadInput(chain);
                    }
                    ObjectNode payload = NODES.objectNode();
                    payload.put(DelegationCreateExecutor.DELEGATE, delegate.employeeNumber());
                    payload.put(DelegationCreateExecutor.FROM, from.toString());
                    payload.put(DelegationCreateExecutor.TO, to.toString());
                    payload.put(DelegationCreateExecutor.FLOWS, flowTypes);
                    return proposals.propose(ctx, DelegationCreateExecutor.ACTION_CODE, payload);
                });
                return AiToolResult.success(answer(Objects.requireNonNull(card, "card")));
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            } catch (IllegalArgumentException | IllegalStateException e) {
                return AiToolResult.failure(
                        e.getMessage() != null && e.getMessage().contains("already proposed")
                                ? "One step at a time. Ask the user about the next one."
                                : e.getMessage());
            }
        }
    }

    /** The screen's flow types named in words or by code, in the screen's order, joined by commas as it sends them. */
    static String flowTypes(JsonNode args) {
        JsonNode node = args.get("flowTypes");
        if (node == null || node.isNull() || (node.isArray() && node.isEmpty())) {
            throw new BadInput("flowTypes is required: at least one of "
                    + String.join(", ", DelegationCreateExecutor.FLOW_LABELS.values()) + ". Ask the user which.");
        }
        if (!node.isArray() || node.size() > MAX_FLOWS) {
            throw new BadInput("flowTypes must be a list of flow type names");
        }
        List<String> wanted = new ArrayList<>();
        for (JsonNode n : node) {
            if (!n.isTextual() || n.asText().isBlank()) {
                throw new BadInput("each flow type must be a name");
            }
            String w = n.asText().trim();
            if (w.replace('_', ' ').equalsIgnoreCase("pay run") || w.equalsIgnoreCase("payrun")) {
                throw new BadInput(DelegationCreateExecutor.PAY_RUN_ON_SCREEN
                        + " Tell the user to open Approvals, then Delegations.");
            }
            String code = DelegationCreateExecutor.FLOW_LABELS.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(w)
                            || e.getValue().equalsIgnoreCase(w)
                            || e.getKey().replace('_', ' ').equalsIgnoreCase(w))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow(() -> new BadInput("No flow type named " + w + ". The flow types are: "
                            + String.join(", ", DelegationCreateExecutor.FLOW_LABELS.values()) + "."));
            wanted.add(code.toUpperCase(Locale.ROOT));
        }
        return String.join(
                ",",
                DelegationCreateExecutor.FLOW_LABELS.keySet().stream()
                        .filter(wanted::contains)
                        .toList());
    }
}
