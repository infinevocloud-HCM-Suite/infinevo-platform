package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-114.3 spec, Tests: the four session endpoints through the shipped filter chain, the real permission check (the
 * {@code employee} role holds {@code core.ai.use} by V200's trigger) and PostgreSQL with row-level security. Built
 * like {@code SubscriptionAiIT}.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class AiSessionIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private UUID tenant;
    private UUID subA;
    private UUID subB;
    private UUID employeeA;

    @BeforeEach
    void setUp() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("AiChatCo " + UUID.randomUUID());
        subscription(tenant, true);
        subA = UUID.randomUUID();
        UUID accountA = member(tenant, subA, "employee");
        employeeA =
                AuthzTestSchema.insertEmployee(tenant, "E-A-" + subA.toString().substring(0, 6), "Asha");
        AuthzTestSchema.linkEmployee(employeeA, accountA);
        subB = UUID.randomUUID();
        member(tenant, subB, "employee");
    }

    @Test
    @DisplayName("create: 201, stored for the token subject with the caller's employee record; list shows it")
    void create_storesCallerAndEmployee() throws Exception {
        UUID id = create(subA, "{\"title\":\"Leave questions\"}");

        assertThat(AiChatRows.sessionColumn(id, "user_id")).isEqualTo(subA);
        assertThat(AiChatRows.sessionColumn(id, "employee_id")).isEqualTo(employeeA);
        assertThat(AiChatRows.sessionColumn(id, "tenant_id")).isEqualTo(tenant);

        mvc.perform(as(subA, get("/api/v1/ai/sessions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].title").value("Leave questions"));
    }

    @Test
    @DisplayName("list: newest activity first; a chat with no message yet is ordered by when it was opened")
    void list_newestActivityFirst() throws Exception {
        UUID older = create(subA, null);
        UUID newer = create(subA, null);
        UUID active = create(subA, null);
        setLastMessageAt(active, "2000-01-01T00:00:00Z"); // older than both: activity, not creation, orders it
        setLastMessageAt(older, "2999-01-01T00:00:00Z");

        String body = mvc.perform(as(subA, get("/api/v1/ai/sessions")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(ids(body)).containsExactly(older, newer, active);
    }

    @Test
    @DisplayName(
            "user A cannot read, list or delete user B's chat in the same tenant: 404, and B's chat is still there")
    void userA_cannotTouchUserBsSession() throws Exception {
        UUID bs = create(subB, "{\"title\":\"B's chat\"}");
        AiChatRows.message(tenant, bs, "USER", "B's question");

        mvc.perform(as(subA, get("/api/v1/ai/sessions/{id}/messages", bs)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(as(subA, delete("/api/v1/ai/sessions/{id}", bs)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        String aList = mvc.perform(as(subA, get("/api/v1/ai/sessions")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(ids(aList)).doesNotContain(bs);

        assertThat(AiChatRows.countForSession("ai_session", bs)).isEqualTo(1);
        assertThat(AiChatRows.countForSession("ai_message", bs)).isEqualTo(1);
        mvc.perform(as(subB, get("/api/v1/ai/sessions/{id}/messages", bs)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("B's question"));
    }

    @Test
    @DisplayName("messages: USER and ASSISTANT only, oldest first, proposedAction from the intent; delete removes the"
            + " chat, its messages and its intents, then messages is 404")
    void delete_removesMessagesAndIntents() throws Exception {
        UUID id = create(subA, null);
        UUID question = AiChatRows.message(tenant, id, "USER", "Apply one day of leave tomorrow");
        AiChatRows.message(tenant, id, "TOOL", "{\"balance\":12}");
        UUID answer = AiChatRows.message(tenant, id, "ASSISTANT", "Shall I apply?");
        UUID intent = AiChatRows.intent(tenant, id, subA, answer);

        mvc.perform(as(subA, get("/api/v1/ai/sessions/{id}/messages", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(question.toString()))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].proposedAction").isEmpty())
                .andExpect(jsonPath("$[1].id").value(answer.toString()))
                .andExpect(jsonPath("$[1].proposedAction.intentId").value(intent.toString()));

        mvc.perform(as(subA, delete("/api/v1/ai/sessions/{id}", id))).andExpect(status().isNoContent());

        assertThat(AiChatRows.countForSession("ai_session", id)).isZero();
        assertThat(AiChatRows.countForSession("ai_message", id)).isZero();
        assertThat(AiChatRows.countForSession("ai_action_intent", id)).isZero();
        mvc.perform(as(subA, get("/api/v1/ai/sessions/{id}/messages", id))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("create with the customer's switch off: 403 AI_DISABLED and nothing is stored")
    void create_switchOff_aiDisabled() throws Exception {
        UUID offTenant = AuthzTestSchema.insertTenant("AiOffCo " + UUID.randomUUID());
        subscription(offTenant, false);
        UUID sub = UUID.randomUUID();
        member(offTenant, sub, "employee");

        mvc.perform(as(offTenant, sub, post("/api/v1/ai/sessions")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AI_DISABLED"));
        assertThat(sessionsOf(offTenant)).isZero();
    }

    @Test
    @DisplayName("a member without core.ai.use: 403 on every endpoint")
    void withoutCoreAiUse_forbidden() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID account = AuthzTestSchema.insertMember(tenant, sub, "noai-" + sub + "@aichat.test");
        UUID role = AuthzTestSchema.insertRole(
                tenant, "no-ai-" + sub.toString().substring(0, 8), "No AI", "core.employee.read");
        AuthzTestSchema.grant(tenant, account, role);
        UUID existing = create(subA, null);

        mvc.perform(as(sub, post("/api/v1/ai/sessions"))).andExpect(status().isForbidden());
        mvc.perform(as(sub, get("/api/v1/ai/sessions"))).andExpect(status().isForbidden());
        mvc.perform(as(sub, get("/api/v1/ai/sessions/{id}/messages", existing))).andExpect(status().isForbidden());
        mvc.perform(as(sub, delete("/api/v1/ai/sessions/{id}", existing))).andExpect(status().isForbidden());
        assertThat(AiChatRows.countForSession("ai_session", existing)).isEqualTo(1);
    }

    // ---- helpers ----

    private UUID create(UUID sub, String body) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/ai/sessions");
        if (body != null) {
            request = request.content(body);
        }
        String response = mvc.perform(as(sub, request))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(json.readTree(response).path("id").asText());
    }

    private List<UUID> ids(String pageBody) throws Exception {
        List<UUID> ids = new ArrayList<>();
        for (JsonNode row : json.readTree(pageBody).path("content")) {
            ids.add(UUID.fromString(row.path("id").asText()));
        }
        return ids;
    }

    private UUID member(UUID tenantId, UUID sub, String roleCode) throws SQLException {
        UUID account = AuthzTestSchema.insertMember(tenantId, sub, "ai-" + sub + "@aichat.test");
        AuthzTestSchema.grant(tenantId, account, AuthzTestSchema.roleId(tenantId, roleCode));
        return account;
    }

    private static void subscription(UUID tenantId, boolean aiOn) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.subscription (id, tenant_id, status, started_on, ai_enabled,"
                                + " ai_monthly_request_cap) VALUES (?, ?, 'ACTIVE', CURRENT_DATE, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setBoolean(3, aiOn);
            ps.setObject(4, aiOn ? 100 : null, java.sql.Types.INTEGER);
            ps.executeUpdate();
        }
    }

    private static void setLastMessageAt(UUID sessionId, String at) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE core.ai_session SET last_message_at = ?::timestamptz WHERE id = ?")) {
            ps.setString(1, at);
            ps.setObject(2, sessionId);
            ps.executeUpdate();
        }
    }

    private static long sessionsOf(UUID tenantId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core.ai_session WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return as(tenant, sub, request);
    }

    private static MockHttpServletRequestBuilder as(UUID tenantId, UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenantId.toString())));
    }
}
