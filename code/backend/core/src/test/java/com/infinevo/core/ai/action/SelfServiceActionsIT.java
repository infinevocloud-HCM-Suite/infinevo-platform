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
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.ApprovalStepDefinition;
import com.infinevo.core.approval.ApproverKind;
import com.infinevo.core.approval.CommentScope;
import com.infinevo.core.approval.StepOrdering;
import com.infinevo.core.approval.SubjectRef;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.core.leave.ExceedBalanceMode;
import com.infinevo.core.leave.LeavePolicy;
import com.infinevo.core.leave.LeavePolicyRepository;
import com.infinevo.core.leave.LeaveTypeRequest;
import com.infinevo.core.leave.LeaveTypeResponse;
import com.infinevo.core.leave.LeaveTypeService;
import com.infinevo.core.leave.LeaveUnit;
import com.infinevo.core.wfh.CreateWfhAllocationRequest;
import com.infinevo.core.wfh.CreateWfhPolicyRequest;
import com.infinevo.core.wfh.WfhAllocationService;
import com.infinevo.core.wfh.WfhPolicyResponse;
import com.infinevo.core.wfh.WfhPolicyService;
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
import java.time.ZoneOffset;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-114.17 spec, Tests: each self-service action proposed through the chat (the scripted model calls the tool) and
 * confirmed through the shipped endpoint, against the real leave and WFH services and PostgreSQL with row-level
 * security. Requests that must already exist are written as rows, as the screens would leave them.
 */
