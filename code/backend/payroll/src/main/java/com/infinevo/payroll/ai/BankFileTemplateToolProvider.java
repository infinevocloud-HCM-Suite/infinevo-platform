package com.infinevo.payroll.ai;

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
import com.infinevo.core.ai.action.AiActionProposals;
import com.infinevo.core.report.ExportFormat;
import com.infinevo.payroll.payrun.bankfile.BankFileField;
import com.infinevo.payroll.payrun.bankfile.BankFileTemplateResponse;
import com.infinevo.payroll.payrun.bankfile.BankFileTemplateService;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * W-114.28 step 6: {@code payroll.propose_bank_file_template}. Takes the columns in words (a field of the template's
 * field list, a fixed text, or a serial number), checks them as the screen does and that the name is free, then
 * proposes; nothing is added until Confirm. Needs {@code payroll.settings.manage}, the Bank files tab's add action.
 */
@Component
public class BankFileTemplateToolProvider implements AiToolProvider {

    static final String TOOL = "payroll.propose_bank_file_template";
    static final int MAX_NAME = 80;
    static final int MAX_HEADING = 60;
    static final int MAX_FIXED = 100;
    static final int MAX_COLUMNS = 40;

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final Supplier<List<BankFileTemplateResponse>> existing;
    private final AiActionProposals proposals;
    private final ObjectNode inputSchema;

    @Autowired
    public BankFileTemplateToolProvider(BankFileTemplateService templates, AiActionProposals proposals) {
        this(templates::list, proposals);
    }

    BankFileTemplateToolProvider(Supplier<List<BankFileTemplateResponse>> existing, AiActionProposals proposals) {
        this.existing = Objects.requireNonNull(existing, "existing must not be null");
        this.proposals = Objects.requireNonNull(proposals, "proposals must not be null");
        Map<String, ObjectNode> props = new LinkedHashMap<>();
        props.put("name", text(MAX_NAME, "The template's name, for example HDFC salary upload."));
        props.put(
                "fileFormat",
                NODES.objectNode()
                        .put("type", "string")
                        .put("description", "CSV or XLSX. Leave out for CSV.")
                        .set("enum", NODES.arrayNode().add("CSV").add("XLSX")));
        props.put(
                "includeHeader",
                NODES.objectNode()
                        .put("type", "boolean")
                        .put("description", "False for a file with no heading row. Leave out for a heading row."));
        ObjectNode column = NODES.objectNode().put("type", "object");
        ObjectNode cp = column.putObject("properties");
        cp.set("heading", text(MAX_HEADING, "The column's heading in the file."));
        cp.set("field", text(60, "A field from the list: " + fieldLabels() + "."));
        cp.set("fixedText", text(MAX_FIXED, "A text that is the same on every row, instead of a field."));
        cp.set(
                "serial",
                NODES.objectNode().put("type", "boolean").put("description", "True for a row number 1, 2, 3."));
        cp.set(
                "format",
                text(20, "For a date, period or amount field, one of its formats. Leave out for the default."));
        column.putArray("required").add("heading");
        column.put("additionalProperties", false);
        ObjectNode columns = NODES.objectNode()
                .put("type", "array")
                .put("maxItems", MAX_COLUMNS)
                .put("minItems", 1);
        columns.set("items", column);
        columns.put("description", "The columns, in order.");
        props.put("columns", columns);
        this.inputSchema = schema(props, "name", "columns");
    }

    @Override
    public List<AiTool> tools() {
        return List.of(new ProposeTemplate());
    }

    private static ObjectNode text(int max, String description) {
        return NODES.objectNode().put("type", "string").put("maxLength", max).put("description", description);
    }

    static String fieldLabels() {
        return String.join(
                ", ",
                Arrays.stream(BankFileField.values()).map(BankFileField::label).toList());
    }

    private final class ProposeTemplate implements AiTool {

        @Override
        public String name() {
            return TOOL;
        }

        @Override
        public String description() {
            return "Proposes adding a bank file template (Payroll settings, Bank files). Nothing is added: the user"
                    + " sees a card and must press Confirm. Ask for the columns the bank wants if they are not given.";
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }

