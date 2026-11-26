package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-114.15 spec, Tests: {@code core.explain_leave_balance} through the chat, the shipped filter chain, the real
 * permission check, the real balance service and PostgreSQL with row-level security. The model is the scripted
 * {@link StubAiModelClient}. Lives in {@code core} beside {@code AiActionConfirmIT}, which seeds the same way.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class LeaveBalanceToolIT extends AbstractIntegrationTest {

    private static final String AS_OF = "2026-06-15";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AiModelClient modelClient;

    private StubAiModelClient stub;
    private UUID tenant;
    private UUID subA;
    private UUID subNoLeave;
    private UUID sessionA;
    private UUID sessionNoLeave;

    @BeforeEach
    void setUp() throws Exception {
        stub = (StubAiModelClient) modelClient;
        stub.reset();
        TenantContext.clear();

        tenant = AuthzTestSchema.insertTenant("AiLeaveBalCo " + UUID.randomUUID());
        subscription(tenant);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID role = AuthzTestSchema.insertRole(
                tenant, "ai-bal-" + suffix, "AI balance", "core.ai.use", "core.leave.read_own");
        UUID aiOnly = AuthzTestSchema.insertRole(tenant, "ai-only-" + suffix, "AI only", "core.ai.use");

        subA = UUID.randomUUID();
        UUID employeeA = AuthzTestSchema.insertEmployee(tenant, "E-A-" + suffix, "Asha");
        AuthzTestSchema.linkEmployee(employeeA, member(tenant, subA, role));
        UUID subB = UUID.randomUUID();
        UUID employeeB = AuthzTestSchema.insertEmployee(tenant, "E-B-" + suffix, "Bala");
        AuthzTestSchema.linkEmployee(employeeB, member(tenant, subB, role));
        subNoLeave = UUID.randomUUID();
        UUID employeeC = AuthzTestSchema.insertEmployee(tenant, "E-C-" + suffix, "Chitra");
        AuthzTestSchema.linkEmployee(employeeC, member(tenant, subNoLeave, aiOnly));

        try (Connection conn = AuthzTestSchema.migrationConnection()) {
            UUID cl = leaveType(conn, tenant, "CL", "Casual Leave");
            UUID policy = policy(conn, tenant, cl);
            // A: 12 granted, 3 carried that ran out on 31 March, 2 days taken in May (after the expiry).
            UUID allocA =
                    allocation(conn, tenant, employeeA, cl, policy, new BigDecimal("12.00"), new BigDecimal("3.00"));
            consumption(conn, tenant, employeeA, allocA, new BigDecimal("2.00"), LocalDate.of(2026, 5, 11));
            // B: a figure A must never see.
            allocation(conn, tenant, employeeB, cl, policy, new BigDecimal("17.00"), BigDecimal.ZERO);
        }
        sessionA = AiChatRows.session(tenant, subA);
        sessionNoLeave = AiChatRows.session(tenant, subNoLeave);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A gets A's figures, equal to GET /me/leave-balances; B's never appear; carry-forward ran out: 0.00")
    void ownFigures_matchTheScreen() throws Exception {
        JsonNode result = ask(subA, sessionA, Map.of("leaveType", "cl", "asOf", AS_OF));

        JsonNode screen = body(mvc.perform(
                                as(subA, get("/api/v1/me/leave-balances").param("asOf", AS_OF)))
                        .andExpect(status().isOk()))
                .get(0);
        JsonNode cl = result.path("balances").get(0);
        assertThat(result.path("balances")).hasSize(1);
        assertThat(cl.path("leaveType").path("code").asText()).isEqualTo("CL");
        assertThat(cl.path("granted").asText()).isEqualTo(two(screen, "entitlementDays"));
        assertThat(cl.path("used").asText()).isEqualTo(two(screen, "consumedDays"));
        assertThat(cl.path("balance").asText()).isEqualTo(two(screen, "remainingDays"));
        assertThat(cl.path("available").asText()).isEqualTo(two(screen, "availableDays"));
        assertThat(cl.path("carriedForward").asText()).isEqualTo("0.00").isEqualTo(two(screen, "carriedForwardDays"));
        assertThat(cl.path("carryForwardExpiresOn").asText()).isEqualTo("2026-03-31");
        assertThat(cl.path("carryForwardExpired").asBoolean()).isTrue();
        assertThat(cl.path("leaveTypeEnded").asBoolean()).isFalse();
        assertThat(cl.path("yearStart").asText()).isEqualTo("2026-01-01");
        assertThat(cl.path("yearEnd").asText()).isEqualTo("2026-12-31");
        assertThat(cl.path("howItAddsUp").asText())
                .isEqualTo("balance 10.00 = granted 12.00 + accrued 0.00 + carried forward 0.00 - used 2.00;"
                        + " available 10.00 = balance 10.00 - pending 0.00, never below zero");

        String stored = toolRows(sessionA).get(0);
        assertThat(stored).doesNotContain("17.00").doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}-");
    }

    @Test
    @DisplayName("a leave type A has no allocation for: the plain sentence and A's own types")
    void unknownType() throws Exception {
        JsonNode result = ask(subA, sessionA, Map.of("leaveType", "Maternity", "asOf", AS_OF));
        assertThat(result.path("message").asText()).isEqualTo(LeaveBalanceAiToolProvider.NO_SUCH_TYPE);
        assertThat(result.path("yourLeaveTypes").findValuesAsText("code")).containsExactly("CL");
    }

    @Test
    @DisplayName("a login without core.leave.read_own is not offered the tool")
    void notOffered_withoutReadOwn() throws Exception {
        stub.recording().script(AiModelResponse.text("ok"));
        mvc.perform(as(
                        subNoLeave,
                        post("/api/v1/ai/sessions/{id}/messages", sessionNoLeave)
                                .content(json.writeValueAsString(Map.of("content", "Why is my leave 10?")))))
                .andExpect(status().isOk());
        assertThat(stub.requests()).isNotEmpty();
        assertThat(stub.requests().get(0).tools())
                .extracting(AiToolDescriptor::name)
                .doesNotContain(LeaveBalanceAiToolProvider.NAME);
    }

    // ---- helpers ----

    /** The scripted model calls the tool once with {@code args}; returns the stored TOOL row's result. */
    private JsonNode ask(UUID sub, UUID session, Map<String, String> args) throws Exception {
        stub.script(
                AiModelResponse.toolCall("call_1", LeaveBalanceAiToolProvider.NAME, json.writeValueAsString(args)),
                AiModelResponse.text("Here is how it adds up."));
        mvc.perform(as(
                        sub,
                        post("/api/v1/ai/sessions/{id}/messages", session)
                                .content(json.writeValueAsString(
                                        Map.of("content", "Why is my casual leave what it is?")))))
                .andExpect(status().isOk());
        List<String> rows = toolRows(session);
        assertThat(rows).hasSize(1);
        JsonNode stored = json.readTree(rows.get(0));
        return stored.has("data") ? stored.path("data") : stored;
    }

    private static String two(JsonNode node, String field) {
        return new BigDecimal(node.path(field).asText()).setScale(2).toPlainString();
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenant.toString())));
    }

    private static List<String> toolRows(UUID session) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT content FROM core.ai_message"
                        + " WHERE session_id = ? AND role = 'TOOL' ORDER BY created_at")) {
            ps.setObject(1, session);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    private static UUID member(UUID tenantId, UUID sub, UUID role) throws SQLException {
        UUID account = AuthzTestSchema.insertMember(tenantId, sub, "aibal-" + sub + "@aibal.test");
        AuthzTestSchema.grant(tenantId, account, role);
        return account;
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

    private static UUID leaveType(Connection conn, UUID tenantId, String code, String name) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.leave_type (id, tenant_id, code, name,"
                + " is_paid, unit, allow_half_day, valid_from, is_active)"
                + " VALUES (?, ?, ?, ?, true, 'DAYS', true, '2024-01-01', true)")) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, name);
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID policy(Connection conn, UUID tenantId, UUID leaveTypeId) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.leave_policy (id, tenant_id,"
                + " leave_type_id, annual_days, exceed_balance_mode, effective_from)"
                + " VALUES (?, ?, ?, 12.00, 'noLimit', '2024-01-01')")) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, leaveTypeId);
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID allocation(
            Connection conn,
            UUID tenantId,
            UUID employeeId,
            UUID leaveTypeId,
            UUID policyId,
            BigDecimal granted,
            BigDecimal carried)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.leave_allocation (id, tenant_id,"
                + " employee_id, leave_type_id, policy_id, leave_year, year_start_date, year_end_date,"
                + " entitlement_days, pro_rate_factor, carried_forward_days, carry_forward_expires_on)"
                + " VALUES (?, ?, ?, ?, ?, '2026', '2026-01-01', '2026-12-31', ?, 1.0, ?, '2026-03-31')")) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, employeeId);
            ps.setObject(4, leaveTypeId);
            ps.setObject(5, policyId);
            ps.setBigDecimal(6, granted);
            ps.setBigDecimal(7, carried);
            ps.executeUpdate();
        }
        return id;
    }

    private static void consumption(
            Connection conn, UUID tenantId, UUID employeeId, UUID allocationId, BigDecimal days, LocalDate on)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.leave_consumption (tenant_id,"
                + " employee_id, allocation_id, consumed_days, consumed_on, period) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            ps.setObject(3, allocationId);
            ps.setBigDecimal(4, days);
            ps.setObject(5, on);
            ps.setString(6, on.toString().substring(0, 7));
            ps.executeUpdate();
        }
    }
}
