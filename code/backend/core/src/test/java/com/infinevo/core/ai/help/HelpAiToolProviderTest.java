package com.infinevo.core.ai.help;

import static com.infinevo.core.ai.help.TestArticles.article;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolResult;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HelpAiToolProviderTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AiToolContext CTX = new AiToolContext(UUID.randomUUID(), UUID.randomUUID(), null);

    private final HelpLibrary library = HelpLibrary.of(List.of(
            article(
                    "leave-types",
                    "/leave/types",
                    null,
                    null,
                    List.of("hr"),
                    List.of("leave type"),
                    "Create a leave type",
                    "1. Open Leave, then Leave types."),
            article(
                    "add-employee",
                    "/employees",
                    "/employees/new",
                    null,
                    List.of("hr"),
                    List.of("employee"),
                    "Add an employee",
                    "1. Click New Employee.")));

    private HelpAiToolProvider provider(HelpViewer viewer) {
        return new HelpAiToolProvider(library, HelpAccess.fixed(viewer));
    }

    private static AiTool tool(HelpAiToolProvider provider, String name) {
        return provider.tools().stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static JsonNode input(String json) throws Exception {
        return JSON.readTree(json);
    }

    @Test
    @DisplayName("two tools, both needing core.ai.use and no employee record; closed input schemas")
    void toolShapes() {
        HelpAiToolProvider provider = provider(new HelpViewer(Set.of(), Set.of(), Set.of()));

        assertThat(provider.tools())
                .extracting(AiTool::name)
                .containsExactly("core.read_help_article", "core.search_help");
        assertThat(provider.tools()).allSatisfy(t -> {
            assertThat(t.requiredAction()).isEqualTo("core.ai.use");
            assertThat(t.needsEmployee()).isFalse();
            assertThat(t.inputSchema().path("additionalProperties").asBoolean(true))
                    .isFalse();
        });
        assertThat(tool(provider, "core.read_help_article")
                        .inputSchema()
                        .path("required")
                        .get(0)
                        .asText())
                .isEqualTo("id");
        assertThat(tool(provider, "core.search_help")
                        .inputSchema()
                        .path("properties")
                        .path("query")
                        .path("maxLength")
                        .asInt())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("read answers id, title, body, screen and route")
    void readsAnArticle() throws Exception {
        HelpAiToolProvider provider = provider(new HelpViewer(Set.of("/employees"), Set.of(), Set.of()));

        AiToolResult result = tool(provider, "core.read_help_article").execute(CTX, input("{\"id\":\"add-employee\"}"));

        assertThat(result.ok()).isTrue();
        assertThat(result.data().path("id").asText()).isEqualTo("add-employee");
        assertThat(result.data().path("title").asText()).isEqualTo("Add an employee");
        assertThat(result.data().path("body").asText()).isEqualTo("1. Click New Employee.");
        assertThat(result.data().path("screen").asText()).isEqualTo("/employees");
        assertThat(result.data().path("route").asText()).isEqualTo("/employees/new");
    }

    @Test
    @DisplayName("reading a hidden article answers exactly as reading an unknown id")
    void hiddenEqualsUnknown() throws Exception {
        HelpAiToolProvider provider = provider(new HelpViewer(Set.of("/me"), Set.of(), Set.of()));
        AiTool read = tool(provider, "core.read_help_article");

        AiToolResult hidden = read.execute(CTX, input("{\"id\":\"leave-types\"}"));
        AiToolResult unknown = read.execute(CTX, input("{\"id\":\"no-such-article\"}"));

        assertThat(hidden.ok()).isFalse();
        assertThat(hidden).isEqualTo(unknown);
        assertThat(hidden.error()).isEqualTo("not found");
    }

    @Test
    @DisplayName("search answers id, title and summary of visible articles only")
    void searches() throws Exception {
        HelpAiToolProvider provider = provider(new HelpViewer(Set.of("/leave/types"), Set.of(), Set.of("hr")));
        AiTool search = tool(provider, "core.search_help");

        AiToolResult found = search.execute(CTX, input("{\"query\":\"add leave type\"}"));
        assertThat(found.ok()).isTrue();
        JsonNode first = found.data().path("articles").get(0);
        assertThat(first.path("id").asText()).isEqualTo("leave-types");
        assertThat(first.has("summary")).isTrue();
        assertThat(first.has("body")).isFalse();
        assertThat(found.data().path("articles")).hasSize(1);

        assertThat(search.execute(CTX, input("{\"query\":\"zzzz\"}")).data().path("articles"))
                .isEmpty();
    }

    @Test
    @DisplayName("bad input runs nothing: an unknown field, a missing or too long query")
    void badInput() throws Exception {
        HelpAiToolProvider provider = provider(new HelpViewer(Set.of("/leave/types"), Set.of(), Set.of()));

        assertThat(tool(provider, "core.search_help")
                        .execute(CTX, input("{\"q\":\"x\"}"))
                        .ok())
                .isFalse();
        assertThat(tool(provider, "core.search_help").execute(CTX, input("{}")).ok())
                .isFalse();
        assertThat(tool(provider, "core.search_help")
                        .execute(CTX, input("{\"query\":\"" + "a".repeat(201) + "\"}"))
                        .error())
                .contains("200");
        assertThat(tool(provider, "core.read_help_article")
                        .execute(CTX, input("{}"))
                        .ok())
                .isFalse();
    }
}
