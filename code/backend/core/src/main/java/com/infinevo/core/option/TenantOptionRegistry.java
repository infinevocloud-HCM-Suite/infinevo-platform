package com.infinevo.core.option;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Registry of all defined tenant option keys (W-104 §4).
 *
 * <p>Collects every {@link OptionDefinition} and {@link OptionDefinitionSource} bean across all modules.
 * Fails startup on a duplicate key or when a default value disagrees with its declared type.
 */
@Component
public class TenantOptionRegistry {

    private final Map<String, OptionDefinition> definitions = new LinkedHashMap<>();

    public TenantOptionRegistry(
            ObjectProvider<OptionDefinition> singleDefinitions,
            ObjectProvider<List<OptionDefinition>> listDefinitions,
            ObjectProvider<OptionDefinitionSource> definitionSources) {
        if (singleDefinitions != null) {
            singleDefinitions.forEach(this::register);
        }
        if (listDefinitions != null) {
            listDefinitions.forEach(list -> {
                if (list != null) {
                    list.forEach(this::register);
                }
            });
        }
        if (definitionSources != null) {
            definitionSources.forEach(source -> {
                if (source != null && source.definitions() != null) {
                    source.definitions().forEach(this::register);
                }
            });
        }
    }

    public synchronized void register(OptionDefinition def) {
        Objects.requireNonNull(def, "OptionDefinition must not be null");
        if (definitions.containsKey(def.key())) {
            throw new IllegalStateException("Duplicate tenant option key registered: " + def.key());
        }
        // Validate defaultValue against declared type
        def.type().validate(def.defaultValue(), def);
        definitions.put(def.key(), def);
    }

    public Optional<OptionDefinition> get(String key) {
        return Optional.ofNullable(definitions.get(key));
    }

    public boolean has(String key) {
        return definitions.containsKey(key);
    }

    public Collection<OptionDefinition> all() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public List<OptionDefinition> byCategory(OptionCategory category) {
        if (category == null) {
            return new ArrayList<>(definitions.values());
        }
        return definitions.values().stream()
                .filter(d -> d.category() == category)
                .toList();
    }
}
