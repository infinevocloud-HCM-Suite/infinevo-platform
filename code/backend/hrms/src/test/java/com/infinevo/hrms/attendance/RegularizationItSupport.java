package com.infinevo.hrms.attendance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.approval.ApprovalStep;
import com.infinevo.core.approval.ApprovalStepRepository;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.org.ReportingLineService;
import com.infinevo.hrms.project.HrmsProjectTestSchema;
import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Shared set-up for the W-40.4 integration tests, with the approval engine real: a submit starts a real
 * {@code REGULARIZATION} instance and a decision runs the real {@link RegularizationOutcomeHandler} after commit. The
 * reporting line is the test application's stand-in, stubbed so the employee reports to {@code manager}.
 *
 * <p>The pool is 6, as {@code TimesheetApprovalSupport}: a decision holds its connection while the handler runs in a
 * second, and the stand-ins open a third.
 *
 * <p>The clock is fixed at 2026-10-06 19:30 IST; the day corrected is 2026-10-05.
 */
@SpringBootTest(classes = HrmsTestApp.class, properties = "spring.datasource.hikari.maximum-pool-size=6")
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            HrmsAttendanceTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
abstract class RegularizationItSupport extends AbstractIntegrationTest {

    protected static final LocalDate DAY = LocalDate.of(2026, 10, 5);
    protected static final Instant NOW = Instant.parse("2026-10-06T14:00:00Z");
    /** 09:00 and 18:00 IST on {@link #DAY}. */
    protected static final String IN_AT = "2026-10-05T09:00:00+05:30";

    protected static final String OUT_AT = "2026-10-05T18:00:00+05:30";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ApprovalStepRepository steps;

    @Autowired
    protected ReportingLineService reportingLineService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    protected final ObjectMapper json = new ObjectMapper();

    protected UUID tenant;
    protected UUID employeeId;
    protected UUID employeeSub;
    protected UUID managerId;
    protected UUID managerSub;
    protected UUID hrSub;

