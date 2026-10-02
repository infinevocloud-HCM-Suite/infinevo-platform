package com.infinevo.hrms.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.hrms.project.AssignmentRequest;
import com.infinevo.hrms.project.HrmsProjectTestSchema;
import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.hrms.project.ResourceNotFoundException;
import com.infinevo.hrms.project.ValidationException;
import com.infinevo.hrms.timesheet.TimesheetRequest.DayLine;
import com.infinevo.hrms.timesheet.TimesheetRequest.ProjectLine;
import com.infinevo.hrms.timesheet.TimesheetRequest.TaskLine;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-42.1 §7, {@code TimesheetCrudIT}: create, replace and delete a draft week against real tables. The rules that
 * need the database (assigned projects, tasks of a project, the one-week-once index) and what a delete leaves behind.
 */
class TimesheetCrudIT extends TimesheetItSupport {

    @BeforeEach
    void bind() {
        inTenant();
    }

    @Test
    @DisplayName("Create saves a DRAFT for the caller; hours round-trip at scale 2; the week ends six days later")
    void createSavesADraftForTheCaller() throws Exception {
        TimesheetRequest request = new TimesheetRequest(
                WEEK,
                List.of(new ProjectLine(
                        projectP,
                        List.of(
                                new TaskLine(
                                        taskP1,
                                        List.of(
                                                new DayLine(WEEK, new BigDecimal("7.5"), "design"),
                                                day(WEEK.plusDays(1), "8"))),
                                new TaskLine(taskP2, List.of(day(WEEK, "0.25")))))));

        TimesheetResponse created = timesheets.create(request);

        assertThat(created.status()).isEqualTo(TimesheetStatus.DRAFT);
        assertThat(created.employeeId())
                .as("the caller, never taken from the body")
                .isEqualTo(empA);
        assertThat(created.weekStartDate()).isEqualTo(WEEK);
        assertThat(created.weekEndDate()).isEqualTo(WEEK.plusDays(6));
        assertThat(created.projects()).hasSize(1);
        assertThat(created.projects().get(0).status()).isEqualTo(TimesheetStatus.DRAFT);
        assertThat(created.projects().get(0).tasks()).hasSize(2);

        // The database's own text for each value: scale 2 whatever the request wrote.
        assertThat(storedHours()).containsExactly("0.25", "7.50", "8.00");
        assertThat(timesheets.get(created.id()).projects().get(0).tasks())
                .flatExtracting(t -> t.days())
                .extracting(d -> d.hours().toPlainString())
                .containsExactlyInAnyOrder("0.25", "7.50", "8.00");
        assertThat(rows("timesheet")).isEqualTo(1);
        assertThat(rows("timesheet_project_entry")).isEqualTo(1);
        assertThat(rows("timesheet_task_entry")).isEqualTo(2);
        assertThat(rows("timesheet_day_entry")).isEqualTo(3);
    }