        @Override
        public String requiredAction() {
            return "payroll.settings.manage";
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            JsonNode args = input == null || input.isNull() ? NODES.objectNode() : input;
            try {
                if (!args.isObject()) {
                    throw new BadInput("input must be a JSON object");
                }
                rejectUnknown(args, inputSchema.path("properties"));
                String name = given(args, "name");
                if (name == null) {
                    throw new BadInput("name is required. Ask the user for it.");
                }
                if (name.length() > MAX_NAME) {
                    throw new BadInput("name must be at most " + MAX_NAME + " characters");
                }
                for (BankFileTemplateResponse t : existing.get()) {
                    if (t.name() != null && t.name().trim().equalsIgnoreCase(name)) {
                        throw new BadInput("A bank file template named " + name + " already exists.");
                    }
                }
                String format = given(args, "fileFormat");
                ExportFormat fileFormat;
                try {
                    fileFormat =
                            format == null ? ExportFormat.CSV : ExportFormat.valueOf(format.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new BadInput("fileFormat must be CSV or XLSX");
                }
                JsonNode header = args.get("includeHeader");
                if (header != null && !header.isNull() && !header.isBoolean()) {
                    throw new BadInput("includeHeader must be true or false");
                }
                ObjectNode payload = NODES.objectNode();
                payload.put(BankFileTemplateCreateExecutor.NAME, name);
                payload.put(BankFileTemplateCreateExecutor.FORMAT, fileFormat.name());
                payload.put(
                        BankFileTemplateCreateExecutor.HEADER, header == null || header.isNull() || header.asBoolean());
                payload.set(BankFileTemplateCreateExecutor.COLUMNS, columns(args.get("columns")));
                AiProposedAction card = proposals.propose(ctx, BankFileTemplateCreateExecutor.ACTION_CODE, payload);
                return AiToolResult.success(answer(card));
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

    /** The columns as the screen would store them, checked by the screen's rules (spec W-88.2 section 4.3). */
    static ArrayNode columns(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new BadInput("A template needs at least one column. Ask the user which columns the bank wants.");
        }
        if (node.size() > MAX_COLUMNS) {
            throw new BadInput("A template has at most " + MAX_COLUMNS + " columns");
        }
        ArrayNode out = NODES.arrayNode();
        Set<String> headings = new HashSet<>();
        int i = 0;
        for (JsonNode c : node) {
            String where = "column " + ++i;
            if (!c.isObject()) {
                throw new BadInput(where + " must be an object");
            }
            for (var it = c.fieldNames(); it.hasNext(); ) {
                String f = it.next();
                if (!List.of("heading", "field", "fixedText", "serial", "format")
                        .contains(f)) {
                    throw new BadInput(where + ": unknown " + f);
                }
            }
            String heading = given(c, "heading");
            if (heading == null) {
                throw new BadInput(where + ": heading is required");
            }
            if (heading.length() > MAX_HEADING) {
                throw new BadInput(where + ": heading must be at most " + MAX_HEADING + " characters");
            }
            if (!headings.add(heading.toLowerCase(Locale.ROOT))) {
                throw new BadInput(where + ": heading " + heading + " is used twice");
            }
            String field = given(c, "field");
            String fixed = c.hasNonNull("fixedText") ? c.get("fixedText").asText() : null;
            boolean serial = c.path("serial").asBoolean(false);
            int sources = (field != null ? 1 : 0) + (fixed != null ? 1 : 0) + (serial ? 1 : 0);
            if (sources != 1) {
                throw new BadInput(where + ": give exactly one of a field, a fixed text or serial");
            }
            ObjectNode col = out.addObject();
            col.put(BankFileTemplateCreateExecutor.HEADING, heading);
            if (serial) {
                col.put(BankFileTemplateCreateExecutor.SOURCE, "SERIAL");
            } else if (fixed != null) {
                if (fixed.length() > MAX_FIXED || fixed.indexOf('\n') >= 0 || fixed.indexOf('\r') >= 0) {
                    throw new BadInput(where + ": fixed text must be one line of at most " + MAX_FIXED + " characters");
                }
                col.put(BankFileTemplateCreateExecutor.SOURCE, "FIXED");
                col.put(BankFileTemplateCreateExecutor.VALUE, fixed);
            } else {
                BankFileField f = field(field)
                        .orElseThrow(() -> new BadInput(
                                where + ": no field " + field + ". The fields are: " + fieldLabels() + "."));
                String format = given(c, "format");
                if (format != null && !f.kind().formats().contains(format)) {
                    throw new BadInput(
                            f.kind().formats().isEmpty()
                                    ? where + ": " + f.label() + " takes no format"
                                    : where + ": format must be one of "
                                            + String.join(", ", f.kind().formats()));
                }
                col.put(BankFileTemplateCreateExecutor.SOURCE, "FIELD");
                col.put(BankFileTemplateCreateExecutor.FIELD, f.name());
                if (format != null) {
                    col.put(BankFileTemplateCreateExecutor.VALUE_FORMAT, format);
                }
            }
        }
        return out;
    }

    /** A field by its label, its label before any bracket, or its code; any case. */
    static Optional<BankFileField> field(String wanted) {
        String w = wanted.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(BankFileField.values())
                .filter(f -> f.name().equalsIgnoreCase(w.replace(' ', '_'))
                        || f.label().toLowerCase(Locale.ROOT).equals(w)
                        || shortLabel(f).equals(w))
                .findFirst();
    }

    private static String shortLabel(BankFileField f) {
        String l = f.label();
        int b = l.indexOf(" (");
        return (b < 0 ? l : l.substring(0, b)).toLowerCase(Locale.ROOT);
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

    private static String given(JsonNode args, String name) {
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
