package com.infinevo.core.ai.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.onboarding.EmployeeOnboardingTaskRepository;
import com.infinevo.core.onboarding.OnboardingProgressResponse;
import com.infinevo.core.onboarding.OnboardingService;
import com.infinevo.core.onboarding.OnboardingTaskTemplate;
import com.infinevo.core.onboarding.OnboardingTaskTemplateRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Assigns the company's onboarding checklist to a new joiner, as the AI helper proposed (W-114.29 step 4). Confirm
 * calls {@link OnboardingService#assignTasksToEmployee}, what {@code POST /api/v1/employees/{id}/onboarding/assign}
 * calls ({@code onboarding/EmployeeOnboardingController.java:53-58}; the employee's Onboarding tab,
 * {@code frontend/src/core/employee/tabs/OnboardingTab.jsx:86}), with the caller as actor. The service quietly does
 * nothing for an employee who already has tasks or when the checklist is empty; here both end {@code FAILED} instead,
 * so a confirmed card always made tasks.
 */
@Component
public class OnboardingAssignExecutor implements AiActionExecutor {

    public static final String ACTION_CODE = "core.onboarding.assign";
    static final String MANAGE = "core.onboarding.manage";
    static final String EMPLOYEE = "employeeNumber";
    static final int MAX_LISTED = 20;

    static final String NO_CHECKLIST =
            "The onboarding checklist has no active tasks. Add them in Settings, Onboarding first.";
    static final String ALREADY = "That employee already has an onboarding checklist. See their Onboarding tab.";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final OnboardingService onboarding;
    private final OnboardingTaskTemplateRepository templates;
    private final EmployeeOnboardingTaskRepository tasks;
    private final EmployeeRepository employees;

    public OnboardingAssignExecutor(
            OnboardingService onboarding,
            OnboardingTaskTemplateRepository templates,
            EmployeeOnboardingTaskRepository tasks,
            EmployeeRepository employees) {
        this.onboarding = Objects.requireNonNull(onboarding, "onboarding must not be null");
        this.templates = Objects.requireNonNull(templates, "templates must not be null");
        this.tasks = Objects.requireNonNull(tasks, "tasks must not be null");
        this.employees = Objects.requireNonNull(employees, "employees must not be null");
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String requiredAction() {
        return MANAGE;
    }

    /** HR work on a new joiner: the HR user need not be an employee. */
    @Override
    public boolean requiresEmployeeProfile() {
        return false;
    }

    @Override
    public AiActionDescription describe(AiToolContext ctx, JsonNode payload) {
        String who = NamedEmployee.label(employees, ctx.tenantId(), OrgMasterCreateExecutor.text(payload, EMPLOYEE));
        List<OnboardingTaskTemplate> checklist = active(templates, ctx.tenantId());
        return new AiActionDescription(
                "Assign the onboarding checklist (" + checklist.size() + " tasks) to " + who,
                List.of(
                        new AiActionField("Employee", who),
                        new AiActionField("Tasks", String.valueOf(checklist.size())),
                        new AiActionField("Checklist", titles(checklist))));
    }

    @Override
    public JsonNode execute(AiToolContext ctx, JsonNode payload) {
        UUID tenantId = ctx.tenantId();
        Employee employee = NamedEmployee.require(employees, tenantId, OrgMasterCreateExecutor.text(payload, EMPLOYEE));
        if (active(templates, tenantId).isEmpty()) {
            throw new IllegalStateException(NO_CHECKLIST);
        }
        if (tasks.countByTenantIdAndEmployeeId(tenantId, employee.getId()) > 0) {
            throw new IllegalStateException(ALREADY);
        }
        OnboardingProgressResponse progress;
        try {
            progress = onboarding.assignTasksToEmployee(
                    tenantId, employee.getId(), ctx.userId().toString());
        } catch (NoSuchElementException e) {
            throw new IllegalStateException(NamedEmployee.GONE);
        }
        ObjectNode result = NODES.objectNode();
        result.put("employeeId", employee.getId().toString());
        result.put("tasks", progress.totalTasks());
        return result;
    }

    static List<OnboardingTaskTemplate> active(OnboardingTaskTemplateRepository templates, UUID tenantId) {
        return templates.findByTenantIdAndIsActiveTrueOrderBySortOrderAscCreatedAtAsc(tenantId);
    }

    /** The first {@link #MAX_LISTED} task titles, in the checklist's order. */
    static String titles(List<OnboardingTaskTemplate> checklist) {
        String listed = checklist.stream()
                .limit(MAX_LISTED)
                .map(OnboardingTaskTemplate::getTitle)
                .collect(Collectors.joining(", "));
        return checklist.size() > MAX_LISTED
                ? listed + " and " + (checklist.size() - MAX_LISTED) + " more"
                : (listed.isEmpty() ? "(none)" : listed);
    }
}
