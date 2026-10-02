package com.infinevo.hrms.timesheet.reminder;

import com.infinevo.hrms.project.AssignmentRequest;
import com.infinevo.hrms.project.AssignmentService;
import com.infinevo.hrms.project.HrmsProjectTestSchema;
import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.hrms.project.Priority;
import com.infinevo.hrms.project.ProjectRequest;
import com.infinevo.hrms.project.ProjectService;
import com.infinevo.hrms.project.ProjectStatus;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Shared set-up for the W-43.2 integration tests: a tenant of its own, and one-line builders for the employees,
 * projects, assignments, timesheets and reporting lines that decide who is late.
 *
 * <p>Nothing is seeded up front: the late query reads every active employee in the tenant, so a fixture that put
 * employees on projects for every test would put them in every answer. Each test makes its own tenant and what it needs.
 *
 * <p>The week the tests chase is {@link #WEEK}, a Monday; {@link #SLOT} is the Wednesday after it, the tenant-local
 * day a sweep would send for.
 */
@SpringBootTest(classes = HrmsTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            HrmsProjectTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
abstract class TimesheetReminderSupport extends AbstractIntegrationTest {

    /** The week being chased: Monday 2026-09-28 to Sunday 2026-10-04. */
    protected static final LocalDate WEEK = LocalDate.of(2026, 9, 28);

    /** A sweep sending on Wednesday 2026-10-07 chases {@link #WEEK}. */
    protected static final LocalDate SLOT = LocalDate.of(2026, 10, 7);

    @Autowired
    protected ProjectService projects;

    @Autowired
    protected AssignmentService assignments;

    @Autowired
    protected TimesheetLateQuery lateQuery;

    protected UUID newTenant() throws SQLException {
        UUID tenant = HrmsProjectTestSchema.insertTenant("Reminder " + UUID.randomUUID());
        HrmsTestApp.ENTITLED.put(tenant, java.util.Set.of(com.infinevo.shared.entitlement.PlatformModule.HRMS));
        return tenant;
    }

    /** An active employee with a name. */
    protected UUID employee(UUID tenant, String first, String last) throws SQLException {
        UUID id = HrmsProjectTestSchema.insertEmployee(tenant, "R-" + UUID.randomUUID());
        HrmsProjectTestSchema.update(
                "UPDATE core.employee SET first_name = ?, last_name = ? WHERE id = ?", first, last, id);
        return id;
    }

    protected void setEmploymentStatus(UUID employee, String status) throws SQLException {
        HrmsProjectTestSchema.update("UPDATE core.employee SET status = ? WHERE id = ?", status, employee);
    }

    protected void softDeleteEmployee(UUID employee) throws SQLException {
        HrmsProjectTestSchema.update("UPDATE core.employee SET is_deleted = true WHERE id = ?", employee);
    }

    /** A {@code STARTED} project with no dates, run by {@code manager}. */
    protected UUID project(UUID tenant, UUID manager) {
        TenantContext.set(tenant);
        try {
            return projects.create(new ProjectRequest(
                            "Reminder " + UUID.randomUUID(),
                            "Dev",
                            "Reminder test",
                            LocalDate.of(2026, 1, 1),
                            null,
                            Priority.MEDIUM,
                            ProjectStatus.STARTED,
                            BigDecimal.valueOf(1000),
                            manager))
                    .id();
        } finally {
            TenantContext.clear();
        }
    }

    protected void setProject(UUID project, String status, LocalDate start, LocalDate end) throws SQLException {
        HrmsProjectTestSchema.update(
                "UPDATE hrms.project SET status = ?, start_date = ?, end_date = ? WHERE id = ?",
                status,
                start,
                end,
                project);
    }

    protected void assign(UUID tenant, UUID project, UUID employee) {
        TenantContext.set(tenant);
        try {
            assignments.assign(project, new AssignmentRequest(employee, LocalDate.of(2026, 1, 1)));
        } finally {
            TenantContext.clear();
        }
    }

    protected void removeAssignment(UUID employee, UUID project) throws SQLException {
        HrmsProjectTestSchema.update(
                "UPDATE hrms.assignment SET is_deleted = true WHERE employee_id = ? AND project_id = ?",
                employee,
                project);
    }

    /** A timesheet header for the week, in the given status. Its lines do not matter to who is late. */
    protected void timesheet(UUID tenant, UUID employee, LocalDate week, String status) throws SQLException {
        HrmsProjectTestSchema.update(
                "INSERT INTO hrms.timesheet (tenant_id, employee_id, week_start_date, week_end_date, status)"
                        + " VALUES (?, ?, ?, ?, ?)",
                tenant,
                employee,
                week,
                week.plusDays(6),
                status);
    }

    /** A reporting line, in force from {@code from} until {@code to} (open when null). */
    protected void reportingLine(UUID tenant, UUID employee, UUID manager, String kind, LocalDate from, LocalDate to)
            throws SQLException {
        HrmsProjectTestSchema.update(
                "INSERT INTO core.reporting_line (tenant_id, employee_id, manager_id, kind, effective_from, effective_to)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                tenant,
                employee,
                manager,
                kind,
                from,
                to);
    }

    /** An employee on a started project, with nothing handed in: late, until a test changes something. */
    protected UUID lateEmployee(UUID tenant, UUID manager, String first, String last) throws SQLException {
        UUID employee = employee(tenant, first, last);
        assign(tenant, project(tenant, manager), employee);
        return employee;
    }
}
