package com.infinevo.core.ai;

import com.infinevo.core.ai.help.HelpLibrary;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The fixed list of deeper pages an answer may link to (W-114.13), {@code help/sub-routes.json}, loaded and checked
 * once by {@link HelpLibrary}. Nothing at run time can add to it.
 */
@Component
public class AiSubRoutes {

    private final HelpLibrary library;

    public AiSubRoutes(HelpLibrary library) {
        this.library = Objects.requireNonNull(library, "library must not be null");
    }

    /** The sub-route paths whose parent is a path in the caller's feed, sorted (rule 5). */
    public List<String> allowedFor(Set<String> feedPaths) {
        if (feedPaths == null || feedPaths.isEmpty()) {
            return List.of();
        }
        return library.subRoutes().entrySet().stream()
                .filter(e -> feedPaths.contains(e.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }
}
