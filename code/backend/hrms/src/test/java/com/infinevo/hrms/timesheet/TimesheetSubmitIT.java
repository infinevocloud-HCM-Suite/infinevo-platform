package com.infinevo.hrms.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalStep;
import com.infinevo.core.employee.Employee;
import com.infinevo.hrms.project.HrmsProjectTestSchema;
import com.infinevo.hrms.project.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-42.3 §7, {@code TimesheetSubmitIT}: submitting a week starts one approval per project, each step on its project's
 * manager, all or nothing.
 */
class TimesheetSubmitIT extends TimesheetApprovalSupport {

    private static final LocalDate W1 = LocalDate.of(2026, 10, 5);
    private static final String BASE = "/api/v1/hrms/timesheets";

    @Test
    @DisplayName("A two-project week gives two instances, one per line, each step assigned to its project's manager")
    void oneInstancePerProjectLineEachOnItsManager() throws Exception {
        UUID sheet = draftWeek(W1);

        TimesheetResponse submitted = submitService.submit(sheet);

        assertThat(submitted.status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(submitted.submittedAt()).isNotNull();
        assertThat(submitted.projects()).allSatisfy(p -> assertThat(p.status()).isEqualTo(TimesheetStatus.SUBMITTED));

        UUID lineA = entryId(sheet, projectA);
        UUID lineB = entryId(sheet, projectB);
        List<ApprovalInstance> forA = instancesOf(lineA);
        List<ApprovalInstance> forB = instancesOf(lineB);
        assertThat(forA).hasSize(1);
        assertThat(forB).hasSize(1);
        assertThat(forA.get(0).getSubjectTable()).isEqualTo(SUBJECT_TABLE);
        assertThat(forA.get(0).getSubjectEmployeeId()).isEqualTo(empA);

        ApprovalStep stepA = newestStep(lineA);
        ApprovalStep stepB = newestStep(lineB);
        assertThat(stepA.getItemRef()).isEqualTo(projectA.toString());
        assertThat(stepA.getAssigneeEmployeeId()).isEqualTo(m1);
        assertThat(stepB.getItemRef()).isEqualTo(projectB.toString());
        assertThat(stepB.getAssigneeEmployeeId()).isEqualTo(m2);
        assertThat(HrmsProjectTestSchema.count(
                        "SELECT count(*) FROM core.approval_instance WHERE tenant_id = ? AND subject_table = ?",
                        tenant,
                        SUBJECT_TABLE))
                .as("only the two lines have instances: nothing is started per week")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("A second submit is 409, over the service and over HTTP, and starts nothing")
    void secondSubmitIsRefused() throws Exception {
        UUID sheet = draftWeek(W1);
        submitService.submit(sheet);

        assertThatThrownBy(() -> submitService.submit(sheet)).isInstanceOf(TimesheetConflictException.class);
        mvc.perform(authed(put(BASE + "/" + sheet + "/submit"), subA)).andExpect(status().isConflict());
        assertThat(HrmsProjectTestSchema.count(
                        "SELECT count(*) FROM core.approval_instance WHERE tenant_id = ? AND subject_table = ?",
                        tenant,
                        SUBJECT_TABLE))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("PUT /{id}/submit is 200 with the week as SUBMITTED, in the standard envelope")
    void submitOverHttp() throws Exception {
        UUID sheet = draftWeek(W1);

        mvc.perform(authed(put(BASE + "/" + sheet + "/submit"), subA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.projects[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.submitted_at").isNotEmpty());
    }

    @Test
    @DisplayName("Another employee's id is 404, and nothing of theirs changes")
    void anotherEmployeesWeekIsNotFound() throws Exception {
        UUID sheet = draftWeek(W1);

        actAs(empB);
        mvc.perform(authed(put(BASE + "/" + sheet + "/submit"), subB)).andExpect(status().isNotFound());
        inTenant();
        assertThatThrownBy(() -> submitService.submit(sheet)).isInstanceOf(ResourceNotFoundException.class);

        assertThat(weekStatus(sheet)).isEqualTo(TimesheetStatus.DRAFT);
        assertThat(instancesOf(entryId(sheet, projectA))).isEmpty();
    }

    @Test
    @DisplayName("With no TIMESHEET definition in force the week stays a draft, nothing starts, and the answer is 409")
    void noDefinitionLeavesTheWeekADraft() throws Exception {
        UUID sheet = draftWeek(W1);
        HrmsProjectTestSchema.update(
                "DELETE FROM core.approval_definition WHERE tenant_id = ? AND flow_type = 'TIMESHEET'", tenant);

        assertThatThrownBy(() -> submitService.submit(sheet))
                .isInstanceOf(TimesheetConflictException.class)
                .hasMessageContaining("TIMESHEET");
        mvc.perform(authed(put(BASE + "/" + sheet + "/submit"), subA)).andExpect(status().isConflict());

        assertThat(weekStatus(sheet)).isEqualTo(TimesheetStatus.DRAFT);
        assertThat(lineStatus(sheet, projectA)).isEqualTo(TimesheetStatus.DRAFT);
        assertThat(lineStatus(sheet, projectB)).isEqualTo(TimesheetStatus.DRAFT);
        assertThat(HrmsProjectTestSchema.count(
                        "SELECT count(*) FROM core.approval_instance WHERE tenant_id = ?", tenant))
                .isZero();
    }

    @Test
    @DisplayName(
            "A project the employee manages themself goes to their reporting manager; with none the step is unassigned")
    void selfManagedProjectGoesToTheReportingManager() throws Exception {
        UUID ownProject;
        UUID ownTask;
        UUID boss = HrmsProjectTestSchema.insertEmployee(tenant, "BOSS-" + UUID.randomUUID());
        ownProject = managedProject("Own " + UUID.randomUUID(), empA);
        ownTask = task(ownProject, "Own task");
        assignments.assign(ownProject, new com.infinevo.hrms.project.AssignmentRequest(empA, W1.minusDays(30)));
        Employee reportingManager = mock(Employee.class);
        when(reportingManager.getId()).thenReturn(boss);
        when(reportingLineService.chainAbove(any(), any())).thenReturn(List.of(reportingManager));

        UUID sheet = timesheets
                .create(request(W1, ownProject, ownTask, day(W1, "2")))
                .id();
        submitService.submit(sheet);
        assertThat(newestStep(entryId(sheet, ownProject)).getAssigneeEmployeeId())
                .isEqualTo(boss);

        when(reportingLineService.chainAbove(any(), any())).thenReturn(List.of());
        UUID next = timesheets
                .create(request(W1.plusDays(7), ownProject, ownTask, day(W1.plusDays(7), "2")))
                .id();
        submitService.submit(next);
        assertThat(newestStep(entryId(next, ownProject)).getAssigneeEmployeeId())
                .as("nobody approves their own hours, and with no reporting line nobody is assigned")
                .isNull();
    }
}
