package com.infinevo.payroll.ai;

import static com.infinevo.core.ai.AiToolInputs.rejectUnknown;
import static com.infinevo.core.ai.AiToolInputs.schema;
import static com.infinevo.core.ai.AiToolInputs.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolInputs.BadInput;
import com.infinevo.core.ai.AiToolProvider;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.payroll.payrun.EmployeePayRunResponse;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.shared.authz.PermissionService;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The payroll officer's two read-only pay run tools (W-114.21): {@code payroll.payrun_summary} and
 * {@code payroll.payrun_next_step}. They summarise a month's regular run against the previous month's and say, in
 * words, what the officer does next. <strong>They never create, lock, compute, approve, pay or cancel a run</strong>
 * (founder, 2026-11-23): only {@link PayRunService#list} and {@link PayRunService#employees} are called, and no pay run
 * action exists in the helper.
 *
 * <p>A separate provider from {@link PayrollAiToolProvider}, whose tools all read the caller's own pay: these read
 * other employees' rows and need no employee record. Both names start with {@code payroll.}, so the registry offers
 * them only to a customer holding Payroll (W-114.7 rule 3). No result carries a run id or an employee id; the screen
 * named is the list, {@code /payroll/runs}.
 */
@Component
public class PayRunAiToolProvider implements AiToolProvider {

    static final String SUMMARY = "payroll.payrun_summary";
    static final String NEXT_STEP = "payroll.payrun_next_step";
    static final String RUN_READ = "payroll.run.read";
    static final String SCREEN_PATH = "/payroll/runs";

    /** Rows are read this many at a time, up to {@link #MAX_ROWS} per run. */
    static final int ROW_PAGE = 200;

    static final int MAX_ROWS = 5000;

    /** Regular runs are read this many at a time, newest period first, while looking for a month. */
    static final int RUN_PAGE = 24;

    static final int RUN_MAX_PAGES = 10;

    /** Rows in a fixed order, as the screen reads them, so no page repeats or skips a row. */
    static final Sort ROW_ORDER = Sort.by(Sort.Direction.ASC, "createdAt", "id");

    /** The list has no order of its own (the screen passes one); the search below needs newest period first. */
    static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("period"), Sort.Order.desc("createdAt"));

    static final String NO_RUN = "There is no pay run for that month.";
    static final String NO_FIGURES = "This run has no figures yet.";
    static final String TOO_MANY = "Too many rows to list; the totals are complete.";
    static final String SERVICE_FAILED = "The Payroll service could not answer just now. Try again later.";
    static final String NOT_ALLOWED = "Not available: the signed-in user is not allowed to see this";

    /** Statuses with computed figures. Before them the totals are zero ({@code PayRunStatus}). */
    static final Set<PayRunStatus> WITH_FIGURES =
            EnumSet.of(PayRunStatus.COMPUTED, PayRunStatus.APPROVED, PayRunStatus.PAID);

    private static final Logger log = LoggerFactory.getLogger(PayRunAiToolProvider.class);
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final PayRunService payRuns;
    private final PermissionService permissions;
    private final TenantClock tenantClock;
    private final TransactionOperations readOnlyTx;
    private final List<AiTool> tools;

    @Autowired
    public PayRunAiToolProvider(
            PayRunService payRuns,
            PermissionService permissions,
            TenantClock tenantClock,
            PlatformTransactionManager transactionManager) {
        this(payRuns, permissions, tenantClock, readOnly(transactionManager));
    }

    PayRunAiToolProvider(
            PayRunService payRuns,
            PermissionService permissions,
            TenantClock tenantClock,
            TransactionOperations readOnlyTx) {
        this.payRuns = Objects.requireNonNull(payRuns, "payRuns must not be null");
        this.permissions = Objects.requireNonNull(permissions, "permissions must not be null");
        this.tenantClock = Objects.requireNonNull(tenantClock, "tenantClock must not be null");
        this.readOnlyTx = Objects.requireNonNull(readOnlyTx, "readOnlyTx must not be null");
        ObjectNode periodSchema = schema(Map.of(
                "period",
                NODES.objectNode()
                        .put("type", "string")
                        .put("pattern", "^[0-9]{4}-[0-9]{2}$")
                        .put("description", "The month, YYYY-MM.")));
        this.tools = List.of(
                new Tool(
                        SUMMARY,
                        "Summarises a month's regular pay run (the newest when no month is given) against the"
                                + " previous month's: totals and differences, and lists of who is in this run but"
                                + " not last month's, in last month's but not this one, zero or negative net pay,"
                                + " a large change in net pay, skipped, and needing attention. Quote the figures"
                                + " as given; do not compute your own. Say \"not in this run\", never \"has left\".",
                        periodSchema,
                        (ctx, args) -> summary(args)),
                new Tool(
                        NEXT_STEP,
                        "Says what the payroll officer does next on a month's regular pay run (this month when no"
                                + " month is given), whether the signed-in user may do it, and the screen. The"
                                + " helper never takes the step itself.",
                        periodSchema,
                        (ctx, args) -> nextStep(args)));
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

    private final class Tool implements AiTool {

        private final String name;
        private final String description;
        private final ObjectNode inputSchema;
        private final BiFunction<AiToolContext, JsonNode, JsonNode> body;

        Tool(
                String name,
                String description,
                ObjectNode inputSchema,
                BiFunction<AiToolContext, JsonNode, JsonNode> body) {
            this.name = name;
            this.description = description;
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
            return RUN_READ;
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            JsonNode args = input == null || input.isNull() ? NODES.objectNode() : input;
            try {
                if (!args.isObject()) {
                    throw new BadInput("input must be a JSON object");
                }
                rejectUnknown(args, inputSchema.path("properties"));
                JsonNode data = readOnlyTx.execute(s -> body.apply(ctx, args));
                return AiToolResult.success(data == null ? NODES.nullNode() : data);
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            } catch (AccessDeniedException e) {
                return AiToolResult.failure(NOT_ALLOWED);
            } catch (RuntimeException e) {
                log.warn("AI tool {} failed: {}", name, e.toString());
                return AiToolResult.failure(SERVICE_FAILED);
            }
        }
    }

    // ---- payroll.payrun_summary ----

    private JsonNode summary(JsonNode args) {
        YearMonth wanted = period(args);
        Optional<PayRunResponse> found = wanted == null ? newestRegular() : regular(wanted);
        if (found.isEmpty()) {
            return message(wanted, NO_RUN);
        }
        PayRunResponse run = found.get();
        YearMonth month = YearMonth.parse(run.period());
        ObjectNode out = NODES.objectNode();
        out.put("period", run.period());
        out.put("status", run.status().name());
        out.put("currency", PayrollAiToolResults.CURRENCY);
        out.put("offCycleRunsThisMonth", offCycleCount(month));
        out.put("screenPath", SCREEN_PATH);
        if (!WITH_FIGURES.contains(run.status())) {
            out.put("message", NO_FIGURES);
            return out;
        }
        if (run.computedAt() != null) {
            out.put("computedAt", run.computedAt().toString());
        }
        PayRunResponse last = regular(month.minusMonths(1))
                .filter(r -> WITH_FIGURES.contains(r.status()))
                .orElse(null);
        if (last == null) {
            out.putNull("comparedWith");
        } else {
            out.put("comparedWith", last.period());
        }
        Rows rows = rows(run);
        Rows lastRows = last == null ? new Rows(List.of(), false) : rows(last);
        PayRunSummary.Result r = PayRunSummary.of(run, rows.rows(), last, lastRows.rows());
        out.set("totals", totals(r.thisRun(), r.lastRun()));
        out.put("negativeNetCount", r.negativeNetCount());
        if (rows.truncated() || lastRows.truncated()) {
            out.put("lists", TOO_MANY);
            return out;
        }
        out.set("inThisRunNotLast", list(r.inThisRunNotLast(), "netPay", null));
        out.set("inLastRunNotThis", list(r.inLastRunNotThis(), null, "lastNetPay"));
        out.set("zeroOrNegativeNet", list(r.zeroOrNegativeNet(), "netPay", null));
        out.set("largeChange", list(r.largeChange(), "netPay", "lastNetPay"));
        out.set("skipped", list(r.skipped(), null, null));
        out.set("needsAttention", list(r.needsAttention(), null, null));
        return out;
    }

    private record Rows(List<EmployeePayRunResponse> rows, boolean truncated) {}

    /** Every row of the run, {@value #ROW_PAGE} at a time, up to {@value #MAX_ROWS}. */
    private Rows rows(PayRunResponse run) {
        List<EmployeePayRunResponse> out = new ArrayList<>();
        for (int page = 0; page * ROW_PAGE < MAX_ROWS; page++) {
            Page<EmployeePayRunResponse> p =
                    payRuns.employees(run.id(), null, PageRequest.of(page, ROW_PAGE, ROW_ORDER));
            out.addAll(p.getContent());
            if (!p.hasNext()) {
                return new Rows(out, false);
            }
        }
        return new Rows(out, true);
    }

    private static ObjectNode totals(PayRunSummary.Totals now, PayRunSummary.Totals before) {
        ObjectNode t = NODES.objectNode();
        t.set("thisRun", totals(now));
        if (before != null) {
            t.set("lastRun", totals(before));
            ObjectNode d = t.putObject("difference");
            d.put("included", now.included() - before.included());
            d.put("skipped", now.skipped() - before.skipped());
            d.put("gross", PayrollAiToolResults.amount(now.gross().subtract(before.gross())));
            d.put("deductions", PayrollAiToolResults.amount(now.deductions().subtract(before.deductions())));
            d.put("netPay", PayrollAiToolResults.amount(now.netPay().subtract(before.netPay())));
            d.put(
                    "employerContributions",
                    PayrollAiToolResults.amount(now.employerContributions().subtract(before.employerContributions())));
        }
        return t;
    }

    private static ObjectNode totals(PayRunSummary.Totals x) {
        ObjectNode o = NODES.objectNode();
        o.put("included", x.included());
        o.put("skipped", x.skipped());
        o.put("gross", PayrollAiToolResults.amount(x.gross()));
        o.put("deductions", PayrollAiToolResults.amount(x.deductions()));
        o.put("netPay", PayrollAiToolResults.amount(x.netPay()));
        o.put("employerContributions", PayrollAiToolResults.amount(x.employerContributions()));
        return o;
    }

    private static ObjectNode list(PayRunSummary.Capped capped, String thisField, String lastField) {
        ObjectNode o = NODES.objectNode();
        o.put("total", capped.total());
        ArrayNode items = o.putArray("items");
        for (PayRunSummary.Item i : capped.items()) {
            ObjectNode row = items.addObject();
            row.put("name", i.name() == null ? "" : i.name());
            row.put("employeeNumber", i.employeeNumber() == null ? "" : i.employeeNumber());
            if (lastField != null && i.lastNet() != null) {
                row.put(lastField, PayrollAiToolResults.amount(i.lastNet()));
            }
            if (thisField != null && i.thisNet() != null) {
                row.put(thisField, PayrollAiToolResults.amount(i.thisNet()));
            }
            if (lastField != null && thisField != null && i.lastNet() != null && i.thisNet() != null) {
                row.put("difference", PayrollAiToolResults.amount(PayRunSummary.difference(i.lastNet(), i.thisNet())));
            }
            if (i.note() != null) {
                row.put("reason", i.note());
            }
        }
        return o;
    }

    // ---- payroll.payrun_next_step ----

    private JsonNode nextStep(JsonNode args) {
        YearMonth wanted = period(args);
        YearMonth month = wanted == null ? YearMonth.from(tenantClock.today()) : wanted;
        Optional<PayRunResponse> run = regular(month);
        PayRunStatus status = run.map(PayRunResponse::status).orElse(null);
        PayRunNextStep.Step step = PayRunNextStep.of(status);
        ObjectNode out = NODES.objectNode();
        out.put("period", month.toString());
        if (status == null) {
            out.putNull("status");
        } else {
            out.put("status", status.name());
        }
        out.put("nextStep", step.nextStep());
        if (status == PayRunStatus.COMPUTING) {
            PayRunResponse r = run.get();
            out.put("progressDone", r.progressDone());
            out.put("progressTotal", r.progressTotal());
        }
        if (step.requiredAction() == null) {
            out.putNull("youCanDoThis");
        } else {
            out.put("youCanDoThis", permissions.holds(step.requiredAction()));
        }
        if (status == PayRunStatus.FAILED) {
            // The reason is free text that may quote data (rule 6): only that it exists.
            out.put("failureReason", "on the run");
        }
        out.put("screenPath", SCREEN_PATH);
        return out;
    }

    // ---- finding runs ----

    /**
     * The month's regular run: the one not cancelled when there is one, else the cancelled one. Regular runs are
     * read newest first; the search stops at the first older month.
     */
    private Optional<PayRunResponse> regular(YearMonth month) {
        String period = month.toString();
        PayRunResponse cancelled = null;
        for (int page = 0; page < RUN_MAX_PAGES; page++) {
            Page<PayRunResponse> p =
                    payRuns.list(null, PayRunType.REGULAR, PageRequest.of(page, RUN_PAGE, NEWEST_FIRST));
            for (PayRunResponse r : p.getContent()) {
                int order = r.period().compareTo(period);
                if (order == 0) {
                    if (r.status() != PayRunStatus.CANCELLED) {
                        return Optional.of(r);
                    }
                    cancelled = cancelled == null ? r : cancelled;
                } else if (order < 0) {
                    return Optional.ofNullable(cancelled);
                }
            }
            if (!p.hasNext()) {
                break;
            }
        }
        return Optional.ofNullable(cancelled);
    }

    private Optional<PayRunResponse> newestRegular() {
        Page<PayRunResponse> p = payRuns.list(null, PayRunType.REGULAR, PageRequest.of(0, RUN_PAGE, NEWEST_FIRST));
        Optional<PayRunResponse> first = p.getContent().stream().findFirst();
        return first.flatMap(r -> regular(YearMonth.parse(r.period())));
    }

    private int offCycleCount(YearMonth month) {
        String period = month.toString();
        int count = 0;
        for (int page = 0; page < RUN_MAX_PAGES; page++) {
            Page<PayRunResponse> p =
                    payRuns.list(null, PayRunType.OFF_CYCLE, PageRequest.of(page, RUN_PAGE, NEWEST_FIRST));
            for (PayRunResponse r : p.getContent()) {
                int order = r.period().compareTo(period);
                if (order == 0 && r.status() != PayRunStatus.CANCELLED) {
                    count++;
                } else if (order < 0) {
                    return count;
                }
            }
            if (!p.hasNext()) {
                break;
            }
        }
        return count;
    }

    private static YearMonth period(JsonNode args) {
        String value = text(args, "period");
        if (value == null) {
            return null;
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BadInput("period must be YYYY-MM");
        }
    }

    private static JsonNode message(YearMonth period, String text) {
        ObjectNode out = NODES.objectNode();
        if (period != null) {
            out.put("period", period.toString());
        }
        out.put("message", text);
        out.put("screenPath", SCREEN_PATH);
        return out;
    }
}
