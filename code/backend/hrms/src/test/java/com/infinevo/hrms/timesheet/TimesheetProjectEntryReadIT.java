package com.infinevo.hrms.timesheet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.hrms.project.HrmsProjectTestSchema;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-42.3 §7, {@code TimesheetProjectEntryReadIT}: the approver reads the one project line they are asked to decide,
 * and nobody else does.
 */
class TimesheetProjectEntryReadIT extends TimesheetApprovalSupport {

    private static final LocalDate W1 = LocalDate.of(2026, 10, 5);
    private static final String BASE = "/api/v1/hrms/timesheets/project-entries/";

    private UUID lineA;
    private UUID lineB;
    private UUID sheet;

    private void submitted() throws Exception {
        sheet = draftWeek(W1);
        submitService.submit(sheet);
        lineA = entryId(sheet, projectA);
        lineB = entryId(sheet, projectB);
    }

    @Test
    @DisplayName("The assignee reads the line: week, employee, project, tasks and days, status and reason")
    void theAssigneeReadsTheLine() throws Exception {
        submitted();

        actAs(m1);
        mvc.perform(authed(get(BASE + lineA), subM1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(lineA.toString()))
                .andExpect(jsonPath("$.data.timesheet_id").value(sheet.toString()))
                .andExpect(jsonPath("$.data.employee_id").value(empA.toString()))
                .andExpect(jsonPath("$.data.week_start_date").value(W1.toString()))
                .andExpect(jsonPath("$.data.project_id").value(projectA.toString()))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.tasks[0].task_id").value(taskA.toString()))
                .andExpect(jsonPath("$.data.tasks[0].days[0].hours").value(4.0))
                // W-48.3 §4: names read at reply time from core.employee and hrms's project and task.
                .andExpect(jsonPath("$.data.employee_name").value("Test"))
                .andExpect(jsonPath("$.data.project_name").value(org.hamcrest.Matchers.startsWith("Approval A ")))
                .andExpect(jsonPath("$.data.tasks[0].task_title").value("Task A"));
    }

    @Test
    @DisplayName("Another manager gets 404 for a line that is not theirs; so does a missing id")
    void anotherManagerGetsNotFound() throws Exception {
        submitted();

        actAs(m2);
        mvc.perform(authed(get(BASE + lineA), subM2)).andExpect(status().isNotFound());
        mvc.perform(authed(get(BASE + UUID.randomUUID()), subM2)).andExpect(status().isNotFound());
        mvc.perform(authed(get(BASE + lineB), subM2)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("An employee without hrms.timesheet.approve gets 403, even for their own line")
    void anEmployeeIsRefused() throws Exception {
        submitted();

        actAs(empA);
        mvc.perform(authed(get(BASE + lineA), subA)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A holder of approve who is assigned no step on the line gets 404: the action alone opens nothing")
    void approveWithoutAStepOnTheLineIsNotEnough() throws Exception {
        submitted();
        UUID bystander = HrmsProjectTestSchema.insertEmployee(tenant, "BYS-" + UUID.randomUUID());
        UUID subBystander = UUID.randomUUID();
        HrmsProjectTestSchema.insertMemberWithActions(tenant, subBystander, "hrms.timesheet.approve");

        actAs(bystander);
        mvc.perform(authed(get(BASE + lineA), subBystander)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("After a decision the assignee still reads the line, with its status and the rejection reason")
    void theLineIsStillReadableAfterTheDecision() throws Exception {
        submitted();
        decide(m2, newestStep(lineB), com.infinevo.core.approval.ApprovalDecision.REJECTED, "Wrong task");

        actAs(m2);
        mvc.perform(authed(get(BASE + lineB), subM2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejection_reason").value("Wrong task"));
    }
}
