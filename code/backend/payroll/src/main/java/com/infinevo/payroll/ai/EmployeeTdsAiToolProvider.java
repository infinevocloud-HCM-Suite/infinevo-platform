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
import com.infinevo.core.employee.EmployeeQueryService;
import com.infinevo.core.employee.EmployeeSummaryResponse;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.tds.EmployeeTds;
import com.infinevo.payroll.tds.EmployeeTdsService;
import com.infinevo.payroll.tds.TdsSpread;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code payroll.employee_tds} (W-114.27): one employee's TDS figures for a payroll officer, the figures the
 * employee's TDS card shows ({@code GET /api/v1/payroll/employees/{id}/tds/{fy}}, {@code payroll.tax_declaration.read}),
 * through the same three {@link EmployeeTdsService} calls. The person is named by employee number or full name. No
 * id, PAN or free-text note is ever returned; a soft-deleted employee answers like an unknown one.
 */
@Component
public class EmployeeTdsAiToolProvider implements AiToolProvider {

    static final String NAME = "payroll.employee_tds";
    static final String TAX_READ = "payroll.tax_declaration.read";
    static final String SCREEN_PATH = "/employees";

    static final String NO_EMPLOYEE = "No employee with that number or name.";
    static final String NO_RECORD = "There is no TDS record for that employee and year.";
    static final String MANY = "More than one employee has that name. Ask which one, by employee number.";
    static final String SERVICE_FAILED = "The Payroll service could not answer just now. Try again later.";
    static final String NOT_ALLOWED = "Not available: the signed-in user is not allowed to see this";

    static final int MAX_MATCHES = 10;
    private static final Pattern FY_INPUT = Pattern.compile("^(\\d{4})-(\\d{2}|\\d{4})$");
    private static final Logger log = LoggerFactory.getLogger(EmployeeTdsAiToolProvider.class);
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final EmployeeQueryService employees;
    private final EmployeeTdsService tds;
    private final TenantClock tenantClock;
    private final TransactionOperations readOnlyTx;
    private final List<AiTool> tools;

    @Autowired
    public EmployeeTdsAiToolProvider(
            EmployeeQueryService employees,
            EmployeeTdsService tds,
            TenantClock tenantClock,
            PlatformTransactionManager transactionManager) {
        this(employees, tds, tenantClock, readOnly(transactionManager));
    }

    EmployeeTdsAiToolProvider(
            EmployeeQueryService employees,
            EmployeeTdsService tds,
            TenantClock tenantClock,
            TransactionOperations readOnlyTx) {
        this.employees = Objects.requireNonNull(employees, "employees must not be null");
        this.tds = Objects.requireNonNull(tds, "tds must not be null");
        this.tenantClock = Objects.requireNonNull(tenantClock, "tenantClock must not be null");
        this.readOnlyTx = Objects.requireNonNull(readOnlyTx, "readOnlyTx must not be null");
        this.tools = List.of(new Tool());
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

        private final ObjectNode inputSchema = schema(
                Map.of(
                        "employee",
                        NODES.objectNode()
                                .put("type", "string")
                                .put("maxLength", 100)
                                .put("description", "The employee number, or the employee's full name."),
                        "financialYear",
                        NODES.objectNode()
                                .put("type", "string")
                                .put("pattern", "^[0-9]{4}-[0-9]{2,4}$")
                                .put(
                                        "description",
                                        "The financial year, April to March, as YYYY-YYYY (2026-2027). Defaults to"
                                                + " the current one.")),
                "employee");

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public String description() {
            return "One employee's TDS for a financial year, as their TDS card shows it: annual tax, deducted so far,"
                    + " remaining, months left and the monthly share. Name the employee by number or full name."
                    + " Quote the figures as given.";
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }

        @Override
        public String requiredAction() {
            return TAX_READ;
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            JsonNode args = input == null || input.isNull() ? NODES.objectNode() : input;
            try {
                if (!args.isObject()) {
                    throw new BadInput("input must be a JSON object");
                }
                rejectUnknown(args, inputSchema.path("properties"));
                String who = text(args, "employee");
                if (who == null) {
                    throw new BadInput("employee is required: an employee number or a full name");
                }
                FinancialYear fy = financialYear(args);
                JsonNode data = readOnlyTx.execute(s -> answer(who.trim(), fy));
                return AiToolResult.success(data == null ? NODES.nullNode() : data);
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            } catch (AccessDeniedException e) {
                return AiToolResult.failure(NOT_ALLOWED);
            } catch (RuntimeException e) {
                log.warn("AI tool {} failed: {}", NAME, e.toString());
                return AiToolResult.failure(SERVICE_FAILED);
            }
        }
    }

