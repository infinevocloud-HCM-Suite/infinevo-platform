package com.infinevo.core.ai.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.leave.LeaveAllocationRequest;
import com.infinevo.core.leave.LeaveAllocationResponse;
import com.infinevo.core.leave.LeaveAllocationService;
import com.infinevo.core.leave.LeaveType;
import com.infinevo.core.leave.LeaveTypeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Gives one employee a leave allocation the AI helper proposed (W-114.29 step 1). Confirm calls
 * {@link LeaveAllocationService#createAllocation}, what {@code POST /api/v1/leave-allocations} calls
 * ({@code leave/LeaveAllocationController.java:112-118}), with the request the Allocations screen sends
 * ({@code frontend/src/core/leave/Allocations.jsx:150-156}): the year as text, the tenant's leave-year window, the
 * opening days. The payload names the employee by number and the leave type by code; both are found again here, so
 * one removed in between, or an overlapping allocation made in between, ends {@code FAILED}.
 */
@Component
public class LeaveAllocateExecutor implements AiActionExecutor {

    public static final String ACTION_CODE = "core.leave.allocate";
    static final String MANAGE = "core.leave_balance.manage";
    static final String EMPLOYEE = "employeeNumber";
    static final String LEAVE_TYPE = "leaveTypeCode";
    static final String YEAR = "leaveYear";
    static final String FROM = "yearStartDate";
    static final String TO = "yearEndDate";
    static final String DAYS = "openingDays";

    static final String NO_TYPE = "That leave type is no longer there or is not active.";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final LeaveAllocationService allocations;
    private final EmployeeRepository employees;
    private final LeaveTypeRepository types;

    public LeaveAllocateExecutor(
            LeaveAllocationService allocations, EmployeeRepository employees, LeaveTypeRepository types) {
        this.allocations = Objects.requireNonNull(allocations, "allocations must not be null");
        this.employees = Objects.requireNonNull(employees, "employees must not be null");
        this.types = Objects.requireNonNull(types, "types must not be null");
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String requiredAction() {
        return MANAGE;
    }

    /** HR work on another person's balance: the HR user need not be an employee. */
    @Override
    public boolean requiresEmployeeProfile() {
        return false;
    }

    @Override
    public AiActionDescription describe(AiToolContext ctx, JsonNode payload) {
        String number = OrgMasterCreateExecutor.text(payload, EMPLOYEE);
        String code = OrgMasterCreateExecutor.text(payload, LEAVE_TYPE);
        String who = NamedEmployee.label(employees, ctx.tenantId(), number);
        String type = types.findByTenantIdAndCode(ctx.tenantId(), code)
                .map(LeaveAllocateExecutor::typeLabel)
                .orElse(code);
        String year = OrgMasterCreateExecutor.text(payload, YEAR);
        String days = OrgMasterCreateExecutor.text(payload, DAYS);
        return new AiActionDescription(
                "Allocate " + days + " days of " + type + " to " + who + " for leave year " + year,
                List.of(
                        new AiActionField("Employee", who),
                        new AiActionField("Leave type", type),
                        new AiActionField("Leave year", year),
                        new AiActionField("From", OrgMasterCreateExecutor.text(payload, FROM)),
                        new AiActionField("To", OrgMasterCreateExecutor.text(payload, TO)),
                        new AiActionField("Opening days", days)));
    }

    @Override
    public JsonNode execute(AiToolContext ctx, JsonNode payload) {
        UUID tenantId = ctx.tenantId();
        Employee employee = NamedEmployee.require(employees, tenantId, OrgMasterCreateExecutor.text(payload, EMPLOYEE));
        LeaveType type = activeType(types, tenantId, OrgMasterCreateExecutor.text(payload, LEAVE_TYPE))
                .orElseThrow(() -> new IllegalStateException(NO_TYPE));
        LeaveAllocationRequest request = new LeaveAllocationRequest(
                employee.getId(),
                type.getId(),
                OrgMasterCreateExecutor.text(payload, YEAR),
                date(payload, FROM),
                date(payload, TO),
                decimal(payload, DAYS));
        LeaveAllocationResponse made;
        try {
            made = allocations.createAllocation(tenantId, request);
        } catch (NoSuchElementException e) {
            throw new IllegalStateException(NO_TYPE);
        }
        ObjectNode result = NODES.objectNode();
        result.put("employeeId", employee.getId().toString());
        result.put("allocationId", made.id().toString());
        return result;
    }

    /** The active leave type with that code. */
    static Optional<LeaveType> activeType(LeaveTypeRepository types, UUID tenantId, String code) {
        if (code == null) {
            return Optional.empty();
        }
        return types.findByTenantIdAndCode(tenantId, code).filter(LeaveType::isActive);
    }

    /** As the screen's leave type list shows it: "Casual Leave (CL)". */
    static String typeLabel(LeaveType t) {
        return t.getName() + " (" + t.getCode() + ")";
    }

    static LocalDate date(JsonNode payload, String name) {
        String value = OrgMasterCreateExecutor.text(payload, name);
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("The date is not valid.");
        }
    }

    static BigDecimal decimal(JsonNode payload, String name) {
        String value = OrgMasterCreateExecutor.text(payload, name);
        try {
            return value == null ? null : new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The number of days is not valid.");
        }
    }
}
