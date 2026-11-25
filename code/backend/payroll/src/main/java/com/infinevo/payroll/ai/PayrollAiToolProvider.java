package com.infinevo.payroll.ai;

import static com.infinevo.core.ai.AiToolInputs.rejectUnknown;
import static com.infinevo.core.ai.AiToolInputs.schema;
import static com.infinevo.core.ai.AiToolInputs.text;

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
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.payroll.payslip.PayslipForbiddenException;
import com.infinevo.payroll.payslip.PayslipNotFoundException;
import com.infinevo.payroll.payslip.PayslipResponse;
import com.infinevo.payroll.payslip.PayslipService;
import com.infinevo.payroll.payslip.PayslipSummaryResponse;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotFoundException;
import com.infinevo.payroll.taxdeclaration.summary.TaxSummaryService;
import com.infinevo.shared.authz.NoEmployeeRecordException;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The AI helper's Payroll tools (W-114.7): four read-only answers about the caller's own payslips and tax declaration.
 *
 * <p>Each tool calls the same service method as the screen's controller does - cited per tool - and declares that
 * controller method's {@code @RequiresAction}. No input names an employee or a pay run (rule 1): the services resolve
 * the caller from the login through {@link EmployeeService#currentEmployee()}, and before a tool calls one it checks
 * that this employee is {@link AiToolContext#employeeId()}. {@code payroll.my_payslip} takes a month and finds the run
 * among the caller's own paid payslips; a month with none answers {@link #NO_PAYSLIP} without calling
 * {@link PayslipService#own}.
 *
 * <p>Not checked here, because {@code AiToolRegistry} checks both before a tool is offered and again before it runs:
 * the {@code requiredAction}, and the Payroll licence, which the registry reads from the {@code payroll.} name prefix
 * through {@code EntitlementService} (rule 3).
 *
 * <p>The payslip tools run in a read-only transaction. The tax tools run in an ordinary one, because
 * {@code TaxDeclarationServiceImpl.readOwn} may write the year's default window
 * ({@code taxdeclaration/TaxDeclarationServiceImpl.java:153}) and {@code TaxSummaryServiceImpl.summary} upserts its
 * summary row ({@code taxdeclaration/summary/TaxSummaryServiceImpl.java:109}); Postgres refuses both inside a read-only
 * transaction. So that a question never starts a declaration - {@code readOwn} creates the year's draft when there is
 * none ({@code :146-149}) - the tool first looks the declaration up and answers {@link #NO_DECLARATION} without calling
 * {@code readOwn} when it is missing.
 *
 * <p>Answers are built by {@link PayrollAiToolResults}: a fixed list of fields, no ids, amounts as two-decimal text
 * (rules 2 and 5). Masking is applied afterwards by the chat loop (rule 6). Bad input and a service refusal both answer
 * a failed {@link AiToolResult} with one plain sentence, never an exception (rule 7).
 */
@Component
public class PayrollAiToolProvider implements AiToolProvider {

    /** The size of {@code payroll.my_payslips}' list: the controller's default page ({@code PayslipController:30}). */
    static final int LIST_SIZE = 12;

    /** How many of the caller's payslips {@code payroll.my_payslip} reads per page while it looks for a month. */
    static final int SEARCH_PAGE_SIZE = 24;

    /** At most this many pages are read looking for a month: 240 payslips, twenty years of monthly pay. */
    static final int SEARCH_MAX_PAGES = 10;

    static final String NO_PAYSLIP = "No payslip for that month yet.";

    static final String NO_PAYSLIPS = "There is no paid payslip yet.";

    static final String NO_DECLARATION = "There is no tax declaration for that financial year yet.";

    static final String NO_EMPLOYEE = "Not available: the signed-in user has no employee record";

    static final String NOT_ALLOWED = "Not available: the signed-in user is not allowed to see this";

    static final String SERVICE_FAILED = "The Payroll service could not answer just now. Try again later.";

    private static final Pattern FY_INPUT = Pattern.compile("^(\\d{4})-(\\d{2}|\\d{4})$");

    private static final Logger log = LoggerFactory.getLogger(PayrollAiToolProvider.class);

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final PayslipService payslipService;
    private final TaxDeclarationService taxDeclarationService;
    private final TaxSummaryService taxSummaryService;
    private final EmployeeService employeeService;
    private final TenantClock tenantClock;
    private final TransactionOperations readOnlyTx;
    private final TransactionOperations tx;
    private final List<AiTool> tools;

    @Autowired
    public PayrollAiToolProvider(
            PayslipService payslipService,
            TaxDeclarationService taxDeclarationService,
            TaxSummaryService taxSummaryService,
            EmployeeService employeeService,
            TenantClock tenantClock,
            PlatformTransactionManager transactionManager) {
        this(
                payslipService,
                taxDeclarationService,
                taxSummaryService,
                employeeService,
                tenantClock,
                readOnly(transactionManager),
                new TransactionTemplate(transactionManager));
    }

    PayrollAiToolProvider(
            PayslipService payslipService,
            TaxDeclarationService taxDeclarationService,
            TaxSummaryService taxSummaryService,
            EmployeeService employeeService,
            TenantClock tenantClock,
            TransactionOperations readOnlyTx,
            TransactionOperations tx) {
        this.payslipService = Objects.requireNonNull(payslipService, "payslipService must not be null");
        this.taxDeclarationService =
                Objects.requireNonNull(taxDeclarationService, "taxDeclarationService must not be null");
        this.taxSummaryService = Objects.requireNonNull(taxSummaryService, "taxSummaryService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.tenantClock = Objects.requireNonNull(tenantClock, "tenantClock must not be null");
        this.readOnlyTx = Objects.requireNonNull(readOnlyTx, "readOnlyTx must not be null");
        this.tx = Objects.requireNonNull(tx, "tx must not be null");
        this.tools = List.of(myPayslips(), myPayslip(), myTaxDeclaration(), myTaxSummary());
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

    // ---- the four tools ----

    /** {@code GET /api/v1/me/payslips} - {@code payslip/PayslipController.java:47-55}. */
    private AiTool myPayslips() {
        return new PayrollTool(
                "payroll.my_payslips",
                "The signed-in user's own paid payslips, newest month first, at most " + LIST_SIZE
                        + ": the month (YYYY-MM), the date it was paid and the net pay. Amounts are text in INR."
                        + " Takes no input.",
                schema(Map.of()),
                "payroll.payslip.read_own",
                true,
                (ctx, input) -> PayrollAiToolResults.payslips(
                        payslipService.listOwn(PageRequest.of(0, LIST_SIZE)).getContent()));
    }

    /** {@code GET /api/v1/me/payslips/{payrunId}} - {@code payslip/PayslipController.java:57-61}. */
    private AiTool myPayslip() {
        return new PayrollTool(
                "payroll.my_payslip",
                "One of the signed-in user's own paid payslips: the month, the run type (REGULAR or OFF_CYCLE),"
                        + " the date paid, paid days, every earning, deduction and reimbursement line (code, name,"
                        + " amount, taxable), gross earnings, total deductions and net pay. Amounts are text in INR,"
                        + " ready-made; do not recompute them. When the month also had an off-cycle run (a bonus or"
                        + " arrears), those payslips are in otherPayslipsThisMonth, in the same shape."
                        + " Optional period, the month as YYYY-MM; the latest paid month when left out.",
                schema(Map.of(
                        "period",
                        NODES.objectNode()
                                .put("type", "string")
                                .put("pattern", "^[0-9]{4}-[0-9]{2}$")
                                .put("description", "The pay month, YYYY-MM. Defaults to the latest paid payslip."))),
                "payroll.payslip.read_own",
                true,
                (ctx, input) -> {
                    YearMonth period = period(input);
                    List<PayslipSummaryResponse> found = findOwn(period);
                    if (found.isEmpty()) {
                        return PayrollAiToolResults.none(
                                period == null ? null : period.toString(), period == null ? NO_PAYSLIPS : NO_PAYSLIP);
                    }
                    List<PayslipResponse> slips = new ArrayList<>(found.size());
                    for (PayslipSummaryResponse slip : found) {
                        slips.add(payslipService.own(slip.payrunId()));
                    }
                    PayslipResponse main = regularFirst(slips);
                    List<PayslipResponse> others = new ArrayList<>(slips);
                    others.remove(main);
                    return PayrollAiToolResults.payslip(main, others);
                });
    }

    /** {@code GET /api/v1/me/tax-declaration/{fy}} - {@code taxdeclaration/MyTaxDeclarationController.java:46-51}. */
    private AiTool myTaxDeclaration() {
        return new PayrollTool(
                "payroll.my_tax_declaration",
                "The signed-in user's own income tax declaration for one financial year (April to March): the tax"
                        + " regime, the status (DRAFT or SUBMITTED), whether it is locked, whether the declaration"
                        + " window is open and whether it can still be edited, and the rent, home-loan and let-out"
                        + " answers. Optional financialYear as YYYY-YYYY, for example 2026-2027; the current year"
                        + " when left out.",
                schema(Map.of("financialYear", financialYearProperty())),
                "payroll.tax_declaration.read_own",
                false,
                (ctx, input) -> {
                    FinancialYear fy = financialYear(input);
                    if (taxDeclarationService.find(ctx.employeeId(), fy.label()).isEmpty()) {
                        return PayrollAiToolResults.none(fy.label(), NO_DECLARATION);
                    }
                    return PayrollAiToolResults.taxDeclaration(taxDeclarationService.readOwn(fy.label()));
                });
    }

    /**
     * {@code GET /api/v1/me/tax-declaration/{fy}/summary} -
     * {@code taxdeclaration/summary/MyTaxSummaryController.java:41-46}.
     */
    private AiTool myTaxSummary() {
        return new PayrollTool(
                "payroll.my_tax_summary",
                "The signed-in user's own income tax summary for one financial year (April to March): the totals"
                        + " declared (house rent, home loan, let-out property, deductions by section group, previous"
                        + " employment, other income) and the computed figures (taxable income, tax on it, tax paid"
                        + " so far, tax still to pay, remaining months). Amounts are text in INR. Optional"
                        + " financialYear as YYYY-YYYY, for example 2026-2027; the current year when left out.",
                schema(Map.of("financialYear", financialYearProperty())),
                "payroll.tax_declaration.read_own",
                false,
                (ctx, input) -> {
                    FinancialYear fy = financialYear(input);
                    if (taxDeclarationService.find(ctx.employeeId(), fy.label()).isEmpty()) {
                        return PayrollAiToolResults.none(fy.label(), NO_DECLARATION);
                    }
                    return PayrollAiToolResults.taxSummary(taxSummaryService.summaryOwn(fy.label()));
                });
    }

    // ---- running a tool ----

    /** One own-data tool: it needs an employee record and answers JSON built without ids. */
    private final class PayrollTool implements AiTool {

        private final String name;
        private final String description;
        private final ObjectNode inputSchema;
        private final String requiredAction;
        private final boolean readOnly;
        private final BiFunction<AiToolContext, JsonNode, JsonNode> body;

        PayrollTool(
                String name,
                String description,
                ObjectNode inputSchema,
                String requiredAction,
                boolean readOnly,
                BiFunction<AiToolContext, JsonNode, JsonNode> body) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
            this.requiredAction = requiredAction;
            this.readOnly = readOnly;
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
                JsonNode data = (readOnly ? readOnlyTx : tx).execute(status -> {
                    requireCallerIs(ctx.employeeId());
                    return body.apply(ctx, args);
                });
                return AiToolResult.success(data == null ? NODES.nullNode() : data);
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            } catch (NotTheCaller | NoEmployeeRecordException | PayslipForbiddenException e) {
                return AiToolResult.failure(NO_EMPLOYEE);
            } catch (AccessDeniedException e) {
                return AiToolResult.failure(NOT_ALLOWED);
            } catch (PayslipNotFoundException e) {
                // The service's message names the run and employee ids; the model gets the plain sentence.
                return AiToolResult.failure(NO_PAYSLIP);
            } catch (DeclarationNotFoundException e) {
                return AiToolResult.failure(NO_DECLARATION);
            } catch (RuntimeException e) {
                log.warn("AI tool {} failed: {}", name, e.toString());
                return AiToolResult.failure(SERVICE_FAILED);
            }
        }
    }

    /**
     * The services read the caller from the login ({@code PayslipServiceImpl.listOwn} and {@code own},
     * {@code TaxDeclarationServiceImpl.currentEmployeeOrDeny}, {@code TaxSummaryServiceImpl.currentEmployeeOrDeny}); the
     * tool runs only when that is the employee the helper's context names.
     */
    private void requireCallerIs(UUID employeeId) {
        Optional<UUID> current = employeeService.currentEmployee().map(EmployeeResponse::id);
        if (current.isEmpty() || !current.get().equals(employeeId)) {
            throw new NotTheCaller();
        }
    }

    /**
     * Every paid payslip of the caller for {@code period}, or for the latest paid month when {@code period} is null.
     * A month can hold one REGULAR run and any number of OFF_CYCLE runs ({@code payroll/V061__payrun_off_cycle.sql}),
     * and {@code EmployeePayRunRepository.findOwnPaidRuns} orders by period only, so the rows of one month may come
     * in any order and may straddle a page: the search reads the caller's list as the screen does
     * ({@code PayslipService.listOwn}, newest month first) a page at a time, keeps every row of the wanted month
     * (once each, by run id), and stops once the list has passed the month.
     */
    private List<PayslipSummaryResponse> findOwn(YearMonth period) {
        String wanted = period == null ? null : period.toString();
        Map<UUID, PayslipSummaryResponse> found = new LinkedHashMap<>();
        for (int page = 0; page < SEARCH_MAX_PAGES; page++) {
            Page<PayslipSummaryResponse> slips = payslipService.listOwn(PageRequest.of(page, SEARCH_PAGE_SIZE));
            for (PayslipSummaryResponse slip : slips.getContent()) {
                if (slip.period() == null) {
                    continue;
                }
                if (wanted == null) {
                    wanted = slip.period();
                }
                int order = slip.period().compareTo(wanted);
                if (order == 0) {
                    found.putIfAbsent(slip.payrunId(), slip);
                } else if (order < 0) {
                    return List.copyOf(found.values());
                }
            }
            if (!slips.hasNext()) {
                break;
            }
        }
        return List.copyOf(found.values());
    }

    /** The month's REGULAR payslip when there is exactly one; otherwise the first row the list gave. */
    static PayslipResponse regularFirst(List<PayslipResponse> slips) {
        PayslipResponse regular = null;
        int regulars = 0;
        for (PayslipResponse slip : slips) {
            if (slip.run() != null
                    && PayRunType.REGULAR.name().equals(slip.run().type())) {
                regular = slip;
                regulars++;
            }
        }
        return regulars == 1 ? regular : slips.get(0);
    }

    // ---- input ----

    private static YearMonth period(JsonNode input) {
        String value = text(input, "period");
        if (value == null) {
            return null;
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BadInput("period must be a month as YYYY-MM");
        }
    }

    /**
     * {@code financialYear} as {@code YYYY-YYYY} (the form the controllers take, {@link FinancialYear#parse}) or the
     * short {@code YYYY-YY}; left out, the financial year that holds today's date in the tenant's zone (rule 4).
     */
    FinancialYear financialYear(JsonNode input) {
        String value = text(input, "financialYear");
        if (value == null) {
            return FinancialYear.containing(tenantClock.today());
        }
        Matcher m = FY_INPUT.matcher(value.trim());
        if (!m.matches()) {
            throw new BadInput("financialYear must be YYYY-YYYY, for example 2026-2027");
        }
        int start = Integer.parseInt(m.group(1));
        String endText = m.group(2);
        int end = endText.length() == 2 ? (start / 100) * 100 + Integer.parseInt(endText) : Integer.parseInt(endText);
        if (endText.length() == 2 && end < start) {
            end += 100;
        }
        if (end != start + 1) {
            throw new BadInput("financialYear must be two years in a row, April to March, for example 2026-2027");
        }
        return FinancialYear.of(start, end);
    }

    private static ObjectNode financialYearProperty() {
        return NODES.objectNode()
                .put("type", "string")
                .put("pattern", "^[0-9]{4}-[0-9]{2,4}$")
                .put(
                        "description",
                        "The financial year, April to March, as YYYY-YYYY (2026-2027). Defaults to the current one.");
    }

    /** The login's employee is not the context's employee, or there is none. */
    private static final class NotTheCaller extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