    private JsonNode answer(String who, FinancialYear fy) {
        List<EmployeeSummaryResponse> matches = find(who);
        if (matches.isEmpty()) {
            return message(NO_EMPLOYEE);
        }
        if (matches.size() > 1) {
            ObjectNode out = message(MANY);
            ArrayNode list = out.putArray("matches");
            for (EmployeeSummaryResponse e : matches.subList(0, Math.min(MAX_MATCHES, matches.size()))) {
                list.addObject().put("name", fullName(e)).put("employeeNumber", e.employeeNumber());
            }
            return out;
        }
        EmployeeSummaryResponse e = matches.get(0);
        Optional<EmployeeTds> active = tds.active(e.id(), fy.label());
        ObjectNode out = NODES.objectNode();
        out.put("name", fullName(e));
        out.put("employeeNumber", e.employeeNumber());
        out.put("financialYear", fy.label());
        out.put("screenPath", SCREEN_PATH);
        if (active.isEmpty()) {
            out.put("message", NO_RECORD);
            return out;
        }
        EmployeeTds record = active.get();
        BigDecimal soFar = tds.yearToDate(e.id(), fy.label());
        TdsSpread spread = tds.spread(record, soFar);
        out.put("currency", PayrollAiToolResults.CURRENCY);
        put(
                out,
                "regime",
                record.getRegime() == null ? null : record.getRegime().name());
        put(
                out,
                "source",
                record.getSource() == null ? null : record.getSource().name());
        put(out, "effectiveFrom", record.getEffectiveFromPeriod());
        put(out, "annualGross", PayrollAiToolResults.amount(record.getAnnualGross()));
        put(out, "annualTaxableIncome", PayrollAiToolResults.amount(record.getAnnualTaxableIncome()));
        put(out, "annualTax", PayrollAiToolResults.amount(record.getAnnualTax()));
        put(out, "deductedSoFar", PayrollAiToolResults.amount(spread.deductedSoFar()));
        put(out, "remaining", PayrollAiToolResults.amount(spread.remaining()));
        out.put("monthsLeft", spread.monthsLeft());
        put(
                out,
                "monthlyShare",
                PayrollAiToolResults.amount(spread.monthlyShare().toAmount()));
        put(
                out,
                "nextPayMonth",
                spread.nextPayMonth() == null ? null : spread.nextPayMonth().toString());
        if (record.getSuggestedAt() != null && record.getSuggestedAnnualTax() != null) {
            out.put("suggestedChangeWaiting", true);
            put(out, "suggestedAnnualTax", PayrollAiToolResults.amount(record.getSuggestedAnnualTax()));
        } else {
            out.put("suggestedChangeWaiting", false);
        }
        out.put(
                "deductedSoFarMeans",
                "TDS on computed, approved and paid regular runs this year, plus prior payroll - as the TDS card shows");
        return out;
    }

    /** An exact employee number first; otherwise an exact full name. Live employees only. */
    private List<EmployeeSummaryResponse> find(String who) {
        String wanted = who.toLowerCase(Locale.ROOT);
        String prefix = who.split("\\s+")[0];
        List<EmployeeSummaryResponse> found =
                employees.search(prefix, null, false, PageRequest.of(0, 100)).getContent();
        List<EmployeeSummaryResponse> byNumber = found.stream()
                .filter(e -> e.employeeNumber() != null
                        && e.employeeNumber().toLowerCase(Locale.ROOT).equals(wanted))
                .toList();
        if (!byNumber.isEmpty()) {
            return byNumber;
        }
        return found.stream()
                .filter(e -> fullName(e).toLowerCase(Locale.ROOT).equals(wanted)
                        || shortName(e).toLowerCase(Locale.ROOT).equals(wanted))
                .toList();
    }

    private FinancialYear financialYear(JsonNode args) {
        String value = text(args, "financialYear");
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

    private static String fullName(EmployeeSummaryResponse e) {
        return String.join(
                        " ",
                        java.util.stream.Stream.of(e.firstName(), e.middleName(), e.lastName())
                                .filter(p -> p != null && !p.isBlank())
                                .toList())
                .trim();
    }

    private static String shortName(EmployeeSummaryResponse e) {
        return String.join(
                        " ",
                        java.util.stream.Stream.of(e.firstName(), e.lastName())
                                .filter(p -> p != null && !p.isBlank())
                                .toList())
                .trim();
    }

    private static ObjectNode message(String text) {
        ObjectNode out = NODES.objectNode();
        out.put("message", text);
        out.put("screenPath", SCREEN_PATH);
        return out;
    }

    private static void put(ObjectNode out, String name, String value) {
        if (value != null) {
            out.put(name, value);
        }
    }
}
