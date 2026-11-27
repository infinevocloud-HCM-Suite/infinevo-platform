package com.infinevo.core.ai.action;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.infinevo.core.leave.LeaveApplyRequest;
import com.infinevo.core.leave.LeavePolicy;
import com.infinevo.core.leave.LeavePolicyRepository;
import com.infinevo.core.leave.LeaveRequestResponse;
import com.infinevo.core.leave.LeaveRequestService;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * The W-114.18 integration set-up: a manager who approves an employee's leave, the real leave and approval services,
 * the shipped filter chain and PostgreSQL with row-level security. The model is the scripted {@link
 * StubAiModelClient}. Leave requests are made through {@link LeaveRequestService#createRequest}, as the Leave screen
 * makes them, so each has its approval instance and pending step.
 */
@SpringBootTest(
        classes = {PermissionGuardTestApp.class, ApprovalItSupport.ItConfig.class},
        properties = "spring.datasource.hikari.maximum-pool-size=14")
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
abstract class ApprovalItSupport extends AbstractIntegrationTest {

    @TestConfiguration
    @Import({AuditIntegratorConfig.class, AuditWriter.class, CorrelationIdFilter.class})
    static class ItConfig {}

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    private AiModelClient modelClient;

    @Autowired
    private LeaveTypeService leaveTypeService;

    @Autowired
    private LeavePolicyRepository leavePolicyRepository;

    @Autowired
    private LeaveRequestService leaveRequestService;

    @Autowired
    private ApprovalDefinitionService definitionService;

    @Autowired
    private PlatformTransactionManager txManager;

    StubAiModelClient stub;
    UUID tenant;
    /** The manager: approves every leave request in this tenant. */
    UUID subManager;
    /** Holds core.approval.decide but is assigned nothing. */
    UUID subOther;

    UUID employeeAsha;
    String ashaNumber;
    UUID managerSession;
    private UUID leaveTypeId;
    private LocalDate nextDay;

    @BeforeEach
    void setUpApprovals() throws Exception {
        stub = (StubAiModelClient) modelClient;
        stub.reset();
        TenantContext.clear();

        tenant = AuthzTestSchema.insertTenant("AiApprovalCo " + UUID.randomUUID());
        subscription(tenant);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID employeeRole = AuthzTestSchema.insertRole(
                tenant, "ai-emp-" + suffix, "AI employee", "core.ai.use", "core.leave.apply");
        UUID approverRole = AuthzTestSchema.insertRole(
                tenant, "ai-appr-" + suffix, "AI approver", "core.ai.use", "core.approval.decide");

        UUID subAsha = UUID.randomUUID();
        ashaNumber = "E-A-" + subAsha.toString().substring(0, 6);
        employeeAsha = AuthzTestSchema.insertEmployee(tenant, ashaNumber, "Asha");
        AuthzTestSchema.linkEmployee(employeeAsha, member(tenant, subAsha, employeeRole));

        subManager = UUID.randomUUID();
        UUID employeeManager = AuthzTestSchema.insertEmployee(
                tenant, "E-M-" + subManager.toString().substring(0, 6), "Meera");
        AuthzTestSchema.linkEmployee(employeeManager, member(tenant, subManager, approverRole));

        subOther = UUID.randomUUID();
        UUID employeeOther = AuthzTestSchema.insertEmployee(
                tenant, "E-O-" + subOther.toString().substring(0, 6), "Omar");
        AuthzTestSchema.linkEmployee(employeeOther, member(tenant, subOther, approverRole));

        seedLeave(employeeManager);
        managerSession = session(tenant, subManager);
        nextDay = LocalDate.now().plusDays(10);
    }

    @AfterEach
    void unbindApprovals() {
        TenantContext.clear();
    }

    /** One pending leave request by Asha on the next free weekday, with this reason. Returns the request's id. */
    UUID applyLeave(String reason) {
        LocalDate day = nextWeekday(nextDay);
        nextDay = day.plusDays(1);
        TenantContext.set(tenant);
        try {
            LeaveRequestResponse created = new TransactionTemplate(txManager)
                    .execute(s -> leaveRequestService.createRequest(
                            tenant,
                            employeeAsha,
                            new LeaveApplyRequest(leaveTypeId, day, day, false, null, reason, null, true)));
            return created.id();
        } finally {
            TenantContext.clear();
        }
    }

    /** The manager sends one message; the model replies with these. Returns the chat answer. */
    JsonNode say(String content, AiModelResponse... replies) throws Exception {
        stub.script(replies);
        return body(mvc.perform(as(
                        subManager,
                        post("/api/v1/ai/sessions/{id}/messages", managerSession)
                                .content(json.writeValueAsString(Map.of("content", content)))))
                .andExpect(status().isOk()));
    }

    static AiModelResponse proposeCall(String id, int position, String number, String decision, String comment)
            throws Exception {
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("position", position);
        args.put("employeeNumber", number);
        args.put("flowType", "LEAVE");
        args.put("decision", decision);
        if (comment != null) {
            args.put("comment", comment);
        }
        return AiModelResponse.toolCall(
                id, "core.propose_approval_decision", new ObjectMapper().writeValueAsString(args));
    }

    /** The intent the chat answer carries, or null. */
    static UUID intentOf(JsonNode answer) {
        String id = answer.path("proposedAction").path("intentId").asText(null);
        return id == null ? null : UUID.fromString(id);
    }

    ResultActions confirm(UUID sub, UUID intent) throws Exception {
        return mvc.perform(as(sub, post("/api/v1/ai/actions/{id}/confirm", intent)));
    }

    ResultActions view(UUID sub, UUID intent) throws Exception {
        return mvc.perform(as(sub, get("/api/v1/ai/actions/{id}", intent)));
    }

    JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenant.toString())));
    }

    /** The tool results the model was sent, oldest first, across every request since the last reset. */
    List<String> toolResults() {
        List<String> out = new ArrayList<>();
        var requests = stub.requests();
        if (requests.isEmpty()) {
            return out;
        }
        for (var m : requests.get(requests.size() - 1).messages()) {
            if (m.toolCallId() != null) {
                out.add(m.content());
            }
        }
        return out;
    }

    /** The decision of the pending step on this leave request's approval, or null while undecided. */
    String stepDecision(UUID leaveId) throws SQLException {
        return scalar(
                "SELECT s.decision::text FROM core.approval_step s JOIN core.leave_request l"
                        + " ON l.approval_instance_id = s.instance_id WHERE l.id = ?",
                leaveId);
    }

    long decidedSteps() throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM core.approval_step WHERE tenant_id = ? AND decision IS NOT NULL")) {
            ps.setObject(1, tenant);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    long intents() throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core.ai_action_intent WHERE tenant_id = ?")) {
            ps.setObject(1, tenant);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** The leave request's status, waiting up to five seconds for {@code expected}: the outcome runs after commit. */
    String leaveStatus(UUID leaveId, String expected) throws Exception {
        String status = null;
        for (int attempt = 0; attempt < 50; attempt++) {
            status = scalar("SELECT status::text FROM core.leave_request WHERE id = ?", leaveId);
            if (expected.equals(status)) {
                return status;
            }
            Thread.sleep(100);
        }
        return status;
    }

    static void execute(String sql, UUID id) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }

    static String scalar(String sql, UUID id) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Casual Leave with a no-limit policy, and a one-step approval by {@code approver}, written as the app writes them. */
    private void seedLeave(UUID approver) {
        TenantContext.set(tenant);
        try {
            new TransactionTemplate(txManager).executeWithoutResult(s -> {
                definitionService.saveDefinition(
                        tenant,
                        ApprovalFlowType.LEAVE,
                        new ApprovalDefinitionRequest(
                                StepOrdering.SEQUENTIAL,
                                CommentScope.PER_STEP,
                                LocalDate.of(2024, 1, 1),
                                List.of(new ApprovalStepDefinition(
                                        ApproverKind.NAMED_EMPLOYEE, approver.toString(), 3, false))));
                LeaveTypeResponse type = leaveTypeService.createLeaveType(
                        tenant,
                        new LeaveTypeRequest(
                                "Casual Leave", "CL", true, LeaveUnit.DAYS, true, LocalDate.of(2024, 1, 1), null));
                leaveTypeId = type.id();
                LeavePolicy policy = new LeavePolicy();
                policy.setTenantId(tenant);
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
        UUID account = AuthzTestSchema.insertMember(tenantId, sub, "aiappr-" + sub + "@aiappr.test");
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

    static UUID session(UUID tenantId, UUID userId) throws SQLException {
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
}
