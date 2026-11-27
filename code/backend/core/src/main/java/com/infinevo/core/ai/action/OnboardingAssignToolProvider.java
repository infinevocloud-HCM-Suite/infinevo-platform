package com.infinevo.core.ai.action;

import static com.infinevo.core.ai.AiToolInputs.rejectUnknown;
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
import com.infinevo.core.employee.EmployeeQueryService;
import com.infinevo.core.employee.EmployeeSummaryResponse;
import com.infinevo.core.onboarding.EmployeeOnboardingTaskRepository;
import com.infinevo.core.onboarding.OnboardingTaskTemplateRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-114.29 step 4: {@code core.propose_onboarding_assign}. Finds the new joiner by employee number or full name within
 * the tenant, refuses an empty checklist and an employee who already has one, then proposes; nothing is assigned until
 * Confirm. The checklist is the company's whole active one, as the screen's button assigns it: there is no choice of
 * template. Needs {@code core.onboarding.manage}, what {@code POST /api/v1/employees/{id}/onboarding/assign} needs
 * ({@code onboarding/EmployeeOnboardingController.java:53-58}).
 */
@Component
public class OnboardingAssignToolProvider implements AiToolProvider {

    static final String TOOL = "core.propose_onboarding_assign";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final EmployeeQueryService employees;
    private final OnboardingTaskTemplateRepository templates;
    private final EmployeeOnboardingTaskRepository tasks;
    private final AiActionProposals proposals;
    private final TransactionOperations tx;
    private final ObjectNode inputSchema;

    @Autowired
    public OnboardingAssignToolProvider(
            EmployeeQueryService employees,
            OnboardingTaskTemplateRepository templates,
            EmployeeOnboardingTaskRepository tasks,
            AiActionProposals proposals,
            PlatformTransactionManager transactionManager) {
        this(employees, templates, tasks, proposals, (TransactionOperations)
                new TransactionTemplate(transactionManager));
    }

    OnboardingAssignToolProvider(
            EmployeeQueryService employees,
            OnboardingTaskTemplateRepository templates,
            EmployeeOnboardingTaskRepository tasks,
            AiActionProposals proposals,
            TransactionOperations tx) {
        this.employees = Objects.requireNonNull(employees, "employees must not be null");
        this.templates = Objects.requireNonNull(templates, "templates must not be null");
        this.tasks = Objects.requireNonNull(tasks, "tasks must not be null");
        this.proposals = Objects.requireNonNull(proposals, "proposals must not be null");
        this.tx = Objects.requireNonNull(tx, "tx must not be null");
        Map<String, ObjectNode> props = new LinkedHashMap<>();
        props.put(
                "employee",
                NODES.objectNode()
                        .put("type", "string")
                        .put("maxLength", 128)
                        .put("description", "The new joiner's employee number, or their full name."));
        this.inputSchema = schema(props, "employee");
    }

    @Override
    public List<AiTool> tools() {
        return List.of(new ProposeOnboardingAssign());
    }

    private final class ProposeOnboardingAssign implements AiTool {

        @Override
        public String name() {
            return TOOL;
        }

        @Override
        public String description() {
            return "Proposes assigning the company's onboarding checklist to a new joiner. Nothing is assigned: the"
                    + " user sees a card and must press Confirm. Ask for the employee if not given.";
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }

        @Override
        public String requiredAction() {
            return OnboardingAssignExecutor.MANAGE;
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            JsonNode args = input == null || input.isNull() ? NODES.objectNode() : input;
            try {
                if (!args.isObject()) {
                    throw new BadInput("input must be a JSON object");
                }
                rejectUnknown(args, inputSchema.path("properties"));
                String who = given(args, "employee");
                if (who == null) {
                    throw new BadInput("employee is required. Ask the user who it is for.");
                }
                AiProposedAction card = tx.execute(s -> {
                    EmployeeSummaryResponse employee = NamedEmployee.one(employees, who);
                    if (OnboardingAssignExecutor.active(templates, ctx.tenantId())
                            .isEmpty()) {
                        throw new BadInput(OnboardingAssignExecutor.NO_CHECKLIST);
                    }
                    if (tasks.countByTenantIdAndEmployeeId(ctx.tenantId(), employee.id()) > 0) {
                        throw new BadInput(NamedEmployee.label(employee)
                                + " already has an onboarding checklist. See their Onboarding tab.");
                    }
                    ObjectNode payload = NODES.objectNode();
                    payload.put(OnboardingAssignExecutor.EMPLOYEE, employee.employeeNumber());
                    return proposals.propose(ctx, OnboardingAssignExecutor.ACTION_CODE, payload);
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
}
