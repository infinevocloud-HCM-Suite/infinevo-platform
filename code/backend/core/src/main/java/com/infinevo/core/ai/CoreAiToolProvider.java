package com.infinevo.core.ai;

import static com.infinevo.core.ai.AiToolInputs.constantNames;
import static com.infinevo.core.ai.AiToolInputs.date;
import static com.infinevo.core.ai.AiToolInputs.dateProperty;
import static com.infinevo.core.ai.AiToolInputs.enumProperty;
import static com.infinevo.core.ai.AiToolInputs.enumValue;
import static com.infinevo.core.ai.AiToolInputs.rejectUnknown;
import static com.infinevo.core.ai.AiToolInputs.requireOrder;
import static com.infinevo.core.ai.AiToolInputs.schema;
import static com.infinevo.core.ai.AiToolInputs.text;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.ai.AiToolInputs.BadInput;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.holiday.HolidayQueryService;
import com.infinevo.core.leave.LeaveBalanceService;
import com.infinevo.core.leave.LeaveEligibilityService;
import com.infinevo.core.leave.LeaveRequestResponse;
import com.infinevo.core.leave.LeaveRequestService;
import com.infinevo.core.leave.LeaveRequestStatus;
import com.infinevo.core.org.DepartmentService;
import com.infinevo.core.org.DesignationService;
import com.infinevo.core.org.WorkLocationService;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.core.wfh.WfhBalanceService;
import com.infinevo.core.wfh.WfhRequestService;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The AI helper's Core tools (W-114.4, spec "Core tools"): seven read-only answers from the caller's own Core data.
 *
 * <p>Each tool calls the same service, with the same arguments, as the screen's controller does - cited per tool. The
 * own-data tools read only {@link AiToolContext#employeeId()}, the caller's own record; no input names an employee, a
 * location or any other row (rule 1). Permissions are not checked here: {@link AiToolRegistry} checks
 * {@code requiredAction} before a tool is offered and again before it runs (rule 3).
 *
 * <p>Every answer passes {@link #withoutIds}: database ids ({@code id}, {@code tenantId}, any {@code ...Id} or
 * {@code ...Ids} field) and any other UUID value are removed, so the model never holds a key it could ask about. Masking
 * and the 8,000-character cut are applied after this, by {@link AiChatServiceImpl}.
 *
 * <p>Bad input - an unknown field, a date that is not {@code YYYY-MM-DD}, an unknown status or kind, a range that ends
 * before it starts - runs nothing and answers a short failure the model can correct.
 */
@Component
public class CoreAiToolProvider implements AiToolProvider {

    /** At most this many leave requests are returned, as {@code GET /api/v1/me/leave-requests} pages by default. */
    static final int MAX_LEAVE_REQUESTS = 25;

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final LeaveBalanceService leaveBalanceService;
    private final LeaveRequestService leaveRequestService;
    private final LeaveEligibilityService leaveEligibilityService;
    private final HolidayQueryService holidayQueryService;
    private final DepartmentService departmentService;
    private final DesignationService designationService;
    private final WorkLocationService workLocationService;
    private final WfhRequestService wfhRequestService;
    private final WfhBalanceService wfhBalanceService;
    private final EmployeeService employeeService;
    private final TenantClock tenantClock;
    private final TransactionOperations tx;
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    private final List<AiTool> tools;

    @Autowired
    public CoreAiToolProvider(
            LeaveBalanceService leaveBalanceService,
            LeaveRequestService leaveRequestService,
            LeaveEligibilityService leaveEligibilityService,
            HolidayQueryService holidayQueryService,
            DepartmentService departmentService,
            DesignationService designationService,
            WorkLocationService workLocationService,
            WfhRequestService wfhRequestService,
            WfhBalanceService wfhBalanceService,
            EmployeeService employeeService,
            TenantClock tenantClock,
            PlatformTransactionManager transactionManager) {
        this(
                leaveBalanceService,
                leaveRequestService,
                leaveEligibilityService,
                holidayQueryService,
                departmentService,
                designationService,
                workLocationService,
                wfhRequestService,
                wfhBalanceService,
                employeeService,
                tenantClock,
                new TransactionTemplate(transactionManager));
    }

    CoreAiToolProvider(
            LeaveBalanceService leaveBalanceService,
            LeaveRequestService leaveRequestService,
            LeaveEligibilityService leaveEligibilityService,
            HolidayQueryService holidayQueryService,
            DepartmentService departmentService,
            DesignationService designationService,
            WorkLocationService workLocationService,
            WfhRequestService wfhRequestService,
            WfhBalanceService wfhBalanceService,
            EmployeeService employeeService,
            TenantClock tenantClock,
            TransactionOperations tx) {
        this.leaveBalanceService = Objects.requireNonNull(leaveBalanceService, "leaveBalanceService must not be null");
        this.leaveRequestService = Objects.requireNonNull(leaveRequestService, "leaveRequestService must not be null");
        this.leaveEligibilityService =
                Objects.requireNonNull(leaveEligibilityService, "leaveEligibilityService must not be null");
        this.holidayQueryService = Objects.requireNonNull(holidayQueryService, "holidayQueryService must not be null");
        this.departmentService = Objects.requireNonNull(departmentService, "departmentService must not be null");
        this.designationService = Objects.requireNonNull(designationService, "designationService must not be null");
        this.workLocationService = Objects.requireNonNull(workLocationService, "workLocationService must not be null");
        this.wfhRequestService = Objects.requireNonNull(wfhRequestService, "wfhRequestService must not be null");
        this.wfhBalanceService = Objects.requireNonNull(wfhBalanceService, "wfhBalanceService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.tenantClock = Objects.requireNonNull(tenantClock, "tenantClock must not be null");
        this.tx = Objects.requireNonNull(tx, "tx must not be null");
        this.tools = List.of(
                myLeaveBalances(),
                myLeaveRequests(),
                leaveTypesForMe(),
                holidays(),
                orgLookup(),
                myWfhRequests(),
                myWfhBalance());
    }

    @Override
    public List<AiTool> tools() {
        return tools;
    }

    // ---- the seven tools ----

    /** {@code GET /api/v1/me/leave-balances} - {@code leave/LeaveAllocationController.java:100-109}. */
    private AiTool myLeaveBalances() {
        return new CoreTool(
                "core.my_leave_balances",
                "The signed-in user's own leave balances, one row per leave type: entitlement, accrued, carried"
                        + " forward, consumed, pending and available days. Optional asOf date (YYYY-MM-DD);"
                        + " today when left out.",
                schema(Map.of("asOf", dateProperty("The date the balances are read on, YYYY-MM-DD."))),
                "core.leave.read_own",
                true,
                (ctx, input) -> {
                    LocalDate asOf = date(input, "asOf");
                    return leaveBalanceService.getBalancesForEmployee(ctx.tenantId(), ctx.employeeId(), asOf);
                });
    }

    /** {@code GET /api/v1/me/leave-requests} - {@code leave/LeaveRequestController.java:116-128}. */
    private AiTool myLeaveRequests() {
        return new CoreTool(
                "core.my_leave_requests",
                "The signed-in user's own leave requests, at most " + MAX_LEAVE_REQUESTS + ", with dates, working"
                        + " days, leave type and status. Optional filters: status, and a from / to date range"
                        + " (YYYY-MM-DD).",
                schema(Map.of(
                        "status",
                        enumProperty("Only requests in this status.", constantNames(LeaveRequestStatus.values())),
                        "from",
                        dateProperty("Only requests on or after this date, YYYY-MM-DD."),
                        "to",
                        dateProperty("Only requests on or before this date, YYYY-MM-DD."))),
                "core.leave.read_own",
                true,
                (ctx, input) -> {
                    LeaveRequestStatus status = enumValue(input, "status", LeaveRequestStatus.class);
                    LocalDate from = date(input, "from");
                    LocalDate to = date(input, "to");
                    requireOrder(from, to);
                    Page<LeaveRequestResponse> page = leaveRequestService.searchRequests(
                            ctx.tenantId(), ctx.employeeId(), status, from, to, PageRequest.of(0, MAX_LEAVE_REQUESTS));
                    ObjectNode out = NODES.objectNode();
                    out.set("requests", json.valueToTree(page.getContent()));
                    out.put("total", page.getTotalElements());
                    out.put("shown", page.getNumberOfElements());
                    return out;
                });
    }

    /** {@code GET /api/v1/leave-types/eligible} - {@code leave/LeaveTypeController.java:87-96}, for the caller only. */
    private AiTool leaveTypesForMe() {
        return new CoreTool(
                "core.leave_types_for_me",
                "The leave types the signed-in user may apply for today, with their rules (paid or not, half days"
                        + " allowed, policy). Takes no input.",
                schema(Map.of()),
                "core.leave.apply",
                true,
                (ctx, input) -> {
                    JsonNode types = json.valueToTree(
                            leaveEligibilityService.getEligibleLeaveTypes(ctx.tenantId(), ctx.employeeId(), today()));
                    // The policy's eligibility rows are only ids once stripped, and its overdrawn list names other
                    // employees (an administrator's preview, LeavePolicyResponse.java:39): neither is the caller's.
                    for (JsonNode type : types) {
                        if (type.path("policy") instanceof ObjectNode policy) {
                            policy.remove(List.of("eligibility", "overdrawnEmployees"));
                        }
                    }
                    return types;
                });
    }

    /**
     * {@code GET /api/v1/holidays} - {@code holiday/HolidayCalendarController.java:80-93}, through
     * {@code holiday/HolidayQueryService.java:54}. The calendar is the caller's own work location's, or the
     * customer's default calendar for a login with no employee record or no location.
     */
    private AiTool holidays() {
        return new CoreTool(
                "core.holidays",
                "Holidays on the signed-in user's holiday calendar between two dates (YYYY-MM-DD), each with its"
                        + " name, dates and whether it is restricted (optional). Defaults: from today, to 12 months"
                        + " after from.",
                schema(Map.of(
                        "from", dateProperty("First date, YYYY-MM-DD. Defaults to today."),
                        "to", dateProperty("Last date, YYYY-MM-DD. Defaults to 12 months after from."))),
                "core.holiday.read",
                false,
                (ctx, input) -> {
                    LocalDate from = date(input, "from");
                    LocalDate to = date(input, "to");
                    LocalDate fromDate = from != null ? from : today();
                    LocalDate toDate = to != null ? to : fromDate.plusMonths(12);
                    requireOrder(fromDate, toDate);
                    return holidayQueryService.holidaysBetween(ownWorkLocation(ctx), fromDate, toDate);
                });
    }

    /**
     * The list behind {@code GET} on each org master, active entries only - {@code org/DepartmentController.java:53},
     * {@code org/DesignationController.java:47}, {@code org/WorkLocationController.java:51}. Those endpoints accept
     * {@code core.org.manage} or {@code core.org.read}; so does this tool.
     */
    private AiTool orgLookup() {
        return new CoreTool(
                "core.org_lookup",
                "The organisation's active departments, designations or work locations, by code and name."
                        + " Required kind: DEPARTMENT, DESIGNATION or LOCATION.",
                schema(
                        Map.of(
                                "kind",
                                enumProperty("Which list to read.", List.of("DEPARTMENT", "DESIGNATION", "LOCATION"))),
                        "kind"),
                "core.org.read",
                List.of("core.org.manage"),
                false,
                (ctx, input) -> {
                    String kind = text(input, "kind");
                    if (kind == null) {
                        throw new BadInput("kind is required: DEPARTMENT, DESIGNATION or LOCATION");
                    }
                    return switch (kind.trim().toUpperCase(Locale.ROOT)) {
                        case "DEPARTMENT" -> departmentService.list(true);
                        case "DESIGNATION" -> designationService.list(true);
                        case "LOCATION" -> workLocationService.list(true);
                        default -> throw new BadInput("kind must be DEPARTMENT, DESIGNATION or LOCATION");
                    };
                });
    }

    /** {@code GET /api/v1/me/wfh-requests} - {@code wfh/MyWfhController.java:53-59}. */
    private AiTool myWfhRequests() {
        return new CoreTool(
                "core.my_wfh_requests",
                "The signed-in user's own work-from-home requests, with dates, working days and status. Takes no"
                        + " input.",
                schema(Map.of()),
                "core.wfh.read_own",
                true,
                (ctx, input) -> wfhRequestService.listEmployeeRequests(ctx.tenantId(), ctx.employeeId()));
    }

    /** {@code GET /api/v1/me/wfh-balance} - {@code wfh/MyWfhController.java:61-69}. */
    private AiTool myWfhBalance() {
        return new CoreTool(
                "core.my_wfh_balance",
                "The signed-in user's own work-from-home balance for the year: entitled, consumed, pending and"
                        + " available days, and this month's limit and use. Optional asOf date (YYYY-MM-DD); today"
                        + " when left out.",
                schema(Map.of("asOf", dateProperty("The date the balance is read on, YYYY-MM-DD."))),
                "core.wfh.read_own",
                true,
                (ctx, input) -> {
                    LocalDate asOf = date(input, "asOf");
                    return wfhBalanceService.getBalanceSummary(
                            ctx.tenantId(), ctx.employeeId(), asOf != null ? asOf : today());
                });
    }

    // ---- running a tool ----

    /** One tool: its description, and a body that answers any value, turned into JSON with no ids. */
    private final class CoreTool implements AiTool {

        private final String name;
        private final String description;
        private final ObjectNode inputSchema;
        private final String requiredAction;
        private final List<String> requiredAnyOf;
        private final boolean needsEmployee;
        private final BiFunction<AiToolContext, JsonNode, Object> body;

        CoreTool(
                String name,
                String description,
                ObjectNode inputSchema,
                String requiredAction,
                boolean needsEmployee,
                BiFunction<AiToolContext, JsonNode, Object> body) {
            this(name, description, inputSchema, requiredAction, List.of(), needsEmployee, body);
        }

        CoreTool(
                String name,
                String description,
                ObjectNode inputSchema,
                String requiredAction,
                List<String> requiredAnyOf,
                boolean needsEmployee,
                BiFunction<AiToolContext, JsonNode, Object> body) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
            this.requiredAction = requiredAction;
            this.requiredAnyOf = requiredAnyOf;
            this.needsEmployee = needsEmployee;
            this.body = body;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }

        @Override
        public String requiredAction() {
            return requiredAction;
        }

        @Override
        public List<String> requiredAnyOf() {
            return requiredAnyOf;
        }

        @Override
        public boolean needsEmployee() {
            return needsEmployee;
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            if (ctx == null || (needsEmployee && ctx.employeeId() == null)) {
                return AiToolResult.failure("Not available: the signed-in user has no employee record");
            }
            JsonNode args = input == null || input.isNull() ? NODES.objectNode() : input;
            try {
                if (!args.isObject()) {
                    throw new BadInput("input must be a JSON object");
                }
                rejectUnknown(args, inputSchema.path("properties"));
                JsonNode data = tx.execute(status -> json.valueToTree(body.apply(ctx, args)));
                return AiToolResult.success(withoutIds(data == null ? NODES.nullNode() : data));
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            }
        }
    }

    /** The answer without database ids (rule 1), by {@link AiToolResults#withoutIds}. */
    static JsonNode withoutIds(JsonNode node) {
        return AiToolResults.withoutIds(node);
    }

    // ---- input ----

    private LocalDate today() {
        return tenantClock.today();
    }

    /** The caller's own work location, for their holiday calendar; {@code null} uses the default calendar. */
    private UUID ownWorkLocation(AiToolContext ctx) {
        if (ctx.employeeId() == null) {
            return null;
        }
        return employeeService
                .currentEmployee()
                .filter(me -> ctx.employeeId().equals(me.id()))
                .map(EmployeeResponse::workLocationId)
                .orElse(null);
    }
}
