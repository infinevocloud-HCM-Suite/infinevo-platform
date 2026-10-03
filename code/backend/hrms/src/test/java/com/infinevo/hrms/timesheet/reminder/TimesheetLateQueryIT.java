package com.infinevo.hrms.timesheet.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-43.2 §7, {@code TimesheetLateQueryIT}: who is late for a week, against the database. The one query behind both
 * reminder audiences.
 */
class TimesheetLateQueryIT extends TimesheetReminderSupport {

    private List<LateEmployee> late(UUID tenant) {
        TenantContext.set(tenant);
        try {
            return lateQuery.lateEmployees(tenant, WEEK);
        } finally {
            TenantContext.clear();
        }
    }

    private List<UUID> lateIds(UUID tenant) {
        return late(tenant).stream().map(LateEmployee::employeeId).toList();
    }

    @Test
    @DisplayName("Of the ten cases, exactly none, DRAFT and CANCELLED are late: not SUBMITTED, APPROVED or REJECTED")
    void onlyThoseWhoHandedNothingInAreLate() throws Exception {
        UUID tenant = newTenant();
        UUID boss = employee(tenant, "Boss", "Person");

        UUID none = lateEmployee(tenant, boss, "A", "None");
        UUID draft = lateEmployee(tenant, boss, "B", "Draft");
        UUID cancelled = lateEmployee(tenant, boss, "C", "Cancelled");
        UUID submitted = lateEmployee(tenant, boss, "D", "Submitted");
        UUID approved = lateEmployee(tenant, boss, "E", "Approved");
        UUID rejected = lateEmployee(tenant, boss, "F", "Rejected");
        timesheet(tenant, draft, WEEK, "DRAFT");
        timesheet(tenant, cancelled, WEEK, "CANCELLED");
        timesheet(tenant, submitted, WEEK, "SUBMITTED");
        timesheet(tenant, approved, WEEK, "APPROVED");
        timesheet(tenant, rejected, WEEK, "REJECTED");

        UUID onCompletedProject = employee(tenant, "G", "Completed");
        UUID completed = project(tenant, boss);
        setProject(completed, "COMPLETED", LocalDate.of(2026, 1, 1), null);
        assign(tenant, completed, onCompletedProject);

        UUID onEndedProject = employee(tenant, "H", "Ended");
        UUID ended = project(tenant, boss);
        setProject(ended, "STARTED", LocalDate.of(2026, 1, 1), WEEK.minusDays(1));
        assign(tenant, ended, onEndedProject);

        UUID noAssignment = employee(tenant, "I", "Unassigned");

        UUID suspended = lateEmployee(tenant, boss, "J", "Suspended");
        setEmploymentStatus(suspended, "SUSPENDED");

        assertThat(lateIds(tenant))
                .as("late: none, DRAFT, CANCELLED, in name order")
                .containsExactly(none, draft, cancelled);
        assertThat(lateIds(tenant))
                .doesNotContain(
                        submitted, approved, rejected, onCompletedProject, onEndedProject, noAssignment, suspended);
    }

    @Test
    @DisplayName("A timesheet for another week does not count: only the chased week decides")
    void anotherWeeksTimesheetDoesNotHelp() throws Exception {
        UUID tenant = newTenant();
        UUID boss = employee(tenant, "Boss", "Person");
        UUID employee = lateEmployee(tenant, boss, "Asha", "Rao");
        timesheet(tenant, employee, WEEK.minusWeeks(1), "APPROVED");
        timesheet(tenant, employee, WEEK.plusWeeks(1), "SUBMITTED");

        assertThat(lateIds(tenant)).containsExactly(employee);
    }

    @Test
    @DisplayName("One person is one row, however many projects they are on")
    void oneRowPerPerson() throws Exception {
        UUID tenant = newTenant();
        UUID boss = employee(tenant, "Boss", "Person");
        UUID employee = lateEmployee(tenant, boss, "Asha", "Rao");
        assign(tenant, project(tenant, boss), employee);
        assign(tenant, project(tenant, boss), employee);

        assertThat(lateIds(tenant)).containsExactly(employee);
    }

