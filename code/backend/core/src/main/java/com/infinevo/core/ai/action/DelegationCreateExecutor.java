package com.infinevo.core.ai.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.approval.ApprovalDelegation;
import com.infinevo.core.approval.ApprovalDelegationRepository;
import com.infinevo.core.approval.DelegationCreateRequest;
import com.infinevo.core.approval.DelegationResponse;
import com.infinevo.core.approval.DelegationService;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Delegates the caller's own approvals to a colleague, as the AI helper proposed (W-114.29 step 3). Confirm calls
 * {@link DelegationService#createDelegation} with the caller's employee record, what
 * {@code POST /api/v1/approval-delegations} calls ({@code approval/DelegationController.java:45-54}), with the request
 * the Delegations screen sends ({@code frontend/src/core/approvals/Delegations.jsx:94-103}): the delegate, the dates and
 * the flow types joined by commas. The payload names the delegate by employee number, found again here; the service's
 * own checks (not oneself, the delegate live in the tenant, no chains) run again, so a chain made in between ends
 * {@code FAILED}.
 */
@Component
public class DelegationCreateExecutor implements AiActionExecutor {

    public static final String ACTION_CODE = "core.approval.delegate_create";
    static final String DELEGATE_ACTION = "core.approval.delegate";
    static final String DELEGATE = "delegateEmployeeNumber";
    static final String FROM = "from";
    static final String TO = "to";
    static final String FLOWS = "flowTypes";

    static final String SELF = "You cannot delegate your approvals to yourself.";
    static final String DELEGATE_IS_DELEGATOR =
            "That colleague has delegated their own approvals for those dates, so they cannot take yours.";
    static final String CALLER_IS_DELEGATE =
            "You are standing in for someone else's approvals on those dates, so you cannot delegate yours.";

    static final String PAY_RUN_ON_SCREEN = "Pay run approvals are handed on from the Delegations screen, not here.";

    /**
     * The Delegations screen's flow types, in its order and with its labels ({@code Delegations.jsx:26-34}), less
     * Pay Run: handing on pay run approvals stays on the screen (no pay run step from the helper, founder rule).
     */
    static final Map<String, String> FLOW_LABELS = flowLabels();

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final DelegationService delegations;
    private final EmployeeRepository employees;

    public DelegationCreateExecutor(DelegationService delegations, EmployeeRepository employees) {
        this.delegations = Objects.requireNonNull(delegations, "delegations must not be null");
        this.employees = Objects.requireNonNull(employees, "employees must not be null");
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String requiredAction() {
        return DELEGATE_ACTION;
    }

    /** The caller's own approvals: the controller refuses a caller with no employee record. */
    @Override
    public boolean requiresEmployeeProfile() {
        return true;
    }

    @Override
    public AiActionDescription describe(AiToolContext ctx, JsonNode payload) {
        String who = NamedEmployee.label(employees, ctx.tenantId(), OrgMasterCreateExecutor.text(payload, DELEGATE));
        String from = OrgMasterCreateExecutor.text(payload, FROM);
        String to = OrgMasterCreateExecutor.text(payload, TO);
        String flows = String.join(", ", labels(OrgMasterCreateExecutor.text(payload, FLOWS)));
        return new AiActionDescription(
                "Delegate your " + flows + " approvals to " + who + " from " + from + " to " + to,
                List.of(
                        new AiActionField("Delegate", who),
                        new AiActionField("From", from),
                        new AiActionField("To", to),
                        new AiActionField("Flow types", flows)));
    }

    @Override
    public JsonNode execute(AiToolContext ctx, JsonNode payload) {
        UUID tenantId = ctx.tenantId();
        if (ctx.employeeId() == null) {
            throw new IllegalStateException("No employee profile found for caller");
        }
        for (String code : OrgMasterCreateExecutor.text(payload, FLOWS).split(",")) {
            if (!FLOW_LABELS.containsKey(code.trim())) {
                throw new IllegalArgumentException(PAY_RUN_ON_SCREEN);
            }
        }
        Employee delegate = NamedEmployee.require(employees, tenantId, OrgMasterCreateExecutor.text(payload, DELEGATE));
        if (delegate.getId().equals(ctx.employeeId())) {
            throw new IllegalStateException(SELF);
        }
        DelegationCreateRequest request = new DelegationCreateRequest(
                delegate.getId(),
                LeaveAllocateExecutor.date(payload, FROM),
                LeaveAllocateExecutor.date(payload, TO),
                OrgMasterCreateExecutor.text(payload, FLOWS));
        DelegationResponse made;
        try {
            made = delegations.createDelegation(tenantId, ctx.employeeId(), request);
        } catch (NoSuchElementException e) {
            throw new IllegalStateException(NamedEmployee.GONE);
        }
        ObjectNode result = NODES.objectNode();
        result.put("delegationId", made.id().toString());
        return result;
    }

    /**
     * What the service would refuse as a chain ({@code approval/DelegationServiceImpl.java:71-89}), so the tool shows
     * no card that cannot be confirmed; {@code null} when there is none.
     */
    static String chainProblem(
            ApprovalDelegationRepository repository,
            UUID tenantId,
            UUID callerId,
            UUID delegateId,
            LocalDate from,
            LocalDate to) {
        boolean delegateIsDelegator =
                repository.findActiveOverlappingForEmployee(tenantId, delegateId, from, to).stream()
                        .map(ApprovalDelegation::getDelegatorEmployeeId)
                        .anyMatch(delegateId::equals);
        if (delegateIsDelegator) {
            return DELEGATE_IS_DELEGATOR;
        }
        boolean callerIsDelegate = repository.findActiveOverlappingForEmployee(tenantId, callerId, from, to).stream()
                .map(ApprovalDelegation::getDelegateEmployeeId)
                .anyMatch(callerId::equals);
        return callerIsDelegate ? CALLER_IS_DELEGATE : null;
    }

    static List<String> labels(String flowTypes) {
        List<String> out = new ArrayList<>();
        if (flowTypes == null) {
            return out;
        }
        for (String code : flowTypes.split(",")) {
            String c = code.trim();
            if (!c.isEmpty()) {
                out.add(FLOW_LABELS.getOrDefault(c, c));
            }
        }
        return out;
    }

    private static Map<String, String> flowLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("LEAVE", "Leave");
        m.put("REGULARIZATION", "Regularization");
        m.put("OVERTIME", "Overtime");
        m.put("REIMBURSEMENT", "Reimbursement");
        m.put("PROOF_OF_INVESTMENT", "Proof of Investment");
        m.put("TIMESHEET", "Timesheet");
        return java.util.Collections.unmodifiableMap(m);
    }
}