@SpringBootTest(classes = {PermissionGuardTestApp.class, SelfServiceActionsIT.ItConfig.class})
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class SelfServiceActionsIT extends AbstractIntegrationTest {

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
    private WfhPolicyService wfhPolicyService;

    @Autowired
    private WfhAllocationService wfhAllocationService;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private ApprovalDefinitionService definitionService;

    @Autowired
    private ApprovalService approvalService;

    private StubAiModelClient stub;
    private UUID tenant;
    private UUID subA;
    private UUID employeeA;
    private UUID employeeB;
    private UUID sessionA;
    private UUID leaveType;
    private UUID leavePolicy;
    private LocalDate day;

    @BeforeEach
    void setUp() throws Exception {
        stub = (StubAiModelClient) modelClient;
        stub.reset();
        TenantContext.clear();

        tenant = AuthzTestSchema.insertTenant("AiSelfServiceCo " + UUID.randomUUID());
        subscription(tenant);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID role = AuthzTestSchema.insertRole(
                tenant,
                "ai-self-" + suffix,
                "AI self-service",
                "core.ai.use",
                "core.leave.apply",
                "core.leave.read_own",
                "core.wfh.apply",
                "core.wfh.read_own");
        subA = UUID.randomUUID();
        employeeA = AuthzTestSchema.insertEmployee(tenant, "E-A-" + suffix, "Asha");
        AuthzTestSchema.linkEmployee(employeeA, member(tenant, subA, role));
        UUID subB = UUID.randomUUID();
        employeeB = AuthzTestSchema.insertEmployee(tenant, "E-B-" + suffix, "Bala");
        AuthzTestSchema.linkEmployee(employeeB, member(tenant, subB, role));

        seed();
        sessionA = AiChatRowsBridge.session(tenant, subA);
        day = nextWeekday(LocalDate.now(ZoneOffset.UTC).plusDays(14));
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "WFH: propose writes no request; confirm creates one PENDING request; a second confirm changes nothing")
    void wfhApply() throws Exception {
        UUID intent = propose(
                "core.propose_wfh_request",
                Map.of("fromDate", day.toString(), "toDate", day.toString(), "reason", "Plumber visit"),
                "core.wfh.apply");
        assertThat(count("SELECT count(*) FROM core.wfh_request WHERE employee_id = ?", employeeA))
                .isZero();

        JsonNode first = body(confirm(intent).andExpect(status().isOk()));
        assertThat(first.path("result").path("status").asText()).isEqualTo("PENDING");
        confirm(intent).andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM core.wfh_request WHERE employee_id = ?", employeeA))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("leave withdraw: PENDING -> WITHDRAWN on confirm; a second confirm changes nothing")
    void leaveWithdraw() throws Exception {
        UUID request = leaveRow(employeeA, day, "PENDING");
        UUID instance = awaitingApproval(ApprovalFlowType.LEAVE, "leave_request", request);
        UUID intent = propose("core.propose_leave_withdraw", Map.of("fromDate", day.toString()), "core.leave.withdraw");
        assertThat(leaveStatus(request)).isEqualTo("PENDING");

        confirm(intent).andExpect(status().isOk());
        confirm(intent).andExpect(status().isOk());

        assertThat(leaveStatus(request)).isEqualTo("WITHDRAWN");
        assertApprovalClosed(instance);
    }

    @Test
    @DisplayName("a pending leave whose start date has passed can still be withdrawn through the helper")
    void leaveWithdraw_pastStart() throws Exception {
        LocalDate past = LocalDate.now(ZoneOffset.UTC).minusDays(3);
        UUID request = leaveRow(employeeA, past, "PENDING");
        UUID intent =
                propose("core.propose_leave_withdraw", Map.of("fromDate", past.toString()), "core.leave.withdraw");

        confirm(intent).andExpect(status().isOk());

        assertThat(leaveStatus(request)).isEqualTo("WITHDRAWN");
    }

    @Test
    @DisplayName("leave cancel: APPROVED -> CANCELLED on confirm, and the consumed days are reversed")
    void leaveCancel() throws Exception {
        UUID request = leaveRow(employeeA, day, "APPROVED");
        consumption(employeeA, request, day);
        UUID intent = propose(
                "core.propose_leave_cancel",
                Map.of("fromDate", day.toString(), "reason", "Plans changed"),
                "core.leave.cancel");

        confirm(intent).andExpect(status().isOk());

        assertThat(leaveStatus(request)).isEqualTo("CANCELLED");
        assertThat(count(
                        "SELECT count(*) FROM core.leave_consumption WHERE leave_request_id = ? AND reverses_id IS"
                                + " NOT NULL",
                        request))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("WFH withdraw: PENDING -> WITHDRAWN; WFH cancel: APPROVED -> CANCELLED")
    void wfhWithdrawAndCancel() throws Exception {
        UUID pending = wfhRow(employeeA, day, "PENDING");
        UUID instance = awaitingApproval(ApprovalFlowType.WORK_FROM_HOME, "wfh_request", pending);
        UUID withdraw = propose("core.propose_wfh_withdraw", Map.of("fromDate", day.toString()), "core.wfh.withdraw");
        confirm(withdraw).andExpect(status().isOk());
        assertThat(wfhStatus(pending)).isEqualTo("WITHDRAWN");
        assertApprovalClosed(instance);

        LocalDate later = nextWeekday(day.plusDays(3));
        UUID approved = wfhRow(employeeA, later, "APPROVED");
        stub.reset();
        UUID cancel = propose("core.propose_wfh_cancel", Map.of("fromDate", later.toString()), "core.wfh.cancel");
        confirm(cancel).andExpect(status().isOk());
        assertThat(wfhStatus(approved)).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("an intent planted with another employee's leave or WFH request is refused at confirm; theirs stays")
    void anotherEmployeesRequestIsRefused() throws Exception {
        UUID theirLeave = leaveRow(employeeB, day, "PENDING");
        UUID theirWfh = wfhRow(employeeB, nextWeekday(day.plusDays(3)), "PENDING");

        UUID leaveIntent = plant("core.leave.withdraw", theirLeave);
        confirm(leaveIntent)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_ACTION_FAILED"))
                .andExpect(jsonPath("$.message").value(SelfServiceCards.LEAVE_GONE));
        UUID wfhIntent = plant("core.wfh.withdraw", theirWfh);
        confirm(wfhIntent)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(SelfServiceCards.WFH_GONE));

        assertThat(leaveStatus(theirLeave)).isEqualTo("PENDING");
        assertThat(wfhStatus(theirWfh)).isEqualTo("PENDING");

        UUID theirApprovedLeave = leaveRow(employeeB, nextWeekday(day.plusDays(6)), "APPROVED");
        UUID theirApprovedWfh = wfhRow(employeeB, nextWeekday(day.plusDays(9)), "APPROVED");
        confirm(plant("core.leave.cancel", theirApprovedLeave))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(SelfServiceCards.LEAVE_GONE));
        confirm(plant("core.wfh.cancel", theirApprovedWfh))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(SelfServiceCards.WFH_GONE));
        assertThat(leaveStatus(theirApprovedLeave)).isEqualTo("APPROVED");
        assertThat(wfhStatus(theirApprovedWfh)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("an intent naming a request that does not exist: the card reads 'not found', with no error")
    void missingRequest_cardSaysNotFound() throws Exception {
        UUID intent = plant("core.leave.withdraw", UUID.randomUUID());
        mvc.perform(as(get("/api/v1/ai/actions/{id}", intent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value(SelfServiceCards.LEAVE_GONE));
        UUID wfhIntent = plant("core.wfh.withdraw", UUID.randomUUID());
        mvc.perform(as(get("/api/v1/ai/actions/{id}", wfhIntent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value(SelfServiceCards.WFH_GONE));
    }

    @Test
    @DisplayName("withdraw asked for a request of B's date finds none of A's: no card, A's open requests listed")
    void proposeOnlyFindsOwn() throws Exception {
        leaveRow(employeeB, day, "PENDING");
        stub.script(
                AiModelResponse.toolCall(
                        "call_1",
                        "core.propose_leave_withdraw",
                        json.writeValueAsString(Map.of("fromDate", day.toString()))),
                AiModelResponse.text("You have no pending leave on that day."));
        mvc.perform(as(post("/api/v1/ai/sessions/{id}/messages", sessionA)
                        .content(json.writeValueAsString(Map.of("content", "Withdraw my leave")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedAction").isEmpty());
        assertThat(count("SELECT count(*) FROM core.ai_action_intent WHERE session_id = ?", sessionA))
                .isZero();
    }

    // ---- helpers ----

    /** The scripted model calls the tool once; the answer carries the card. Returns the intent. */
    private UUID propose(String tool, Map<String, String> args, String actionCode) throws Exception {
        stub.script(
                AiModelResponse.toolCall("call_1", tool, json.writeValueAsString(args)),
                AiModelResponse.text("Press Confirm to go ahead."));
        JsonNode answer = body(mvc.perform(as(post("/api/v1/ai/sessions/{id}/messages", sessionA)
                        .content(json.writeValueAsString(Map.of("content", "Please do it")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedAction.status").value("PROPOSED"))
                .andExpect(jsonPath("$.proposedAction.actionCode").value(actionCode)));
        return UUID.fromString(answer.path("proposedAction").path("intentId").asText());
    }

    private ResultActions confirm(UUID intent) throws Exception {
        return mvc.perform(as(post("/api/v1/ai/actions/{id}/confirm", intent)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(subA.toString()).claim("tenant_id", tenant.toString())));
    }

    /** Casual Leave with a no-limit policy, and a WFH policy with a quota for A, written as the app writes them. */
    private void seed() {
        TenantContext.set(tenant);
        try {
            new TransactionTemplate(txManager).executeWithoutResult(s -> {
                for (ApprovalFlowType flow : List.of(ApprovalFlowType.LEAVE, ApprovalFlowType.WORK_FROM_HOME)) {
                    definitionService.saveDefinition(
                            tenant,
                            flow,
                            new ApprovalDefinitionRequest(
                                    StepOrdering.SEQUENTIAL,
                                    CommentScope.PER_STEP,
                                    LocalDate.of(2024, 1, 1),
                                    List.of(new ApprovalStepDefinition(
                                            ApproverKind.NAMED_EMPLOYEE, employeeB.toString(), 3, false))));
                }
                LeaveTypeResponse type = leaveTypeService.createLeaveType(
                        tenant,
                        new LeaveTypeRequest(
                                "Casual Leave", "CL", true, LeaveUnit.DAYS, true, LocalDate.of(2024, 1, 1), null));
                leaveType = type.id();
                LeavePolicy policy = new LeavePolicy();
                policy.setTenantId(tenant);
                policy.setLeaveTypeId(type.id());
                policy.setAnnualDays(new BigDecimal("12.00"));
                policy.setEffectiveFrom(LocalDate.of(2024, 1, 1));
                policy.setExceedBalanceMode(ExceedBalanceMode.NO_LIMIT);
                leavePolicy = leavePolicyRepository.save(policy).getId();

                LocalDate today = LocalDate.now(ZoneOffset.UTC);
                WfhPolicyResponse wfh = wfhPolicyService.createPolicy(
                        tenant,
                        new CreateWfhPolicyRequest(
                                "WFH",
                                "Work from home",
                                new BigDecimal("24"),
                                new BigDecimal("10"),
                                true,
                                0,
                                null,
                                false,
                                false,
                                LocalDate.of(2024, 1, 1),
                                true));
                wfhAllocationService.createAllocation(
                        tenant,
                        new CreateWfhAllocationRequest(
                                employeeA,
                                wfh.id(),
                                String.valueOf(today.getYear()),
                                today.withDayOfYear(1),
                                today.withDayOfYear(1).plusYears(2).minusDays(1),
                                new BigDecimal("24"),
                                BigDecimal.ONE));
            });
        } finally {
            TenantContext.clear();
        }
    }

    private UUID leaveRow(UUID employee, LocalDate on, String status) throws SQLException {
        return insert(
                "INSERT INTO core.leave_request (tenant_id, employee_id, leave_type_id, from_date, to_date,"
                        + " working_days, status) VALUES (?, ?, ?, ?, ?, 1.00, ?) RETURNING id",
                tenant,
                employee,
                leaveType,
                on,
                on,
                status);
    }

    /** A pending approval for the request, started as the screens start it, and linked to the row. */
    private UUID awaitingApproval(ApprovalFlowType flow, String table, UUID requestId) throws SQLException {
        TenantContext.set(tenant);
        UUID instance;
        try {
            instance = new TransactionTemplate(txManager)
                    .execute(s -> approvalService.start(flow, new SubjectRef(table, requestId), employeeA));
        } finally {
            TenantContext.clear();
        }
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("UPDATE core." + table + " SET approval_instance_id = ? WHERE id = ?")) {
            ps.setObject(1, instance);
            ps.setObject(2, requestId);
            ps.executeUpdate();
        }
        return instance;
    }

    /** The withdrawn request's approval is closed: no longer pending, and no step is left undecided. */
    private void assertApprovalClosed(UUID instance) throws SQLException {
        assertThat(scalar("SELECT status FROM core.approval_instance WHERE id = ?", instance))
                .isNotEqualTo("PENDING");
        assertThat(count(
                        "SELECT count(*) FROM core.approval_step WHERE instance_id = ? AND decision IS NULL", instance))
                .isZero();
    }

    private UUID wfhRow(UUID employee, LocalDate on, String status) throws SQLException {
        return insert(
                "INSERT INTO core.wfh_request (tenant_id, employee_id, from_date, to_date, working_days, reason,"
                        + " status) VALUES (?, ?, ?, ?, 1.00, 'Home', ?) RETURNING id",
                tenant,
                employee,
                on,
                on,
                status);
    }

    /** One day of A's leave counted against an allocation, as an approval would write it. */
    private void consumption(UUID employee, UUID request, LocalDate on) throws SQLException {
        UUID allocation = insert(
                "INSERT INTO core.leave_allocation (tenant_id, employee_id, leave_type_id, policy_id, leave_year,"
                        + " year_start_date, year_end_date, entitlement_days, pro_rate_factor, carried_forward_days)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, 12.00, 1.0, 0) RETURNING id",
                tenant,
                employee,
                leaveType,
                leavePolicy,
                String.valueOf(on.getYear()),
                on.withDayOfYear(1),
                on.withDayOfYear(1).plusYears(1).minusDays(1));
        insert(
                "INSERT INTO core.leave_consumption (tenant_id, employee_id, allocation_id, leave_request_id,"
                        + " consumed_days, consumed_on, period) VALUES (?, ?, ?, ?, 1.00, ?, ?) RETURNING id",
                tenant,
                employee,
                allocation,
                request,
                on,
                on.toString().substring(0, 7));
    }

    /** An intent for A whose payload names {@code requestId}, as if the payload had been tampered with. */
    private UUID plant(String actionCode, UUID requestId) throws SQLException {
        return insert(
                "INSERT INTO core.ai_action_intent (tenant_id, session_id, user_id, action_code, payload, expires_at)"
                        + " VALUES (?, ?, ?, ?, ?::jsonb, now() + interval '10 minutes') RETURNING id",
                tenant,
                sessionA,
                subA,
                actionCode,
                "{\"requestId\":\"" + requestId + "\",\"reason\":null}");
    }

    private String leaveStatus(UUID id) throws SQLException {
        return scalar("SELECT status FROM core.leave_request WHERE id = ?", id);
    }

    private String wfhStatus(UUID id) throws SQLException {
        return scalar("SELECT status FROM core.wfh_request WHERE id = ?", id);
    }

    private static UUID insert(String sql, Object... values) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                ps.setObject(i + 1, values[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
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

    private static long count(String sql, UUID id) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static UUID member(UUID tenantId, UUID sub, UUID role) throws SQLException {
        UUID account = AuthzTestSchema.insertMember(tenantId, sub, "aiself-" + sub + "@aiself.test");
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

    private static LocalDate nextWeekday(LocalDate from) {
        LocalDate d = from;
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** The chat session row, written as {@code AiActionConfirmIT} writes it. */
    private static final class AiChatRowsBridge {
        static UUID session(UUID tenantId, UUID userId) throws SQLException {
            return insert(
                    "INSERT INTO core.ai_session (tenant_id, user_id) VALUES (?, ?) RETURNING id", tenantId, userId);
        }
    }
}
