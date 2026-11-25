package com.infinevo.hrms.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.ai.AiToolDescriptor;
import com.infinevo.core.ai.AiToolRegistry;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.hrms.attendance.ClockService;
import com.infinevo.hrms.attendance.HrmsAttendanceTestSchema;
import com.infinevo.hrms.attendance.RegularizationService;
import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.hrms.timesheet.TimesheetService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * W-114.6 spec, Tests (integration), against real Postgres with row-level security: the HRMS tools run through the real
 * {@link AiToolRegistry}, the real permission and entitlement checks and the real clock service. Employee A asking gets
 * only A's sessions; a login without {@code core.attendance.read_own} is not offered the tool; a customer without HRMS
 * is offered none and cannot run one.
 *
 * <p>The spec places this test in {@code app} with the stub model scripted to call the tool. This task was confined to
 * {@code hrms}, so it calls the registry the chat loop calls ({@code AiChatServiceImpl}) directly instead.
 */
@SpringBootTest(classes = HrmsTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            HrmsAttendanceTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class HrmsAiToolsIT extends AbstractIntegrationTest {

    private static final Pattern ANY_UUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final LocalDate DAY_A = LocalDate.of(2026, 11, 17);
    private static final LocalDate DAY_B = LocalDate.of(2026, 11, 18);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClockService clockService;

    @Autowired
    private RegularizationService regularizationService;

    @Autowired
    private TimesheetService timesheetService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private TenantClock tenantClock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private EntitlementService entitlementService;

    private AiToolRegistry registry;
    private UUID tenantId;
    private UUID subA;
    private UUID subB;
    private UUID subNoRead;
    private UUID employeeA;
    private UUID employeeB;

    @BeforeEach
    void setUp() throws Exception {
        tenantId = HrmsAttendanceTestSchema.insertTenant("AI tools tenant " + UUID.randomUUID());
        HrmsTestApp.ENTITLED.put(tenantId, Set.of(PlatformModule.HRMS));
        subA = UUID.randomUUID();
        subB = UUID.randomUUID();
        subNoRead = UUID.randomUUID();
        HrmsAttendanceTestSchema.insertMemberWithActions(
                tenantId, subA, "hrms.attendance.mark", "core.attendance.read_own", "hrms.timesheet.read_own");
        HrmsAttendanceTestSchema.insertMemberWithActions(
                tenantId, subB, "hrms.attendance.mark", "core.attendance.read_own", "hrms.timesheet.read_own");
        HrmsAttendanceTestSchema.insertMemberWithActions(tenantId, subNoRead, "hrms.attendance.mark");
        employeeA = HrmsAttendanceTestSchema.insertEmployee(tenantId, "EMP-A-" + shortId());
        employeeB = HrmsAttendanceTestSchema.insertEmployee(tenantId, "EMP-B-" + shortId());

        // One closed session each, A on the 17th and B on the 18th, clocked through the real endpoints.
        clockDay(subA, employeeA, DAY_A);
        clockDay(subB, employeeB, DAY_B);
        // Remarks wrapping (rule 7) is proven in HrmsAiToolProviderTest: HrmsTestApp does not scan core's
        // AttendanceRepository, so ClockServiceImpl reads no remarks in this harness.

        HrmsAiToolProvider provider = new HrmsAiToolProvider(
                clockService,
                regularizationService,
                timesheetService,
                employeeService,
                tenantClock,
                transactionManager);
        registry = new AiToolRegistry(permissionService, entitlementService, List.of(provider));
    }

    @AfterEach
    void tearDown() {
        HrmsTestApp.ENTITLED.remove(tenantId);
        HrmsTestApp.CURRENT_EMPLOYEE.remove();
        HrmsTestApp.CUSTOM_CLOCK.remove();
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("employee A asks for their sessions and gets only A's, with no id")
    void employeeA_getsOnlyOwnSessions() {
        AiToolContext ctx = signIn(subA, employeeA);
        AiToolResult result = registry.execute(
                "hrms.my_attendance_sessions",
                ctx,
                objectMapper.createObjectNode().put("from", "2026-11-01").put("to", "2026-11-24"));

        assertThat(result.ok()).as(String.valueOf(result.error())).isTrue();
        JsonNode sessions = result.data().path("sessions");
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).path("attendanceDate").asText()).isEqualTo(DAY_A.toString());
        assertThat(ANY_UUID.matcher(result.data().toString()).find())
                .as(result.data().toString())
                .isFalse();

        // And B, asking the same, gets B's day and not A's.
        AiToolContext ctxB = signIn(subB, employeeB);
        AiToolResult resultB = registry.execute(
                "hrms.my_attendance_sessions",
                ctxB,
                objectMapper.createObjectNode().put("from", "2026-11-01").put("to", "2026-11-24"));
        assertThat(resultB.data().path("sessions")).hasSize(1);
        assertThat(resultB.data().path("sessions").get(0).path("attendanceDate").asText())
                .isEqualTo(DAY_B.toString());
    }

    @Test
    @DisplayName("a context naming B while A is signed in runs nothing")
    void contextForAnotherEmployee_refused() {
        signIn(subA, employeeA);
        AiToolContext forged = new AiToolContext(tenantId, subA, employeeB);
        AiToolResult result = registry.execute(
                "hrms.my_attendance_sessions",
                forged,
                objectMapper.createObjectNode().put("from", "2026-11-01").put("to", "2026-11-24"));

        assertThat(result.ok()).isFalse();
        assertThat(result.data()).isNull();
    }

    @Test
    @DisplayName("a login without core.attendance.read_own is not offered the sessions tool, and cannot run it")
    void withoutReadOwn_notOffered() {
        AiToolContext ctx = signIn(subNoRead, employeeA);

        assertThat(registry.toolsFor(ctx))
                .extracting(AiToolDescriptor::name)
                .containsExactly("hrms.my_attendance_today");
        assertThat(registry.execute("hrms.my_attendance_sessions", ctx, objectMapper.createObjectNode())
                        .ok())
                .isFalse();
    }

    @Test
    @DisplayName("HRMS held: all five offered; a customer without HRMS is offered none and cannot run one")
    void withoutHrms_noneOffered() {
        AiToolContext ctx = signIn(subA, employeeA);
        assertThat(registry.toolsFor(ctx)).hasSize(5);

        HrmsTestApp.ENTITLED.remove(tenantId);
        String other = "AI tools no-HRMS tenant " + UUID.randomUUID();
        UUID unlicensed = insertUnlicensedTenant(other);
        AiToolContext ctxUnlicensed = signInTo(unlicensed, subA, employeeA);

        assertThat(registry.toolsFor(ctxUnlicensed)).isEmpty();
        AiToolResult refused =
                registry.execute("hrms.my_attendance_today", ctxUnlicensed, objectMapper.createObjectNode());
        assertThat(refused.ok()).isFalse();
        assertThat(refused.error()).isEqualTo("Not permitted");
    }

    // ---- helpers ----

    private static UUID insertUnlicensedTenant(String name) {
        try {
            UUID tenant = HrmsAttendanceTestSchema.insertTenant(name);
            HrmsAttendanceTestSchema.insertMemberWithActions(
                    tenant, UUID.randomUUID(), "hrms.attendance.mark", "core.attendance.read_own");
            return tenant;
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private AiToolContext signIn(UUID sub, UUID employeeId) {
        return signInTo(tenantId, sub, employeeId);
    }

    /** Binds the tenant and the login as the chat request does, and the login's employee record. */
    private AiToolContext signInTo(UUID tenant, UUID sub, UUID employeeId) {
        TenantContext.set(tenant);
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(sub.toString(), null, List.of()));
        HrmsTestApp.CURRENT_EMPLOYEE.set(employee(tenant, employeeId));
        return new AiToolContext(tenant, sub, employeeId);
    }

    private void clockDay(UUID sub, UUID employeeId, LocalDate day) throws Exception {
        HrmsTestApp.CURRENT_EMPLOYEE.set(employee(tenantId, employeeId));
        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(Instant.parse(day + "T03:30:00Z"), ZoneOffset.UTC));
        mvc.perform(post("/api/v1/hrms/attendance/clock-in")
                        .header("X-Tenant-ID", tenantId.toString())
                        .with(jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@hrms.test"))))
                .andExpect(status().isCreated());
        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(Instant.parse(day + "T12:30:00Z"), ZoneOffset.UTC));
        mvc.perform(post("/api/v1/hrms/attendance/clock-out")
                        .header("X-Tenant-ID", tenantId.toString())
                        .with(jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@hrms.test"))))
                .andExpect(status().isOk());
        HrmsTestApp.CUSTOM_CLOCK.remove();
        HrmsTestApp.CURRENT_EMPLOYEE.remove();
    }

    private static EmployeeResponse employee(UUID tenant, UUID id) {
        return new EmployeeResponse(
                id,
                tenant,
                "EMP-" + id.toString().substring(0, 4),
                "Test",
                null,
                "Employee",
                "MALE",
                LocalDate.of(2026, 4, 1),
                null,
                EmploymentStatus.ACTIVE,
                id + "@hrms.test",
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
