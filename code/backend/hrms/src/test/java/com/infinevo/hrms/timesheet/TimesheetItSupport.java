package com.infinevo.hrms.timesheet;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.hrms.project.AssignmentRequest;
import com.infinevo.hrms.project.AssignmentService;
import com.infinevo.hrms.project.HrmsProjectTestSchema;
import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.hrms.project.Priority;
import com.infinevo.hrms.project.ProjectRequest;
import com.infinevo.hrms.project.ProjectService;
import com.infinevo.hrms.project.ProjectStatus;
import com.infinevo.hrms.project.TaskRequest;
import com.infinevo.hrms.project.TaskService;
import com.infinevo.hrms.project.TaskStatus;
import com.infinevo.hrms.timesheet.TimesheetRequest.DayLine;
import com.infinevo.hrms.timesheet.TimesheetRequest.ProjectLine;
import com.infinevo.hrms.timesheet.TimesheetRequest.TaskLine;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Shared set-up for the W-42.1 integration tests: one tenant entitled to HRMS, a manager, two employees with a login
 * each, a project the first employee is assigned to (with two tasks), and a project they are not.
 *
 * <p>Services are called with a tenant bound and a current employee set, as the W-41 tests do; the HTTP tests send a
 * token, whose subject is the employee's login, and the same current employee, which the test application's
 * {@code EmployeeService} stands in for.
 */
@SpringBootTest(classes = HrmsTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            HrmsProjectTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
abstract class TimesheetItSupport extends AbstractIntegrationTest {

    /** A Monday: 2026-10-05. */
    protected static final LocalDate WEEK = LocalDate.of(2026, 10, 5);

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ProjectService projects;

    @Autowired
    protected TaskService tasks;

    @Autowired
    protected AssignmentService assignments;

    @Autowired
    protected TimesheetService timesheets;

    protected final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    protected UUID tenant;
    protected UUID managerEmp;
    protected UUID empA;
    protected UUID empB;
    protected UUID subA;
    protected UUID subB;
    protected UUID projectP;
    protected UUID taskP1;
    protected UUID taskP2;
    protected UUID projectQ;
    protected UUID taskQ1;

    @BeforeEach
    void seedBase() throws SQLException {
        tenant = HrmsProjectTestSchema.insertTenant("Timesheet Tenant " + UUID.randomUUID());
        HrmsTestApp.ENTITLED.put(tenant, Set.of(PlatformModule.HRMS));
        managerEmp = HrmsProjectTestSchema.insertEmployee(tenant, "MGR-" + UUID.randomUUID());
        empA = HrmsProjectTestSchema.insertEmployee(tenant, "EMP-A-" + UUID.randomUUID());
        empB = HrmsProjectTestSchema.insertEmployee(tenant, "EMP-B-" + UUID.randomUUID());
        subA = UUID.randomUUID();
        subB = UUID.randomUUID();
        HrmsProjectTestSchema.insertMember(tenant, subA, "employee");
        HrmsProjectTestSchema.insertMember(tenant, subB, "employee");

        TenantContext.set(tenant);
        try {
            projectP = project("Project P " + UUID.randomUUID());
            assignments.assign(projectP, new AssignmentRequest(empA, LocalDate.of(2026, 1, 1)));
            taskP1 = task(projectP, "Task P1");
            taskP2 = task(projectP, "Task P2");
            projectQ = project("Project Q " + UUID.randomUUID());
            taskQ1 = task(projectQ, "Task Q1");
        } finally {
            TenantContext.clear();
        }
        actAs(empA);
    }

    @AfterEach
    void cleanBase() {
        HrmsTestApp.ENTITLED.remove(tenant);
        HrmsTestApp.CURRENT_EMPLOYEE.remove();
        TenantContext.clear();
    }

    /** Binds the tenant for a service call made straight from the test. */
    protected void inTenant() {
        TenantContext.set(tenant);
    }

    protected UUID project(String name) {
        return projects.create(new ProjectRequest(
                        name,
                        "Dev",
                        "Timesheet test",
                        LocalDate.of(2026, 1, 1),
                        null,
                        Priority.MEDIUM,
                        ProjectStatus.STARTED,
                        BigDecimal.valueOf(1000),
                        managerEmp))
                .id();
    }

    protected UUID task(UUID project, String title) {
        return tasks.create(project, new TaskRequest(title, "test", null, null, Priority.MEDIUM, TaskStatus.TODO, 8))
                .id();
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

    protected static DayLine day(LocalDate date, String hours) {
        return new DayLine(date, new BigDecimal(hours), null);
    }

    /** One project, one task, the given days. */
    protected static TimesheetRequest request(LocalDate week, UUID project, UUID task, DayLine... days) {
        return new TimesheetRequest(
                week, List.of(new ProjectLine(project, List.of(new TaskLine(task, List.of(days))))));
    }

    /** A sound week on project P, task P1, 8 hours on the Monday. */
    protected TimesheetRequest simple(LocalDate week) {
        return request(week, projectP, taskP1, day(week, "8"));
    }

    protected String body(TimesheetRequest request) throws Exception {
        return json.writeValueAsString(request);
    }

    /** A request carrying a token for {@code sub} and the tenant header, as {@code ProjectPermissionIT} sends them. */
    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, UUID sub) {
        return builder.header("X-Tenant-ID", tenant.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@hrms.test")));
    }

    protected long rows(String table) throws SQLException {
        return HrmsProjectTestSchema.count("SELECT count(*) FROM hrms." + table + " WHERE tenant_id = ?", tenant);
    }
}
