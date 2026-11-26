package com.infinevo.core.ai;

import static com.infinevo.core.ai.AiToolInputs.date;
import static com.infinevo.core.ai.AiToolInputs.dateProperty;
import static com.infinevo.core.ai.AiToolInputs.rejectUnknown;
import static com.infinevo.core.ai.AiToolInputs.schema;
import static com.infinevo.core.ai.AiToolInputs.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiToolInputs.BadInput;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.leave.LeaveAllocation;
import com.infinevo.core.leave.LeaveAllocationRepository;
import com.infinevo.core.leave.LeaveBalanceResponse;
import com.infinevo.core.leave.LeaveBalanceService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code core.explain_leave_balance} (W-114.15): how one of the caller's own leave balances is made up - granted,
 * accrued, carried forward, used, pending and available, the leave year's dates, and the sum written out by the tool.
 *
 * <p>The same read as {@code GET /api/v1/me/leave-balances} ({@code leave/LeaveAllocationController.java:100-110}):
 * {@link LeaveBalanceService#getBalancesForEmployee} for the caller, plus the caller's own allocations for the year
 * dates. The caller is {@link EmployeeService#currentEmployee()} and must be the context's employee (rule 1); no input
 * names an employee. Kept beside {@link CoreAiToolProvider} rather than in it, so that one's constructor does not grow.
 */
@Component
public class LeaveBalanceAiToolProvider implements AiToolProvider {

    static final String NAME = "core.explain_leave_balance";
    static final String NO_EMPLOYEE = "No employee record is linked to your login.";
    static final String NO_SUCH_TYPE = "You have no allocation for that leave type.";
    static final int MAX_TYPE = 100;

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final LeaveBalanceService balances;
    private final LeaveAllocationRepository allocations;
    private final EmployeeService employees;
    private final TransactionOperations readOnlyTx;
    private final List<AiTool> tools;

    @Autowired
    public LeaveBalanceAiToolProvider(
            LeaveBalanceService balances,
            LeaveAllocationRepository allocations,
            EmployeeService employees,
            PlatformTransactionManager transactionManager) {
        this(balances, allocations, employees, readOnly(transactionManager));
    }

    LeaveBalanceAiToolProvider(
            LeaveBalanceService balances,
            LeaveAllocationRepository allocations,
            EmployeeService employees,
            TransactionOperations readOnlyTx) {
        this.balances = Objects.requireNonNull(balances, "balances must not be null");
        this.allocations = Objects.requireNonNull(allocations, "allocations must not be null");
        this.employees = Objects.requireNonNull(employees, "employees must not be null");
        this.readOnlyTx = Objects.requireNonNull(readOnlyTx, "readOnlyTx must not be null");
        this.tools = List.of(new ExplainBalance());
    }

    private static TransactionOperations readOnly(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }

    @Override
    public List<AiTool> tools() {
        return tools;
    }

    private final class ExplainBalance implements AiTool {

        private final ObjectNode inputSchema = schema(Map.of(
                "leaveType",
                NODES.objectNode()
                        .put("type", "string")
                        .put("maxLength", MAX_TYPE)
                        .put("description", "Optional: one leave type, by its code (CL) or name (Casual Leave)."),
                "asOf",
                dateProperty("The date the balance is read on, YYYY-MM-DD. Today when left out.")));

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public String description() {
            return "How the signed-in user's own leave balance is made up, per leave type: granted, accrued, carried"
                    + " forward (already cut to what still counts after its expiry), used, balance, pending and"
                    + " available days, the leave year's start and end, when a carry-forward expires and whether it"
                    + " has (carryForwardExpired), whether the leave type itself has ended (leaveTypeEnded), and howItAddsUp - the sum written out with the figures (quote it, do not compute). There"
                    + " is no opening balance, and the number of days lost when a carry-forward expires is not"
                    + " recorded. For the plain figures use core.my_leave_balances.";
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }

        @Override
        public String requiredAction() {
            return "core.leave.read_own";
        }

        @Override
        public boolean needsEmployee() {
            return true;
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            if (ctx == null || ctx.employeeId() == null) {
                return AiToolResult.failure(NO_EMPLOYEE);
            }
            JsonNode args = input == null || input.isNull() ? NODES.objectNode() : input;
            try {
                if (!args.isObject()) {
                    throw new BadInput("input must be a JSON object");
                }
                rejectUnknown(args, inputSchema.path("properties"));
                String type = text(args, "leaveType");
                if (type != null && type.length() > MAX_TYPE) {
                    throw new BadInput("leaveType must be at most " + MAX_TYPE + " characters");
                }
                LocalDate asked = date(args, "asOf");
                // The service's own default, fixed here so the answer can say which day it read.
                LocalDate asOf = asked != null ? asked : LocalDate.now(ZoneOffset.UTC);
                return readOnlyTx.execute(status -> {
                    Optional<UUID> me = employees.currentEmployee().map(EmployeeResponse::id);
                    if (me.isEmpty() || !me.get().equals(ctx.employeeId())) {
                        return AiToolResult.failure(NO_EMPLOYEE);
                    }
                    List<LeaveBalanceResponse> all =
                            balances.getBalancesForEmployee(ctx.tenantId(), ctx.employeeId(), asOf);
                    List<LeaveBalanceResponse> chosen = type == null || type.isBlank() ? all : match(all, type);
                    if (chosen.isEmpty()) {
                        return AiToolResult.success(noSuchType(all));
                    }
                    Map<String, LeaveBalanceExplanation.Year> years = new HashMap<>();
                    for (LeaveAllocation a :
                            allocations.findByTenantIdAndEmployeeId(ctx.tenantId(), ctx.employeeId())) {
                        years.putIfAbsent(LeaveBalanceExplanation.key(a), LeaveBalanceExplanation.Year.of(a));
                    }
                    return AiToolResult.success(LeaveBalanceExplanation.explain(chosen, years, asOf));
                });
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            }
        }
    }

    /** By code first, then by name, ignoring case (rule 2). */
    static List<LeaveBalanceResponse> match(List<LeaveBalanceResponse> all, String wanted) {
        String w = wanted.strip().toLowerCase(Locale.ROOT);
        List<LeaveBalanceResponse> byCode = all.stream()
                .filter(b -> b.leaveTypeCode() != null
                        && b.leaveTypeCode().toLowerCase(Locale.ROOT).equals(w))
                .toList();
        if (!byCode.isEmpty()) {
            return byCode;
        }
        return all.stream()
                .filter(b -> b.leaveTypeName() != null
                        && b.leaveTypeName().toLowerCase(Locale.ROOT).equals(w))
                .toList();
    }

    /** The plain sentence and the leave types the caller does have. */
    private static JsonNode noSuchType(List<LeaveBalanceResponse> all) {
        ObjectNode out = NODES.objectNode();
        out.put("found", false);
        out.put("message", NO_SUCH_TYPE);
        ArrayNode types = out.putArray("yourLeaveTypes");
        all.stream()
                .map(b -> NODES.objectNode().put("code", b.leaveTypeCode()).put("name", b.leaveTypeName()))
                .distinct()
                .forEach(types::add);
        return out;
    }
}
