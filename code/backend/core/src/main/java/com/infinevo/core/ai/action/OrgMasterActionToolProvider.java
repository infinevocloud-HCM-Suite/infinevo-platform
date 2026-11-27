package com.infinevo.core.ai.action;

import static com.infinevo.core.ai.AiToolInputs.rejectUnknown;
import static com.infinevo.core.ai.AiToolInputs.schema;

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
import com.infinevo.core.org.DepartmentService;
import com.infinevo.core.org.DesignationService;
import com.infinevo.core.org.WorkLocationService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-114.28 steps 1-3: {@code core.propose_department}, {@code core.propose_designation} and
 * {@code core.propose_work_location}. Each checks the code is not taken (inactive ones included - the index is over
 * every row) and that the name is not already in use, then proposes; nothing is written until Confirm. All need
 * {@code core.org.manage}, the screens' create action.
 */
@Component
public class OrgMasterActionToolProvider implements AiToolProvider {

    static final int MAX_CODE = 50;
    static final int MAX_TEXT = 100;
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final AiActionProposals proposals;
    private final TransactionOperations tx;
    private final List<AiTool> tools;

    /** What the tool reads of one list: every code and name, inactive ones included. */
    record Existing(String code, String name) {}

    @Autowired
    public OrgMasterActionToolProvider(
            DepartmentService departments,
            DesignationService designations,
            WorkLocationService locations,
            AiActionProposals proposals,
            PlatformTransactionManager transactionManager) {
        this(
                () -> departments.list(false).stream().map(d -> new Existing(d.code(), d.name())),
                () -> designations.list(false).stream().map(d -> new Existing(d.code(), d.name())),
                () -> locations.list(false).stream().map(d -> new Existing(d.code(), d.name())),
                proposals,
                new TransactionTemplate(transactionManager));
    }

    OrgMasterActionToolProvider(
            Supplier<Stream<Existing>> departments,
            Supplier<Stream<Existing>> designations,
            Supplier<Stream<Existing>> locations,
            AiActionProposals proposals,
            TransactionOperations tx) {
        this.proposals = Objects.requireNonNull(proposals, "proposals must not be null");
        this.tx = Objects.requireNonNull(tx, "tx must not be null");
        this.tools = List.of(
                new ProposeTool(
                        "core.propose_department",
                        "department",
                        OrgMasterExecutors.Department.ACTION_CODE,
                        departments,
                        false),
                new ProposeTool(
                        "core.propose_designation",
                        "designation",
                        OrgMasterExecutors.Designation.ACTION_CODE,
                        designations,
                        false),
                new ProposeTool(
                        "core.propose_work_location",
                        "work location",
                        OrgMasterExecutors.Location.ACTION_CODE,
                        locations,
                        true));
    }

    @Override
    public List<AiTool> tools() {
        return tools;
    }

    private static ObjectNode textProperty(int max, String description) {
        return NODES.objectNode().put("type", "string").put("maxLength", max).put("description", description);
    }

    private final class ProposeTool implements AiTool {

        private final String name;
        private final String kind;
        private final String actionCode;
        private final Supplier<Stream<Existing>> existing;
        private final boolean address;
        private final ObjectNode inputSchema;

        ProposeTool(String name, String kind, String actionCode, Supplier<Stream<Existing>> existing, boolean address) {
            this.name = name;
            this.kind = kind;
            this.actionCode = actionCode;
            this.existing = existing;
            this.address = address;
            Map<String, ObjectNode> props = new LinkedHashMap<>();
            props.put("code", textProperty(MAX_CODE, "A short code, unique in the company, for example FIN."));
            props.put("name", textProperty(MAX_TEXT, "The " + kind + "'s name."));
            if (address) {
                props.put("addressLine1", textProperty(200, "Street address, if given."));
                props.put("city", textProperty(MAX_TEXT, "City, if given."));
                props.put("state", textProperty(MAX_TEXT, "State, if given."));
                props.put("stateCode", textProperty(4, "The state's two-digit GST code, if given."));
                props.put("zipCode", textProperty(10, "PIN code, if given."));
            }
            this.inputSchema = schema(props, "code", "name");
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return "Proposes adding a " + kind + ". Nothing is added: the user sees a card and must press Confirm."
                    + " Ask for the code and the name if they are not given; never make up a code.";
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }

        @Override
        public String requiredAction() {
            return "core.org.manage";
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            JsonNode args = input == null || input.isNull() ? NODES.objectNode() : input;
            try {
                if (!args.isObject()) {
                    throw new BadInput("input must be a JSON object");
                }
                rejectUnknown(args, inputSchema.path("properties"));
                String code = given(args, "code");
                String label = given(args, "name");
                if (code == null || label == null) {
                    throw new BadInput("code and name are required. Ask the user for them.");
                }
                AiProposedAction card = tx.execute(s -> {
                    for (Existing e : existing.get().toList()) {
                        if (e.code() != null && e.code().trim().equalsIgnoreCase(code)) {
                            throw new BadInput("A " + kind + " with code " + code + " already exists.");
                        }
                        if (e.name() != null && e.name().trim().equalsIgnoreCase(label)) {
                            throw new BadInput("A " + kind + " named " + label + " already exists.");
                        }
                    }
                    ObjectNode payload = NODES.objectNode();
                    payload.put(OrgMasterCreateExecutor.CODE, code.toUpperCase(Locale.ROOT));
                    payload.put(OrgMasterCreateExecutor.NAME, label);
                    if (address) {
                        for (String f : List.of(
                                OrgMasterCreateExecutor.ADDRESS,
                                OrgMasterCreateExecutor.CITY,
                                OrgMasterCreateExecutor.STATE,
                                OrgMasterCreateExecutor.STATE_CODE,
                                OrgMasterCreateExecutor.ZIP)) {
                            String v = given(args, f);
                            if (v == null) {
                                payload.putNull(f);
                            } else {
                                payload.put(f, v);
                            }
                        }
                    }
                    return proposals.propose(ctx, actionCode, payload);
                });
                return AiToolResult.success(answer(Objects.requireNonNull(card, "card")));
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            } catch (IllegalArgumentException | IllegalStateException e) {
                return AiToolResult.failure(
                        e.getMessage() != null && e.getMessage().contains("already proposed")
                                ? "One step at a time. Ask the user about the next one."
                                : e.getMessage());
            }
        }
    }

    /** What the model reads: the card, and that the user must confirm. No ids. */
    static JsonNode answer(AiProposedAction card) {
        ObjectNode out = NODES.objectNode();
        out.put("status", card.status().name());
        out.put("summary", card.summary());
        ArrayNode fields = out.putArray("fields");
        for (AiActionField field : card.fields()) {
            fields.addObject().put("label", field.label()).put("value", field.value());
        }
        out.put("note", "Nothing has been added yet. The user must press Confirm on the card.");
        return out;
    }

    /** A text value, or {@code null} when absent or blank. */
    static String given(JsonNode args, String name) {
        JsonNode v = args.get(name);
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isTextual()) {
            throw new BadInput(name + " must be text");
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }
}
