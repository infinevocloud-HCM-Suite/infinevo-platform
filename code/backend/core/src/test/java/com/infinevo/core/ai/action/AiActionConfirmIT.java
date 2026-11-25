package com.infinevo.core.ai.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiModelClient;
import com.infinevo.core.ai.AiModelResponse;
import com.infinevo.core.ai.StubAiModelClient;
import com.infinevo.core.approval.ApprovalDefinitionRequest;
import com.infinevo.core.approval.ApprovalDefinitionService;
import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalStepDefinition;
import com.infinevo.core.approval.ApproverKind;
import com.infinevo.core.approval.CommentScope;
import com.infinevo.core.approval.StepOrdering;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.core.leave.ExceedBalanceMode;
import com.infinevo.core.leave.LeavePolicy;
import com.infinevo.core.leave.LeavePolicyRepository;
import com.infinevo.core.leave.LeaveTypeRequest;
import com.infinevo.core.leave.LeaveTypeResponse;
import com.infinevo.core.leave.LeaveTypeService;
import com.infinevo.core.leave.LeaveUnit;
import com.infinevo.shared.audit.AuditIntegratorConfig;
import com.infinevo.shared.audit.AuditWriter;
import com.infinevo.shared.logging.CorrelationIdFilter;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-114.8 spec, Tests: propose through the chat, then confirm, cancel and read through the shipped filter chain, the
 * real permission check, the real leave service and PostgreSQL with row-level security. The model is the scripted
 * {@link StubAiModelClient}; the audit listener is on, so the leave request's own audit row is written as in the app.
 */
