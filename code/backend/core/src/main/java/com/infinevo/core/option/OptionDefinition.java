package com.infinevo.core.option;

import java.util.Objects;

/**
 * Definition of a typed tenant option in the code registry (W-104 §4).
 */
public record OptionDefinition(
        String key,
        OptionValueType type,
        OptionCategory category,
        String defaultValue,
        String label,
        String description,
        Integer min,
        Integer max,
        String pattern) {

    public OptionDefinition {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(defaultValue, "defaultValue must not be null");
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(description, "description must not be null");
        type.validate(defaultValue, null);
    }

    public static OptionDefinition of(
            String key,
            OptionValueType type,
            OptionCategory category,
            String defaultValue,
            String label,
            String description) {
        return new OptionDefinition(key, type, category, defaultValue, label, description, null, null, null);
    }

    public static OptionDefinition of(
            String key,
            OptionValueType type,
            OptionCategory category,
            String defaultValue,
            String label,
            String description,
            Integer min,
            Integer max) {
        return new OptionDefinition(key, type, category, defaultValue, label, description, min, max, null);
    }
}
