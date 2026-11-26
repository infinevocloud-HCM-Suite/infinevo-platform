package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.help.HelpAccess;
import com.infinevo.core.ai.help.HelpLibrary;
import com.infinevo.core.ai.help.HelpViewer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-114.13: the screen a client path is on, and the screens a few words find, from the caller's own feed only. */
class AiScreenResolverTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HelpLibrary LIBRARY = new HelpLibrary();

    private static HelpViewer feed(String... keyThenPath) {
        Map<String, String> leaves = new LinkedHashMap<>();
        for (int i = 0; i < keyThenPath.length; i += 2) {
            leaves.put(keyThenPath[i], keyThenPath[i + 1]);
        }
        return new HelpViewer(Set.copyOf(leaves.values()), Set.of(), Set.of(), Set.of(), leaves);
    }

    private static final HelpViewer HR = feed(
            "core.leave.types", "/leave/types",
            "core.leave.requests", "/leave/requests",
            "core.leave.allocations", "/leave/allocations",
            "core.employee", "/employees",
            "payroll.runs", "/payroll/runs",
            "payroll.settings", "/payroll/settings",
            "core.me", "/me");

    private static final HelpViewer EMPLOYEE = feed("core.me", "/me", "hrms.attendance", "/hrms/attendance");

    private static AiScreenResolver resolver(HelpViewer viewer) {
        return new AiScreenResolver(LIBRARY, HelpAccess.fixed(viewer));
    }

    @Test
    @DisplayName("an exact path gives that screen, with its name from screens.json and the menu's own path")
    void exact() {
        assertThat(resolver(HR).resolve("/leave/types")).contains(new AiScreen("Leave types", "/leave/types"));
    }

    @Test
    @DisplayName("/payroll/runs/123 gives Payroll runs; /payroll/runsX gives nothing; the longest prefix wins")
    void prefix() {
        assertThat(resolver(HR).resolve("/payroll/runs/123")).contains(new AiScreen("Payroll runs", "/payroll/runs"));
        assertThat(resolver(HR).resolve("/payroll/runsX")).isEmpty();
        assertThat(resolver(HR).resolve("/payroll/settings/epf"))
                .map(AiScreen::path)
                .contains("/payroll/settings");
        assertThat(resolver(HR).resolve("/me/leave")).map(AiScreen::path).contains("/me");
    }

    @Test
    @DisplayName("a path the caller may not open, a 201-character path, a blank or relative path: nothing")
    void notInFeedOrUnusable() {
        assertThat(resolver(EMPLOYEE).resolve("/leave/types")).isEmpty();
        assertThat(resolver(HR).resolve("/leave/types/" + "x".repeat(188))).isEmpty();
        assertThat(resolver(HR).resolve("/me/" + "x".repeat(196))).isPresent();
        assertThat(resolver(HR).resolve("")).isEmpty();
        assertThat(resolver(HR).resolve(null)).isEmpty();
        assertThat(resolver(HR).resolve("leave/types")).isEmpty();
    }

    @Test
    @DisplayName("a title is made from a key screens.json does not name: nav word dropped, _ as space, capital first")
    void titleFromKey() {
        assertThat(AiScreenResolver.titleFromKey("core.leave.types")).isEqualTo("Leave types");
        assertThat(AiScreenResolver.titleFromKey("hrms.loss_of_pay")).isEqualTo("Loss of pay");
        assertThat(new AiScreenResolver(LIBRARY, HelpAccess.fixed(feed("core.unnamed_thing", "/x"))).resolve("/x"))
                .contains(new AiScreen("Unnamed thing", "/x"));
    }

    @Test
    @DisplayName("find('leave types') ranks Leave types first, returns at most five, and nothing for no match")
    void find() {
        assertThat(resolver(HR).find("take me to leave types"))
                .first()
                .isEqualTo(new AiScreen("Leave types", "/leave/types"));
        assertThat(resolver(HR).find("leave"))
                .hasSizeLessThanOrEqualTo(5)
                .extracting(AiScreen::path)
                .contains("/leave/types", "/leave/requests", "/leave/allocations");
        assertThat(resolver(HR).find("spaceship")).isEmpty();
        assertThat(resolver(HR).find("take me to")).isEmpty();
        assertThat(resolver(EMPLOYEE).find("leave types")).isEmpty();
    }

    @Test
    @DisplayName("core.find_screen: title and path only, from the caller's feed; bad input refused")
    void findScreenTool() throws Exception {
        AiTool tool = new AiScreenToolProvider(resolver(HR)).tools().get(0);
        AiToolContext ctx = new AiToolContext(UUID.randomUUID(), UUID.randomUUID(), null);

        assertThat(tool.name()).isEqualTo("core.find_screen");
        assertThat(tool.requiredAction()).isEqualTo("core.ai.use");
        AiToolResult found = tool.execute(ctx, JSON.readTree("{\"query\":\"leave types\"}"));
        assertThat(found.ok()).isTrue();
        JsonNode first = found.data().path("screens").get(0);
        assertThat(first.path("title").asText()).isEqualTo("Leave types");
        assertThat(first.path("path").asText()).isEqualTo("/leave/types");
        assertThat(found.data().toString()).doesNotContain("core.leave", "/api/", "manage");

        assertThat(tool.execute(ctx, JSON.readTree("{\"query\":\"\"}")).ok()).isFalse();
        assertThat(tool.execute(ctx, JSON.readTree("{\"query\":\"" + "x".repeat(101) + "\"}"))
                        .ok())
                .isFalse();
        assertThat(tool.execute(ctx, JSON.readTree("{\"query\":\"a\",\"other\":1}"))
                        .ok())
                .isFalse();
    }

    @Test
    @DisplayName("the request's context: only a text path starting with / and at most 200 characters is used")
    void contextPath() throws Exception {
        assertThat(AiMessageContext.pathOf(JSON.readTree("{\"path\":\"/leave/types\"}")))
                .isEqualTo("/leave/types");
        assertThat(AiMessageContext.pathOf(JSON.readTree("{\"path\":5}"))).isNull();
        assertThat(AiMessageContext.pathOf(JSON.readTree("\"/leave/types\""))).isNull();
        assertThat(AiMessageContext.pathOf(JSON.readTree("{\"path\":\"/" + "x".repeat(200) + "\"}")))
                .isNull();
        assertThat(AiMessageContext.pathOf(JSON.readTree("{\"path\":\"javascript:x\"}")))
                .isNull();
        assertThat(AiMessageContext.pathOf(null)).isNull();
    }
}