@SpringBootTest(
        classes = {PermissionGuardTestApp.class, AiActionConfirmIT.ItConfig.class},
        properties = "spring.datasource.hikari.maximum-pool-size=14")
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class AiActionConfirmIT extends AbstractIntegrationTest {

    @TestConfiguration
    @Import({AuditIntegratorConfig.class, AuditWriter.class, CorrelationIdFilter.class})
    static class ItConfig {}

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AiModelClient modelClient;

    @Autowired
    private LeaveTypeService leaveTypeService;

    @Autowired
    private LeavePolicyRepository leavePolicyRepository;

    @Autowired
    private ApprovalDefinitionService definitionService;

    @Autowired
    private PlatformTransactionManager txManager;

    private StubAiModelClient stub;
    private UUID tenant;
    private UUID subA;
    private UUID subB;
    private UUID employeeA;
    private UUID sessionA;
    private UUID otherTenant;
    private UUID subOther;
    private LocalDate day;

    @BeforeEach
    void setUp() throws Exception {
        stub = (StubAiModelClient) modelClient;
        stub.reset();
        TenantContext.clear();

        tenant = AuthzTestSchema.insertTenant("AiActionCo " + UUID.randomUUID());
        subscription(tenant);
        UUID role = AuthzTestSchema.insertRole(
                tenant,
                "ai-leave-" + UUID.randomUUID().toString().substring(0, 8),
                "AI leave",
                "core.ai.use",
                "core.leave.apply");
        subA = UUID.randomUUID();
        employeeA =
                AuthzTestSchema.insertEmployee(tenant, "E-A-" + subA.toString().substring(0, 6), "Asha");
        AuthzTestSchema.linkEmployee(employeeA, member(tenant, subA, role));
        subB = UUID.randomUUID();
        UUID employeeB =
                AuthzTestSchema.insertEmployee(tenant, "E-B-" + subB.toString().substring(0, 6), "Bala");
        AuthzTestSchema.linkEmployee(employeeB, member(tenant, subB, role));

        otherTenant = AuthzTestSchema.insertTenant("AiActionOther " + UUID.randomUUID());
        subscription(otherTenant);
        UUID otherRole = AuthzTestSchema.insertRole(
                otherTenant, "ai-other-" + UUID.randomUUID().toString().substring(0, 8), "AI", "core.ai.use");
        subOther = UUID.randomUUID();
        UUID employeeOther = AuthzTestSchema.insertEmployee(
                otherTenant, "E-O-" + subOther.toString().substring(0, 6), "Omar");
        AuthzTestSchema.linkEmployee(employeeOther, member(otherTenant, subOther, otherRole));

        seedLeave(tenant, employeeB);
        sessionA = session(tenant, subA);
        day = nextWeekday(LocalDate.now().plusDays(7));
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "propose writes PROPOSED and no leave; confirm creates one PENDING leave; confirm again: same id, one row")
    void confirm_createsOneLeave() throws Exception {
        UUID intent = propose(day, day);
        assertThat(leaveRows(employeeA)).isZero();
        assertThat(intentColumn(intent, "status")).isEqualTo("PROPOSED");

        JsonNode first = body(confirm(subA, intent, null).andExpect(status().isOk()));
        assertThat(first.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(first.path("result").path("status").asText()).isEqualTo("PENDING");
        String leaveId = first.path("result").path("leaveRequestId").asText();
        assertThat(leaveId).isNotBlank();

        JsonNode second = body(confirm(subA, intent, null).andExpect(status().isOk()));
        assertThat(second.path("result").path("leaveRequestId").asText()).isEqualTo(leaveId);
        assertThat(leaveRows(employeeA)).isEqualTo(1);
        assertThat(leaveColumn(UUID.fromString(leaveId), "status")).isEqualTo("PENDING");
        assertThat(leaveColumn(UUID.fromString(leaveId), "employee_id")).isEqualTo(employeeA.toString());
    }

    @Test
    @DisplayName("ten parallel confirms create one leave request, and every answer names it")
    void parallelConfirms_oneLeave() throws Exception {
        UUID intent = propose(day, day);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<MvcResult>> calls = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                calls.add(() -> confirm(subA, intent, null).andReturn());
            }
            List<String> ids = new ArrayList<>();
            for (Future<MvcResult> f : pool.invokeAll(calls, 120, TimeUnit.SECONDS)) {
                MvcResult r = f.get();
                assertThat(r.getResponse().getStatus()).isEqualTo(200);
                ids.add(json.readTree(r.getResponse().getContentAsString())
                        .path("result")
                        .path("leaveRequestId")
                        .asText());
            }
            assertThat(ids).hasSize(10).doesNotContain("");
            assertThat(ids.stream().distinct()).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(leaveRows(employeeA)).isEqualTo(1);
    }

    @Test
    @DisplayName("tenant B gets 404 for tenant A's intent, and so does another user of tenant A; nothing is created")
    void otherTenantAndUser_notFound() throws Exception {
        UUID intent = propose(day, day);

        mvc.perform(as(otherTenant, subOther, post("/api/v1/ai/actions/{id}/confirm", intent)))
                .andExpect(status().isNotFound());
        confirm(subB, intent, null).andExpect(status().isNotFound());
        mvc.perform(as(tenant, subB, get("/api/v1/ai/actions/{id}", intent))).andExpect(status().isNotFound());

        assertThat(leaveRows(employeeA)).isZero();
        assertThat(intentColumn(intent, "status")).isEqualTo("PROPOSED");
    }

    @Test
    @DisplayName("result.traceId equals the trace_id on the leave request's audit rows and on the confirm row")
    void traceId_linksAudit() throws Exception {
        UUID intent = propose(day, day);
        String trace = "w1148-" + UUID.randomUUID().toString().substring(0, 8);

        JsonNode answer = body(confirm(subA, intent, trace).andExpect(status().isOk()));

        assertThat(answer.path("result").path("traceId").asText()).isEqualTo(trace);
        String leaveId = answer.path("result").path("leaveRequestId").asText();
        List<String> leaveTraces = auditTraces("leave_request", leaveId);
        assertThat(leaveTraces).isNotEmpty().containsOnly(trace);
        List<String[]> intentRows = auditRows(intent);
        assertThat(intentRows).extracting(r -> r[0]).containsExactly("INSERT", "UPDATE");
        assertThat(intentRows.get(1)[1]).isEqualTo(trace);
    }

    @Test
    @DisplayName("C8: confirm with a body naming other dates creates the leave for the stored dates")
    void bodyIgnored() throws Exception {
        UUID intent = propose(day, day);
        LocalDate other = day.plusDays(14);
        String body = "{\"fromDate\":\"" + other + "\",\"toDate\":\"" + other + "\",\"leaveTypeId\":\""
                + UUID.randomUUID() + "\"}";

        JsonNode answer = body(mvc.perform(as(tenant, subA, post("/api/v1/ai/actions/{id}/confirm", intent))
                        .content(body))
                .andExpect(status().isOk()));

        UUID leaveId =
                UUID.fromString(answer.path("result").path("leaveRequestId").asText());
        assertThat(leaveColumn(leaveId, "from_date")).isEqualTo(day.toString());
        assertThat(leaveColumn(leaveId, "to_date")).isEqualTo(day.toString());
    }

    @Test
    @DisplayName("C3: after the chat session is deleted, confirm answers 404 and no leave is created")
    void sessionDeleted_notFound() throws Exception {
        UUID intent = propose(day, day);
        execute("DELETE FROM core.ai_session WHERE id = ?", sessionA);

        confirm(subA, intent, null).andExpect(status().isNotFound());
        assertThat(leaveRows(employeeA)).isZero();
    }

    @Test
    @DisplayName("C5: past its time: 410 AI_ACTION_EXPIRED, EXPIRED; a later confirm still runs nothing")
    void expired_gone() throws Exception {
        UUID intent = propose(day, day);
        execute("UPDATE core.ai_action_intent SET expires_at = now() - interval '1 minute' WHERE id = ?", intent);

        confirm(subA, intent, null)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("AI_ACTION_EXPIRED"));
        assertThat(intentColumn(intent, "status")).isEqualTo("EXPIRED");
        confirm(subA, intent, null).andExpect(status().isGone());
        assertThat(leaveRows(employeeA)).isZero();
    }

    @Test
    @DisplayName("C10: cancel then read: CANCELLED, 409 on confirm; audit_log holds the INSERT and the UPDATE rows")
    void cancel_audited() throws Exception {
        UUID intent = propose(day, day);

        mvc.perform(as(tenant, subA, post("/api/v1/ai/actions/{id}/cancel", intent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(as(tenant, subA, post("/api/v1/ai/actions/{id}/cancel", intent)))
                .andExpect(status().isOk());
        confirm(subA, intent, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ACTION_CANCELLED"));
        mvc.perform(as(tenant, subA, get("/api/v1/ai/actions/{id}", intent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("Casual Leave")))
                .andExpect(jsonPath("$.fields[0].value").value("Casual Leave"));

        List<String[]> rows = auditRows(intent);
        assertThat(rows).extracting(r -> r[0]).containsExactly("INSERT", "UPDATE");
        assertThat(rows.get(1)[2]).contains("CANCELLED").doesNotContain("Family");
        assertThat(leaveRows(employeeA)).isZero();
    }

    @Test
    @DisplayName("a refusal at confirm (an overlapping leave made meanwhile): 409 AI_ACTION_FAILED, FAILED, one leave")
    void refusedAtConfirm_failed() throws Exception {
        UUID first = propose(day, day);
        stub.reset();
        UUID second = propose(day, day);
        confirm(subA, first, null).andExpect(status().isOk());

        confirm(subA, second, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ACTION_FAILED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("overlaps")));
        assertThat(intentColumn(second, "status")).isEqualTo("FAILED");
        assertThat(leaveRows(employeeA)).isEqualTo(1);
    }

    @Test
    @DisplayName("the chat answer carries proposedAction; the messages list links it by intentId")
    void chatAnswerCarriesCard() throws Exception {
        UUID intent = propose(day, day);

        mvc.perform(as(tenant, subA, get("/api/v1/ai/sessions/{id}/messages", sessionA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].proposedAction.intentId").value(intent.toString()));
    }

    // ---- helpers ----

    /** Asks the helper to apply Casual Leave; the scripted model calls the tool, then answers. Returns the intent. */
    private UUID propose(LocalDate from, LocalDate to) throws Exception {
        String args = json.writeValueAsString(Map.of(
                "leaveType", "casual leave", "fromDate", from.toString(), "toDate", to.toString(), "reason", "Family"));
        stub.script(
                AiModelResponse.toolCall("call_1", "core.propose_leave_request", args),
                AiModelResponse.text("Shall I apply it? Press Confirm."));
        JsonNode answer = body(mvc.perform(as(
                        tenant,
                        subA,
                        post("/api/v1/ai/sessions/{id}/messages", sessionA)
                                .content(json.writeValueAsString(Map.of("content", "Apply casual leave")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedAction.status").value("PROPOSED"))
                .andExpect(jsonPath("$.proposedAction.actionCode").value("core.leave.apply")));
        return UUID.fromString(answer.path("proposedAction").path("intentId").asText());
    }

    private ResultActions confirm(UUID sub, UUID intent, String trace) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/ai/actions/{id}/confirm", intent);
        if (trace != null) {
            request.header(CorrelationIdFilter.CORRELATION_ID_HEADER, trace);
        }
        return mvc.perform(as(tenant, sub, request));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder as(UUID tenantId, UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenantId.toString())));
    }

    /** Casual Leave with a no-limit policy, and a one-step approval by {@code approver}, written as the app writes them. */
    private void seedLeave(UUID tenantId, UUID approver) {
        TenantContext.set(tenantId);
        try {
            new TransactionTemplate(txManager).executeWithoutResult(s -> {
                definitionService.saveDefinition(
                        tenantId,
                        ApprovalFlowType.LEAVE,
                        new ApprovalDefinitionRequest(
                                StepOrdering.SEQUENTIAL,
                                CommentScope.PER_STEP,
                                LocalDate.of(2024, 1, 1),
                                List.of(new ApprovalStepDefinition(
                                        ApproverKind.NAMED_EMPLOYEE, approver.toString(), 3, false))));
                LeaveTypeResponse type = leaveTypeService.createLeaveType(
                        tenantId,
                        new LeaveTypeRequest(
                                "Casual Leave", "CL", true, LeaveUnit.DAYS, true, LocalDate.of(2024, 1, 1), null));
                LeavePolicy policy = new LeavePolicy();
                policy.setTenantId(tenantId);
                policy.setLeaveTypeId(type.id());
                policy.setAnnualDays(new BigDecimal("12.00"));
                policy.setEffectiveFrom(LocalDate.of(2024, 1, 1));
                policy.setExceedBalanceMode(ExceedBalanceMode.NO_LIMIT);
                leavePolicyRepository.save(policy);
            });
        } finally {
            TenantContext.clear();
        }
    }

    private static LocalDate nextWeekday(LocalDate from) {
        LocalDate d = from;
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    private static UUID member(UUID tenantId, UUID sub, UUID role) throws SQLException {
        UUID account = AuthzTestSchema.insertMember(tenantId, sub, "aiact-" + sub + "@aiact.test");
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

    private static void execute(String sql, UUID id) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }

    private static long leaveRows(UUID employeeId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core.leave_request WHERE employee_id = ?")) {
            ps.setObject(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static String leaveColumn(UUID id, String column) throws SQLException {
        return scalar("SELECT " + column + "::text FROM core.leave_request WHERE id = ?", id);
    }

    private static String intentColumn(UUID id, String column) throws SQLException {
        return scalar("SELECT " + column + "::text FROM core.ai_action_intent WHERE id = ?", id);
    }

    private static String scalar(String sql, UUID id) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * The audit rows of a row, oldest first. Written after commit in a transaction of their own, so a test reading
     * right after the response may need a moment; this waits up to five seconds for at least one.
     */
    private static List<String> auditTraces(String table, String entityId) throws Exception {
        List<String> traces = new ArrayList<>();
        for (int attempt = 0; attempt < 50 && traces.isEmpty(); attempt++) {
            try (Connection conn = AuthzTestSchema.migrationConnection();
                    PreparedStatement ps = conn.prepareStatement("SELECT trace_id FROM core.audit_log"
                            + " WHERE entity_table = ? AND entity_id = ? ORDER BY occurred_at")) {
                ps.setString(1, table);
                ps.setString(2, entityId);
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
        return traces;
    }

    /** {@code [operation, trace_id, new_values]} of the intent's audit rows, oldest first. */
    private static List<String[]> auditRows(UUID intent) throws SQLException {
        List<String[]> rows = new ArrayList<>();
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT operation, trace_id, new_values::text,"
                        + " old_values::text FROM core.audit_log WHERE entity_table = 'ai_action_intent'"
                        + " AND entity_id = ? ORDER BY occurred_at")) {
            ps.setString(1, intent.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new String[] {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)});
                }
            }
        }
        for (String[] row : rows) {
            assertThat(row[2]).doesNotContain("payload").doesNotContain("leaveTypeId");
        }
        return rows;
    }
}
