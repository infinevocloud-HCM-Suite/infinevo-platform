package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.impersonation.ImpersonationController;
import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-114.13b spec, Tests: with a real act-as session opened by platform staff (as {@code NavigationBrandingIT} does),
 * every AI call is {@code 403 AI_DISABLED} and nothing is stored or counted, though the target holds
 * {@code core.ai.use}; the target signed in as themselves still gets 200.
 */
@SpringBootTest(classes = {PermissionGuardTestApp.class, AiActAsIT.ItConfig.class})
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class AiActAsIT extends AbstractIntegrationTest {

    @TestConfiguration
    @Import(ImpersonationController.class)
    static class ItConfig {}

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AiModelClient modelClient;

    private UUID tenant;
    private UUID targetSub;
    private String targetEmail;
    private UUID targetSession;

    @BeforeEach
    void setUp() throws Exception {
        ((StubAiModelClient) modelClient).reset();
        TenantContext.clear();
        tenant = AuthzTestSchema.insertTenant("AiActAsCo " + UUID.randomUUID());
        subscription(tenant);
        UUID role = AuthzTestSchema.insertRole(
                tenant, "ai-actas-" + UUID.randomUUID().toString().substring(0, 8), "AI", "core.ai.use");
        targetSub = UUID.randomUUID();
        targetEmail = "target-" + targetSub + "@aiactas.test";
        UUID account = AuthzTestSchema.insertMember(tenant, targetSub, targetEmail);
        AuthzTestSchema.grant(tenant, account, role);
        targetSession = AiChatRows.session(tenant, targetSub);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("acting as a user with core.ai.use: status, sessions, history, a question, delete: 403 AI_DISABLED")
    void actingAs_everyAiCallRefused() throws Exception {
        String impersonation = openSession();
        List<MockHttpServletRequestBuilder> calls = List.of(
                get("/api/v1/ai/status"),
                get("/api/v1/ai/sessions"),
                get("/api/v1/ai/sessions/{id}/messages", targetSession),
                post("/api/v1/ai/sessions/{id}/messages", targetSession)
                        .content(json.writeValueAsString(Map.of("content", "What is my leave?"))),
                post("/api/v1/ai/sessions").content(json.writeValueAsString(Map.of("title", "x"))),
                delete("/api/v1/ai/sessions/{id}", targetSession),
                post("/api/v1/ai/actions/{id}/confirm", UUID.randomUUID()),
                get("/api/v1/ai/link-targets"));

        for (MockHttpServletRequestBuilder call : calls) {
            mvc.perform(staff(call).header("X-Impersonation", impersonation))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AI_DISABLED"))
                    .andExpect(jsonPath("$.message").value(AiActAsGuard.MESSAGE));
        }

        assertThat(AiChatRows.countForSession("ai_message", targetSession)).isZero();
        assertThat(AiChatRows.sessionColumn(targetSession, "id")).isNotNull();
        assertThat(usageCount(tenant)).isZero();
    }

    @Test
    @DisplayName("the same user signed in as themselves: status 200, on")
    void ownLogin_stillOn() throws Exception {
        mvc.perform(as(tenant, targetSub, get("/api/v1/ai/status")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    // ---- helpers ----

    private UUID staffSub;
    private String staffEmail;

    /** Platform staff open an act-as session on the target, through the real controller. */
    private String openSession() throws Exception {
        UUID platform = PlatformTenant.DEFAULT_PLATFORM_TENANT_ID;
        staffSub = UUID.randomUUID();
        staffEmail = "staff-" + staffSub + "@infinevo.local";
        UUID staffAccount = AuthzTestSchema.insertMember(platform, staffSub, staffEmail);
        AuthzTestSchema.grant(platform, staffAccount, AuthzTestSchema.roleId(platform, "platform-admin"));
        String opened = mvc.perform(staff(post("/api/v1/tenants/{id}/impersonations", tenant)
                        .content(json.writeValueAsString(Map.of("email", targetEmail, "reason", "W-114.13b test")))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(opened).path("sessionId").asText();
    }

    private MockHttpServletRequestBuilder staff(MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(staffSub.toString())
                        .claim("tenant_id", PlatformTenant.DEFAULT_PLATFORM_TENANT_ID.toString())
                        .claim("email", staffEmail)));
    }

    private static MockHttpServletRequestBuilder as(UUID tenantId, UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenantId.toString())));
    }

    private static void subscription(UUID tenantId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.subscription (id, tenant_id, status, started_on, ai_enabled,"
                                + " ai_monthly_request_cap) VALUES (?, ?, 'ACTIVE', CURRENT_DATE, true, 1000)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        }
    }

    private static long usageCount(UUID tenantId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT coalesce(sum(request_count), 0) FROM core.ai_usage WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
