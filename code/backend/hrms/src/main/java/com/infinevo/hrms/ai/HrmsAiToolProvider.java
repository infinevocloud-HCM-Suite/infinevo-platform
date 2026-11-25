package com.infinevo.hrms.ai;

import static com.infinevo.core.ai.AiToolInputs.constantNames;
import static com.infinevo.core.ai.AiToolInputs.date;
import static com.infinevo.core.ai.AiToolInputs.dateProperty;
import static com.infinevo.core.ai.AiToolInputs.enumProperty;
import static com.infinevo.core.ai.AiToolInputs.enumValue;
import static com.infinevo.core.ai.AiToolInputs.rejectUnknown;
import static com.infinevo.core.ai.AiToolInputs.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolInputs.BadInput;
import com.infinevo.core.ai.AiToolProvider;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.hrms.attendance.ClockService;
import com.infinevo.hrms.attendance.RegularizationService;
import com.infinevo.hrms.project.ValidationException;
import com.infinevo.hrms.timesheet.TimesheetService;
import com.infinevo.hrms.timesheet.TimesheetStatus;
import com.infinevo.shared.authz.NoEmployeeRecordException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The AI helper's HRMS tools (W-114.6): five read-only answers about the caller's own attendance and timesheets.
 *
 * <p>Each tool calls the same service method, with the same arguments, as the screen's controller does - cited per
 * tool - and declares that controller method's {@code @RequiresAction}. No input names an employee (rule 1): the
 * services resolve the caller from the login through {@link EmployeeService#currentEmployee()}, and before a tool calls
 * one it checks that this employee is {@link AiToolContext#employeeId()}; a login with no employee record, or a
 * different one, runs nothing.
 *
 * <p>Not checked here, because {@code AiToolRegistry} checks both before a tool is offered and again before it runs:
 * the {@code requiredAction}, and the HRMS licence, which the registry reads from the {@code hrms.} name prefix
 * through {@code EntitlementService} (rule 3).
 *
 * <p>A range defaults to the last 30 days and is refused past {@link ClockService#MAX_RANGE_DAYS} (rule 2). Bad input
 * and a service refusal both answer a failed {@link AiToolResult} with one plain sentence, never an exception (rule 5).
 * Answers are built by {@link HrmsAiToolResults}: no ids, other people's text wrapped (rules 4 and 7). Masking is
 * applied afterwards by the chat loop (rule 6).
 */
@Component
public class HrmsAiToolProvider implements AiToolProvider {

    /** The default range, back from today, when neither end is given. */
    static final int DEFAULT_RANGE_DAYS = 30;

    /** {@code ClockService.MAX_RANGE_DAYS} and {@code RegularizationService.MAX_RANGE_DAYS} are both 93. */
    static final int MAX_RANGE_DAYS = ClockService.MAX_RANGE_DAYS;

    static final String RANGE_TOO_LONG =
            "That range is longer than " + MAX_RANGE_DAYS + " days. Ask for " + MAX_RANGE_DAYS + " days or fewer.";

    static final String NO_EMPLOYEE = "Not available: the signed-in user has no employee record";

    static final String NOT_ALLOWED = "Not available: the signed-in user is not allowed to see this";

    static final String SERVICE_FAILED = "The HRMS service could not answer just now. Try again later.";

    private static final Logger log = LoggerFactory.getLogger(HrmsAiToolProvider.class);

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final ClockService clockService;
    private final RegularizationService regularizationService;
    private final TimesheetService timesheetService;
    private final EmployeeService employeeService;
    private final TenantClock tenantClock;
    private final TransactionOperations tx;
    private final List<AiTool> tools;

    @Autowired
    public HrmsAiToolProvider(
            ClockService clockService,
            RegularizationService regularizationService,
            TimesheetService timesheetService,
            EmployeeService employeeService,
            TenantClock tenantClock,
            PlatformTransactionManager transactionManager) {
        this(
                clockService,
                regularizationService,
                timesheetService,
                employeeService,
                tenantClock,
                readOnly(transactionManager));
    }

    HrmsAiToolProvider(
            ClockService clockService,
            RegularizationService regularizationService,
            TimesheetService timesheetService,
            EmployeeService employeeService,
            TenantClock tenantClock,
            TransactionOperations tx) {
        this.clockService = Objects.requireNonNull(clockService, "clockService must not be null");
        this.regularizationService =
                Objects.requireNonNull(regularizationService, "regularizationService must not be null");
        this.timesheetService = Objects.requireNonNull(timesheetService, "timesheetService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.tenantClock = Objects.requireNonNull(tenantClock, "tenantClock must not be null");
        this.tx = Objects.requireNonNull(tx, "tx must not be null");
        this.tools = List.of(
                myAttendanceToday(), myAttendanceSessions(), myRegularizations(), myTimesheetWeek(), myTimesheets());
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

    // ---- the five tools ----

    /** {@code GET /api/v1/hrms/attendance/today} - {@code attendance/ClockController.java:50-54}. */
    private AiTool myAttendanceToday() {
        return new HrmsTool(
                "hrms.my_attendance_today",
                "The signed-in user's own clock status today: whether they are clocked in, today's clock sessions"
                        + " with clock-in and clock-out times (ISO, UTC), and minutes worked so far. Takes no input.",
                schema(Map.of()),
                "hrms.attendance.mark",
                (ctx, input) -> HrmsAiToolResults.today(clockService.today()));
    }

    /** {@code GET /api/v1/hrms/attendance/sessions/mine} - {@code attendance/ClockController.java:56-62}. */
    private AiTool myAttendanceSessions() {
        return new HrmsTool(
                "hrms.my_attendance_sessions",
                "The signed-in user's own clock sessions between two dates (YYYY-MM-DD), newest first: date, clock-in"
                        + " and clock-out times (ISO, UTC), minutes worked, and the day's attendance status. Defaults"
                        + " to the last " + DEFAULT_RANGE_DAYS + " days; at most " + MAX_RANGE_DAYS + " days.",
                schema(rangeProperties()),
                "core.attendance.read_own",
                (ctx, input) -> {
                    Range range = range(input);
                    return HrmsAiToolResults.sessions(
                            range.fromText(), range.toText(), clockService.mySessions(range.from(), range.to()));
                });
    }

    /** {@code GET /api/v1/hrms/attendance/regularizations/mine} - {@code attendance/RegularizationController.java:49-55}. */
    private AiTool myRegularizations() {
        return new HrmsTool(
                "hrms.my_regularizations",
                "The signed-in user's own attendance regularization requests for dates between two dates"
                        + " (YYYY-MM-DD), newest first: the date, the corrected times, their reason, the status and"
                        + " the decision. Defaults to the last " + DEFAULT_RANGE_DAYS + " days; at most "
                        + MAX_RANGE_DAYS + " days.",
                schema(rangeProperties()),
                "core.attendance.read_own",
                (ctx, input) -> {
                    Range range = range(input);
                    return HrmsAiToolResults.regularizations(
                            range.fromText(), range.toText(), regularizationService.mine(range.from(), range.to()));
                });
    }

    /** {@code GET /api/v1/me/timesheet} - {@code portal/MyTimesheetController.java:43-51}. */
    private AiTool myTimesheetWeek() {
        return new HrmsTool(
                "hrms.my_timesheet_week",
                "The signed-in user's own timesheet for one week, by project, task and day, with hours and status."
                        + " Optional weekStart, the Monday of the week (YYYY-MM-DD); this week when left out.",
                schema(Map.of("weekStart", dateProperty("The Monday the week starts on, YYYY-MM-DD."))),
                "hrms.timesheet.read_own",
                (ctx, input) -> {
                    LocalDate weekStart = date(input, "weekStart");
                    if (weekStart != null && weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
                        throw new BadInput("weekStart must be a Monday; a timesheet week runs Monday to Sunday");
                    }
                    return HrmsAiToolResults.week(
                            weekStart == null ? null : weekStart.toString(),
                            timesheetService.forWeek(weekStart).orElse(null));
                });
    }

    /** {@code GET /api/v1/hrms/timesheets/mine} - {@code timesheet/TimesheetController.java:93-102}. */
    private AiTool myTimesheets() {
        Map<String, ObjectNode> props = new HashMap<>(rangeProperties(
                "Only weeks starting on or after this date, YYYY-MM-DD.",
                "Only weeks starting on or before this date, YYYY-MM-DD."));
        props.put("status", enumProperty("Only timesheets in this status.", constantNames(TimesheetStatus.values())));
        return new HrmsTool(
                "hrms.my_timesheets",
                "The signed-in user's own timesheets, newest week first, each with its projects, tasks, daily hours"
                        + " and status. Optional from / to (YYYY-MM-DD, compared with the week's start; default the"
                        + " last " + DEFAULT_RANGE_DAYS + " days, at most " + MAX_RANGE_DAYS + " days) and status.",
                schema(props),
                "hrms.timesheet.read_own",
                (ctx, input) -> {
                    Range range = range(input);
                    TimesheetStatus status = enumValue(input, "status", TimesheetStatus.class);
                    return HrmsAiToolResults.timesheets(
                            range.fromText(),
                            range.toText(),
                            status == null ? null : status.name(),
                            timesheetService.listMine(range.from(), range.to(), status, null));
                });
    }

    // ---- running a tool ----

    /** One own-data tool: it needs an employee record, runs read-only, and answers JSON built without ids. */
    private final class HrmsTool implements AiTool {

        private final String name;
        private final String description;
        private final ObjectNode inputSchema;
        private final String requiredAction;
        private final BiFunction<AiToolContext, JsonNode, JsonNode> body;

        HrmsTool(
                String name,
                String description,
                ObjectNode inputSchema,
                String requiredAction,
                BiFunction<AiToolContext, JsonNode, JsonNode> body) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
            this.requiredAction = requiredAction;
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
                JsonNode data = tx.execute(status -> {
                    requireCallerIs(ctx.employeeId());
                    return body.apply(ctx, args);
                });
                return AiToolResult.success(data == null ? NODES.nullNode() : data);
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            } catch (NotTheCaller | NoEmployeeRecordException e) {
                return AiToolResult.failure(NO_EMPLOYEE);
            } catch (AccessDeniedException e) {
                return AiToolResult.failure(NOT_ALLOWED);
            } catch (ClockService.ValidationException
                    | RegularizationService.ValidationException
                    | ValidationException e) {
                // The services' own refusals are already one plain sentence about the input (rule 5).
                return AiToolResult.failure(e.getMessage());
            } catch (RuntimeException e) {
                log.warn("AI tool {} failed: {}", name, e.toString());
                return AiToolResult.failure(SERVICE_FAILED);
            }
        }
    }

    /**
     * The services read the caller from the login ({@code ClockServiceImpl.requireCurrentEmployee},
     * {@code RegularizationServiceImpl.requireCurrentEmployee}, {@code TimesheetServiceImpl.currentEmployee}); the tool
     * runs only when that is the employee the helper's context names.
     */
    private void requireCallerIs(UUID employeeId) {
        Optional<UUID> current = employeeService.currentEmployee().map(EmployeeResponse::id);
        if (current.isEmpty() || !current.get().equals(employeeId)) {
            throw new NotTheCaller();
        }
    }

    // ---- input ----

    private record Range(LocalDate from, LocalDate to) {
        String fromText() {
            return from.toString();
        }

        String toText() {
            return to.toString();
        }
    }

    /**
     * {@code from} and {@code to}: both left out is the last {@link #DEFAULT_RANGE_DAYS} days to today; only
     * {@code from} runs to today (or to {@code from} itself if that is later); only {@code to} starts
     * {@link #DEFAULT_RANGE_DAYS} days before it. Refused if it ends before it starts or is longer than
     * {@link #MAX_RANGE_DAYS} days, measured as the services measure it ({@code ChronoUnit.DAYS.between}).
     */
    private Range range(JsonNode input) {
        LocalDate from = date(input, "from");
        LocalDate to = date(input, "to");
        if (from == null && to == null) {
            to = tenantClock.today();
            from = to.minusDays(DEFAULT_RANGE_DAYS);
        } else if (to == null) {
            LocalDate today = tenantClock.today();
            to = today.isBefore(from) ? from : today;
        } else if (from == null) {
            from = to.minusDays(DEFAULT_RANGE_DAYS);
        }
        if (to.isBefore(from)) {
            throw new BadInput("to must be on or after from");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new BadInput(RANGE_TOO_LONG);
        }
        return new Range(from, to);
    }

    private static Map<String, ObjectNode> rangeProperties() {
        return rangeProperties(
                "First date, YYYY-MM-DD. Defaults to " + DEFAULT_RANGE_DAYS + " days before to.",
                "Last date, YYYY-MM-DD. Defaults to today.");
    }

    private static Map<String, ObjectNode> rangeProperties(String fromDescription, String toDescription) {
        return Map.of("from", dateProperty(fromDescription), "to", dateProperty(toDescription));
    }

    /** The login's employee is not the context's employee, or there is none. */
    private static final class NotTheCaller extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
