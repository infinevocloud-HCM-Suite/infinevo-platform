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
import com.infinevo.core.employee.EmployeeQueryService;
import com.infinevo.core.employee.EmployeeSummaryResponse;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.core.wfh.WfhAllocationRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-114.29 step 2: {@code core.propose_wfh_allocation}. Finds the employee (number or full name) within the tenant and
 * fills the rest as the WFH Allocations screen does ({@code frontend/src/core/wfh/WfhAllocations.jsx:51-61, 198-204}):
 * the year as text, 1 January to 31 December of it unless dates are given, a pro-rate factor of 1 unless one is given,
 * no policy. Refuses an allocation that would overlap one already there, then proposes; nothing is written until
 * Confirm. Needs {@code core.wfh_balance.manage}, what {@code POST /api/v1/wfh-allocations} needs
 * ({@code wfh/WfhAllocationController.java:34-40}). One employee per card: the screen has no call for many at once.
 */
@Component
public class WfhAllocationToolProvider implements AiToolProvider {

    static final String TOOL = "core.propose_wfh_allocation";
    static final BigDecimal MAX_DAYS = new BigDecimal("365");

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final EmployeeQueryService employees;
    private final WfhAllocationRepository allocations;
    private final Supplier<LocalDate> today;
    private final AiActionProposals proposals;
    private final TransactionOperations tx;
    private final ObjectNode inputSchema;

    @Autowired
    public WfhAllocationToolProvider(
            EmployeeQueryService employees,
            WfhAllocationRepository allocations,
            TenantClock clock,
            AiActionProposals proposals,
            PlatformTransactionManager transactionManager) {
        this(employees, allocations, clock::today, proposals, new TransactionTemplate(transactionManager));
    }

    WfhAllocationToolProvider(
            EmployeeQueryService employees,
            WfhAllocationRepository allocations,
            Supplier<LocalDate> today,
            AiActionProposals proposals,
            TransactionOperations tx) {
        this.employees = Objects.requireNonNull(employees, "employees must not be null");
        this.allocations = Objects.requireNonNull(allocations, "allocations must not be null");
        this.today = Objects.requireNonNull(today, "today must not be null");
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
                "year",
                NODES.objectNode()
                        .put("type", "integer")
                        .put("minimum", LeaveAllocationToolProvider.MIN_YEAR)
                        .put("maximum", LeaveAllocationToolProvider.MAX_YEAR)
                        .put("description", "The allocation year. Leave out for this year."));
        props.put("from", dateProperty("The first day, YYYY-MM-DD. Leave out for 1 January of the year."));
        props.put("to", dateProperty("The last day, YYYY-MM-DD. Leave out for 31 December of the year."));
        props.put(
                "days",
                NODES.objectNode()
                        .put("type", "number")
                        .put("minimum", 0)
                        .put("maximum", 365)
                        .put("description", "The WFH quota in days, for example 12."));
        props.put(
                "proRateFactor",
                NODES.objectNode()
                        .put("type", "number")
                        .put("minimum", 0)
                        .put("maximum", 1)
                        .put("description", "A pro-rate factor from 0 to 1. Leave out for 1 (the full quota)."));
        this.inputSchema = schema(props, "employee", "days");
    }

    @Override
    public List<AiTool> tools() {
        return List.of(new ProposeWfhAllocation());
    }

    private final class ProposeWfhAllocation implements AiTool {

        @Override
        public String name() {
            return TOOL;
        }

        @Override
        public String description() {
            return "Proposes giving one employee a yearly work-from-home quota. Nothing is written: the user sees a card"
                    + " and must press Confirm. One employee at a time. Ask for the employee and the days if they are"
                    + " not given.";
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }

        @Override
        public String requiredAction() {
            return WfhAllocateExecutor.MANAGE;
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
                BigDecimal days = LeaveAllocationToolProvider.number(args, "days", 2, MAX_DAYS);
                if (who == null || days == null) {
                    throw new BadInput("employee and days are required. Ask the user for them.");
                }
                BigDecimal factor = LeaveAllocationToolProvider.number(args, "proRateFactor", 4, BigDecimal.ONE);
                // As the screen: `values.proRateFactor || 1.0` (WfhAllocations.jsx:60).
                BigDecimal sentFactor = factor == null || factor.signum() == 0 ? BigDecimal.ONE : factor;
                LocalDate fromGiven = date(args, "from");
                LocalDate toGiven = date(args, "to");
                JsonNode yearNode = args.get("year");
                int year;
                if (yearNode != null && !yearNode.isNull()) {
                    if (!yearNode.isIntegralNumber()
                            || yearNode.asInt() < LeaveAllocationToolProvider.MIN_YEAR
                            || yearNode.asInt() > LeaveAllocationToolProvider.MAX_YEAR) {
                        throw new BadInput("year must be a whole number, for example 2026");
                    }
                    year = yearNode.asInt();
                } else {
                    year = fromGiven != null ? fromGiven.getYear() : today.get().getYear();
                }
                LocalDate from = fromGiven != null ? fromGiven : LocalDate.of(year, 1, 1);
                LocalDate to = toGiven != null ? toGiven : LocalDate.of(year, 12, 31);
                requireOrder(from, to);
                AiProposedAction card = tx.execute(s -> {
                    EmployeeSummaryResponse employee = NamedEmployee.one(employees, who);
                    if (allocations.existsOverlappingAllocation(ctx.tenantId(), employee.id(), from, to)
                            || allocations
                                    .findByTenantIdAndEmployeeIdAndWfhYear(
                                            ctx.tenantId(), employee.id(), String.valueOf(year))
                                    .isPresent()) {
                        throw new BadInput(NamedEmployee.label(employee) + " already has a WFH allocation for " + year
                                + " or overlapping " + from + " to " + to + ". See the WFH allocations screen.");
                    }
                    ObjectNode payload = NODES.objectNode();
                    payload.put(WfhAllocateExecutor.EMPLOYEE, employee.employeeNumber());
                    payload.put(WfhAllocateExecutor.YEAR, String.valueOf(year));
                    payload.put(WfhAllocateExecutor.FROM, from.toString());
                    payload.put(WfhAllocateExecutor.TO, to.toString());
                    payload.put(WfhAllocateExecutor.DAYS, days.toPlainString());
                    payload.put(WfhAllocateExecutor.FACTOR, sentFactor.toPlainString());
                    return proposals.propose(ctx, WfhAllocateExecutor.ACTION_CODE, payload);
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
