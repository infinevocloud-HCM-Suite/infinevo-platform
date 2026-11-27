package com.infinevo.core.ai.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.org.OrgMasterService;
import com.infinevo.core.org.WorkLocationService;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Adds a department, designation or work location the AI helper proposed (W-114.28 steps 1-3). Confirm calls the
 * {@code create} the screen's endpoint calls ({@code org/DepartmentController.java:40-46} and its two siblings), so a
 * code taken in the meantime is refused with the service's words. One subclass per list, each a bean with its own
 * action code; all three need {@code core.org.manage}, the endpoints' action.
 */
abstract class OrgMasterCreateExecutor implements AiActionExecutor {

    static final String CODE = "code";
    static final String NAME = "name";
    static final String ADDRESS = "addressLine1";
    static final String CITY = "city";
    static final String STATE = "state";
    static final String STATE_CODE = "stateCode";
    static final String ZIP = "zipCode";
    static final String NONE = "(none)";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    /** "department", "designation" or "work location", as the card and the refusal name it. */
    abstract String kind();

    /** The path of the screen that lists it, for the link after Confirm. */
    abstract String screen();

    /** Creates from the stored payload and answers the new row's id and code. */
    abstract Created create(JsonNode payload);

    record Created(String id, String code) {}

    @Override
    public String requiredAction() {
        return "core.org.manage";
    }

    /** Set-up of the company, not about oneself: an administrator need not be an employee. */
    @Override
    public boolean requiresEmployeeProfile() {
        return false;
    }

    @Override
    public AiActionDescription describe(AiToolContext ctx, JsonNode payload) {
        List<AiActionField> fields = new ArrayList<>();
        fields.add(field("Code", text(payload, CODE)));
        fields.add(field("Name", text(payload, NAME)));
        for (String[] f : new String[][] {
            {"Address", ADDRESS}, {"City", CITY}, {"State", STATE}, {"State code", STATE_CODE}, {"PIN code", ZIP}
        }) {
            if (payload != null && payload.has(f[1])) {
                fields.add(field(f[0], text(payload, f[1])));
            }
        }
        return new AiActionDescription("Add " + kind() + " " + text(payload, NAME), fields);
    }

    @Override
    public JsonNode execute(AiToolContext ctx, JsonNode payload) {
        Created created;
        try {
            created = create(payload);
        } catch (OrgMasterService.ValidationException e) {
            throw new IllegalArgumentException(reasons(e));
        } catch (OrgMasterService.DuplicateCodeException | WorkLocationService.FilingAddressAlreadySetException e) {
            throw new IllegalStateException(e.getMessage());
        }
        ObjectNode result = NODES.objectNode();
        result.put("id", created.id());
        result.put("code", created.code());
        result.put("screen", screen());
        return result;
    }

    static String reasons(OrgMasterService.ValidationException e) {
        if (e.fieldErrors() == null || e.fieldErrors().isEmpty()) {
            return "The details were not valid.";
        }
        return String.join("; ", new TreeMap<>(e.fieldErrors()).values());
    }

    static String text(JsonNode p, String name) {
        if (p == null) {
            return null;
        }
        JsonNode v = p.get(name);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static AiActionField field(String label, String value) {
        return new AiActionField(label, value == null || value.isBlank() ? NONE : value);
    }
}
