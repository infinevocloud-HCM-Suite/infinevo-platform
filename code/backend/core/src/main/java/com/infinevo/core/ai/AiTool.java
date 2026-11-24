package com.infinevo.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * One thing the AI helper may do for a user (W-114.3).
 *
 * <p>A tool runs as the caller, in the caller's tenant, and must scope everything it reads to {@link AiToolContext}.
 * It is run only through {@link AiToolRegistry#execute}, which checks {@link #requiredAction()} and
 * {@link #requiredAnyOf()} at that moment, so the tool does not check them itself.
 */
public interface AiTool {

    /** Unique, {@code module.verb_noun} in lower case with the module first - for example {@code core.my_leave_balances}. */
    String name();

    /** What the tool does, for the model. */
    String description();

    /** The JSON Schema of {@code input}, for the model. */
    JsonNode inputSchema();

    /** The action a caller must hold to use the tool - or any code of {@link #requiredAnyOf()}. */
    String requiredAction();

    /** Alternative actions, any of which also lets a caller use the tool. Most tools leave it empty. */
    default List<String> requiredAnyOf() {
        return List.of();
    }

    /**
     * Whether the tool reads the caller's own employee record (W-114.4, rule 4). Such a tool is not offered, and not
     * run, for a login with no employee record ({@link AiToolContext#employeeId()} {@code null}).
     */
    default boolean needsEmployee() {
        return false;
    }

    AiToolResult execute(AiToolContext ctx, JsonNode input);
}
