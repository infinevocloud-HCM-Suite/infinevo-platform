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
import com.infinevo.core.leave.LeaveAllocationRepository;
import com.infinevo.core.leave.LeavePolicyRepository;
import com.infinevo.core.leave.LeaveType;
import com.infinevo.core.leave.LeaveTypeRepository;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.core.tenant.TenantProfileService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-114.29 step 1: {@code core.propose_leave_allocation}. Finds the employee (number or full name) and the active leave
 * type (name or code) within the tenant, works out the leave-year window as the Allocations screen does
 * ({@code frontend/src/core/leave/Allocations.jsx:39-42}: the tenant's leave-year start month, April when unset),
 * refuses a type with no policy in effect and an allocation that would overlap one already there, then proposes;
 * nothing is written until Confirm. Needs {@code core.leave_balance.manage}, what
 * {@code POST /api/v1/leave-allocations} needs ({@code leave/LeaveAllocationController.java:112-118}). One employee per
 * card: the screen has no call for many at once.
 */
@Component
public class LeaveAllocationToolProvider implements AiToolProvider {

    static final String TOOL = "core.propose_leave_allocation";
    static final int DEFAULT_START_MONTH = 4;
    static final int MAX_LISTED = 20;
    static final int MIN_YEAR = 2000;
    static final int MAX_YEAR = 2100;
    static final BigDecimal MAX_DAYS = new BigDecimal("366");

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final EmployeeQueryService employees;
    private final LeaveTypeRepository types;
    private final LeavePolicyRepository policies;
    private final LeaveAllocationRepository allocations;
    private final Supplier<LocalDate> today;
    private final IntSupplier startMonth;
    private final AiActionProposals proposals;
    private final TransactionOperations tx;
    private final ObjectNode inputSchema;

    @Autowired
    public LeaveAllocationToolProvider(
            EmployeeQueryService employees,
            LeaveTypeRepository types,
            LeavePolicyRepository policies,
            LeaveAllocationRepository allocations,
            TenantProfileService profiles,
            TenantClock clock,
            AiActionProposals proposals,
            PlatformTransactionManager transactionManager) {
        this(
                employees,
                types,
                policies,
                allocations,
                clock::today,
                () -> startMonth(profiles),
                proposals,
                new TransactionTemplate(transactionManager));
    }

    LeaveAllocationToolProvider(
            EmployeeQueryService employees,
            LeaveTypeRepository types,
            LeavePolicyRepository policies,
            LeaveAllocationRepository allocations,
            Supplier<LocalDate> today,
            IntSupplier startMonth,
            AiActionProposals proposals,
            TransactionOperations tx) {
        this.employees = Objects.requireNonNull(employees, "employees must not be null");
        this.types = Objects.requireNonNull(types, "types must not be null");
        this.policies = Objects.requireNonNull(policies, "policies must not be null");
        this.allocations = Objects.requireNonNull(allocations, "allocations must not be null");
        this.today = Objects.requireNonNull(today, "today must not be null");
        this.startMonth = Objects.requireNonNull(startMonth, "startMonth must not be null");
        this.proposals = Objects.requireNonNull(proposals, "proposals must not be null");
        this.tx = Objects.requireNonNull(tx, "tx must not be null");
        Map<String, ObjectNode> props = new LinkedHashMap<>();
        props.put(
                "employee",
                NODES.objectNode()
                        .put("type", "string")
                        .put("maxLength", 128)
                        .put("description", "The employee number, or the employee's full name."));
        props.put(
                "leaveType",
                NODES.objectNode()
                        .put("type", "string")
                        .put("maxLength", 128)
                        .put("description", "The leave type's name or code, for example Casual Leave or CL."));
        props.put(
                "year",
                NODES.objectNode()
                        .put("type", "integer")
                        .put("minimum", MIN_YEAR)
                        .put("maximum", MAX_YEAR)
                        .put("description", "The year the leave year starts in. Leave out for this year."));
        props.put(
                "days",
                NODES.objectNode()
                        .put("type", "number")
                        .put("minimum", 0)
                        .put("description", "The opening days to allocate, for example 12 or 7.5."));
        this.inputSchema = schema(props, "employee", "leaveType", "days");
    }

    @Override
    public List<AiTool> tools() {
        return List.of(new ProposeAllocation());
    }

    private final class ProposeAllocation implements AiTool {

        @Override
        public String name() {
            return TOOL;
        }

        @Override
        public String description() {
            return "Proposes giving one employee a leave type's opening days for a leave year. Nothing is written: the"
                    + " user sees a card and must press Confirm. One employee at a time. Ask for the employee, the leave"
                    + " type and the days if they are not given.";
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }

        @Override
        public String requiredAction() {
            return LeaveAllocateExecutor.MANAGE;
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
                String wantedType = given(args, "leaveType");
                BigDecimal days = number(args, "days", 2, MAX_DAYS);
                if (who == null || wantedType == null || days == null) {
                    throw new BadInput("employee, leaveType and days are required. Ask the user for them.");
                }
                Integer yearGiven = year(args);
                AiProposedAction card = tx.execute(s -> {
                    EmployeeSummaryResponse employee = NamedEmployee.one(employees, who);
                    LeaveType type =
                            match(types.findByTenantIdAndIsActiveTrueOrderByCodeAsc(ctx.tenantId()), wantedType);
                    int year = yearGiven != null ? yearGiven : today.get().getYear();
                    LocalDate from = LocalDate.of(year, startMonth.getAsInt(), 1);
                    LocalDate to = from.plusYears(1).minusDays(1);
                    if (policies.findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                                    ctx.tenantId(), type.getId(), from)
                            .isEmpty()) {
                        throw new BadInput(LeaveAllocateExecutor.typeLabel(type) + " has no leave policy in effect on "
                                + from + ". Set one on the Leave policies screen first.");
                    }
                    if (allocations.existsOverlappingAllocation(
                            ctx.tenantId(), employee.id(), type.getId(), from, to)) {
                        throw new BadInput(NamedEmployee.label(employee) + " already has a "
                                + LeaveAllocateExecutor.typeLabel(type) + " allocation overlapping " + from + " to "
                                + to + ". See the Leave allocations screen.");
                    }
                    ObjectNode payload = NODES.objectNode();
                    payload.put(LeaveAllocateExecutor.EMPLOYEE, employee.employeeNumber());
                    payload.put(LeaveAllocateExecutor.LEAVE_TYPE, type.getCode());
                    payload.put(LeaveAllocateExecutor.YEAR, String.valueOf(year));
                    payload.put(LeaveAllocateExecutor.FROM, from.toString());
                    payload.put(LeaveAllocateExecutor.TO, to.toString());
                    payload.put(LeaveAllocateExecutor.DAYS, days.toPlainString());
                    return proposals.propose(ctx, LeaveAllocateExecutor.ACTION_CODE, payload);
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

    /** The leave type of that name or code, any case; an unknown one lists the active types. */
    static LeaveType match(List<LeaveType> all, String wanted) {
        if (all.isEmpty()) {
            throw new BadInput("There is no active leave type yet. Make one on the Leave types screen first.");
        }
        String w = wanted.trim();
        return all.stream()
                .filter(t -> (t.getName() != null && t.getName().trim().equalsIgnoreCase(w))
                        || (t.getCode() != null && t.getCode().trim().equalsIgnoreCase(w)))
                .findFirst()
                .orElseThrow(() -> new BadInput("No leave type named " + w + ". The leave types are: "
                        + all.stream()
                                .limit(MAX_LISTED)
                                .map(LeaveAllocateExecutor::typeLabel)
                                .collect(Collectors.joining(", "))
                        + "."));
    }

    /** The tenant's leave-year start month; April when unset or unreadable, as the screen assumes. */
    static int startMonth(TenantProfileService profiles) {
        try {
            Short month = profiles.current().leaveYearStartMonth();
            return month == null || month < 1 || month > 12 ? DEFAULT_START_MONTH : month;
        } catch (RuntimeException e) {
            return DEFAULT_START_MONTH;
        }
    }

    private static Integer year(JsonNode args) {
        JsonNode v = args.get("year");
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.canConvertToInt() || !v.isIntegralNumber()) {
            throw new BadInput("year must be a whole number, for example 2026");
        }
        int year = v.asInt();
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new BadInput("year must be between " + MIN_YEAR + " and " + MAX_YEAR);
        }
        return year;
    }

    /**
     * A number of days, or {@code null} when absent: a JSON number or numeric text, not negative, at most
     * {@code scale} decimals and at most {@code max}.
     */
    static BigDecimal number(JsonNode args, String name, int scale, BigDecimal max) {
        JsonNode v = args.get(name);
        if (v == null || v.isNull()) {
            return null;
        }
        BigDecimal value;
        try {
            if (v.isNumber()) {
                value = v.decimalValue();
            } else if (v.isTextual()) {
                value = new BigDecimal(v.asText().trim());
            } else {
                throw new BadInput(name + " must be a number");
            }
        } catch (NumberFormatException e) {
            throw new BadInput(name + " must be a number");
        }
        value = value.stripTrailingZeros();
        if (value.scale() < 0) {
            value = value.setScale(0);
        }
        if (value.signum() < 0 || value.compareTo(max) > 0) {
            throw new BadInput(name + " must be between 0 and " + max.toPlainString());
        }
        if (value.scale() > scale) {
            throw new BadInput(name + " may have at most " + scale + " decimals");
        }
        return value;
    }
}
