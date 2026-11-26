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
import com.infinevo.core.authz.Role;
import com.infinevo.core.authz.RoleRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The AI helper's role guide (W-114.12): {@code core.my_role_guide}, what the caller's roles can do and should do.
 *
 * <p>"Can do" is never read from the guide text: it is the caller's navigation feed, through {@link HelpAccess}, which
 * already reflects what the customer changed in a role. A guide line whose screen is not a leaf of that feed is dropped.
 * Reading a role the caller does not hold needs {@value #ROLE_READ}; then every line comes back marked
 * {@code forThatRole} and without a link. No role id, user id or action code is returned.
 */
@Component
public class RoleGuideAiToolProvider implements AiToolProvider {

    private static final Logger log = LoggerFactory.getLogger(RoleGuideAiToolProvider.class);

    static final String NAME = "core.my_role_guide";
    static final String REQUIRED_ACTION = "core.ai.use";
    static final String ROLE_READ = "core.role.read";
    static final String NOT_FOUND = "not found";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final HelpLibrary library;
    private final HelpAccess access;
    private final Function<Set<String>, Map<String, String>> roleNames;
    private final List<AiTool> tools;

    @Autowired
    public RoleGuideAiToolProvider(HelpLibrary library, HelpAccess access, ObjectProvider<RoleRepository> roles) {
        this(library, access, codes -> namesFromRepository(roles.getIfAvailable(), codes));
    }

    /** For tests: role names from a plain function. */
    RoleGuideAiToolProvider(
            HelpLibrary library, HelpAccess access, Function<Set<String>, Map<String, String>> roleNames) {
        this.library = Objects.requireNonNull(library, "library must not be null");
        this.access = Objects.requireNonNull(access, "access must not be null");
        this.roleNames = Objects.requireNonNull(roleNames, "roleNames must not be null");
        this.tools = List.of(new GuideTool());
    }

    @Override
    public List<AiTool> tools() {
        return tools;
    }

    /** Names of the given codes in the bound tenant; an empty map when they cannot be read. */
    private static Map<String, String> namesFromRepository(RoleRepository repository, Set<String> codes) {
        Optional<UUID> tenant = TenantContext.current();
        if (repository == null || tenant.isEmpty() || codes.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, String> names = new HashMap<>();
            for (Role role : repository.findByTenantIdOrderByCodeAsc(tenant.get())) {
                if (codes.contains(role.getCode()) && role.getName() != null) {
                    names.put(role.getCode(), role.getName());
                }
            }
            return names;
        } catch (RuntimeException e) {
            log.warn(
                    "Role guide: role names could not be read; codes are shown ({})",
                    e.getClass().getName());
            return Map.of();
        }
    }

    private ObjectNode overview(HelpViewer viewer) {
        Set<String> held = new TreeSet<>(viewer.roles());
        Map<String, String> names = roleNames.apply(held);
        ObjectNode out = NODES.objectNode();
        ArrayNode roles = out.putArray("roles");
        ArrayNode guides = NODES.arrayNode();
        for (String code : held) {
            Optional<RoleGuide> guide = library.guide(code);
            roles.addObject()
                    .put("code", code)
                    .put("name", name(code, guide, names))
                    .put("hasGuide", guide.isPresent());
            guide.ifPresent(g -> {
                ObjectNode entry = guides.addObject().put("role", code);
                ArrayNode sections = entry.putArray("sections");
                g.sections().stream()
                        .filter(s -> s.lines().stream().anyMatch(l -> keeps(l, viewer)))
                        .forEach(s -> sections.add(s.heading()));
            });
        }
        ArrayNode screens = out.putArray("screens");
        viewer.leaves().forEach((key, path) -> screens.addObject()
                .put("name", library.screenNames().getOrDefault(key, path))
                .put("path", path));
        out.set("guides", guides);
        return out;
    }

    private AiToolResult guideOf(HelpViewer viewer, String role) {
        String code = role.strip().toLowerCase(Locale.ROOT);
        boolean holds = viewer.roles().contains(code);
        Optional<RoleGuide> guide = library.guide(code);
        if (!holds) {
            if (guide.isEmpty() || !viewer.actions().contains(ROLE_READ)) {
                return AiToolResult.failure(NOT_FOUND);
            }
            return AiToolResult.success(guideJson(guide.get(), viewer, false));
        }
        if (guide.isEmpty()) {
            Map<String, String> names = roleNames.apply(Set.of(code));
            ObjectNode out = NODES.objectNode()
                    .put("role", code)
                    .put("name", name(code, guide, names))
                    .put("hasGuide", false);
            out.putArray("sections");
            return AiToolResult.success(out);
        }
        return AiToolResult.success(guideJson(guide.get(), viewer, true));
    }

    /**
     * Whether the caller can do this duty: its screen is a leaf of their feed, its extra action (when the line names
     * one) is held, and its help article (when it has one) is one they may see - which also checks the article's
     * module, so a customer without Payroll is not told to read a payslip on My self-service.
     */
    private boolean keeps(RoleGuide.Line line, HelpViewer viewer) {
        return viewer.leaves().containsKey(line.screen())
                && (line.action() == null || viewer.actions().contains(line.action()))
                && (line.article() == null
                        || library.find(line.article(), viewer).isPresent());
    }

    /** {@code own}: only the duties the caller can do, with links; otherwise every line, no links. */
    private ObjectNode guideJson(RoleGuide guide, HelpViewer viewer, boolean own) {
        ObjectNode out = NODES.objectNode()
                .put("role", guide.role())
                .put("name", guide.title())
                .put("hasGuide", true);
        ArrayNode sections = out.putArray("sections");
        for (RoleGuide.Section section : guide.sections()) {
            ArrayNode lines = NODES.arrayNode();
            for (RoleGuide.Line line : section.lines()) {
                String path = viewer.leaves().get(line.screen());
                if (own && !keeps(line, viewer)) {
                    continue;
                }
                ObjectNode row = lines.addObject().put("text", line.text());
                ObjectNode screen = row.putObject("screen")
                        .put("name", library.screenNames().get(line.screen()));
                if (own) {
                    screen.put("path", path);
                    if (line.article() != null) {
                        row.put("article", line.article());
                    }
                } else {
                    row.put("forThatRole", true);
                }
            }
            if (!lines.isEmpty()) {
                sections.addObject().put("heading", section.heading()).set("lines", lines);
            }
        }
        return out;
    }

    private static String name(String code, Optional<RoleGuide> guide, Map<String, String> names) {
        return guide.map(RoleGuide::title).orElseGet(() -> names.getOrDefault(code, code));
    }

    private final class GuideTool implements AiTool {

        private final ObjectNode inputSchema = schema(Map.of(
                "role",
                NODES.objectNode()
                        .put("type", "string")
                        .put("description", "Optional: one role code from the first answer's roles, for example hr.")));

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public String description() {
            return "What the user's roles can do and should do. Without role: their roles, every screen they can"
                    + " open (name and path) and the section headings of each role's duty guide. With role: that"
                    + " role's duties, each with its screen and, when there is one, a help article id to read with"
                    + " core.read_help_article.";
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
                String role = text(args, "role");
                HelpViewer viewer = access.currentViewer();
                if (role == null || role.isBlank()) {
                    return AiToolResult.success(overview(viewer));
                }
                return guideOf(viewer, role);
            } catch (BadInput e) {
                return AiToolResult.failure(e.getMessage());
            }
        }
    }
}
