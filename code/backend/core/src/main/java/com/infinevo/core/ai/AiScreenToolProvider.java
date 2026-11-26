package com.infinevo.core.ai;

import static com.infinevo.core.ai.AiToolInputs.rejectUnknown;
import static com.infinevo.core.ai.AiToolInputs.schema;
import static com.infinevo.core.ai.AiToolInputs.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiToolInputs.BadInput;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * {@code core.find_screen} (W-114.13): up to five screens of the caller's own menu that match a few words, each
 * {@code { title, path }}, for a "take me to ..." answer. Only screens in the caller's feed come back (rule 3); no key,
 * action code, API path or id (rule 4).
 */
@Component
public class AiScreenToolProvider implements AiToolProvider {

    static final String NAME = "core.find_screen";
    static final int MAX_QUERY = 100;

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final AiScreenResolver resolver;
    private final List<AiTool> tools;

    public AiScreenToolProvider(AiScreenResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
        this.tools = List.of(new FindScreen());
    }

    @Override
    public List<AiTool> tools() {
        return tools;
    }

    private final class FindScreen implements AiTool {

        private final ObjectNode inputSchema = schema(
                Map.of(
                        "query",
                        NODES.objectNode()
                                .put("type", "string")
                                .put("minLength", 1)
                                .put("maxLength", MAX_QUERY)
                                .put("description", "The screen in the user's words, for example leave types.")),
                "query");

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public String description() {
            return "Find screens the user can open, by a few words. Answers up to 5 screens, each with its title and"
                    + " path, best first; link one as [title](app:/path).";
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema;
        }

        @Override
        public String requiredAction() {
            return "core.ai.use";
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            JsonNode args = input == null ? NODES.objectNode() : input;
            try {
                rejectUnknown(args, inputSchema.path("properties"));
                String query = text(args, "query");
                if (query == null || query.isBlank()) {
                    throw new BadInput("query is required");
                }
                if (query.length() > MAX_QUERY) {
                    throw new BadInput("query must be at most " + MAX_QUERY + " characters");
                }
                ObjectNode out = NODES.objectNode();
                ArrayNode screens = out.putArray("screens");
                for (AiScreen screen : resolver.find(query)) {
                    screens.addObject().put("title", screen.title()).put("path", screen.path());
                }
                return AiToolResult.success(out);
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            }
        }
    }
}
