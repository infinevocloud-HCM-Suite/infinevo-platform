package com.infinevo.core.ai.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiModelClient;
import com.infinevo.core.ai.AiModelResponse;
import com.infinevo.core.ai.AiToolDescriptor;
import com.infinevo.core.ai.StubAiModelClient;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.core.invitation.KeycloakProvisioningService;
import com.infinevo.shared.tenant.TenantContext;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-114.28 spec, Tests ({@code AdminStepsIT}): the tenant admin proposes a department, a holiday and a user
 * invitation through the chat and confirms each through the shipped endpoint, against the real services and
 * PostgreSQL with row-level security. The model is the scripted {@link StubAiModelClient}.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class AdminStepsIT extends AbstractIntegrationTest {

    private static final List<String> TOOLS = List.of(
            "core.propose_department",
            "core.propose_designation",
            "core.propose_work_location",
            "core.propose_holiday",
            "core.propose_user_invite");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AiModelClient modelClient;

    @MockBean
    private KeycloakProvisioningService keycloak;

    private StubAiModelClient stub;
    private UUID tenant;
    private UUID subAdmin;
    private UUID subEmployee;
    private UUID calendar;
    private String code;

    @BeforeEach
    void setUp() throws Exception {
        stub = (StubAiModelClient) modelClient;
        stub.reset();
        TenantContext.clear();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        tenant = AuthzTestSchema.insertTenant("AiAdminCo " + suffix);
        execute(
                "INSERT INTO core.subscription (id, tenant_id, status, started_on, ai_enabled, ai_monthly_request_cap)"
                        + " VALUES (?, ?, 'ACTIVE', CURRENT_DATE, true, 1000)",
                UUID.randomUUID(),
                tenant);
        UUID adminRole = AuthzTestSchema.insertRole(
                tenant,
                "ai-admin-" + suffix,
                "Admin",
                "core.ai.use",
                "core.org.manage",
                "core.holiday.manage",
                "core.user.manage",
                "core.role.assign");
        UUID employeeRole = AuthzTestSchema.insertRole(tenant, "ai-emp-" + suffix, "Staff " + suffix, "core.ai.use");
        subAdmin = UUID.randomUUID();
        member(tenant, subAdmin, adminRole);
        subEmployee = UUID.randomUUID();
        UUID employee = AuthzTestSchema.insertEmployee(tenant, "E-A-" + suffix, "Ravi");
        AuthzTestSchema.linkEmployee(employee, member(tenant, subEmployee, employeeRole));
        calendar = UUID.randomUUID();
        execute(
                "INSERT INTO core.holiday_calendar (id, tenant_id, name, is_default) VALUES (?, ?, 'India', true)",
                calendar,
                tenant);
        code = "OPS" + suffix.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a department: none until Confirm; Confirm makes one; a second confirm makes none")
    void department() throws Exception {
        UUID intent = propose(subAdmin, "core.propose_department", Map.of("code", code, "name", "Operations"));
        assertThat(count("SELECT count(*) FROM core.department WHERE tenant_id = ? AND code = ?", code))
                .isZero();

        JsonNode first = body(confirm(intent).andExpect(status().isOk()));
        assertThat(first.path("status").asText()).isEqualTo("CONFIRMED");
        body(confirm(intent).andExpect(status().isOk()));
        assertThat(count("SELECT count(*) FROM core.department WHERE tenant_id = ? AND code = ?", code))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a code taken between proposal and Confirm ends FAILED, and no second row is made")
    void department_takenMeanwhile() throws Exception {
        UUID intent = propose(subAdmin, "core.propose_department", Map.of("code", code, "name", "Operations"));
        execute(
                "INSERT INTO core.department (id, tenant_id, code, name) VALUES (?, ?, ?, 'Someone else')",
                UUID.randomUUID(),
                tenant,
                code);

        confirm(intent)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ACTION_FAILED"));
        assertThat(count("SELECT count(*) FROM core.department WHERE tenant_id = ? AND code = ?", code))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a holiday: none until Confirm; Confirm adds one to the default calendar; a second adds none")
    void holiday() throws Exception {
        UUID intent = propose(subAdmin, "core.propose_holiday", Map.of("name", "Diwali", "from", "2026-11-08"));
        String sql = "SELECT count(*) FROM core.holiday WHERE tenant_id = ? AND name = ?";
        assertThat(count(sql, "Diwali")).isZero();

        body(confirm(intent).andExpect(status().isOk()));
        body(confirm(intent).andExpect(status().isOk()));
        assertThat(count(sql, "Diwali")).isEqualTo(1);
        assertThat(count(
                        "SELECT count(*) FROM core.holiday WHERE tenant_id = ? AND calendar_id::text = ?",
                        calendar.toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("an invitation: none until Confirm; Confirm makes one with the role; a second makes none")
    void invite() throws Exception {
        String email = "new-" + code.toLowerCase() + "@admin.test";
        UUID intent = propose(subAdmin, "core.propose_user_invite", Map.of("email", email, "roles", List.of("Admin")));
        String sql = "SELECT count(*) FROM core.user_invitation WHERE tenant_id = ? AND email = ?";
        assertThat(count(sql, email)).isZero();

        body(confirm(intent).andExpect(status().isOk()));
        body(confirm(intent).andExpect(status().isOk()));
        assertThat(count(sql, email)).isEqualTo(1);
        assertThat(count(
                        "SELECT count(*) FROM core.user_invitation_role r JOIN core.user_invitation i"
                                + " ON i.id = r.invitation_id WHERE i.tenant_id = ? AND i.email = ?",
                        email))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a designation and a work location: none until Confirm; Confirm makes one; a second makes none")
    void designationAndLocation() throws Exception {
        UUID designation =
                propose(subAdmin, "core.propose_designation", Map.of("code", code, "name", "Team lead " + code));
        assertThat(count("SELECT count(*) FROM core.designation WHERE tenant_id = ? AND code = ?", code))
                .isZero();
        body(confirm(designation).andExpect(status().isOk()));
        body(confirm(designation).andExpect(status().isOk()));
        assertThat(count("SELECT count(*) FROM core.designation WHERE tenant_id = ? AND code = ?", code))
                .isEqualTo(1);

        UUID location = propose(
                subAdmin,
                "core.propose_work_location",
                Map.of("code", code, "name", "Pune " + code, "city", "Pune", "state", "Maharashtra"));
        assertThat(count("SELECT count(*) FROM core.work_location WHERE tenant_id = ? AND code = ?", code))
                .isZero();
        body(confirm(location).andExpect(status().isOk()));
        body(confirm(location).andExpect(status().isOk()));
        assertThat(count("SELECT count(*) FROM core.work_location WHERE tenant_id = ? AND code = ?", code))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a department name taken between proposal and Confirm ends FAILED; no second Finance")
    void departmentName_takenMeanwhile() throws Exception {
        UUID intent = propose(subAdmin, "core.propose_department", Map.of("code", code, "name", "Operations"));
        execute(
                "INSERT INTO core.department (id, tenant_id, code, name) VALUES (?, ?, 'OTHER1', 'operations')",
                UUID.randomUUID(),
                tenant);

        confirm(intent)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ACTION_FAILED"));
        assertThat(count("SELECT count(*) FROM core.department WHERE tenant_id = ? AND code = ?", code))
                .isZero();
    }

    @Test
    @DisplayName("the same holiday added between proposal and Confirm ends FAILED; still one")
    void holiday_addedMeanwhile() throws Exception {
        UUID intent = propose(subAdmin, "core.propose_holiday", Map.of("name", "Holi", "from", "2026-03-04"));
        execute(
                "INSERT INTO core.holiday (tenant_id, calendar_id, name, from_date, to_date)"
                        + " VALUES (?, ?, 'Holi', DATE '2026-03-04', DATE '2026-03-04')",
                tenant,
                calendar);

        confirm(intent)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ACTION_FAILED"));
        assertThat(count("SELECT count(*) FROM core.holiday WHERE tenant_id = ? AND name = ?", "Holi"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("an invitation sent between proposal and Confirm ends FAILED; still one")
    void invite_sentMeanwhile() throws Exception {
        String email = "twice-" + code.toLowerCase() + "@admin.test";
        UUID first = propose(subAdmin, "core.propose_user_invite", Map.of("email", email));
        UUID second = propose(subAdmin, "core.propose_user_invite", Map.of("email", email));
        body(confirm(second).andExpect(status().isOk()));

        confirm(first)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ACTION_FAILED"));
        assertThat(count("SELECT count(*) FROM core.user_invitation WHERE tenant_id = ? AND email = ?", email))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a user without the actions is never offered the tools, and a call to one makes nothing")
    void notOffered() throws Exception {
        JsonNode answer = say(
                subEmployee,
                AiModelResponse.toolCall(
                        "call_1",
                        "core.propose_department",
                        json.writeValueAsString(Map.of("code", code, "name", "Operations"))));

        List<String> offered = new ArrayList<>();
        for (AiToolDescriptor d : stub.requests().get(0).tools()) {
            offered.add(d.name());
        }
        assertThat(offered).doesNotContainAnyElementsOf(TOOLS);
        assertThat(answer.path("proposedAction").isMissingNode()
                        || answer.path("proposedAction").isNull())
                .isTrue();
        assertThat(count("SELECT count(*) FROM core.department WHERE tenant_id = ? AND code = ?", code))
                .isZero();
    }

    // ---- helpers ----

    private JsonNode say(UUID sub, AiModelResponse call) throws Exception {
        stub.script(call, AiModelResponse.text("Press Confirm."));
        UUID session = session(tenant, sub);
        return body(mvc.perform(as(
                        sub,
                        post("/api/v1/ai/sessions/{id}/messages", session)
                                .content(json.writeValueAsString(Map.of("content", "Set this up")))))
                .andExpect(status().isOk()));
    }

    private UUID propose(UUID sub, String tool, Map<String, Object> input) throws Exception {
        JsonNode answer = say(sub, AiModelResponse.toolCall("call_1", tool, json.writeValueAsString(input)));
        assertThat(answer.path("proposedAction").path("intentId").asText())
                .as(answer.toString())
                .isNotBlank();
        return UUID.fromString(answer.path("proposedAction").path("intentId").asText());
    }

    private ResultActions confirm(UUID intent) throws Exception {
        return mvc.perform(as(subAdmin, post("/api/v1/ai/actions/{id}/confirm", intent)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenant.toString())));
    }

    private long count(String sql, String value) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenant);
            ps.setString(2, value);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static UUID member(UUID tenantId, UUID sub, UUID role) throws SQLException {
        UUID account = AuthzTestSchema.insertMember(tenantId, sub, "aiadmin-" + sub + "@aiadmin.test");
        AuthzTestSchema.grant(tenantId, account, role);
        return account;
    }

    private static UUID session(UUID tenantId, UUID userId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.ai_session (tenant_id, user_id) VALUES (?, ?) RETURNING id")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private static void execute(String sql, Object... values) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            ps.executeUpdate();
        }
    }
}