    @Test
    @DisplayName("A project must have been running during the week: not yet started, or ended before it, is not")
    void theProjectMustOverlapTheWeek() throws Exception {
        UUID tenant = newTenant();
        UUID boss = employee(tenant, "Boss", "Person");
        UUID startsLater = employee(tenant, "A", "Later");
        UUID endsInWeek = employee(tenant, "B", "Ending");
        UUID startsInWeek = employee(tenant, "C", "Starting");
        UUID endsBefore = employee(tenant, "D", "Before");

        UUID later = project(tenant, boss);
        setProject(later, "STARTED", WEEK.plusWeeks(1), null);
        assign(tenant, later, startsLater);
        UUID ending = project(tenant, boss);
        setProject(ending, "STARTED", LocalDate.of(2026, 1, 1), WEEK.plusDays(6));
        assign(tenant, ending, endsInWeek);
        UUID starting = project(tenant, boss);
        setProject(starting, "STARTED", WEEK.plusDays(6), null);
        assign(tenant, starting, startsInWeek);
        UUID before = project(tenant, boss);
        setProject(before, "STARTED", LocalDate.of(2026, 1, 1), WEEK.minusDays(1));
        assign(tenant, before, endsBefore);

        assertThat(lateIds(tenant)).containsExactlyInAnyOrder(endsInWeek, startsInWeek);
    }

    @Test
    @DisplayName("A terminated or deleted employee, a removed assignment and a deleted project are not late")
    void deadRowsAreNotLate() throws Exception {
        UUID tenant = newTenant();
        UUID boss = employee(tenant, "Boss", "Person");
        UUID terminated = lateEmployee(tenant, boss, "A", "Terminated");
        setEmploymentStatus(terminated, "TERMINATED");
        UUID deletedEmployee = lateEmployee(tenant, boss, "B", "Deleted");
        softDeleteEmployee(deletedEmployee);
        UUID removed = employee(tenant, "C", "Removed");
        UUID kept = project(tenant, boss);
        assign(tenant, kept, removed);
        removeAssignment(removed, kept);
        UUID onDeletedProject = employee(tenant, "D", "Gone");
        UUID gone = project(tenant, boss);
        assign(tenant, gone, onDeletedProject);
        com.infinevo.hrms.project.HrmsProjectTestSchema.update(
                "UPDATE hrms.project SET is_deleted = true WHERE id = ?", gone);

        assertThat(lateIds(tenant)).isEmpty();
    }

    @Test
    @DisplayName("The manager comes with the employee: the primary line in force on the week's last day")
    void theQueryCarriesThePrimaryManager() throws Exception {
        UUID tenant = newTenant();
        UUID boss = employee(tenant, "Boss", "Person");
        UUID other = employee(tenant, "Other", "Person");
        UUID withBoss = lateEmployee(tenant, boss, "A", "WithBoss");
        UUID noLine = lateEmployee(tenant, boss, "B", "NoLine");
        UUID dotted = lateEmployee(tenant, boss, "C", "Dotted");
        reportingLine(tenant, withBoss, boss, "PRIMARY", LocalDate.of(2026, 1, 1), null);
        reportingLine(tenant, dotted, other, "INDIRECT", LocalDate.of(2026, 1, 1), null);

        List<LateEmployee> late = late(tenant);

        assertThat(late).extracting(LateEmployee::employeeId).containsExactly(withBoss, noLine, dotted);
        assertThat(late.get(0).primaryManagerId()).isEqualTo(boss);
        assertThat(late.get(1).primaryManagerId()).as("no reporting line").isNull();
        assertThat(late.get(2).primaryManagerId())
                .as("only a PRIMARY line makes a manager")
                .isNull();
        assertThat(late.get(0).name()).isEqualTo("A WithBoss");
    }

    @Test
    @DisplayName("Tenant B's rows never appear in tenant A's answer, and the answer is by name")
    void anotherTenantIsNeverSeen() throws Exception {
        UUID tenantA = newTenant();
        UUID tenantB = newTenant();
        UUID bossA = employee(tenantA, "Boss", "A");
        UUID bossB = employee(tenantB, "Boss", "B");
        UUID zed = lateEmployee(tenantA, bossA, "Zed", "Last");
        UUID amy = lateEmployee(tenantA, bossA, "Amy", "First");
        UUID inB = lateEmployee(tenantB, bossB, "Ben", "Other");

        assertThat(lateIds(tenantA)).containsExactly(amy, zed);
        assertThat(lateIds(tenantB)).containsExactly(inB);
    }
}
