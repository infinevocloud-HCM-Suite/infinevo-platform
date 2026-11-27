package com.infinevo.core.ai;

import static com.infinevo.core.ai.AiToolInputs.date;
import static com.infinevo.core.ai.AiToolInputs.dateProperty;
import static com.infinevo.core.ai.AiToolInputs.enumProperty;
import static com.infinevo.core.ai.AiToolInputs.rejectUnknown;
import static com.infinevo.core.ai.AiToolInputs.schema;
import static com.infinevo.core.ai.AiToolInputs.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiToolInputs.BadInput;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeQueryService;
import com.infinevo.core.employee.EmployeeQueryService.HeadcountGroup;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.leave.LeaveRequestResponse;
import com.infinevo.core.leave.LeaveRequestService;
import com.infinevo.core.leave.LeaveRequestStatus;
import com.infinevo.core.org.OrgMaster;
import com.infinevo.core.org.ReportingLineService;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.shared.authz.PermissionService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The HR and manager team tools (W-114.19): who is on leave, who joined, who left, and headcount. Each shows a caller
 * only what the matching screen shows, through the same service and the same action: the Leave requests screen
 * ({@code core.leave.read} for everyone, {@code core.leave.read_team} for self and direct reports) and the employee
 * list ({@code core.employee.read}, which has no team scoping, so joiners, leavers and headcount have no manager
 * version).
 *
 * <p>Every row is built here from named fields. No id, contact detail, leave type or leave reason is ever returned:
 * "Sick" or "Maternity" is health information (founder, 2026-11-23), so a leave row reads "On leave". Names and group
 * labels are cut to one line of at most {@value #MAX_LABEL} characters, so a name cannot carry a paragraph.
 */
@Component
public class TeamAiToolProvider implements AiToolProvider {

    static final String ON_LEAVE = "core.team_on_leave";
    static final String JOINERS = "core.joiners";
    static final String LEAVERS = "core.leavers";
    static final String HEADCOUNT = "core.headcount";

    static final String LEAVE_READ = "core.leave.read";
    static final String LEAVE_READ_TEAM = "core.leave.read_team";
    static final String EMPLOYEE_READ = "core.employee.read";

    static final int MAX_ROWS = 50;
    static final int MAX_DAYS = 31;
    static final int MAX_LABEL = 100;
    static final String NO_GROUP = "(none)";

    /** Every string field a row may carry: short labels and dates, never free text (W-114.18 rule T1). */
    static final Set<String> LABEL_FIELDS = Set.of(
            "name",
            "employeeNumber",
            "department",
            "designation",
            "location",
            "from",
            "to",
            "halfDay",
            "dateOfJoining",
            "lastWorkingDate",
            "status");

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final LeaveRequestService leaveRequestService;
    private final EmployeeRepository employeeRepository;
    private final EmployeeQueryService employeeQueryService;
    private final ReportingLineService reportingLineService;
    private final PermissionService permissionService;
    private final TenantClock tenantClock;
    private final TransactionOperations tx;
    private final List<AiTool> tools;

    @Autowired
    public TeamAiToolProvider(
            LeaveRequestService leaveRequestService,
            EmployeeRepository employeeRepository,
            EmployeeQueryService employeeQueryService,
            ReportingLineService reportingLineService,
            PermissionService permissionService,
            TenantClock tenantClock,
            PlatformTransactionManager transactionManager) {
        this(
                leaveRequestService,
                employeeRepository,
                employeeQueryService,
                reportingLineService,
                permissionService,
                tenantClock,
                new TransactionTemplate(transactionManager));
    }

    TeamAiToolProvider(
            LeaveRequestService leaveRequestService,
            EmployeeRepository employeeRepository,
            EmployeeQueryService employeeQueryService,
            ReportingLineService reportingLineService,
            PermissionService permissionService,
            TenantClock tenantClock,
            TransactionOperations tx) {
        this.leaveRequestService = Objects.requireNonNull(leaveRequestService, "leaveRequestService must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
        this.employeeQueryService =
                Objects.requireNonNull(employeeQueryService, "employeeQueryService must not be null");
        this.reportingLineService =
                Objects.requireNonNull(reportingLineService, "reportingLineService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
        this.tenantClock = Objects.requireNonNull(tenantClock, "tenantClock must not be null");
        this.tx = Objects.requireNonNull(tx, "tx must not be null");
        this.tools = List.of(
                new Tool(
                        ON_LEAVE,
                        "Lists who is on approved leave in a date range (default today, at most " + MAX_DAYS
                                + " days): name, employee number, department and dates. HR sees everyone; a manager"
                                + " sees themselves and their direct reports. The type of leave is never shown.",
                        LEAVE_READ_TEAM,
                        List.of(LEAVE_READ),
                        schema(Map.of(
                                "from", dateProperty("First day, YYYY-MM-DD. Default today."),
                                "to", dateProperty("Last day, YYYY-MM-DD. Default the same as from."))),
                        this::onLeave),
                new Tool(
                        JOINERS,
                        "Lists employees who joined in a month (default this month): name, employee number,"
                                + " department, designation and date of joining.",
                        EMPLOYEE_READ,
                        List.of(),
                        monthSchema(),
                        (ctx, args) -> joiners(args)),
                new Tool(
                        LEAVERS,
                        "Lists employees whose last working day is in a month (default this month): name, employee"
                                + " number, department and last working date.",
                        EMPLOYEE_READ,
                        List.of(),
                        monthSchema(),
                        (ctx, args) -> leavers(args)),
                new Tool(
                        HEADCOUNT,
                        "Counts employees on the books (not terminated), in total and optionally per department or"
                                + " per work location.",
                        EMPLOYEE_READ,
                        List.of(),
                        schema(Map.of(
                                "by",
                                enumProperty(
                                        "Group by department or location; leave out for the total only.",
                                        List.of("department", "location")))),
                        (ctx, args) -> headcount(args)));
    }

    @Override
    public List<AiTool> tools() {
        return tools;
    }

    private interface Body {
        ObjectNode run(AiToolContext ctx, JsonNode args);
    }

    private final class Tool implements AiTool {

        private final String name;
        private final String description;
        private final String requiredAction;
        private final List<String> requiredAnyOf;
        private final ObjectNode inputSchema;
        private final Body body;

        Tool(
                String name,
                String description,
                String requiredAction,
                List<String> requiredAnyOf,
                ObjectNode inputSchema,
                Body body) {
            this.name = name;
            this.description = description;
            this.requiredAction = requiredAction;
            this.requiredAnyOf = requiredAnyOf;
            this.inputSchema = inputSchema;
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
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            JsonNode args = input == null || input.isNull() ? NODES.objectNode() : input;
            try {
                if (!args.isObject()) {
                    throw new BadInput("input must be a JSON object");
                }
                rejectUnknown(args, inputSchema.path("properties"));
                ObjectNode out = tx.execute(s -> body.run(ctx, args));
                return AiToolResult.success(Objects.requireNonNull(out, "out"));
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            }
        }
    }

    // ---- core.team_on_leave ----

    private ObjectNode onLeave(AiToolContext ctx, JsonNode args) {
        LocalDate today = tenantClock.today();
        LocalDate from = date(args, "from");
        LocalDate to = date(args, "to");
        if (from == null) {
            from = to == null ? today : to;
        }
        if (to == null) {
            to = from;
        }
        if (to.isBefore(from)) {
            throw new BadInput("to must not be before from");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new BadInput("The range can be at most " + MAX_DAYS + " days.");
        }
        PageRequest page = PageRequest.of(0, MAX_ROWS);
        Page<LeaveRequestResponse> found;
        if (permissionService.holds(LEAVE_READ)) {
            found = leaveRequestService.searchRequests(
                    ctx.tenantId(), null, LeaveRequestStatus.APPROVED, from, to, page);
        } else if (permissionService.holds(LEAVE_READ_TEAM) && ctx.employeeId() != null) {
            List<UUID> allowed = new ArrayList<>();
            allowed.add(ctx.employeeId());
            allowed.addAll(reportingLineService.directReportIds(ctx.employeeId(), today));
            found = leaveRequestService.searchRequestsForEmployees(
                    ctx.tenantId(), allowed, LeaveRequestStatus.APPROVED, from, to, page);
        } else {
            throw new BadInput("Not available: you cannot see other people's leave.");
        }
        List<LeaveRequestResponse> requests = found.getContent();
        Map<UUID, Employee> people = live(
                ctx,
                requests.stream()
                        .map(LeaveRequestResponse::employeeId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList());
        ObjectNode out = NODES.objectNode();
        out.put("from", from.toString());
        out.put("to", to.toString());
        ArrayNode rows = out.putArray("onLeave");
        requests.stream()
                .filter(r -> people.containsKey(r.employeeId()))
                .sorted(Comparator.comparing(LeaveRequestResponse::fromDate))
                .forEach(r -> {
                    Employee e = people.get(r.employeeId());
                    ObjectNode row = rows.addObject();
                    row.put("name", label(fullName(e)));
                    row.put("employeeNumber", label(e.getEmployeeNumber()));
                    row.put("department", group(e.getDepartment()));
                    row.put("from", String.valueOf(r.fromDate()));
                    row.put("to", String.valueOf(r.toDate()));
                    row.put("halfDay", r.isHalfDay() ? "yes" : "no");
                    row.put("status", "On leave");
                });
        // A soft-deleted employee is in no answer, the count included: the requests of this page that were left out
        // come off the total, so the total and the rows agree.
        long leftOut = requests.size() - rows.size();
        more(out, found.getTotalElements() - leftOut, rows.size());
        out.put("note", "The type of leave is not shown. Open the Leave requests screen for it.");
        return out;
    }

    /** The live employees among {@code ids}: a soft-deleted one is in no answer. */
    private Map<UUID, Employee> live(AiToolContext ctx, List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return employeeRepository.findByTenantIdAndIdIn(ctx.tenantId(), ids).stream()
                .filter(e -> !e.isDeleted())
                .collect(Collectors.toMap(Employee::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    // ---- core.joiners / core.leavers ----

    private ObjectNode joiners(JsonNode args) {
        YearMonth month = month(args);
        Page<Employee> page = employeeQueryService.joinedBetween(month.atDay(1), month.atEndOfMonth());
        ObjectNode out = NODES.objectNode();
        out.put("month", month.toString());
        ArrayNode rows = out.putArray("joiners");
        for (Employee e : page.getContent()) {
            ObjectNode row = rows.addObject();
            row.put("name", label(fullName(e)));
            row.put("employeeNumber", label(e.getEmployeeNumber()));
            row.put("department", group(e.getDepartment()));
            row.put("designation", group(e.getDesignation()));
            row.put("dateOfJoining", String.valueOf(e.getDateOfJoining()));
        }
        more(out, page.getTotalElements(), page.getNumberOfElements());
        return out;
    }

    private ObjectNode leavers(JsonNode args) {
        YearMonth month = month(args);
        Page<Employee> page = employeeQueryService.leftBetween(month.atDay(1), month.atEndOfMonth());
        ObjectNode out = NODES.objectNode();
        out.put("month", month.toString());
        ArrayNode rows = out.putArray("leavers");
        for (Employee e : page.getContent()) {
            ObjectNode row = rows.addObject();
            row.put("name", label(fullName(e)));
            row.put("employeeNumber", label(e.getEmployeeNumber()));
            row.put("department", group(e.getDepartment()));
            row.put("lastWorkingDate", String.valueOf(e.getTerminationDate()));
        }
        more(out, page.getTotalElements(), page.getNumberOfElements());
        return out;
    }

    // ---- core.headcount ----

    private ObjectNode headcount(JsonNode args) {
        String by = text(args, "by");
        ObjectNode out = NODES.objectNode();
        out.put("total", employeeQueryService.headcount());
        if (by != null) {
            HeadcountGroup group =
                    switch (by) {
                        case "department" -> HeadcountGroup.DEPARTMENT;
                        case "location" -> HeadcountGroup.LOCATION;
                        default -> throw new BadInput("by must be department or location");
                    };
            out.put("by", by);
            ArrayNode groups = out.putArray("groups");
            for (EmployeeRepository.GroupCount g : employeeQueryService.headcountBy(group)) {
                ObjectNode row = groups.addObject();
                row.put(group == HeadcountGroup.LOCATION ? "location" : "department", label(g.getName()));
                row.put("count", g.getCount());
            }
        }
        return out;
    }

    // ---- helpers ----

    private YearMonth month(JsonNode args) {
        String value = text(args, "month");
        if (value == null) {
            return YearMonth.from(tenantClock.today());
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BadInput("month must be YYYY-MM");
        }
    }

    private static ObjectNode monthSchema() {
        return schema(Map.of(
                "month",
                NODES.objectNode()
                        .put("type", "string")
                        .put("pattern", "^[0-9]{4}-[0-9]{2}$")
                        .put("description", "The month, YYYY-MM. Default this month.")));
    }

    private static void more(ObjectNode out, long total, int shown) {
        out.put("total", total);
        if (total > shown) {
            out.put("more", "and " + (total - shown) + " more - open the screen to see them all");
        }
    }

    private static String fullName(Employee e) {
        String first = e.getFirstName() == null ? "" : e.getFirstName();
        String last = e.getLastName() == null ? "" : e.getLastName();
        return (first + " " + last).trim();
    }

    private static String group(OrgMaster master) {
        return master == null ? NO_GROUP : label(master.getName());
    }

    /** One line of at most {@value #MAX_LABEL} characters; blank becomes {@value #NO_GROUP}. */
    static String label(String value) {
        if (value == null || value.isBlank()) {
            return NO_GROUP;
        }
        String line = value.replaceAll("[\\r\\n\\t\\u2028\\u2029]+", " ")
                .replaceAll(" {2,}", " ")
                .trim();
        return line.length() > MAX_LABEL ? line.substring(0, MAX_LABEL) : line;
    }
}
