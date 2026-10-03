package com.infinevo.hrms.overtime;

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
import com.infinevo.core.employee.EmployeeRepository;
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
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 * Shared set-up for the W-40.6 integration tests, with the approval engine, {@code OvertimeService} and the pay input
 * ledger real: a submit starts a real {@code OVERTIME} instance (the seeded flow, manager and HR in any order), and the
 * last decision runs the real {@link OvertimeRequestOutcomeHandler} after commit.
 *
 * <p>The test application's {@code EmployeeRepository} and {@code ReportingLineService} are stand-ins: they are
 * stubbed so the employee exists for {@code OvertimeServiceImpl}'s check, reports to {@code manager}, and the
 * {@code hr} role resolves to the HR employee. The pool is 6, as {@code RegularizationItSupport}.
 */
@SpringBootTest(classes = HrmsTestApp.class, properties = "spring.datasource.hikari.maximum-pool-size=6")
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            HrmsProjectTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
abstract class OvertimeRequestItSupport extends AbstractIntegrationTest {

    protected static final String BASE = "/api/v1/hrms/overtime-requests";

    /** A date in the past by the system clock {@code OvertimeServiceImpl} checks against. */
    protected static final LocalDate DAY = LocalDate.now(ZoneOffset.UTC).minusDays(3);

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ApprovalStepRepository steps;

    @Autowired
    protected ReportingLineService reportingLineService;

    @Autowired
    protected EmployeeRepository employeeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    protected final ObjectMapper json = new ObjectMapper();

    protected UUID tenant;
    protected UUID employeeId;
    protected UUID employeeSub;
    protected UUID colleagueId;
    protected UUID colleagueSub;
    protected UUID managerId;
    protected UUID managerSub;
    protected UUID hrId;
    protected UUID hrSub;

    @BeforeEach
    void seedOvertime() throws SQLException {
        reset(reportingLineService, employeeRepository);
        tenant = HrmsProjectTestSchema.insertTenant("Overtime Tenant " + UUID.randomUUID());
        HrmsTestApp.ENTITLED.put(tenant, Set.of(PlatformModule.HRMS));

        employeeId = HrmsProjectTestSchema.insertEmployee(tenant, "OT-E-" + UUID.randomUUID());
        colleagueId = HrmsProjectTestSchema.insertEmployee(tenant, "OT-C-" + UUID.randomUUID());
        managerId = HrmsProjectTestSchema.insertEmployee(tenant, "OT-M-" + UUID.randomUUID());
        hrId = HrmsProjectTestSchema.insertEmployee(tenant, "OT-H-" + UUID.randomUUID());
        employeeSub = UUID.randomUUID();
        colleagueSub = UUID.randomUUID();
        managerSub = UUID.randomUUID();
        hrSub = UUID.randomUUID();
        HrmsProjectTestSchema.insertMember(tenant, employeeSub, "employee");
        HrmsProjectTestSchema.insertMember(tenant, colleagueSub, "employee");
        HrmsProjectTestSchema.insertMemberWithActions(tenant, managerSub, "core.approval.decide");
        HrmsProjectTestSchema.insertMember(tenant, hrSub, "hr");

        // Every employee of the tenant exists for OvertimeServiceImpl's check.
        Employee anyEmployee = mock(Employee.class);
        when(employeeRepository.findByIdAndTenantIdAndDeletedFalse(any(), eq(tenant)))
                .thenReturn(Optional.of(anyEmployee));
        // The hr role resolves to the HR employee; hrSub is the tenant's only holder of it.
        Employee hr = mock(Employee.class);
        when(hr.getId()).thenReturn(hrId);
        when(employeeRepository.findByTenantIdAndUserAccountIdAndDeletedFalse(eq(tenant), any()))
                .thenReturn(Optional.of(hr));
        // Both employees report to the manager.
        Employee manager = mock(Employee.class);
        when(manager.getId()).thenReturn(managerId);
        when(reportingLineService.chainAbove(any(), any())).thenReturn(List.of(manager));

        actAs(employeeId);
    }

