package com.infinevo.core.ai.help;

import static com.infinevo.core.ai.AiToolInputs.rejectUnknown;
import static com.infinevo.core.ai.AiToolInputs.schema;
import static com.infinevo.core.ai.AiToolInputs.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolInputs.BadInput;
import com.infinevo.core.ai.AiToolProvider;
import com.infinevo.core.ai.AiToolResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import org.springframework.stereotype.Component;

/**
 * The AI helper's two help tools (W-114.11): {@code core.read_help_article}, which reads one article by the id the
 * model picked from the prompt's catalogue, and the fallback {@code core.search_help}.
 *
 * <p>Both check access again, now, through {@link HelpAccess}: an article the caller may not see is never listed, and
 * reading it answers {@link #NOT_FOUND} - the same answer as for an id that does not exist. Article ids are our own
 * slugs, not database ids, so they are returned as they are.
 */
@Component
public class HelpAiToolProvider implements AiToolProvider {

    static final String SEARCH = "core.search_help";
    static final String READ = "core.read_help_article";
    static final String REQUIRED_ACTION = "core.ai.use";
    static final int MAX_QUERY = 200;
    static final String NOT_FOUND = "not found";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final HelpLibrary library;
    private final HelpAccess access;
    private final List<AiTool> tools;

    public HelpAiToolProvider(HelpLibrary library, HelpAccess access) {
        this.library = Objects.requireNonNull(library, "library must not be null");
        this.access = Objects.requireNonNull(access, "access must not be null");
        this.tools = List.of(read(), search());
    }

    @Override
    public List<AiTool> tools() {
        return tools;
    }

    private AiTool read() {
        return new HelpTool(
                READ,
                "Read one help article - the steps for a task in the product - by its id from the list 'Help"
                        + " articles you can read'. Answers the title, the steps, the screen path and, when the"
                        + " task is on a deeper page, that page's path.",
                schema(
                        Map.of(
                                "id",
                                NODES.objectNode()
                                        .put("type", "string")
                                        .put("description", "The article id, for example leave-types.")),
                        "id"),
                (viewer, input) -> {
                    String id = text(input, "id");
                    if (id == null) {
                        throw new BadInput("id is required");
                    }
                    return library.find(id, viewer)
                            .map(a -> {
                                ObjectNode out = NODES.objectNode()
                                        .put("id", a.id())
                                        .put("title", a.title())
                                        .put("body", a.body())
                                        .put("screen", a.screen());
                                if (a.route() != null) {
                                    out.put("route", a.route());
                                }
                                return AiToolResult.success(out);
                            })
                            .orElseGet(() -> AiToolResult.failure(NOT_FOUND));
                });
    }

    private AiTool search() {
        return new HelpTool(
                SEARCH,
                "Search the help articles by words, when no line of 'Help articles you can read' fits the"
                        + " question. Answers up to 5 articles (id, title, summary); read one with "
                        + READ
                        + ".",
                schema(
                        Map.of(
                                "query",
                                NODES.objectNode()
                                        .put("type", "string")
                                        .put("maxLength", MAX_QUERY)
                                        .put("description", "The user's words, for example add leave type.")),
                        "query"),
                (viewer, input) -> {
                    String query = text(input, "query");
                    if (query == null) {
                        throw new BadInput("query is required");
                    }
                    if (query.length() > MAX_QUERY) {
                        throw new BadInput("query must be at most " + MAX_QUERY + " characters");
                    }
                    ObjectNode out = NODES.objectNode();
                    ArrayNode list = out.putArray("articles");
                    for (HelpArticle a : library.search(query, viewer)) {
                        list.addObject()
                                .put("id", a.id())
                                .put("title", a.title())
                                .put("summary", a.summary());
                    }
                    return AiToolResult.success(out);
                });
    }

    private final class HelpTool implements AiTool {

        private final String name;
        private final String description;
        private final ObjectNode inputSchema;
        private final BiFunction<HelpViewer, JsonNode, AiToolResult> body;

        HelpTool(
                String name,
                String description,
                ObjectNode inputSchema,
                BiFunction<HelpViewer, JsonNode, AiToolResult> body) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
            this.body = body;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public JsonNode inputSchema() {
            return inputSchema;
        }

        @Override
        public String requiredAction() {
            return REQUIRED_ACTION;
        }

        @Override
        public AiToolResult execute(AiToolContext ctx, JsonNode input) {
            JsonNode args = input == null ? NODES.objectNode() : input;
            try {
                rejectUnknown(args, inputSchema.path("properties"));
                return body.apply(access.currentViewer(), args);
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            }
        }
    }
}