    @BeforeEach
    void seedRegularization() throws SQLException {
        reset(reportingLineService);
        tenant = HrmsAttendanceTestSchema.insertTenant("Regularization Tenant " + UUID.randomUUID());
        HrmsTestApp.ENTITLED.put(tenant, Set.of(PlatformModule.HRMS));

        employeeId = HrmsAttendanceTestSchema.insertEmployee(tenant, "REG-E-" + UUID.randomUUID());
        managerId = HrmsAttendanceTestSchema.insertEmployee(tenant, "REG-M-" + UUID.randomUUID());
        employeeSub = UUID.randomUUID();
        managerSub = UUID.randomUUID();
        hrSub = UUID.randomUUID();
        HrmsAttendanceTestSchema.insertMember(tenant, employeeSub, "employee");
        HrmsAttendanceTestSchema.insertMemberWithActions(tenant, managerSub, "core.approval.decide");
        HrmsAttendanceTestSchema.insertMember(tenant, hrSub, "hr");

        Employee manager = mock(Employee.class);
        when(manager.getId()).thenReturn(managerId);
        when(reportingLineService.chainAbove(eq(employeeId), any())).thenReturn(List.of(manager));

        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(NOW, ZoneOffset.UTC));
        actAs(employeeId);
    }

    @AfterEach
    void cleanRegularization() {
        HrmsTestApp.ENTITLED.remove(tenant);
        HrmsTestApp.CURRENT_EMPLOYEE.remove();
        HrmsTestApp.CUSTOM_CLOCK.remove();
        TenantContext.clear();
        reset(reportingLineService);
    }

    /** Makes {@code employee} the logged-in employee, as the test application's {@code EmployeeService} reports. */
    protected void actAs(UUID employee) {
        HrmsTestApp.CURRENT_EMPLOYEE.set(new EmployeeResponse(
                employee,
                tenant,
                "E-" + employee.toString().substring(0, 6),
                "Test",
                null,
                "Employee",
                "MALE",
                LocalDate.of(2026, 4, 1),
                null,
                EmploymentStatus.ACTIVE,
                employee + "@hrms.test",
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now()));
    }

    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, UUID sub) {
        return builder.header("X-Tenant-ID", tenant.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@hrms.test")));
    }

    protected static String body(LocalDate date, String inAt, String outAt, String reason) {
        return "{\"date\":\"" + date + "\",\"inAt\":\"" + inAt + "\",\"outAt\":\"" + outAt + "\",\"reason\":\"" + reason
                + "\"}";
    }

    /** Clocks the employee in at {@code at} over HTTP and leaves the session open. */
    protected void clockIn(Instant at) throws Exception {
        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(at, ZoneOffset.UTC));
        actAs(employeeId);
        mvc.perform(authed(post("/api/v1/hrms/attendance/clock-in"), employeeSub))
                .andExpect(status().isCreated());
        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** Clocks the employee out at {@code at} over HTTP. */
    protected void clockOut(Instant at) throws Exception {
        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(at, ZoneOffset.UTC));
        actAs(employeeId);
        mvc.perform(authed(post("/api/v1/hrms/attendance/clock-out"), employeeSub))
                .andExpect(status().isOk());
        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** Submits 09:00 to 18:00 IST on {@link #DAY} as the employee and returns the response body. */
    protected JsonNode submit() throws Exception {
        actAs(employeeId);
        MvcResult result = mvc.perform(authed(post("/api/v1/hrms/attendance/regularizations"), employeeSub)
                        .content(body(DAY, IN_AT, OUT_AT, "Forgot to clock out")))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    /** The single step of the instance, read in a transaction under the tenant. */
    protected ApprovalStep onlyStep(UUID instanceId) {
        TenantContext.set(tenant);
        try {
            List<ApprovalStep> found = new TransactionTemplate(transactionManager)
                    .execute(status -> steps.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenant, instanceId));
            if (found == null || found.size() != 1) {
                throw new AssertionError("expected one step, found " + (found == null ? 0 : found.size()));
            }
            return found.get(0);
        } finally {
            TenantContext.clear();
        }
    }

    /** The manager decides the step through core's real endpoint. */
    protected void decide(UUID stepId, String decision, String comment) throws Exception {
        actAs(managerId);
        String content = comment == null
                ? "{\"decision\":\"" + decision + "\"}"
                : "{\"decision\":\"" + decision + "\",\"comment\":\"" + comment + "\"}";
        mvc.perform(authed(post("/api/v1/approvals/steps/" + stepId + "/decide"), managerSub)
                        .content(content))
                .andExpect(status().isOk());
        actAs(employeeId);
    }

    protected record SessionRow(
            UUID id, Instant clockInAt, Instant clockOutAt, String origin, String voidReason, UUID regularizationId) {}

    /** Every session of the employee on the date, in clock-in order, read as the owner. */
    protected List<SessionRow> sessions(LocalDate date) throws SQLException {
        List<SessionRow> rows = new ArrayList<>();
        try (Connection conn = HrmsAttendanceTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, clock_in_at, clock_out_at, origin, void_reason, regularization_id "
                                + "FROM hrms.clock_session WHERE tenant_id = ? AND employee_id = ? "
                                + "AND attendance_date = ? ORDER BY clock_in_at, created_at")) {
            ps.setObject(1, tenant);
            ps.setObject(2, employeeId);
            ps.setObject(3, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new SessionRow(
                            rs.getObject("id", UUID.class),
                            rs.getTimestamp("clock_in_at").toInstant(),
                            rs.getTimestamp("clock_out_at") == null
                                    ? null
                                    : rs.getTimestamp("clock_out_at").toInstant(),
                            rs.getString("origin"),
                            rs.getString("void_reason"),
                            rs.getObject("regularization_id", UUID.class)));
                }
            }
        }
        return rows;
    }

    protected record RequestRow(String status, Instant decidedAt, UUID decidedBy, String decisionComment) {}

    protected RequestRow request(UUID id) throws SQLException {
        try (Connection conn = HrmsAttendanceTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT status, decided_at, decided_by, decision_comment "
                        + "FROM hrms.attendance_regularization WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new RequestRow(
                        rs.getString("status"),
                        rs.getTimestamp("decided_at") == null
                                ? null
                                : rs.getTimestamp("decided_at").toInstant(),
                        rs.getObject("decided_by", UUID.class),
                        rs.getString("decision_comment"));
            }
        }
    }

    protected long requestCount() throws SQLException {
        return HrmsProjectTestSchema.count(
                "SELECT count(*) FROM hrms.attendance_regularization WHERE tenant_id = ?", tenant);
    }
}
