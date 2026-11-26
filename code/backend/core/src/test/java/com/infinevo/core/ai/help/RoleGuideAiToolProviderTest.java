package com.infinevo.core.ai.help;

import static com.infinevo.core.ai.help.TestArticles.article;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoleGuideAiToolProviderTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AiToolContext CTX = new AiToolContext(UUID.randomUUID(), UUID.randomUUID(), null);

    private static final RoleGuide HR = new RoleGuide(
            "hr",
            "HR",
            List.of(
                    new RoleGuide.Section(
                            "Every day",
                            List.of(new RoleGuide.Line(
                                    "Check the leave requests.", "core.leave.requests", "leave-requests-review"))),
                    new RoleGuide.Section(
                            "Once a year",
                            List.of(
                                    new RoleGuide.Line("Review the leave types.", "core.leave.types", "leave-types"),
                                    new RoleGuide.Line("Publish the holidays.", "hrms.holidays", null)))));

    private static final RoleGuide MANAGER = new RoleGuide(
            "manager",
            "Manager",
            List.of(new RoleGuide.Section(
                    "Every day",
                    List.of(new RoleGuide.Line("Approve what is waiting.", "core.approvals.inbox", null)))));

    private static final RoleGuide PAYROLL = new RoleGuide(
            "payroll-officer",
            "Payroll officer",
            List.of(new RoleGuide.Section(
                    "Every month", List.of(new RoleGuide.Line("Run the payroll.", "payroll.runs", "run-payroll")))));

    private static final RoleGuide FINANCE = new RoleGuide(
            "finance",
            "Finance",
            List.of(new RoleGuide.Section(
                    "Every month",
                    List.of(
                            new RoleGuide.Line("Approve the pay run.", "payroll.runs", null, "payroll.run.approve"),
                            new RoleGuide.Line("Read your payslip.", "core.me", "view-payslip", null)))));

    private static final Map<String, String> NAMES = Map.of(
            "core.leave.requests", "Leave requests",
            "core.leave.types", "Leave types",
            "hrms.holidays", "Holidays",
            "core.approvals.inbox", "Approvals inbox",
            "payroll.runs", "Payroll runs",
            "core.me", "My self-service",
            "core.roles", "Roles");

    private final HelpLibrary library = HelpLibrary.of(
            List.of(
                    article("leave-requests-review", "/leave/requests"),
                    article("leave-types", "/leave/types"),
                    article("run-payroll", "/payroll/runs"),
                    new HelpArticle(
                            "view-payslip",
                            "See my payslip",
                            "Summary.",
                            List.of("employee"),
                            "/me",
                            null,
                            "payroll.payslip.read_own",
                            List.of("payslip"),
                            "1. Open My Payslips.",
                            PlatformModule.PAYROLL)),
            List.of(HR, MANAGER, PAYROLL, FINANCE),
            NAMES);

    private static Map<String, String> leaves(String... keyThenPath) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyThenPath.length; i += 2) {
            map.put(keyThenPath[i], keyThenPath[i + 1]);
        }
        return map;
    }

    private static HelpViewer viewer(Set<String> roles, Set<String> actions, Map<String, String> leaves) {
        return new HelpViewer(Set.copyOf(leaves.values()), actions, roles, Set.of(), leaves);
    }

    private AiTool tool(HelpViewer viewer) {
        return new RoleGuideAiToolProvider(library, HelpAccess.fixed(viewer), codes -> Map.of("auditor", "Auditor"))
                .tools()
                .get(0);
    }

    private static JsonNode run(AiTool tool, String json) throws Exception {
        AiToolResult result = tool.execute(CTX, JSON.readTree(json));
        assertThat(result.ok()).as(result.error()).isTrue();
        return result.data();
    }

    /** Neither an action code nor an id ever comes back. */
    private static void noCodesNoIds(JsonNode data) {
        String text = data.toString();
        assertThat(text).doesNotContain("core.ai.use", "core.role.read", "core.leave.manage", "screen\":\"core.");
        assertThat(text).doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}-");
    }

    @Test
    @DisplayName("one tool, core.my_role_guide, needing core.ai.use, with an optional role")
    void shape() {
        AiTool tool = tool(viewer(Set.of(), Set.of(), Map.of()));

        assertThat(tool.name()).isEqualTo("core.my_role_guide");
        assertThat(tool.requiredAction()).isEqualTo("core.ai.use");
        assertThat(tool.inputSchema().path("required")).isEmpty();
        assertThat(tool.inputSchema().path("properties").has("role")).isTrue();
        assertThat(tool.execute(CTX, JSON.createObjectNode().put("other", 1)).ok())
                .isFalse();
    }

    @Test
    @DisplayName("a caller with hr and manager gets both roles, both guides' headings and every screen of the feed")
    void overviewForTwoRoles() throws Exception {
        HelpViewer hrManager = viewer(
                Set.of("hr", "manager"),
                Set.of("core.leave.manage"),
                leaves(
                        "core.leave.requests", "/leave/requests",
                        "core.approvals.inbox", "/approvals",
                        "core.me", "/me"));

        JsonNode out = run(tool(hrManager), "{}");

        assertThat(out.path("roles").findValuesAsText("code")).containsExactly("hr", "manager");
        assertThat(out.path("roles").findValuesAsText("name")).containsExactly("HR", "Manager");
        assertThat(out.path("roles").get(0).path("hasGuide").asBoolean()).isTrue();
        assertThat(out.path("screens").findValuesAsText("name"))
                .containsExactly("Leave requests", "Approvals inbox", "My self-service");
        assertThat(out.path("screens").findValuesAsText("path"))
                .containsExactly("/leave/requests", "/approvals", "/me");
        assertThat(out.path("guides").findValuesAsText("role")).containsExactly("hr", "manager");
        // "Once a year" has no line this HR user can open, so its heading is not offered.
        assertThat(out.path("guides").get(0).path("sections").toString()).isEqualTo("[\"Every day\"]");
        noCodesNoIds(out);
    }

    @Test
    @DisplayName(
            "the hr guide drops every line whose screen is not in the caller's feed; kept lines link and name an article")
    void linesOutsideTheFeedAreDropped() throws Exception {
        HelpViewer hrWithoutHrms = viewer(
                Set.of("hr"),
                Set.of(),
                leaves("core.leave.requests", "/leave/requests", "core.leave.types", "/leave/types"));

        JsonNode out = run(tool(hrWithoutHrms), "{\"role\":\"hr\"}");

        assertThat(out.path("name").asText()).isEqualTo("HR");
        assertThat(out.findValuesAsText("text"))
                .containsExactly("Check the leave requests.", "Review the leave types.")
                .doesNotContain("Publish the holidays.");
        JsonNode first = out.path("sections").get(0).path("lines").get(0);
        assertThat(first.path("screen").path("name").asText()).isEqualTo("Leave requests");
        assertThat(first.path("screen").path("path").asText()).isEqualTo("/leave/requests");
        assertThat(first.path("article").asText()).isEqualTo("leave-requests-review");
        assertThat(first.has("forThatRole")).isFalse();
        noCodesNoIds(out);
    }

    @Test
    @DisplayName("after the admin took leave types away from hr, the leave types line is gone")
    void roleChangedByTheCustomer() throws Exception {
        HelpViewer hr = viewer(Set.of("hr"), Set.of(), leaves("core.leave.requests", "/leave/requests"));

        JsonNode out = run(tool(hr), "{\"role\":\"hr\"}");

        assertThat(out.findValuesAsText("text")).containsExactly("Check the leave requests.");
        assertThat(out.path("sections").findValuesAsText("heading")).containsExactly("Every day");
    }

    @Test
    @DisplayName("a caller holding only a customer-made role: hasGuide false, the role's own name, screens listed")
    void customRole() throws Exception {
        HelpViewer auditor = viewer(Set.of("auditor"), Set.of(), leaves("core.me", "/me"));

        JsonNode out = run(tool(auditor), "{}");

        assertThat(out.path("roles").get(0).path("name").asText()).isEqualTo("Auditor");
        assertThat(out.path("roles").get(0).path("hasGuide").asBoolean()).isFalse();
        assertThat(out.path("guides")).isEmpty();
        assertThat(out.path("screens").findValuesAsText("path")).containsExactly("/me");

        JsonNode own = run(tool(auditor), "{\"role\":\"auditor\"}");
        assertThat(own.path("hasGuide").asBoolean()).isFalse();
        assertThat(own.path("sections")).isEmpty();
    }

    @Test
    @DisplayName(
            "a duty is dropped when its action is not held or its article is hidden (no Payroll), even on a held screen")
    void dutyTheCallerCannotDo() throws Exception {
        Map<String, String> feed = leaves("payroll.runs", "/payroll/runs", "core.me", "/me");
        HelpViewer cannot = new HelpViewer(Set.copyOf(feed.values()), Set.of(), Set.of("finance"), Set.of(), feed);
        HelpViewer can = new HelpViewer(
                Set.copyOf(feed.values()),
                Set.of("payroll.run.approve", "payroll.payslip.read_own"),
                Set.of("finance"),
                Set.of(PlatformModule.PAYROLL),
                feed);

        JsonNode none = run(tool(cannot), "{\"role\":\"finance\"}");
        assertThat(none.path("sections")).isEmpty();
        assertThat(run(tool(cannot), "{}").path("guides").get(0).path("sections"))
                .isEmpty();

        JsonNode both = run(tool(can), "{\"role\":\"finance\"}");
        assertThat(both.findValuesAsText("text")).containsExactly("Approve the pay run.", "Read your payslip.");
        noCodesNoIds(both);
        assertThat(both.toString()).doesNotContain("payroll.run.approve");
    }

    @Test
    @DisplayName("a role the caller does not hold is not found without core.role.read")
    void otherRoleRefused() {
        HelpViewer employee = viewer(Set.of("employee"), Set.of(), leaves("core.me", "/me"));

        AiToolResult result =
                tool(employee).execute(CTX, JSON.createObjectNode().put("role", "payroll-officer"));

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).isEqualTo("not found");
        assertThat(tool(employee)
                        .execute(CTX, JSON.createObjectNode().put("role", "nobody"))
                        .error())
                .isEqualTo("not found");
    }

    @Test
    @DisplayName("with core.role.read the whole guide comes back, each line marked forThatRole and without a link")
    void otherRoleWithRoleRead() throws Exception {
        HelpViewer admin = viewer(Set.of("tenant-admin"), Set.of("core.role.read"), leaves("core.roles", "/roles"));

        JsonNode out = run(tool(admin), "{\"role\":\"Payroll-Officer\"}");

        assertThat(out.path("role").asText()).isEqualTo("payroll-officer");
        JsonNode line = out.path("sections").get(0).path("lines").get(0);
        assertThat(line.path("text").asText()).isEqualTo("Run the payroll.");
        assertThat(line.path("forThatRole").asBoolean()).isTrue();
        assertThat(line.path("screen").path("name").asText()).isEqualTo("Payroll runs");
        assertThat(line.path("screen").has("path")).isFalse();
        assertThat(line.has("article")).isFalse();
        noCodesNoIds(out);
    }
}
