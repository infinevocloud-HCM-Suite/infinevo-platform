package com.infinevo.core.ai.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.wfh.CreateWfhAllocationRequest;
import com.infinevo.core.wfh.WfhAllocationRepository;
import com.infinevo.core.wfh.WfhAllocationResponse;
import com.infinevo.core.wfh.WfhAllocationService;
import com.infinevo.core.wfh.WfhPolicy;
import com.infinevo.core.wfh.WfhPolicyRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Gives one employee a work-from-home allocation the AI helper proposed (W-114.29 step 2). Confirm calls
 * {@link WfhAllocationService#createAllocation}, what {@code POST /api/v1/wfh-allocations} calls
 * ({@code wfh/WfhAllocationController.java:34-40}), with the request the WFH Allocations screen sends
 * ({@code frontend/src/core/wfh/WfhAllocations.jsx:54-61}): no policy (the service uses the company's default one),
 * the year as text, the dates, the quota and the pro-rate factor. The payload names the employee by number; one removed
 * in between, or an overlapping allocation made in between, ends {@code FAILED}.
 */
@Component
public class WfhAllocateExecutor implements AiActionExecutor {

    public static final String ACTION_CODE = "core.wfh.allocate";
    static final String MANAGE = "core.wfh_balance.manage";
    static final String EMPLOYEE = "employeeNumber";
    static final String YEAR = "wfhYear";
    static final String FROM = "yearStartDate";
    static final String TO = "yearEndDate";
    static final String DAYS = "entitlementDays";
    static final String FACTOR = "proRateFactor";

    static final String DEFAULT_POLICY = "The company's default WFH policy";
    static final String SAME_YEAR = "That employee already has a WFH allocation for that year.";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final WfhAllocationService allocations;
    private final WfhAllocationRepository allocationRepository;
    private final WfhPolicyRepository policies;
    private final EmployeeRepository employees;

    public WfhAllocateExecutor(
            WfhAllocationService allocations,
            WfhAllocationRepository allocationRepository,
            WfhPolicyRepository policies,
            EmployeeRepository employees) {
        this.allocations = Objects.requireNonNull(allocations, "allocations must not be null");
        this.allocationRepository =
                Objects.requireNonNull(allocationRepository, "allocationRepository must not be null");
        this.policies = Objects.requireNonNull(policies, "policies must not be null");
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

    /** HR work on another person's quota: the HR user need not be an employee. */
    @Override
    public boolean requiresEmployeeProfile() {
        return false;
    }

    @Override
    public AiActionDescription describe(AiToolContext ctx, JsonNode payload) {
        String who = NamedEmployee.label(employees, ctx.tenantId(), OrgMasterCreateExecutor.text(payload, EMPLOYEE));
        String year = OrgMasterCreateExecutor.text(payload, YEAR);
        String days = OrgMasterCreateExecutor.text(payload, DAYS);
        String policy = policies.findFirstByTenantIdAndActiveTrueOrderByEffectiveFromDesc(ctx.tenantId())
                .map(WfhPolicy::getName)
                .orElse(DEFAULT_POLICY);
        return new AiActionDescription(
                "Allocate " + days + " WFH days to " + who + " for " + year,
                List.of(
                        new AiActionField("Employee", who),
                        new AiActionField("Allocation year", year),
                        new AiActionField("From", OrgMasterCreateExecutor.text(payload, FROM)),
                        new AiActionField("To", OrgMasterCreateExecutor.text(payload, TO)),
                        new AiActionField("Quota (days)", days),
                        new AiActionField("Pro-rate factor", OrgMasterCreateExecutor.text(payload, FACTOR)),
                        new AiActionField("Policy", policy)));
    }

    @Override
    public JsonNode execute(AiToolContext ctx, JsonNode payload) {
        UUID tenantId = ctx.tenantId();
        Employee employee = NamedEmployee.require(employees, tenantId, OrgMasterCreateExecutor.text(payload, EMPLOYEE));
        String year = OrgMasterCreateExecutor.text(payload, YEAR);
        // The table's unique (tenant, employee, year) would fail only at commit, past the refusal; say it here.
        if (allocationRepository
                .findByTenantIdAndEmployeeIdAndWfhYear(tenantId, employee.getId(), year)
                .isPresent()) {
            throw new IllegalStateException(SAME_YEAR);
        }
        CreateWfhAllocationRequest request = new CreateWfhAllocationRequest(
                employee.getId(),
                null,
                year,
                LeaveAllocateExecutor.date(payload, FROM),
                LeaveAllocateExecutor.date(payload, TO),
                LeaveAllocateExecutor.decimal(payload, DAYS),
                LeaveAllocateExecutor.decimal(payload, FACTOR));
        WfhAllocationResponse made;
        try {
            made = allocations.createAllocation(tenantId, request);
        } catch (NoSuchElementException e) {
            throw new IllegalStateException(NamedEmployee.GONE);
        }
        ObjectNode result = NODES.objectNode();
        result.put("employeeId", employee.getId().toString());
        result.put("allocationId", made.id().toString());
        return result;
    }
}
