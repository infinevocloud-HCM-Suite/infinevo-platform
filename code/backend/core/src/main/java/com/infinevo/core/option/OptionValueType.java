package com.infinevo.core.option;

import com.fasterxml.jackson.databind.ObjectMapper;

public enum OptionValueType {
    BOOLEAN,
    STRING,
    INTEGER,
    JSON;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public void validate(String rawValue, OptionDefinition def) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Option value must not be null or blank");
        }
        switch (this) {
            case BOOLEAN -> {
                String trimmed = rawValue.trim();
                if (!"true".equalsIgnoreCase(trimmed) && !"false".equalsIgnoreCase(trimmed)) {
                    throw new IllegalArgumentException(
                            "Value must be 'true' or 'false' for BOOLEAN option: " + rawValue);
                }
            }
            case INTEGER -> {
                try {
                    int val = Integer.parseInt(rawValue.trim());
                    if (def != null) {
                        if (def.min() != null && val < def.min()) {
                            throw new IllegalArgumentException(
                                    "Value " + val + " is below minimum allowed " + def.min());
                        }
                        if (def.max() != null && val > def.max()) {
                            throw new IllegalArgumentException(
                                    "Value " + val + " is above maximum allowed " + def.max());
                        }
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Value must be a valid integer: " + rawValue);
                }
            }
            case STRING -> {
                if (def != null && def.pattern() != null && !def.pattern().isBlank()) {
                    if (!rawValue.matches(def.pattern())) {
                        throw new IllegalArgumentException("Value does not match required pattern: " + def.pattern());
                    }
                }
            }
            case JSON -> {
                String trimmed = rawValue.trim();
                if ((!trimmed.startsWith("{") || !trimmed.endsWith("}"))
                        && (!trimmed.startsWith("[") || !trimmed.endsWith("]"))) {
                    throw new IllegalArgumentException("Value must be valid JSON object or array: " + rawValue);
                }
                try {
                    OBJECT_MAPPER.readTree(trimmed);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Value must be valid JSON: " + e.getMessage());
                }
            }
        }
    }
}
