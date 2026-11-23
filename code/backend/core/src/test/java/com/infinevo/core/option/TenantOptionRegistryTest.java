package com.infinevo.core.option;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantOptionRegistryTest {

    @Test
    @DisplayName("Registering duplicate key fails startup with IllegalStateException")
    void duplicateKeyFailsStartup() {
        TenantOptionRegistry registry = new TenantOptionRegistry(null, null, null);
        OptionDefinition def1 = OptionDefinition.of(
                "core.test.switch",
                OptionValueType.BOOLEAN,
                OptionCategory.GENERAL,
                "true",
                "Test switch",
                "Description 1");
        OptionDefinition def2 = OptionDefinition.of(
                "core.test.switch",
                OptionValueType.BOOLEAN,
                OptionCategory.GENERAL,
                "false",
                "Test switch duplicate",
                "Description 2");

        registry.register(def1);
        assertThatThrownBy(() -> registry.register(def2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate tenant option key registered: core.test.switch");
    }

    @Test
    @DisplayName("Option definition with mistyped default value fails validation on creation")
    void mistypedDefaultFailsValidation() {
        assertThatThrownBy(() -> OptionDefinition.of(
                        "core.test.bad_bool",
                        OptionValueType.BOOLEAN,
                        OptionCategory.GENERAL,
                        "invalid_boolean",
                        "Bad Bool",
                        "Desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value must be 'true' or 'false'");

        assertThatThrownBy(() -> OptionDefinition.of(
                        "core.test.bad_int",
                        OptionValueType.INTEGER,
                        OptionCategory.GENERAL,
                        "not_a_number",
                        "Bad Int",
                        "Desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Value must be a valid integer");
    }

    @Test
    @DisplayName("Registry finds definitions by key and category")
    void registryFindsByKeyAndCategory() {
        TenantOptionRegistry registry = new TenantOptionRegistry(null, null, null);
        OptionDefinition gen = OptionDefinition.of(
                "core.test.general", OptionValueType.BOOLEAN, OptionCategory.GENERAL, "true", "General option", "Desc");
        OptionDefinition sec = OptionDefinition.of(
                "core.test.security",
                OptionValueType.INTEGER,
                OptionCategory.SECURITY,
                "42",
                "Security option",
                "Desc",
                1,
                100);

        registry.register(gen);
        registry.register(sec);

        assertThat(registry.has("core.test.general")).isTrue();
        assertThat(registry.has("core.test.unknown")).isFalse();
        assertThat(registry.get("core.test.security")).isPresent();

        List<OptionDefinition> secList = registry.byCategory(OptionCategory.SECURITY);
        assertThat(secList).containsExactly(sec);

        List<OptionDefinition> allList = registry.byCategory(null);
        assertThat(allList).containsExactly(gen, sec);
    }
}
