package com.infinevo.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How an AI tool describes and reads its input (W-114.4): a closed JSON Schema ({@code additionalProperties: false}),
 * unknown fields refused, dates as {@code YYYY-MM-DD}, enums by name. Moved out of {@link CoreAiToolProvider} unchanged
 * so every module's tools refuse bad input the same way. A refusal is a {@link BadInput} whose message the model can
 * read and correct.
 */
public final class AiToolInputs {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private AiToolInputs() {}

    /** Refuses any field of {@code args} that {@code properties} (a schema's {@code properties}) does not name. */
    public static void rejectUnknown(JsonNode args, JsonNode properties) {
        Iterator<String> names = args.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!properties.has(name)) {
                throw new BadInput("unknown input '" + name + "'; this tool takes "
                        + (properties.isEmpty()
                                ? "no input"
                                : String.join(", ", (Iterable<String>) properties::fieldNames)));
            }
        }
    }

    /** The text field, or {@code null} when absent or null; refuses a non-text or blank value. */
    public static String text(JsonNode args, String name) {
        JsonNode value = args.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new BadInput(name + " must be text");
        }
        return value.asText();
    }

    /** The date field as {@code YYYY-MM-DD}, or {@code null} when absent. */
    public static LocalDate date(JsonNode args, String name) {
        String value = text(args, name);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BadInput(name + " must be a date as YYYY-MM-DD");
        }
    }

    /** The enum field by constant name, any case, or {@code null} when absent. */
    public static <E extends Enum<E>> E enumValue(JsonNode args, String name, Class<E> type) {
        String value = text(args, name);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadInput(name + " must be one of " + constantNames(type.getEnumConstants()));
        }
    }

    /** Refuses a range that ends before it starts; either end may be {@code null}. */
    public static void requireOrder(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BadInput("to must be on or after from");
        }
    }

    /** A closed object schema of these properties, sorted by name, with the given required fields. */
    public static ObjectNode schema(Map<String, ObjectNode> properties, String... required) {
        ObjectNode schema = NODES.objectNode().put("type", "object");
        ObjectNode props = schema.putObject("properties");
        properties.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> props.set(e.getKey(), e.getValue()));
        if (required.length > 0) {
            ArrayNode req = schema.putArray("required");
            Arrays.stream(required).forEach(req::add);
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    public static ObjectNode dateProperty(String description) {
        return NODES.objectNode().put("type", "string").put("format", "date").put("description", description);
    }

    public static ObjectNode enumProperty(String description, List<String> values) {
        ObjectNode property = NODES.objectNode().put("type", "string").put("description", description);
        ArrayNode allowed = property.putArray("enum");
        values.forEach(allowed::add);
        return property;
    }

    public static List<String> constantNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    /** The model's input cannot be used; the message goes back to it as the tool's error. */
    public static final class BadInput extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public BadInput(String message) {
            super(message);
        }
    }
}