    @AfterEach
    void cleanOvertime() {
        HrmsTestApp.ENTITLED.remove(tenant);
        HrmsTestApp.CURRENT_EMPLOYEE.remove();
        TenantContext.clear();
        reset(reportingLineService, employeeRepository);
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

    protected static String body(LocalDate date, String hours, String remarks) {
        return "{\"overtimeDate\":\"" + date + "\",\"hours\":" + hours
                + (remarks == null ? "" : ",\"remarks\":\"" + remarks + "\"") + "}";
    }

    /** Submits {@code hours} on {@link #DAY} as {@code employee} and returns the response body. */
    protected JsonNode submitAs(UUID employee, UUID sub, String hours) throws Exception {
        actAs(employee);
        MvcResult result = mvc.perform(authed(post(BASE), sub).content(body(DAY, hours, "Release night")))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    /** The approval instance started for the request. */
    protected UUID instanceOf(UUID requestId) throws SQLException {
        return HrmsProjectTestSchema.uuid(
                "SELECT id FROM core.approval_instance WHERE tenant_id = ? AND subject_table = ? AND subject_id = ?",
                tenant,
                OvertimeRequestWorkflowService.SUBJECT_TABLE,
                requestId);
    }

    /** The instance's steps, in order, read in a transaction under the tenant. */
    protected List<ApprovalStep> stepsOf(UUID instanceId) {
        TenantContext.set(tenant);
        try {
            List<ApprovalStep> found = new TransactionTemplate(transactionManager)
                    .execute(status -> steps.findByTenantIdAndInstanceIdOrderByStepIndexAsc(tenant, instanceId));
            return found == null ? List.of() : found;
        } finally {
            TenantContext.clear();
        }
    }

    /** The step assigned to {@code assignee}. */
    protected ApprovalStep stepFor(UUID instanceId, UUID assignee) {
        return stepsOf(instanceId).stream()
                .filter(s -> assignee.equals(s.getAssigneeEmployeeId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no step assigned to " + assignee));
    }

    /** {@code approver}, logged in as {@code sub}, decides the step through core's real endpoint. */
    protected void decide(UUID stepId, UUID approver, UUID sub, String decision) throws Exception {
        actAs(approver);
        mvc.perform(authed(post("/api/v1/approvals/steps/" + stepId + "/decide"), sub)
                        .content("{\"decision\":\"" + decision + "\"}"))
                .andExpect(status().isOk());
        actAs(employeeId);
    }

    protected String statusOf(UUID requestId) throws SQLException {
        try (Connection conn = HrmsProjectTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT status FROM core.overtime_request WHERE id = ?")) {
            ps.setObject(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    protected record LedgerRow(String kind, BigDecimal quantity, BigDecimal amount, UUID employeeId) {}

    /** The pay input rows the request posted, read as the owner. */
    protected List<LedgerRow> ledgerRows(UUID requestId) throws SQLException {
        List<LedgerRow> rows = new ArrayList<>();
        try (Connection conn = HrmsProjectTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT kind, quantity, amount, employee_id "
                        + "FROM core.pay_input WHERE tenant_id = ? AND source_ref = ?")) {
            ps.setObject(1, tenant);
            ps.setString(2, "overtime_request:" + requestId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new LedgerRow(
                            rs.getString("kind"),
                            rs.getBigDecimal("quantity"),
                            rs.getBigDecimal("amount"),
                            rs.getObject("employee_id", UUID.class)));
                }
            }
        }
        return rows;
    }

    protected long ledgerCount() throws SQLException {
        return HrmsProjectTestSchema.count("SELECT count(*) FROM core.pay_input WHERE tenant_id = ?", tenant);
    }

    protected long requestCount() throws SQLException {
        return HrmsProjectTestSchema.count("SELECT count(*) FROM core.overtime_request WHERE tenant_id = ?", tenant);
    }
}
