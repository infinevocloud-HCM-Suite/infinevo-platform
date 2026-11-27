package com.infinevo.core.ai.action;

import static com.infinevo.core.ai.AiToolInputs.date;
import static com.infinevo.core.ai.AiToolInputs.dateProperty;
import static com.infinevo.core.ai.AiToolInputs.enumProperty;
import static com.infinevo.core.ai.AiToolInputs.schema;
import static com.infinevo.core.ai.AiToolInputs.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiProposedAction;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolInputs.BadInput;
import com.infinevo.core.ai.AiToolProvider;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmploymentType;
import com.infinevo.core.org.DepartmentRepository;
import com.infinevo.core.org.DesignationRepository;
import com.infinevo.core.org.OrgMaster;
import com.infinevo.core.org.OrgMasterRepository;
import com.infinevo.core.org.WorkLocationRepository;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code core.propose_employee_create} (W-114.20): HR gives a new employee's details in chat and the helper proposes
 * them as a card. Nothing is written until HR presses Confirm. Department, designation and work location are given by
 * name and resolved within the tenant; the request is checked by {@link EmployeeService#checkCreate}, the checks the
 * Add employee screen runs, so a duplicate number, an email in use or a full plan is refused with no card.
 *
 * <p>Mobile, salary, bank, PAN, Aadhaar, status, termination, portal login and the reporting manager are never set
 * here: a call naming one is refused with a sentence saying where HR sets it.
 */
@Component
public class EmployeeActionToolProvider implements AiToolProvider {

    static final String NAME = "core.propose_employee_create";
    static final int MAX_TEXT = 100;

    static final String MANAGER_NOT_HERE = "The reporting manager is not set from chat. After the employee is added,"
            + " set it on the employee's page.";
    static final String NOT_HERE = "Mobile, salary, bank, PAN, Aadhaar, status and portal login are not set from"
            + " chat. Set them on the employee's page after the employee is added.";

    /** Fields a model may try that chat never sets: refused with {@link #NOT_HERE}, never stored. */
    static final Set<String> NEVER_FROM_CHAT = Set.of(
            "mobile",
            "phone",
            "status",
            "terminationDate",
            "terminationReason",
            "terminationRemarks",
            "portalEnabled",
            "salary",
            "ctc",
            "bank",
            "bankAccount",
            "ifsc",
            "pan",
            "aadhaar",
            "uan");

    static final Set<String> MANAGER_FIELDS = Set.of("manager", "reportingManager", "managerName");

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final EmployeeService employeeService;
    private final DepartmentRepository departments;
    private final DesignationRepository designations;
    private final WorkLocationRepository locations;
    private final AiActionProposals proposals;
    private final TransactionOperations tx;
    private final List<AiTool> tools;

    @Autowired
    public EmployeeActionToolProvider(
            EmployeeService employeeService,
            DepartmentRepository departments,
            DesignationRepository designations,
            WorkLocationRepository locations,
            AiActionProposals proposals,
            PlatformTransactionManager transactionManager) {
        this(
                employeeService,
                departments,
                designations,
                locations,
                proposals,
                new TransactionTemplate(transactionManager));
    }

    EmployeeActionToolProvider(
            EmployeeService employeeService,
            DepartmentRepository departments,
            DesignationRepository designations,
            WorkLocationRepository locations,
            AiActionProposals proposals,
            TransactionOperations tx) {
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.departments = Objects.requireNonNull(departments, "departments must not be null");
        this.designations = Objects.requireNonNull(designations, "designations must not be null");
        this.locations = Objects.requireNonNull(locations, "locations must not be null");
        this.proposals = Objects.requireNonNull(proposals, "proposals must not be null");
        this.tx = Objects.requireNonNull(tx, "tx must not be null");
        this.tools = List.of(new ProposeTool());
    }

    @Override
    public List<AiTool> tools() {
        return tools;
    }

    private static ObjectNode textProperty(String description) {
        return NODES.objectNode()
                .put("type", "string")
                .put("maxLength", MAX_TEXT)
                .put("description", description);
    }

    private final class ProposeTool implements AiTool {

        private final ObjectNode inputSchema;

        ProposeTool() {
            Map<String, ObjectNode> props = new LinkedHashMap<>();
            props.put("employeeNumber", textProperty("The employee number HR gives. Never make one up."));
            props.put("firstName", textProperty("First name."));
            props.put("middleName", textProperty("Middle name, if given."));
            props.put("lastName", textProperty("Last name, if given."));
            props.put("gender", enumProperty("Gender, if given.", List.of("MALE", "FEMALE", "OTHER", "UNDISCLOSED")));
            props.put("dateOfJoining", dateProperty("Date of joining, YYYY-MM-DD."));
            props.put("workEmail", textProperty("Work email, if given."));
            props.put("department", textProperty("Department by its name."));
            props.put("designation", textProperty("Designation by its name."));
            props.put("workLocation", textProperty("Work location by its name."));
            props.put(
                    "employmentType",
                    enumProperty(
                            "Employment type, if given.",
                            Arrays.stream(EmploymentType.values())
                                    .map(Enum::name)
                                    .toList()));
            props.put("probationEndDate", dateProperty("Probation end date, YYYY-MM-DD, if given."));
            props.put(
                    "noticePeriodDays",
                    NODES.objectNode()
                            .put("type", "integer")
                            .put("minimum", 0)
                            .put("description", "Notice period in days, if given."));
            inputSchema = schema(props, "employeeNumber", "firstName", "dateOfJoining");
        }

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public String description() {
            return "Proposes adding a new employee with the details HR gave. Nothing is added: HR sees a card with"
                    + " every field and must press Confirm. Employee number, first name and date of joining are"
                    + " required; ask HR for any that is missing. The reporting manager, mobile, salary, bank,"
                    + " PAN, Aadhaar and portal login are not set here.";
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }

        @Override
        public String requiredAction() {
            return EmployeeCreateExecutor.ACTION_CODE;
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            JsonNode args = input == null || input.isNull() ? NODES.objectNode() : input;
            try {
                if (!args.isObject()) {
                    throw new BadInput("input must be a JSON object");
                }
                refuseFieldsNotFromChat(args, inputSchema.path("properties"));
                String number = text(args, "employeeNumber");
                String first = text(args, "firstName");
                LocalDate joining = date(args, "dateOfJoining");
                if (number == null) {
                    throw new BadInput("employeeNumber is required. Ask HR for it.");
                }
                if (first == null) {
                    throw new BadInput("firstName is required. Ask HR for it.");
                }
                if (joining == null) {
                    throw new BadInput("dateOfJoining is required. Ask HR for it.");
                }
                String type = text(args, "employmentType");
                EmploymentType employmentType = type == null ? null : employmentType(type);
                Integer notice = notice(args);
                AiProposedAction card = tx.execute(s -> {
                    EmployeeRequest request = new EmployeeRequest(
                            number.trim(),
                            first.trim(),
                            trimmed(text(args, "middleName")),
                            trimmed(text(args, "lastName")),
                            text(args, "gender"),
                            joining,
                            null,
                            null,
                            trimmed(text(args, "workEmail")),
                            null,
                            null,
                            resolve(departments, ctx, text(args, "department"), "department"),
                            resolve(designations, ctx, text(args, "designation"), "designation"),
                            resolve(locations, ctx, text(args, "workLocation"), "work location"),
                            employmentType,
                            date(args, "probationEndDate"),
                            notice,
                            null,
                            null);
                    employeeService.checkCreate(request);
                    return proposals.propose(
                            ctx, EmployeeCreateExecutor.ACTION_CODE, EmployeeCreateExecutor.payload(request));
                });
                return AiToolResult.success(answer(Objects.requireNonNull(card, "card")));
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            } catch (EmployeeService.ValidationException e) {
                return AiToolResult.failure(EmployeeCreateExecutor.reasons(e));
            } catch (EmployeeService.DuplicateEmployeeNumberException
                    | EmployeeService.EmployeeLimitReachedException
                    | IllegalArgumentException
                    | IllegalStateException e) {
                return AiToolResult.failure(
                        e.getMessage() == null || e.getMessage().isBlank()
                                ? "The employee was refused."
                                : e.getMessage());
            }
        }
    }

    /** A field chat never sets gets its own sentence; any other unknown field is refused by name. */
    private static void refuseFieldsNotFromChat(JsonNode args, JsonNode properties) {
        for (Iterator<String> it = args.fieldNames(); it.hasNext(); ) {
            String name = it.next();
            if (MANAGER_FIELDS.contains(name)) {
                throw new BadInput(MANAGER_NOT_HERE);
            }
            if (NEVER_FROM_CHAT.contains(name)) {
                throw new BadInput(NOT_HERE);
            }
            if (!properties.has(name)) {
                throw new BadInput("Unknown field " + name + ".");
            }
        }
    }

    /** An active master of the tenant by name, ignoring case; none or several is refused with the valid names. */
    private static <T extends OrgMaster> UUID resolve(
            OrgMasterRepository<T> repo, AiToolContext ctx, String name, String what) {
        if (name == null) {
            return null;
        }
        List<T> active = repo.findByTenantIdAndActiveTrueOrderByCodeAsc(ctx.tenantId());
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        List<T> matches = active.stream()
                .filter(m -> m.getName() != null
                        && m.getName().trim().toLowerCase(Locale.ROOT).equals(wanted))
                .toList();
        if (matches.size() == 1) {
            return matches.get(0).getId();
        }
        if (matches.size() > 1) {
            String codes =
                    String.join(", ", matches.stream().map(OrgMaster::getCode).toList());
            throw new BadInput("More than one " + what + " is named '" + name.trim() + "' (codes " + codes
                    + "); ask HR which one.");
        }
        String valid = active.isEmpty()
                ? "there are none set up"
                : "the valid names are "
                        + String.join(
                                ", ", active.stream().map(OrgMaster::getName).toList());
        throw new BadInput("No " + what + " is named '" + name.trim() + "'; " + valid + ".");
    }

    /** What the model reads: the card, and that HR must confirm. No ids (W-114.4 rule 1). */
    private static JsonNode answer(AiProposedAction card) {
        ObjectNode out = NODES.objectNode();
        out.put("status", card.status().name());
        out.put("summary", card.summary());
        ArrayNode fields = out.putArray("fields");
        for (AiActionField field : card.fields()) {
            fields.addObject().put("label", field.label()).put("value", field.value());
        }
        out.put("note", "Nothing has been added yet. HR must press Confirm on the card.");
        return out;
    }

    private static EmploymentType employmentType(String value) {
        try {
            return EmploymentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadInput("employmentType must be one of "
                    + String.join(
                            ", ",
                            Arrays.stream(EmploymentType.values())
                                    .map(Enum::name)
                                    .toList()));
        }
    }

    private static Integer notice(JsonNode args) {
        JsonNode v = args.get("noticePeriodDays");
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isIntegralNumber() || !v.canConvertToInt() || v.asInt() < 0) {
            throw new BadInput("noticePeriodDays must be a whole number of days");
        }
        return v.asInt();
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }
}
