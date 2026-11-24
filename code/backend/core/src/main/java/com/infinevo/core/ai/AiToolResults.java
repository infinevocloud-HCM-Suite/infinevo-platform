package com.infinevo.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * What every module's AI tool does to its answer before it leaves the tool (W-114.4 rule 1, W-114.6 rule 4): strip the
 * database ids. Moved out of {@link CoreAiToolProvider} unchanged so {@code hrms} and {@code payroll} tools use the same
 * rule rather than a copy of it.
 */
public final class AiToolResults {

    private static final Pattern UUID_TEXT =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private AiToolResults() {}

    /**
     * A copy of the node with every database id removed: fields named {@code id} or {@code tenantId}, fields ending
     * {@code Id}, {@code Ids}, {@code _id} or {@code _ids}, and any other field or array element whose text is a UUID.
     */
    public static JsonNode withoutIds(JsonNode node) {
        if (node == null) {
            return NODES.nullNode();
        }
        if (node.isObject()) {
            ObjectNode out = NODES.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isIdName(field.getKey()) || isUuid(field.getValue())) {
                    continue;
                }
                out.set(field.getKey(), withoutIds(field.getValue()));
            }
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = NODES.arrayNode();
            for (JsonNode element : node) {
                if (!isUuid(element)) {
                    out.add(withoutIds(element));
                }
            }
            return out;
        }
        return node.deepCopy();
    }

    private static boolean isIdName(String key) {
        return key.equals("id")
                || key.equalsIgnoreCase("tenantId")
                || key.endsWith("Id")
                || key.endsWith("Ids")
                || key.endsWith("_id")
                || key.endsWith("_ids");
    }

    private static boolean isUuid(JsonNode value) {
        return value != null
                && value.isTextual()
                && UUID_TEXT.matcher(value.asText().trim()).matches();
    }
}
