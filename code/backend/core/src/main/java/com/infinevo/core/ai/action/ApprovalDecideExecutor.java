package com.infinevo.core.ai.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.approval.ApprovalDecideRequest;
import com.infinevo.core.approval.ApprovalDecision;
import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.ApprovalStep;
import com.infinevo.core.approval.ApprovalStepRepository;
import com.infinevo.core.employee.EmployeeService;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Decides one approval step the AI helper proposed (W-114.18). One step per intent (rule T5); the card is built from
 * the stored step id and the database, the employee's reason in full (T3); at confirm the step must still be pending
 * and the leave or WFH request still in the status it had when proposed (T7). Never sends {@code approvedAmount}, and
 * refuses a flow type outside {@link ApprovalItemReader#DECIDABLE} even when the payload names one.
 */
@Component
public class ApprovalDecideExecutor implements AiActionExecutor {

    static final String ACTION_CODE = "core.approval.decide";

    static final String STEP_ID = "stepId";
    static final String DECISION = "decision";
    static final String COMMENT = "comment";
    static final String EXPECTED_STEP_STATE = "expectedStepState";
    static final String EXPECTED_ITEM_STATUS = "expectedItemStatus";
    static final String PENDING = "PENDING";

    static final String CHANGED = "This request changed after it was proposed. Ask again.";
    static final String GONE = "That approval was not found.";
    static final String NOT_HERE = "This kind of request is decided on the Approvals screen only.";
    static final String ALREADY_DECIDED = "This step has already been decided.";
    static final String NOT_APPROVER = "You are not the approver for this request.";
    static final String NO_COMMENT = "(none)";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final ApprovalService approvalService;
    private final ApprovalStepRepository steps;
    private final ApprovalInstanceRepository instances;
    private final EmployeeService employeeService;
    private final ApprovalItemReader items;

    ApprovalDecideExecutor(
            ApprovalService approvalService,
            ApprovalStepRepository steps,
            ApprovalInstanceRepository instances,
            EmployeeService employeeService,
            ApprovalItemReader items) {
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService must not be null");
        this.steps = Objects.requireNonNull(steps, "steps must not be null");
        this.instances = Objects.requireNonNull(instances, "instances must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.items = Objects.requireNonNull(items, "items must not be null");
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String requiredAction() {
        return ACTION_CODE;
    }

    /** An approver is identified by user, not by an employee record ({@code ApprovalController} never asks for one). */
    @Override
    public boolean requiresEmployeeProfile() {
        return false;
    }

    /** The one payload shape: a single step id (T5) and the states expected at confirm (T7). */
    static ObjectNode payload(UUID stepId, ApprovalDecision decision, String comment, String expectedItemStatus) {
        ObjectNode payload = NODES.objectNode();
        payload.put(STEP_ID, stepId.toString());
        payload.put(DECISION, decision.name());
        if (comment == null) {
            payload.putNull(COMMENT);
        } else {
            payload.put(COMMENT, comment);
        }
        payload.put(EXPECTED_STEP_STATE, PENDING);
        if (expectedItemStatus == null) {
            payload.putNull(EXPECTED_ITEM_STATUS);
        } else {
            payload.put(EXPECTED_ITEM_STATUS, expectedItemStatus);
        }
        return payload;
    }

    private record Loaded(ApprovalStep step, ApprovalInstance instance) {}

    private Loaded load(AiToolContext ctx, JsonNode payload) {
        UUID stepId = uuid(payload == null ? null : payload.path(STEP_ID).asText(null));
        if (stepId == null) {
            return null;
        }
        ApprovalStep step = steps.findByTenantIdAndId(ctx.tenantId(), stepId).orElse(null);
        if (step == null) {
            return null;
        }
        ApprovalInstance instance = instances
                .findByTenantIdAndId(ctx.tenantId(), step.getInstanceId())
                .orElse(null);
        return instance == null ? null : new Loaded(step, instance);
    }

    @Override
    public AiActionDescription describe(AiToolContext ctx, JsonNode payload) {
        ApprovalDecision decision = decision(payload);
        String comment = text(payload, COMMENT);
        String decisionText = decision == null ? "" : decision.name();
        AiActionField decisionField = new AiActionField("Decision", decisionText);
        AiActionField commentField = new AiActionField("Comment", comment == null ? NO_COMMENT : comment);
        Loaded loaded = load(ctx, payload);
        if (loaded == null) {
            return new AiActionDescription(GONE, List.of(decisionField, commentField));
        }
        ApprovalInstance instance = loaded.instance();
        ApprovalFlowType type = instance.getFlowType();
        ApprovalItemReader.Item item = items.read(
                ctx.tenantId(), type, instance.getSubjectId(), loaded.step().getItemRef());
        String requester = requester(instance.getSubjectEmployeeId());
        List<AiActionField> fields = new ArrayList<>();
        fields.add(new AiActionField("Request", label(type)));
        fields.add(new AiActionField("Requested by", requester));
        fields.add(new AiActionField("Details", item.details()));
        fields.add(new AiActionField(
                "Reason (the employee's own words)",
                item.hasReason()
                        ? (item.reason() == null || item.reason().isBlank() ? NO_COMMENT : item.reason())
                        : ApprovalItemReader.OPEN_FOR_NOTE));
        fields.add(new AiActionField(
                "Waiting since",
                loaded.step().getCreatedAt() == null
                        ? ""
                        : loaded.step()
                                .getCreatedAt()
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toString()));
        fields.add(decisionField);
        fields.add(commentField);
        String verb = decision == ApprovalDecision.REJECTED ? "Reject" : "Approve";
        return new AiActionDescription(
                verb + " " + label(type).toLowerCase(java.util.Locale.ROOT) + " for " + requester, fields);
    }

    @Override
    public JsonNode execute(AiToolContext ctx, JsonNode payload) {
        ApprovalDecision decision = decision(payload);
        if (decision == null) {
            throw new IllegalArgumentException("The decision must be APPROVED or REJECTED.");
        }
        Loaded loaded = load(ctx, payload);
        if (loaded == null) {
            throw new IllegalStateException(GONE);
        }
        ApprovalInstance instance = loaded.instance();
        if (!ApprovalItemReader.DECIDABLE.contains(instance.getFlowType())) {
            throw new IllegalStateException(NOT_HERE);
        }
        // T7: nothing is decided when the step or its request moved on since the proposal.
        if (loaded.step().getDecision() != null || !PENDING.equals(text(payload, EXPECTED_STEP_STATE))) {
            throw new IllegalStateException(loaded.step().getDecision() != null ? ALREADY_DECIDED : CHANGED);
        }
        ApprovalItemReader.Item item = items.read(
                ctx.tenantId(),
                instance.getFlowType(),
                instance.getSubjectId(),
                loaded.step().getItemRef());
        if (!Objects.equals(text(payload, EXPECTED_ITEM_STATUS), item.status())) {
            throw new IllegalStateException(CHANGED);
        }
        try {
            approvalService.decide(loaded.step().getId(), new ApprovalDecideRequest(decision, text(payload, COMMENT)));
        } catch (AccessDeniedException e) {
            throw new IllegalStateException(NOT_APPROVER);
        } catch (NoSuchElementException e) {
            throw new IllegalStateException(GONE);
        } catch (IllegalStateException e) {
            String message = e.getMessage();
            if (message != null && message.contains("has already been decided")) {
                throw new IllegalStateException(ALREADY_DECIDED);
            }
            throw e;
        }
        ObjectNode result = NODES.objectNode();
        result.put("instanceId", instance.getId().toString());
        result.put("decision", decision.name());
        return result;
    }

    static String label(ApprovalFlowType type) {
        if (type == null) {
            return "Request";
        }
        return switch (type) {
            case LEAVE -> "Leave request";
            case WORK_FROM_HOME -> "Work from home request";
            case REGULARIZATION -> "Attendance regularization";
            case OVERTIME -> "Overtime request";
            case TIMESHEET -> "Timesheet";
            case REIMBURSEMENT -> "Reimbursement";
            case PROOF_OF_INVESTMENT -> "Proof of investment";
            case PAY_RUN -> "Pay run";
        };
    }

    private String requester(UUID employeeId) {
        if (employeeId == null) {
            return "";
        }
        Map<UUID, String> names = employeeService.displayNames(List.of(employeeId));
        String name = names == null ? null : names.get(employeeId);
        return name == null ? "" : name;
    }

    private static ApprovalDecision decision(JsonNode payload) {
        String value = text(payload, DECISION);
        if (value == null) {
            return null;
        }
        try {
            return ApprovalDecision.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String text(JsonNode payload, String name) {
        if (payload == null) {
            return null;
        }
        JsonNode value = payload.get(name);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static UUID uuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
