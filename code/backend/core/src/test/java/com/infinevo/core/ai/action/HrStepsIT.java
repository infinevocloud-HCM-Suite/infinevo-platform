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
 * W-114.29 spec, Tests ({@code HrStepsIT}): HR proposes a leave allocation, a WFH allocation, a delegation of their
 * own approvals and an onboarding checklist through the chat and confirms each through the shipped endpoint, against
 * the real services and PostgreSQL with row-level security. The model is the scripted {@link StubAiModelClient}.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class HrStepsIT extends AbstractIntegrationTest {

    private static final List<String> TOOLS = List.of(
            "core.propose_leave_allocation",
            "core.propose_wfh_allocation",
            "core.propose_delegation",
            "core.propose_onboarding_assign");

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
    private UUID subHr;
    private UUID subEmployee;
    private UUID subManager;
    private UUID mohan;
    private String otherNumber;
    private UUID hrEmployee;
    private UUID asha;
    private String ashaNumber;
    private UUID leaveType;
    private UUID leavePolicy;

    @BeforeEach
    void setUp() throws Exception {
        stub = (StubAiModelClient) modelClient;
        stub.reset();
        TenantContext.clear();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        tenant = AuthzTestSchema.insertTenant("AiHrCo " + suffix);
        execute(
                "INSERT INTO core.subscription (id, tenant_id, status, started_on, ai_enabled, ai_monthly_request_cap)"
                        + " VALUES (?, ?, 'ACTIVE', CURRENT_DATE, true, 1000)",
                UUID.randomUUID(),
                tenant);
        UUID hrRole = AuthzTestSchema.insertRole(
                tenant,
                "ai-hr-" + suffix,
                "HR " + suffix,
                "core.ai.use",
                "core.leave_balance.manage",
                "core.wfh_balance.manage",
                "core.approval.delegate",
                "core.onboarding.manage",
                "core.employee.read");
        // As production's manager role (V176, V022): delegate, but team read only, so no employee search.
        UUID managerRole = AuthzTestSchema.insertRole(
                tenant,
                "ai-mgr-" + suffix,
                "Manager " + suffix,
                "core.ai.use",
                "core.approval.delegate",
                "core.employee.read_team");
        subManager = UUID.randomUUID();
        mohan = AuthzTestSchema.insertEmployee(tenant, "E-M-" + suffix, "Mohan");
        AuthzTestSchema.linkEmployee(mohan, member(tenant, subManager, managerRole));
        // Another company with an employee: never found from this one.
        UUID otherTenant = AuthzTestSchema.insertTenant("AiHrOther " + suffix);
        otherNumber = "E-O-" + suffix;
        AuthzTestSchema.insertEmployee(otherTenant, otherNumber, "Olga");
        UUID employeeRole = AuthzTestSchema.insertRole(tenant, "ai-emp-" + suffix, "Staff " + suffix, "core.ai.use");
        subHr = UUID.randomUUID();
        hrEmployee = AuthzTestSchema.insertEmployee(tenant, "E-H-" + suffix, "Hema");
        AuthzTestSchema.linkEmployee(hrEmployee, member(tenant, subHr, hrRole));
        subEmployee = UUID.randomUUID();
        UUID ravi = AuthzTestSchema.insertEmployee(tenant, "E-R-" + suffix, "Ravi");
        AuthzTestSchema.linkEmployee(ravi, member(tenant, subEmployee, employeeRole));
        ashaNumber = "E-A-" + suffix;
        asha = AuthzTestSchema.insertEmployee(tenant, ashaNumber, "Asha");
        leaveType = UUID.randomUUID();
        execute(
                "INSERT INTO core.leave_type (id, tenant_id, code, name, is_paid, unit, allow_half_day, valid_from,"
                        + " is_active) VALUES (?, ?, 'CL', 'Casual Leave', true, 'DAYS', true, '2024-01-01', true)",
                leaveType,
                tenant);
        leavePolicy = UUID.randomUUID();
        execute(
                "INSERT INTO core.leave_policy (id, tenant_id, leave_type_id, annual_days, exceed_balance_mode,"
                        + " effective_from) VALUES (?, ?, ?, 12.00, 'noLimit', '2024-01-01')",
                leavePolicy,
                tenant,
                leaveType);
        execute(
                "INSERT INTO core.onboarding_task_template (id, tenant_id, title, task_type, assigned_role,"
                        + " is_mandatory, due_days_after_joining, sort_order, is_active)"
                        + " VALUES (?, ?, 'Laptop handover', 'MANUAL_TASK', 'EMPLOYEE', true, 3, 1, true)",
                UUID.randomUUID(),
                tenant);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    private static final String LEAVE_ROWS =
            "SELECT count(*) FROM core.leave_allocation WHERE tenant_id = ? AND employee_id::text = ?";
    private static final String WFH_ROWS =
            "SELECT count(*) FROM core.wfh_allocation WHERE tenant_id = ? AND employee_id::text = ?";

    @Test
    @DisplayName("a leave allocation: none until Confirm; Confirm makes one in the April leave year; a second none")
    void leaveAllocation() throws Exception {
        UUID intent = propose(
                "core.propose_leave_allocation",
                Map.of("employee", ashaNumber, "leaveType", "Casual Leave", "year", 2026, "days", 7.5));
        assertThat(count(LEAVE_ROWS, asha.toString())).isZero();

        JsonNode first = body(confirm(intent).andExpect(status().isOk()));
        assertThat(first.path("status").asText()).isEqualTo("CONFIRMED");
        body(confirm(intent).andExpect(status().isOk()));
        assertThat(count(LEAVE_ROWS, asha.toString())).isEqualTo(1);
        assertThat(count(
                        "SELECT count(*) FROM core.leave_allocation WHERE tenant_id = ? AND employee_id::text = ?"
                                + " AND year_start_date = '2026-04-01' AND year_end_date = '2027-03-31'"
                                + " AND entitlement_days = 7.50 AND leave_year = '2026'",
                        asha.toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a leave allocation made between proposal and Confirm ends FAILED, and no second row is made")
    void leaveAllocation_overlapMeanwhile() throws Exception {
        UUID intent = propose(
                "core.propose_leave_allocation",
                Map.of("employee", ashaNumber, "leaveType", "CL", "year", 2026, "days", 12));
        execute(
                "INSERT INTO core.leave_allocation (tenant_id, employee_id, leave_type_id, policy_id, leave_year,"
                        + " year_start_date, year_end_date, entitlement_days, pro_rate_factor, carried_forward_days)"
                        + " VALUES (?, ?, ?, ?, '2026', '2026-04-01', '2027-03-31', 12.00, 1.0, 0)",
                tenant,
                asha,
                leaveType,
                leavePolicy);

        confirm(intent)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ACTION_FAILED"));
        assertThat(count(LEAVE_ROWS, asha.toString())).isEqualTo(1);
    }

    @Test
    @DisplayName("a WFH allocation: none until Confirm; Confirm makes one for the calendar year; a second none")
    void wfhAllocation() throws Exception {
        UUID intent = propose("core.propose_wfh_allocation", Map.of("employee", ashaNumber, "year", 2026, "days", 12));
        assertThat(count(WFH_ROWS, asha.toString())).isZero();

        body(confirm(intent).andExpect(status().isOk()));
        body(confirm(intent).andExpect(status().isOk()));
        assertThat(count(WFH_ROWS, asha.toString())).isEqualTo(1);
        assertThat(count(
                        WFH_ROWS + " AND wfh_year = '2026' AND year_start_date = '2026-01-01'"
                                + " AND year_end_date = '2026-12-31' AND pro_rate_factor = 1",
                        asha.toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a WFH allocation made between proposal and Confirm ends FAILED, and no second row is made")
    void wfhAllocation_overlapMeanwhile() throws Exception {
        UUID intent = propose("core.propose_wfh_allocation", Map.of("employee", ashaNumber, "year", 2026, "days", 12));
        UUID policy = UUID.randomUUID();
        execute(
                "INSERT INTO core.wfh_policy (id, tenant_id, code, name, effective_from)"
                        + " VALUES (?, ?, 'WFH', 'Remote', '2026-01-01')",
                policy,
                tenant);
        execute(
                "INSERT INTO core.wfh_allocation (tenant_id, employee_id, policy_id, wfh_year, year_start_date,"
                        + " year_end_date) VALUES (?, ?, ?, '2026-H2', '2026-07-01', '2026-12-31')",
                tenant,
                asha,
                policy);

        confirm(intent)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ACTION_FAILED"));
        assertThat(count(WFH_ROWS, asha.toString())).isEqualTo(1);
    }

    @Test
    @DisplayName("a delegation of the caller's approvals: none until Confirm; Confirm makes one; a second none")
    void delegation() throws Exception {
        UUID intent = propose(
                "core.propose_delegation",
                Map.of(
                        "delegate",
                        ashaNumber,
                        "from",
                        "2026-12-01",
                        "to",
                        "2026-12-10",
                        "flowTypes",
                        List.of("Leave", "Timesheet")));
        String sql = "SELECT count(*) FROM core.approval_delegation WHERE tenant_id = ? AND delegator_employee_id::text"
                + " = ?";
        assertThat(count(sql, hrEmployee.toString())).isZero();

        body(confirm(intent).andExpect(status().isOk()));
        body(confirm(intent).andExpect(status().isOk()));
        assertThat(count(sql, hrEmployee.toString())).isEqualTo(1);
        assertThat(count(
                        sql + " AND delegate_employee_id = '" + asha + "' AND flow_types = 'LEAVE,TIMESHEET'"
                                + " AND effective_from = '2026-12-01' AND effective_to = '2026-12-10'",
                        hrEmployee.toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("an onboarding checklist: no tasks until Confirm; Confirm assigns them; a second assigns none")
    void onboarding() throws Exception {
        UUID intent = propose("core.propose_onboarding_assign", Map.of("employee", ashaNumber));
        String sql = "SELECT count(*) FROM core.employee_onboarding_task WHERE tenant_id = ? AND employee_id::text = ?";
        assertThat(count(sql, asha.toString())).isZero();
        long checklist = count(
                "SELECT count(*) FROM core.onboarding_task_template WHERE tenant_id = ? AND is_active = ?::boolean",
                "true");

        body(confirm(intent).andExpect(status().isOk()));
        body(confirm(intent).andExpect(status().isOk()));
        assertThat(checklist).isPositive();
        assertThat(count(sql, asha.toString())).isEqualTo(checklist);
    }

    @Test
    @DisplayName("a user without the actions is never offered the four tools, and a call to one makes nothing")
    void notOffered() throws Exception {
        JsonNode answer = say(
                subEmployee,
                AiModelResponse.toolCall(
                        "call_1",
                        "core.propose_leave_allocation",
                        json.writeValueAsString(
                                Map.of("employee", ashaNumber, "leaveType", "CL", "year", 2026, "days", 1))));

        List<String> offered = new ArrayList<>();
        for (AiToolDescriptor d : stub.requests().get(0).tools()) {
            offered.add(d.name());
        }
        assertThat(offered).doesNotContainAnyElementsOf(TOOLS);
        assertThat(answer.path("proposedAction").isMissingNode()
                        || answer.path("proposedAction").isNull())
                .isTrue();
        assertThat(count(LEAVE_ROWS, asha.toString())).isZero();
    }

    @Test
    @DisplayName("a manager (delegate, no employee search) gets one refusal whoever is named, and no card")
    void delegation_managerCannotSearch() throws Exception {
        for (String who : List.of(ashaNumber, "E-NOBODY")) {
            stub.reset();
            JsonNode answer = say(
                    subManager,
                    AiModelResponse.toolCall(
                            "call_1",
                            "core.propose_delegation",
                            json.writeValueAsString(Map.of(
                                    "delegate",
                                    who,
                                    "from",
                                    "2026-12-01",
                                    "to",
                                    "2026-12-10",
                                    "flowTypes",
                                    List.of("Leave")))));
            assertThat(answer.path("proposedAction").isMissingNode()
                            || answer.path("proposedAction").isNull())
                    .isTrue();
            assertThat(lastText()).contains(NamedEmployee.NO_SEARCH).doesNotContain("Asha");
        }
        assertThat(count(
                        "SELECT count(*) FROM core.approval_delegation WHERE tenant_id = ?"
                                + " AND delegator_employee_id::text = ?",
                        mohan.toString()))
                .isZero();
    }

    @Test
    @DisplayName("another company's employee number answers like an unknown one, and no card")
    void otherTenant_notFound() throws Exception {
        JsonNode answer = say(
                subHr,
                AiModelResponse.toolCall(
                        "call_1",
                        "core.propose_onboarding_assign",
                        json.writeValueAsString(Map.of("employee", otherNumber))));
        assertThat(answer.path("proposedAction").isMissingNode()
                        || answer.path("proposedAction").isNull())
                .isTrue();
        assertThat(lastText()).contains(NamedEmployee.NO_EMPLOYEE).doesNotContain("Olga");
    }

    // ---- helpers ----

    /** What the tool answered: the last message of the model's second request. */
    private String lastText() {
        var messages = stub.requests().get(1).messages();
        return String.valueOf(messages.get(messages.size() - 1).content());
    }

    private JsonNode say(UUID sub, AiModelResponse call) throws Exception {
        stub.script(call, AiModelResponse.text("Press Confirm."));
        UUID session = session(tenant, sub);
        return body(mvc.perform(as(
                        sub,
                        post("/api/v1/ai/sessions/{id}/messages", session)
                                .content(json.writeValueAsString(Map.of("content", "Do this")))))
                .andExpect(status().isOk()));
    }

    private UUID propose(String tool, Map<String, Object> input) throws Exception {
        JsonNode answer = say(subHr, AiModelResponse.toolCall("call_1", tool, json.writeValueAsString(input)));
        assertThat(answer.path("proposedAction").path("intentId").asText())
                .as(answer.toString())
                .isNotBlank();
        return UUID.fromString(answer.path("proposedAction").path("intentId").asText());
    }

    private ResultActions confirm(UUID intent) throws Exception {
        return mvc.perform(as(subHr, post("/api/v1/ai/actions/{id}/confirm", intent)));
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
        UUID account = AuthzTestSchema.insertMember(tenantId, sub, "aihr-" + sub + "@aihr.test");
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
