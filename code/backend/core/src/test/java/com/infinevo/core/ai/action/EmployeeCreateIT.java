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
import com.infinevo.shared.audit.AuditIntegratorConfig;
import com.infinevo.shared.audit.AuditWriter;
import com.infinevo.shared.logging.CorrelationIdFilter;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-114.20 spec, Tests ({@code EmployeeCreateIT}): HR proposes an employee through the chat and confirms it through
 * the shipped endpoint, against the real employee service, the plan's employee limit and PostgreSQL with row-level
 * security. The model is the scripted {@link StubAiModelClient}; the audit listener is on.
 */
@SpringBootTest(
        classes = {PermissionGuardTestApp.class, EmployeeCreateIT.ItConfig.class},
        properties = "spring.datasource.hikari.maximum-pool-size=14")
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class EmployeeCreateIT extends AbstractIntegrationTest {

    @TestConfiguration
    @Import({AuditIntegratorConfig.class, AuditWriter.class, CorrelationIdFilter.class})
    static class ItConfig {}

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AiModelClient modelClient;

    private StubAiModelClient stub;
    private UUID tenant;
    private UUID subHr;
    private UUID subEmployee;
    private UUID finance;
    private String number;

    @BeforeEach
    void setUp() throws Exception {
        stub = (StubAiModelClient) modelClient;
        stub.reset();
        TenantContext.clear();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        tenant = AuthzTestSchema.insertTenant("AiAddCo " + suffix);
        execute(
                "INSERT INTO core.subscription (id, tenant_id, status, started_on, ai_enabled, ai_monthly_request_cap,"
                        + " employee_limit) VALUES (?, ?, 'ACTIVE', CURRENT_DATE, true, 1000, 50)",
                UUID.randomUUID(),
                tenant);
        UUID hrRole = AuthzTestSchema.insertRole(
                tenant, "ai-hr-" + suffix, "HR", "core.ai.use", "core.employee.create", "core.employee.read");
        UUID employeeRole = AuthzTestSchema.insertRole(
                tenant, "ai-emp-" + suffix, "Employee", "core.ai.use", "core.leave.read_own");
        subHr = UUID.randomUUID();
        member(tenant, subHr, hrRole);
        subEmployee = UUID.randomUUID();
        UUID employee = AuthzTestSchema.insertEmployee(tenant, "E-X-" + suffix, "Ravi");
        AuthzTestSchema.linkEmployee(employee, member(tenant, subEmployee, employeeRole));
        finance = UUID.randomUUID();
        execute(
                "INSERT INTO core.department (id, tenant_id, code, name) VALUES (?, ?, 'FIN', 'Finance')",
                finance,
                tenant);
        number = "E-207-" + suffix;
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("no employee until Confirm; confirm creates one in Finance; a second confirm creates none")
    void confirm_createsOne() throws Exception {
        UUID intent = propose(subHr);
        assertThat(employees(number)).isZero();

        JsonNode first = body(confirm(intent, null).andExpect(status().isOk()));
        assertThat(first.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(first.path("result").path("employeeNumber").asText()).isEqualTo(number);
        String id = first.path("result").path("employeeId").asText();
        assertThat(scalar("SELECT department_id::text FROM core.employee WHERE id = ?::uuid", id))
                .isEqualTo(finance.toString());

        JsonNode second = body(confirm(intent, null).andExpect(status().isOk()));
        assertThat(second.path("result").path("employeeId").asText()).isEqualTo(id);
        assertThat(employees(number)).isEqualTo(1);
    }

    @Test
    @DisplayName("ten parallel confirms create one employee, and every answer names it")
    void parallelConfirms_one() throws Exception {
        UUID intent = propose(subHr);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<MvcResult>> calls = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                calls.add(() -> confirm(intent, null).andReturn());
            }
            Set<String> ids = new HashSet<>();
            for (Future<MvcResult> f : pool.invokeAll(calls, 120, TimeUnit.SECONDS)) {
                MvcResult r = f.get();
                assertThat(r.getResponse().getStatus()).isEqualTo(200);
                ids.add(json.readTree(r.getResponse().getContentAsString())
                        .path("result")
                        .path("employeeId")
                        .asText());
            }
            assertThat(ids).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(employees(number)).isEqualTo(1);
    }

    @Test
    @DisplayName("a number taken between proposal and confirm ends FAILED with the service's message")
    void numberTakenMeanwhile_failed() throws Exception {
        UUID intent = propose(subHr);
        AuthzTestSchema.insertEmployee(tenant, number, "Someone");

        confirm(intent, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ACTION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already in use")));
        assertThat(scalar("SELECT status FROM core.ai_action_intent WHERE id = ?::uuid", intent.toString()))
                .isEqualTo("FAILED");
        assertThat(employees(number)).isEqualTo(1);
    }

    @Test
    @DisplayName("at the plan limit no card; the limit reached between proposal and confirm ends FAILED")
    void limit() throws Exception {
        UUID intent = propose(subHr);
        execute("UPDATE core.subscription SET employee_limit = 1 WHERE tenant_id = ?", tenant);

        confirm(intent, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Your plan allows 1")));
        assertThat(employees(number)).isZero();

        stub.reset();
        JsonNode answer = say(subHr, toolCall());
        assertThat(answer.path("proposedAction").isMissingNode()
                        || answer.path("proposedAction").isNull())
                .isTrue();
        assertThat(toolResult()).contains("Your plan allows 1");
    }

    @Test
    @DisplayName("a user without core.employee.create is never offered the tool, and a call to it adds nobody")
    void notOffered() throws Exception {
        JsonNode answer = say(subEmployee, toolCall());

        List<String> offered = new ArrayList<>();
        for (AiToolDescriptor d : stub.requests().get(0).tools()) {
            offered.add(d.name());
        }
        assertThat(offered).doesNotContain("core.propose_employee_create");
        assertThat(answer.path("proposedAction").isMissingNode()
                        || answer.path("proposedAction").isNull())
                .isTrue();
        assertThat(employees(number)).isZero();
    }

    @Test
    @DisplayName("result.traceId equals the trace_id on the new employee's audit row")
    void traceId_linksAudit() throws Exception {
        UUID intent = propose(subHr);
        String trace = "w11420-" + UUID.randomUUID().toString().substring(0, 8);

        JsonNode answer = body(confirm(intent, trace).andExpect(status().isOk()));

        assertThat(answer.path("result").path("traceId").asText()).isEqualTo(trace);
        String id = answer.path("result").path("employeeId").asText();
        List<String> traces = new ArrayList<>();
        for (int attempt = 0; attempt < 50 && traces.isEmpty(); attempt++) {
            try (Connection conn = AuthzTestSchema.migrationConnection();
                    PreparedStatement ps = conn.prepareStatement("SELECT trace_id FROM core.audit_log"
                            + " WHERE entity_table = 'employee' AND entity_id = ?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        traces.add(rs.getString(1));
                    }
                }
            }
            if (traces.isEmpty()) {
                Thread.sleep(100);
            }
        }
        assertThat(traces).isNotEmpty().contains(trace);
    }

    // ---- helpers ----

    private AiModelResponse toolCall() throws Exception {
        return AiModelResponse.toolCall(
                "call_1",
                "core.propose_employee_create",
                json.writeValueAsString(Map.of(
                        "employeeNumber", number,
                        "firstName", "Priya",
                        "lastName", "Nair",
                        "dateOfJoining", "2026-12-01",
                        "department", "finance")));
    }

    private JsonNode say(UUID sub, AiModelResponse call) throws Exception {
        stub.script(call, AiModelResponse.text("Press Confirm."));
        UUID session = session(tenant, sub);
        return body(mvc.perform(as(
                        sub,
                        post("/api/v1/ai/sessions/{id}/messages", session)
                                .content(json.writeValueAsString(Map.of("content", "Add Priya Nair")))))
                .andExpect(status().isOk()));
    }

    private UUID propose(UUID sub) throws Exception {
        JsonNode answer = say(sub, toolCall());
        assertThat(answer.path("proposedAction").path("actionCode").asText()).isEqualTo("core.employee.create");
        return UUID.fromString(answer.path("proposedAction").path("intentId").asText());
    }

    private String toolResult() {
        var requests = stub.requests();
        for (var m : requests.get(requests.size() - 1).messages()) {
            if (m.toolCallId() != null) {
                return m.content();
            }
        }
        return "";
    }

    private ResultActions confirm(UUID intent, String trace) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/ai/actions/{id}/confirm", intent);
        if (trace != null) {
            request.header(CorrelationIdFilter.CORRELATION_ID_HEADER, trace);
        }
        return mvc.perform(as(subHr, request));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenant.toString())));
    }

    private long employees(String employeeNumber) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM core.employee WHERE tenant_id = ? AND employee_number = ?")) {
            ps.setObject(1, tenant);
            ps.setString(2, employeeNumber);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static UUID member(UUID tenantId, UUID sub, UUID role) throws SQLException {
        UUID account = AuthzTestSchema.insertMember(tenantId, sub, "aiadd-" + sub + "@aiadd.test");
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

    private static String scalar(String sql, String id) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
