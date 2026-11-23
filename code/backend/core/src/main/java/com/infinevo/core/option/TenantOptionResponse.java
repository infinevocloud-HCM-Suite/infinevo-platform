package com.infinevo.core.option;

public record TenantOptionResponse(
        String key,
        OptionCategory category,
        OptionValueType valueType,
        String label,
        String description,
        String value,
        String defaultValue,
        boolean overridden) {}
