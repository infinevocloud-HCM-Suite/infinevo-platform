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
import com.infinevo.core.leave.LeaveTypeRequest;
import com.infinevo.core.leave.LeaveTypeService;
import com.infinevo.core.leave.LeaveUnit;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-114.19 spec, Tests ({@code TeamAiToolsIT}): the four team tools through the chat, the real permission check, the
 * real leave, employee and reporting-line services and PostgreSQL with row-level security. The model is the scripted
 * {@link StubAiModelClient}; what each caller is offered is read from the request the stub received.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class TeamAiToolsIT extends AbstractIntegrationTest {

    private static final List<String> TEAM_TOOLS =
            List.of("core.team_on_leave", "core.joiners", "core.leavers", "core.headcount");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AiModelClient modelClient;

    @Autowired
    private LeaveTypeService leaveTypeService;

    @Autowired
    private TenantClock tenantClock;

    @Autowired
    private PlatformTransactionManager txManager;

    private StubAiModelClient stub;
    private UUID tenant;
    private UUID subHr;
    private UUID subManager;
    private UUID subEmployee;
    private UUID otherTenant;
    private UUID subHrOther;
    private LocalDate today;
    private String suffix;

    @BeforeEach
    void setUp() throws Exception {
        stub = (StubAiModelClient) modelClient;
        stub.reset();
        TenantContext.clear();
        suffix = UUID.randomUUID().toString().substring(0, 6);

        tenant = AuthzTestSchema.insertTenant("AiTeamCo " + suffix);
        subscription(tenant);
        TenantContext.set(tenant);
        try {
            today = new TransactionTemplate(txManager).execute(st -> tenantClock.today());
        } finally {
            TenantContext.clear();
        }
        UUID hrRole = AuthzTestSchema.insertRole(
                tenant, "ai-hr-" + suffix, "HR", "core.ai.use", "core.leave.read", "core.employee.read");
        UUID managerRole = AuthzTestSchema.insertRole(
                tenant, "ai-mgr-" + suffix, "Manager", "core.ai.use", "core.leave.read_team");
        UUID employeeRole = AuthzTestSchema.insertRole(
                tenant, "ai-emp-" + suffix, "Employee", "core.ai.use", "core.leave.read_own", "core.leave.apply");

        subHr = UUID.randomUUID();
        UUID hr = AuthzTestSchema.insertEmployee(tenant, "E-H-" + suffix, "Hema");
        AuthzTestSchema.linkEmployee(hr, member(tenant, subHr, hrRole));
        subManager = UUID.randomUUID();
        UUID manager = AuthzTestSchema.insertEmployee(tenant, "E-M-" + suffix, "Meera");
        AuthzTestSchema.linkEmployee(manager, member(tenant, subManager, managerRole));
        subEmployee = UUID.randomUUID();
        UUID report = AuthzTestSchema.insertEmployee(tenant, "E-R-" + suffix, "Ravi");
        AuthzTestSchema.linkEmployee(report, member(tenant, subEmployee, employeeRole));
        UUID other = AuthzTestSchema.insertEmployee(tenant, "E-O-" + suffix, "Omar");
        UUID gone = AuthzTestSchema.insertEmployee(tenant, "E-D-" + suffix, "Dina");
        execute(
                "INSERT INTO core.reporting_line (tenant_id, employee_id, manager_id, kind, effective_from)"
                        + " VALUES (?, ?, ?, 'PRIMARY', DATE '2024-01-01')",
                tenant,
                report,
                manager);

        UUID type = leaveType(tenant);
        for (UUID e : List.of(manager, report, other, gone)) {
            leave(tenant, e, type, today, today, "APPROVED");
        }
        leave(tenant, hr, type, today, today, "PENDING");
        // Joined this month: Omar and the soft-deleted Dina. Left this month: Ravi.
        execute("UPDATE core.employee SET date_of_joining = ? WHERE id = ? OR id = ?", today, other, gone);
        execute("UPDATE core.employee SET termination_date = ? WHERE id = ?", today, report);
        execute("UPDATE core.employee SET is_deleted = true WHERE id = ?", gone);

        otherTenant = AuthzTestSchema.insertTenant("AiTeamOther " + suffix);
        subscription(otherTenant);
        UUID hrOtherRole = AuthzTestSchema.insertRole(
                otherTenant, "ai-hr-" + suffix, "HR", "core.ai.use", "core.leave.read", "core.employee.read");
        subHrOther = UUID.randomUUID();
        UUID hrOther = AuthzTestSchema.insertEmployee(otherTenant, "E-X-" + suffix, "Xena");
        AuthzTestSchema.linkEmployee(hrOther, member(otherTenant, subHrOther, hrOtherRole));
        leave(otherTenant, hrOther, leaveType(otherTenant), today, today, "APPROVED");
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("HR sees everyone on approved leave today, no pending one, no deleted one, and no leave type")
    void hr_seesEveryone() throws Exception {
        String result = call(tenant, subHr, "core.team_on_leave", "{}");

        assertThat(json.readTree(result).path("onLeave")).hasSize(3);
        assertThat(json.readTree(result).path("total").asLong()).isEqualTo(3);
        assertThat(result).contains("Meera").contains("Ravi").contains("Omar");
        assertThat(result).doesNotContain("Dina").doesNotContain("Hema").doesNotContain("Xena");
        assertThat(result).doesNotContain("Maternity");
    }

    @Test
    @DisplayName("a date range reaches the database: the leave search's date filter works (42P18 fixed)")
    void dateRange_works() throws Exception {
        String result = call(
                tenant,
                subHr,
                "core.team_on_leave",
                json.writeValueAsString(Map.of(
                        "from",
                        today.minusDays(1).toString(),
                        "to",
                        today.plusDays(1).toString())));
        assertThat(result).contains("Meera").contains("Omar");

        String empty = call(
                tenant,
                subHr,
                "core.team_on_leave",
                json.writeValueAsString(Map.of(
                        "from",
                        today.plusDays(5).toString(),
                        "to",
                        today.plusDays(6).toString())));
        assertThat(empty).doesNotContain("Meera").contains("\"total\":0");

        // The Leave requests screen's own date filter, which failed the same way: both dates, one, or none. The
        // tenant holds five requests today (four approved, one pending), the soft-deleted employee's included.
        assertThat(screenTotal(Map.of("from", today.toString(), "to", today.toString())))
                .isEqualTo(5);
        assertThat(screenTotal(Map.of())).isEqualTo(5);
        assertThat(screenTotal(Map.of("from", today.toString()))).isEqualTo(5);
        assertThat(screenTotal(Map.of("to", today.toString()))).isEqualTo(5);
        assertThat(screenTotal(Map.of("from", today.plusDays(5).toString()))).isZero();
        assertThat(screenTotal(Map.of("to", today.minusDays(1).toString()))).isZero();
        assertThat(screenTotal(Map.of("status", "APPROVED"))).isEqualTo(4);
    }

    /** The Leave requests screen's total for these query parameters, as HR. */
    private long screenTotal(Map<String, String> params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/leave-requests");
        params.forEach(request::param);
        String body = mvc.perform(as(tenant, subHr, request))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode page = json.readTree(body);
        // Spring Data's page as it stands, or its stable DTO form, whichever the app serialises.
        return page.has("totalElements")
                ? page.path("totalElements").asLong()
                : page.path("page").path("totalElements").asLong(-1);
    }

    @Test
    @DisplayName("a manager sees only self and direct reports, and is not offered joiners, leavers or headcount")
    void manager_seesTeam() throws Exception {
        String result = call(tenant, subManager, "core.team_on_leave", "{}");

        assertThat(result).contains("Meera").contains("Ravi");
        assertThat(result).doesNotContain("Omar").doesNotContain("Maternity");
        assertThat(offered())
                .contains("core.team_on_leave")
                .doesNotContain("core.joiners", "core.leavers", "core.headcount");

        String joiners = call(tenant, subManager, "core.joiners", "{}");
        assertThat(joiners).doesNotContain("Omar").doesNotContain("\"joiners\"");
    }

    @Test
    @DisplayName("an employee with only own-data actions is offered none of the four")
    void employee_offeredNone() throws Exception {
        call(tenant, subEmployee, "core.team_on_leave", "{}");

        assertThat(offered()).doesNotContainAnyElementsOf(TEAM_TOOLS);
    }

    @Test
    @DisplayName("tenant B's HR sees only tenant B")
    void otherTenant_isolated() throws Exception {
        String result = call(otherTenant, subHrOther, "core.team_on_leave", "{}");

        assertThat(result).contains("Xena").doesNotContain("Meera").doesNotContain("Ravi");
    }

    @Test
    @DisplayName("joiners and leavers this month leave out the soft-deleted; headcount equals the limit guard's count")
    void joinersLeaversHeadcount() throws Exception {
        String joiners = call(tenant, subHr, "core.joiners", "{}");
        assertThat(joiners).contains("Omar").doesNotContain("Dina");

        String leavers = call(tenant, subHr, "core.leavers", "{}");
        assertThat(leavers).contains("Ravi").doesNotContain("Omar");

        JsonNode headcount = json.readTree(call(tenant, subHr, "core.headcount", "{\"by\":\"department\"}"));
        long guard = count(
                "SELECT count(*) FROM core.employee WHERE tenant_id = ? AND is_deleted = false AND status <> 'TERMINATED'",
                tenant);
        assertThat(headcount.path("total").asLong()).isEqualTo(guard).isEqualTo(4);
        long sum = 0;
        for (JsonNode g : headcount.path("groups")) {
            sum += g.path("count").asLong();
        }
        assertThat(sum).isEqualTo(guard);
    }

    // ---- helpers ----

    /** The caller asks; the scripted model calls the tool, then answers. Returns the tool's result as sent back. */
    private String call(UUID tenantId, UUID sub, String tool, String args) throws Exception {
        stub.reset();
        stub.script(AiModelResponse.toolCall("call_1", tool, args), AiModelResponse.text("Here you are."));
        UUID session = session(tenantId, sub);
        mvc.perform(as(
                        tenantId,
                        sub,
                        post("/api/v1/ai/sessions/{id}/messages", session)
                                .content(json.writeValueAsString(Map.of("content", "Tell me")))))
                .andExpect(status().isOk());
        var requests = stub.requests();
        for (var m : requests.get(requests.size() - 1).messages()) {
            if (m.toolCallId() != null) {
                return m.content();
            }
        }
        throw new AssertionError("no tool result was sent to the model");
    }

    /** The tool names the last caller was offered. */
    private List<String> offered() {
        List<String> names = new ArrayList<>();
        for (AiToolDescriptor d : stub.requests().get(0).tools()) {
            names.add(d.name());
        }
        return names;
    }

    private static MockHttpServletRequestBuilder as(UUID tenantId, UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenantId.toString())));
    }

    private UUID leaveType(UUID tenantId) {
        TenantContext.set(tenantId);
        try {
            return new TransactionTemplate(txManager).execute(s -> leaveTypeService
                    .createLeaveType(
                            tenantId,
                            new LeaveTypeRequest(
                                    "Maternity Leave",
                                    "ML",
                                    true,
                                    LeaveUnit.DAYS,
                                    true,
                                    LocalDate.of(2024, 1, 1),
                                    null))
                    .id());
        } finally {
            TenantContext.clear();
        }
    }

    private static void leave(UUID tenantId, UUID employee, UUID type, LocalDate from, LocalDate to, String status)
            throws SQLException {
        execute(
                "INSERT INTO core.leave_request (tenant_id, employee_id, leave_type_id, from_date, to_date,"
                        + " working_days, status, reason) VALUES (?, ?, ?, ?, ?, 1.00, ?, 'Doctor said rest')",
                tenantId,
                employee,
                type,
                from,
                to,
                status);
    }

    private UUID member(UUID tenantId, UUID sub, UUID role) throws SQLException {
        UUID account = AuthzTestSchema.insertMember(tenantId, sub, "aiteam-" + sub + "@aiteam.test");
        AuthzTestSchema.grant(tenantId, account, role);
        return account;
    }

    private static void subscription(UUID tenantId) throws SQLException {
        execute(
                "INSERT INTO core.subscription (id, tenant_id, status, started_on, ai_enabled,"
                        + " ai_monthly_request_cap) VALUES (?, ?, 'ACTIVE', CURRENT_DATE, true, 1000)",
                UUID.randomUUID(),
                tenantId);
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
}
