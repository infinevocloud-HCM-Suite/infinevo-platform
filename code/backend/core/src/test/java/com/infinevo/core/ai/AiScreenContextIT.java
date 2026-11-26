package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-114.13 Verification: the screen the user is on reaches the prompt only as the caller's own menu item, and links
 * are offered only for screens the caller may open. Built like {@code AiMessageIT}: the shipped filter chain, the real
 * permission check and navigation feed, PostgreSQL with row-level security, the scripted stub model.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class AiScreenContextIT extends AbstractIntegrationTest {

    private static final String SCREEN_LINE = "The user is on the screen \"Leave types\" (/leave/types).";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AiModelClient modelClient;

    private StubAiModelClient stub;
    private UUID tenant;
    private UUID hr;
    private UUID employee;

    @BeforeEach
    void setUp() throws SQLException {
        stub = (StubAiModelClient) modelClient;
        stub.reset();
        tenant = AuthzTestSchema.insertTenant("AiScreenCo " + UUID.randomUUID());
        subscription(tenant, true);
        String tag = UUID.randomUUID().toString().substring(0, 8);
        UUID hrRole = AuthzTestSchema.insertRole(
                tenant,
                "ai-hr-" + tag,
                "AI HR",
                "core.ai.use",
                "core.leave.read",
                "core.leave_type.manage",
                "core.employee.read");
        UUID employeeRole = AuthzTestSchema.insertRole(
                tenant, "ai-emp-" + tag, "AI employee", "core.ai.use", "core.employee.read_own");
        hr = UUID.randomUUID();
        AuthzTestSchema.grant(tenant, AuthzTestSchema.insertMember(tenant, hr, "hr-" + hr + "@screen.test"), hrRole);
        employee = UUID.randomUUID();
        AuthzTestSchema.grant(
                tenant,
                AuthzTestSchema.insertMember(tenant, employee, "emp-" + employee + "@screen.test"),
                employeeRole);
    }

    @Test
    @DisplayName("HR on /leave/types: the model request names the screen; the stored row holds no path")
    void hrGetsTheScreenLine() throws Exception {
        UUID session = AiChatRows.session(tenant, hr);
        stub.script(AiModelResponse.text("This is Leave types."));

        ask(hr, session, "What is this page?", Map.of("path", "/leave/types")).andExpect(status().isOk());

        assertThat(stub.requests().get(0).systemPrompt()).contains(SCREEN_LINE);
        assertThat(messageText(session)).noneMatch(text -> text.contains("/leave/types"));
    }

    @Test
    @DisplayName("an employee who cannot open /leave/types sends it: no screen line, the same as no path")
    void employeeWithoutTheScreen() throws Exception {
        UUID session = AiChatRows.session(tenant, employee);
        stub.script(AiModelResponse.text("I can't see that."));

        ask(employee, session, "What is this page?", Map.of("path", "/leave/types"))
                .andExpect(status().isOk());

        assertThat(stub.requests().get(0).systemPrompt()).doesNotContain("The user is on the screen \"");
    }

    @Test
    @DisplayName("core.find_screen as that employee does not return Leave types; as HR it does")
    void findScreenOnlyOwnFeed() throws Exception {
        UUID mine = AiChatRows.session(tenant, employee);
        stub.script(
                AiModelResponse.toolCall("call_1", "core.find_screen", "{\"query\":\"leave types\"}"),
                AiModelResponse.text("No such screen for you."));
        ask(employee, mine, "Take me to leave types", null).andExpect(status().isOk());
        assertThat(lastMessage(stub.requests().get(1))).doesNotContain("/leave/types");

        stub.reset();
        UUID theirs = AiChatRows.session(tenant, hr);
        stub.script(
                AiModelResponse.toolCall("call_1", "core.find_screen", "{\"query\":\"leave types\"}"),
                AiModelResponse.text("[Leave types](app:/leave/types)"));
        ask(hr, theirs, "Take me to leave types", null).andExpect(status().isOk());
        assertThat(lastMessage(stub.requests().get(1))).contains("\"path\":\"/leave/types\"");
    }

    @Test
    @DisplayName("a 5,000-character path or a non-text context: 200, treated as no context")
    void oversizedContext() throws Exception {
        UUID session = AiChatRows.session(tenant, hr);
        stub.script(AiModelResponse.text("ok"), AiModelResponse.text("ok"));

        ask(hr, session, "Hi", Map.of("path", "/leave/types/" + "x".repeat(5000)))
                .andExpect(status().isOk());
        ask(hr, session, "Hi again", Map.of("path", List.of(1, 2))).andExpect(status().isOk());

        assertThat(stub.requests()).allSatisfy(r -> assertThat(r.systemPrompt()).doesNotContain(SCREEN_LINE));
        assertThat(messageText(session)).noneMatch(text -> text.contains("xxxx"));
    }

    @Test
    @DisplayName("GET /api/v1/ai/link-targets: HR holds /employees/new, the employee does not; off is 403 AI_DISABLED")
    void linkTargets() throws Exception {
        mvc.perform(as(tenant, hr, get("/api/v1/ai/link-targets")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths").isArray())
                .andExpect(jsonPath("$.paths[?(@ == '/employees/new')]").exists());
        mvc.perform(as(tenant, employee, get("/api/v1/ai/link-targets")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths[?(@ == '/employees/new')]").doesNotExist());

        UUID off = AuthzTestSchema.insertTenant("AiScreenOff " + UUID.randomUUID());
        subscription(off, false);
        UUID sub = UUID.randomUUID();
        AuthzTestSchema.grant(
                off,
                AuthzTestSchema.insertMember(off, sub, "off-" + sub + "@screen.test"),
                AuthzTestSchema.insertRole(off, "ai-off-" + sub.toString().substring(0, 8), "AI", "core.ai.use"));
        mvc.perform(as(off, sub, get("/api/v1/ai/link-targets")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AI_DISABLED"));
    }

    private ResultActions ask(UUID sub, UUID session, String content, Map<String, Object> context) throws Exception {
        Map<String, Object> body =
                context == null ? Map.of("content", content) : Map.of("content", content, "context", context);
        return mvc.perform(as(
                tenant,
                sub,
                post("/api/v1/ai/sessions/{id}/messages", session).content(json.writeValueAsString(body))));
    }

    private static String lastMessage(AiModelRequest request) {
        return request.messages().get(request.messages().size() - 1).content();
    }

    private static void subscription(UUID tenantId, boolean aiOn) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.subscription (id, tenant_id, status, started_on, ai_enabled,"
                                + " ai_monthly_request_cap) VALUES (?, ?, 'ACTIVE', CURRENT_DATE, ?, 100)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setBoolean(3, aiOn);
            ps.executeUpdate();
        }
    }

    /** Every stored message's content for the session, read as the schema owner. */
    private static List<String> messageText(UUID sessionId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT content FROM core.ai_message WHERE session_id = ?")) {
            ps.setObject(1, sessionId);
            List<String> values = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    values.add(rs.getString(1));
                }
            }
            return values;
        }
    }

    private static MockHttpServletRequestBuilder as(UUID tenantId, UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenantId.toString())));
    }
}
