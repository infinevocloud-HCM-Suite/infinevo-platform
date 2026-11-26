package com.infinevo.core.ai.help;

import static com.infinevo.core.ai.AiToolInputs.rejectUnknown;
import static com.infinevo.core.ai.AiToolInputs.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolInputs.BadInput;
import com.infinevo.core.ai.AiToolProvider;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.setup.SetupChecklistResponse;
import com.infinevo.core.setup.SetupChecklistService;
import com.infinevo.core.setup.SetupStepResponse;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * {@code core.setup_status} (W-114.16): what is done and what is left on the customer's setup checklist, each step
 * with the screen that finishes it.
 *
 * <p>The steps are exactly those {@link SetupChecklistService#getChecklist} returns - the call the Setup screen makes,
 * which already leaves out a module the customer does not hold. It is not a pure read: it adds rows for newly
 * applicable steps and refreshes the completion cache, as opening the screen does. Skipping a step is a write behind
 * {@code core.tenant.manage} and is not offered. A step's screen is given only when the caller's own menu holds its
 * menu item, and its article only when the caller may see it, so the helper never links what the user cannot open.
 * No step id, tenant id or timestamp is returned.
 */
@Component
public class SetupAiToolProvider implements AiToolProvider {

    static final String NAME = "core.setup_status";
    static final String REQUIRED_ACTION = "core.tenant.read";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final SetupChecklistService checklist;
    private final HelpLibrary library;
    private final HelpAccess access;
    private final List<AiTool> tools;

    public SetupAiToolProvider(SetupChecklistService checklist, HelpLibrary library, HelpAccess access) {
        this.checklist = Objects.requireNonNull(checklist, "checklist must not be null");
        this.library = Objects.requireNonNull(library, "library must not be null");
        this.access = Objects.requireNonNull(access, "access must not be null");
        this.tools = List.of(new StatusTool());
    }

    @Override
    public List<AiTool> tools() {
        return tools;
    }

    /** The state the Setup screen shows: done, pre-filled and waiting for a review, skipped, or not done. */
    static String state(SetupStepResponse step) {
        if (step.skipped()) {
            return "SKIPPED";
        }
        if (step.completed()) {
            return step.prefilled() ? "REVIEW" : "DONE";
        }
        return "NOT_DONE";
    }

    /** The step's screen when the caller's menu holds its item: the item itself, or a fixed sub-route below it. */
    Optional<ObjectNode> screen(SetupStepScreens.Screen screen, SetupStepResponse step, HelpViewer viewer) {
        String itemPath = viewer.leaves().get(screen.menuKey());
        if (itemPath == null) {
            return Optional.empty();
        }
        String name = library.screenNames().getOrDefault(screen.menuKey(), step.label());
        if (screen.path().equals(itemPath)) {
            return Optional.of(NODES.objectNode().put("name", name).put("path", screen.path()));
        }
        if (itemPath.equals(library.subRoutes().get(screen.path()))) {
            return Optional.of(
                    NODES.objectNode().put("name", name + " - " + step.label()).put("path", screen.path()));
        }
        return Optional.empty();
    }

    private final class StatusTool implements AiTool {

        private final ObjectNode inputSchema = schema(Map.of());

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public String description() {
            return "The customer's setup checklist, as the Setup screen shows it: total, done and skipped counts and"
                    + " every step in order with its state - DONE, REVIEW (filled in by the country template and not"
                    + " saved since: ask the user to check it), SKIPPED or NOT_DONE - and, when the user may open it,"
                    + " the screen that finishes it and the help article id. List the steps it returns, not-done"
                    + " first, and add none of your own. It cannot skip or complete a step: the user does that on the"
                    + " Setup screen.";
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema.deepCopy();
        }

        @Override
        public String requiredAction() {
            return REQUIRED_ACTION;
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            JsonNode args = input == null || input.isNull() ? NODES.objectNode() : input;
            try {
                if (!args.isObject()) {
                    throw new BadInput("input must be a JSON object");
                }
                rejectUnknown(args, inputSchema.path("properties"));
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            }
            SetupChecklistResponse list = checklist.getChecklist(TenantContext.require());
            HelpViewer viewer = access.currentViewer();
            ObjectNode out = NODES.objectNode();
            out.put("total", list.totalCount());
            out.put("done", list.completedCount());
            out.put("skipped", list.skippedCount());
            ArrayNode steps = out.putArray("steps");
            for (SetupStepResponse step : list.steps()) {
                ObjectNode row = steps.addObject();
                row.put("label", step.label());
                row.put("state", state(step));
                SetupStepScreens.of(step.code()).ifPresent(s -> {
                    screen(s, step, viewer).ifPresent(node -> row.set("screen", node));
                    if (s.article() != null && library.find(s.article(), viewer).isPresent()) {
                        row.put("article", s.article());
                    }
                });
            }
            return AiToolResult.success(out);
        }
    }
}