    @Test
    @DisplayName(
            "Replace rewrites every entry, including a line that keeps its project and task; delete leaves no rows")
    void replaceThenDelete() throws Exception {
        TimesheetResponse created =
                timesheets.create(request(WEEK, projectP, taskP1, day(WEEK, "8"), day(WEEK.plusDays(1), "8")));
        Instant before = created.updatedAt();

        TimesheetRequest again = new TimesheetRequest(
                WEEK,
                List.of(new ProjectLine(
                        projectP,
                        List.of(
                                new TaskLine(taskP1, List.of(day(WEEK, "6"))),
                                new TaskLine(taskP2, List.of(day(WEEK.plusDays(2), "3.5")))))));
        TimesheetResponse replaced = timesheets.replace(created.id(), again);

        assertThat(replaced.id()).as("the same timesheet").isEqualTo(created.id());
        assertThat(storedHours()).as("only the new hours remain").containsExactly("3.50", "6.00");
        assertThat(rows("timesheet_task_entry")).isEqualTo(2);
        assertThat(rows("timesheet_day_entry")).isEqualTo(2);
        assertThat(timesheets.get(created.id()).updatedAt()).isAfter(before);

        timesheets.delete(created.id());

        assertThat(rows("timesheet")).isZero();
        assertThat(rows("timesheet_project_entry")).isZero();
        assertThat(rows("timesheet_task_entry")).isZero();
        assertThat(rows("timesheet_day_entry"))
                .as("delete leaves no entry rows behind")
                .isZero();
        assertThatThrownBy(() -> timesheets.get(created.id())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("A second create for the same week is 409, and is allowed again after the first is deleted")
    void oneTimesheetPerWeek() {
        TimesheetResponse first = timesheets.create(simple(WEEK));

        assertThatThrownBy(() -> timesheets.create(simple(WEEK))).isInstanceOf(TimesheetConflictException.class);
        assertThat(timesheets.create(simple(WEEK.plusDays(7))).status())
                .as("another week is fine")
                .isEqualTo(TimesheetStatus.DRAFT);

        timesheets.delete(first.id());
        assertThat(timesheets.create(simple(WEEK)).id()).isNotEqualTo(first.id());
    }

    @Test
    @DisplayName("A cancelled timesheet does not hold its week")
    void cancelledWeekIsFree() throws Exception {
        TimesheetResponse first = timesheets.create(simple(WEEK));
        HrmsProjectTestSchema.update("UPDATE hrms.timesheet SET status = 'CANCELLED' WHERE id = ?", first.id());

        TimesheetResponse second = timesheets.create(simple(WEEK));

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(timesheets.forWeek(WEEK))
                .get()
                .extracting(TimesheetResponse::id)
                .isEqualTo(second.id());
    }

    @Test
    @DisplayName(
            "Replace or delete of a timesheet that is not a draft is 409, whatever its status; a rejected one is resubmitted")
    void nonDraftIsLocked() throws Exception {
        for (String status : new String[] {"SUBMITTED", "APPROVED", "REJECTED"}) {
            TimesheetResponse created = timesheets.create(simple(WEEK));
            HrmsProjectTestSchema.update("UPDATE hrms.timesheet SET status = ? WHERE id = ?", status, created.id());

            // A rejected week is replaced by a resubmit of its rejected projects (W-42.3, TimesheetDecisionIT).
            if (!status.equals("REJECTED")) {
                assertThatThrownBy(() -> timesheets.replace(created.id(), simple(WEEK)))
                        .as("replace %s", status)
                        .isInstanceOf(TimesheetConflictException.class)
                        .hasMessageContaining(status);
            }
            assertThatThrownBy(() -> timesheets.delete(created.id()))
                    .as("delete %s", status)
                    .isInstanceOf(TimesheetConflictException.class);

            assertThat(timesheets.get(created.id()).status())
                    .as("untouched")
                    .isEqualTo(TimesheetStatus.valueOf(status));
            assertThat(rows("timesheet_day_entry"))
                    .as("its entries are still there")
                    .isEqualTo(1);
            HrmsProjectTestSchema.update("DELETE FROM hrms.timesheet WHERE id = ?", created.id());
        }
    }

    @Test
    @DisplayName("A project the caller is not assigned to is 400, and so is one that does not exist; nothing is stored")
    void projectMustBeAssigned() throws Exception {
        assertThatThrownBy(() -> timesheets.create(request(WEEK, projectQ, taskQ1, day(WEEK, "8"))))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.fieldErrors())
                        .containsKey("projects[0].project_id"));
        UUID ghost = UUID.randomUUID();
        assertThatThrownBy(() -> timesheets.create(request(WEEK, ghost, taskP1, day(WEEK, "8"))))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.fieldErrors())
                        .containsKey("projects[0].project_id"));

        assertThat(rows("timesheet")).isZero();
    }

    @Test
    @DisplayName("A project the caller was assigned to but that is since deleted is refused")
    void deletedProjectIsRefused() {
        UUID mine = project("Soon gone " + UUID.randomUUID());
        assignments.assign(mine, new AssignmentRequest(empA, WEEK));
        UUID aTask = task(mine, "T");
        projects.delete(mine);

        assertThatThrownBy(() -> timesheets.create(request(WEEK, mine, aTask, day(WEEK, "8"))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("A task must be a live task of that project: another project's task and a deleted task are 400")
    void taskMustBeOnTheProject() {
        assertThatThrownBy(() -> timesheets.create(request(WEEK, projectP, taskQ1, day(WEEK, "8"))))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.fieldErrors())
                        .containsKey("projects[0].tasks[0].task_id"));

        tasks.delete(taskP2);
        assertThatThrownBy(() -> timesheets.create(request(WEEK, projectP, taskP2, day(WEEK, "8"))))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.fieldErrors())
                        .containsKey("projects[0].tasks[0].task_id"));
        assertThat(timesheets.create(simple(WEEK)).status())
                .as("the live task still works")
                .isEqualTo(TimesheetStatus.DRAFT);
    }

    @Test
    @DisplayName("The week of a draft cannot be changed by a replace")
    void weekCannotChange() {
        TimesheetResponse created = timesheets.create(simple(WEEK));

        assertThatThrownBy(() -> timesheets.replace(created.id(), simple(WEEK.plusDays(7))))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.fieldErrors())
                        .containsKey("week_start_date"));
        assertThat(timesheets.get(created.id()).weekStartDate()).isEqualTo(WEEK);
    }

    @Test
    @DisplayName("A rejected replace changes nothing: the old entries are still there")
    void invalidReplaceKeepsTheOldEntries() throws Exception {
        TimesheetResponse created = timesheets.create(simple(WEEK));

        assertThatThrownBy(() -> timesheets.replace(created.id(), request(WEEK, projectP, taskP1, day(WEEK, "25"))))
                .isInstanceOf(ValidationException.class);

        assertThat(storedHours()).containsExactly("8.00");
    }

    @Test
    @DisplayName("A login with no employee record is the permission error on every operation, not a server error")
    void noEmployeeIsPermissionDenied() {
        HrmsTestApp.CURRENT_EMPLOYEE.remove();
        UUID anyId = UUID.randomUUID();

        assertThatThrownBy(() -> timesheets.create(simple(WEEK))).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> timesheets.replace(anyId, simple(WEEK))).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> timesheets.delete(anyId)).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> timesheets.get(anyId)).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> timesheets.listMine(null, null, null, null))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> timesheets.forWeek(null)).isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName(
            "List: the caller's own, newest week first, filtered by dates, status and project; never anyone else's")
    void listMine() throws Exception {
        assignments.assign(projectQ, new AssignmentRequest(empA, WEEK));
        timesheets.create(simple(WEEK));
        TimesheetResponse second = timesheets.create(simple(WEEK.plusDays(7)));
        TimesheetResponse third =
                timesheets.create(request(WEEK.plusDays(14), projectQ, taskQ1, day(WEEK.plusDays(14), "4")));
        HrmsProjectTestSchema.update("UPDATE hrms.timesheet SET status = 'SUBMITTED' WHERE id = ?", second.id());

        // Another employee's timesheet on the same project is not in the caller's list.
        assignments.assign(projectP, new AssignmentRequest(empB, WEEK));
        actAs(empB);
        timesheets.create(simple(WEEK));
        actAs(empA);

        assertThat(timesheets.listMine(null, null, null, null))
                .extracting(TimesheetResponse::weekStartDate)
                .containsExactly(WEEK.plusDays(14), WEEK.plusDays(7), WEEK);
        assertThat(timesheets.listMine(null, null, null, null))
                .allSatisfy(t -> assertThat(t.employeeId()).isEqualTo(empA));
        assertThat(timesheets.listMine(WEEK.plusDays(7), null, null, null))
                .extracting(TimesheetResponse::id)
                .containsExactly(third.id(), second.id());
        assertThat(timesheets.listMine(null, WEEK.plusDays(7), null, null)).hasSize(2);
        assertThat(timesheets.listMine(null, null, TimesheetStatus.SUBMITTED, null))
                .extracting(TimesheetResponse::id)
                .containsExactly(second.id());
        assertThat(timesheets.listMine(null, null, null, projectQ))
                .extracting(TimesheetResponse::id)
                .containsExactly(third.id());
        assertThatThrownBy(() -> timesheets.listMine(WEEK.plusDays(7), WEEK, null, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Two creates for the same week at once: exactly one wins, the other is 409, one timesheet is stored")
    void concurrentCreatesOneWins() throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Callable<Object> create = () -> {
                    TenantContext.set(tenant);
                    actAs(empA);
                    try {
                        go.await();
                        return timesheets.create(simple(WEEK));
                    } catch (RuntimeException e) {
                        return e;
                    } finally {
                        HrmsTestApp.CURRENT_EMPLOYEE.remove();
                        TenantContext.clear();
                    }
                };
                results.add(pool.submit(create));
            }
            go.countDown();

            long won = 0;
            long conflicts = 0;
            for (Future<Object> result : results) {
                Object outcome = result.get();
                if (outcome instanceof TimesheetResponse) {
                    won++;
                } else if (outcome instanceof TimesheetConflictException) {
                    conflicts++;
                } else {
                    throw new AssertionError("unexpected outcome: " + outcome);
                }
            }
            assertThat(won).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(rows("timesheet")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    /** The stored hours as the database prints them, in order: the proof of scale 2. */
    private List<String> storedHours() throws SQLException {
        List<String> hours = new ArrayList<>();
        try (Connection conn = HrmsProjectTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT hours::text FROM hrms.timesheet_day_entry WHERE tenant_id = ? ORDER BY hours")) {
            ps.setObject(1, tenant);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    hours.add(rs.getString(1));
                }
            }
        }
        return hours;
    }
}
