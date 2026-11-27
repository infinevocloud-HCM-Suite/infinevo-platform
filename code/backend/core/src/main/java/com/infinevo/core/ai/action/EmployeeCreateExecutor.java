package com.infinevo.core.ai.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmploymentType;
import com.infinevo.core.org.DepartmentRepository;
import com.infinevo.core.org.DesignationRepository;
import com.infinevo.core.org.OrgMaster;
import com.infinevo.core.org.OrgMasterRepository;
import com.infinevo.core.org.WorkLocationRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adds an employee HR proposed through the AI helper (W-114.20). Confirm calls {@link EmployeeService#create}, the
 * method the Add employee screen calls, so every rule of that screen runs again: a number or email taken, or the plan's
 * limit reached, since the proposal ends the intent {@code FAILED} with the service's message.
 *
 * <p>The request is built from the stored payload with mobile, status, termination and portal fields always
 * {@code null}: they are never set from chat. The card is built from the payload, the org masters named by their
 * stored ids, and a line saying what is not set here.
 */
@Component
public class EmployeeCreateExecutor implements AiActionExecutor {

    public static final String ACTION_CODE = "core.employee.create";

    static final String NUMBER = "employeeNumber";
    static final String FIRST = "firstName";
    static final String MIDDLE = "middleName";
    static final String LAST = "lastName";
    static final String GENDER = "gender";
    static final String JOINING = "dateOfJoining";
    static final String EMAIL = "workEmail";
    static final String DEPARTMENT_ID = "departmentId";
    static final String DESIGNATION_ID = "designationId";
    static final String LOCATION_ID = "workLocationId";
    static final String EMPLOYMENT_TYPE = "employmentType";
    static final String PROBATION_END = "probationEndDate";
    static final String NOTICE_DAYS = "noticePeriodDays";

    static final String NOT_SET_HERE = "Reporting manager, mobile, salary, bank, statutory details and portal login."
            + " Set them on the employee's page.";
    static final String NONE = "(none)";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final EmployeeService employeeService;
    private final DepartmentRepository departments;
    private final DesignationRepository designations;
    private final WorkLocationRepository locations;

    EmployeeCreateExecutor(
            EmployeeService employeeService,
            DepartmentRepository departments,
            DesignationRepository designations,
            WorkLocationRepository locations) {
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.departments = Objects.requireNonNull(departments, "departments must not be null");
        this.designations = Objects.requireNonNull(designations, "designations must not be null");
        this.locations = Objects.requireNonNull(locations, "locations must not be null");
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String requiredAction() {
        return ACTION_CODE;
    }

    /** An HR user need not be an employee (spec, "At confirm"). */
    @Override
    public boolean requiresEmployeeProfile() {
        return false;
    }

    /** The request the screen would send, with every field chat may not set left null. */
    static EmployeeRequest request(JsonNode p) {
        String type = text(p, EMPLOYMENT_TYPE);
        String notice = text(p, NOTICE_DAYS);
        return new EmployeeRequest(
                text(p, NUMBER),
                text(p, FIRST),
                text(p, MIDDLE),
                text(p, LAST),
                text(p, GENDER),
                date(p, JOINING),
                null,
                null,
                text(p, EMAIL),
                null,
                null,
                uuid(p, DEPARTMENT_ID),
                uuid(p, DESIGNATION_ID),
                uuid(p, LOCATION_ID),
                type == null ? null : EmploymentType.valueOf(type),
                date(p, PROBATION_END),
                notice == null ? null : Integer.valueOf(notice),
                null,
                null);
    }

    /** The payload for {@code request}: only the fields chat may set. */
    static ObjectNode payload(EmployeeRequest r) {
        ObjectNode p = NODES.objectNode();
        put(p, NUMBER, r.employeeNumber());
        put(p, FIRST, r.firstName());
        put(p, MIDDLE, r.middleName());
        put(p, LAST, r.lastName());
        put(p, GENDER, r.gender());
        put(p, JOINING, r.dateOfJoining() == null ? null : r.dateOfJoining().toString());
        put(p, EMAIL, r.workEmail());
        put(p, DEPARTMENT_ID, r.departmentId() == null ? null : r.departmentId().toString());
        put(
                p,
                DESIGNATION_ID,
                r.designationId() == null ? null : r.designationId().toString());
        put(
                p,
                LOCATION_ID,
                r.workLocationId() == null ? null : r.workLocationId().toString());
        put(
                p,
                EMPLOYMENT_TYPE,
                r.employmentType() == null ? null : r.employmentType().name());
        put(
                p,
                PROBATION_END,
                r.probationEndDate() == null ? null : r.probationEndDate().toString());
        put(
                p,
                NOTICE_DAYS,
                r.noticePeriodDays() == null ? null : r.noticePeriodDays().toString());
        return p;
    }

    @Override
    public AiActionDescription describe(AiToolContext ctx, JsonNode payload) {
        List<AiActionField> fields = new ArrayList<>();
        fields.add(field("Employee number", text(payload, NUMBER)));
        fields.add(field("First name", text(payload, FIRST)));
        fields.add(field("Middle name", text(payload, MIDDLE)));
        fields.add(field("Last name", text(payload, LAST)));
        fields.add(field("Gender", text(payload, GENDER)));
        fields.add(field("Date of joining", text(payload, JOINING)));
        fields.add(field("Work email", text(payload, EMAIL)));
        fields.add(field("Department", name(departments, ctx, uuid(payload, DEPARTMENT_ID))));
        fields.add(field("Designation", name(designations, ctx, uuid(payload, DESIGNATION_ID))));
        fields.add(field("Work location", name(locations, ctx, uuid(payload, LOCATION_ID))));
        fields.add(field("Employment type", text(payload, EMPLOYMENT_TYPE)));
        fields.add(field("Probation end date", text(payload, PROBATION_END)));
        fields.add(field("Notice period (days)", text(payload, NOTICE_DAYS)));
        fields.add(new AiActionField("Not set here", NOT_SET_HERE));
        String name = String.join(
                        " ",
                        List.of(Objects.toString(text(payload, FIRST), ""), Objects.toString(text(payload, LAST), "")))
                .trim();
        return new AiActionDescription("Add employee " + name + " (" + text(payload, NUMBER) + ")", fields);
    }

    @Override
    public JsonNode execute(AiToolContext ctx, JsonNode payload) {
        EmployeeResponse created;
        try {
            created = employeeService.create(request(payload));
        } catch (EmployeeService.ValidationException e) {
            throw new IllegalArgumentException(reasons(e));
        } catch (EmployeeService.DuplicateEmployeeNumberException | EmployeeService.EmployeeLimitReachedException e) {
            throw new IllegalStateException(e.getMessage());
        }
        ObjectNode result = NODES.objectNode();
        result.put("employeeId", created.id().toString());
        result.put("employeeNumber", created.employeeNumber());
        result.put("note", "Reporting manager is not set. Set it on the employee's page.");
        return result;
    }

    /** The field messages of a validation refusal, in a stable order, as one line for the user. */
    static String reasons(EmployeeService.ValidationException e) {
        if (e.fieldErrors() == null || e.fieldErrors().isEmpty()) {
            return "The employee details were not valid.";
        }
        return String.join("; ", new TreeMap<>(e.fieldErrors()).values());
    }

    private static <T extends OrgMaster> String name(OrgMasterRepository<T> repo, AiToolContext ctx, UUID id) {
        if (id == null) {
            return null;
        }
        return repo.findByIdAndTenantId(id, ctx.tenantId())
                .map(OrgMaster::getName)
                .orElse(null);
    }

    private static AiActionField field(String label, String value) {
        return new AiActionField(label, value == null || value.isBlank() ? NONE : value);
    }

    private static void put(ObjectNode p, String name, String value) {
        if (value == null) {
            p.putNull(name);
        } else {
            p.put(name, value);
        }
    }

    private static String text(JsonNode p, String name) {
        if (p == null) {
            return null;
        }
        JsonNode v = p.get(name);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static LocalDate date(JsonNode p, String name) {
        String v = text(p, name);
        return v == null ? null : LocalDate.parse(v);
    }

    private static UUID uuid(JsonNode p, String name) {
        String v = text(p, name);
        return v == null ? null : UUID.fromString(v);
    }
}
