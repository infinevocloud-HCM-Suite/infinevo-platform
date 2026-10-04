package com.infinevo.hrms.timesheet;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * W-42.4 §7, {@code TimesheetReviewListIT}: the three review lists and the by-id read, over HTTP. The fixture is
 * {@link TimesheetReviewSupport}'s: project A under {@code m1}, B under {@code m2}; employee {@code empA} with four weeks
 * (submitted, approved, one line rejected, draft) each with a line on both projects; {@code empB} with one submitted
 * week on B; {@code reporter} over {@code empA} only.
 */
class TimesheetReviewListIT extends TimesheetReviewSupport {

    private static final String BASE = "/api/v1/hrms/timesheets";

    private ResultActions asM1(String url) throws Exception {
        actAs(m1);
        return mvc.perform(authed(get(url), subM1));
    }

    private ResultActions asM2(String url) throws Exception {
        actAs(m2);
        return mvc.perform(authed(get(url), subM2));
    }

    private ResultActions asReporter(String url) throws Exception {
        actAs(reporter);
        return mvc.perform(authed(get(url), subReporter));
    }

    private ResultActions asHr(String url) throws Exception {
        actAs(hrEmp);
        return mvc.perform(authed(get(url), subHr));
    }

    // ── managed: the project manager's list ──────────────────────────────────────────────────

    @Test
    @DisplayName("managed for M1: the three non-draft weeks that touch project A, newest first, only A's line in each")
    void managedShowsOnlyTheManagersProjects() throws Exception {
        asM1(BASE + "/managed")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.total_elements").value(3))
                .andExpect(jsonPath("$.data.content[*].id")
                        .value(contains(sheetW3.toString(), sheetW2.toString(), sheetW1.toString())))
                .andExpect(jsonPath("$.data.content[*].projects.length()").value(contains(1, 1, 1)))
                .andExpect(
                        jsonPath("$.data.content[*].projects[*].project_id").value(everyItem(is(projectA.toString()))));
    }

    @Test
    @DisplayName("W-48.3: managed rows carry employee_name, project_name and task_title, still trimmed to M1's project")
    void managedRowsCarryNames() throws Exception {
        asM1(BASE + "/managed")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].employee_name").value(everyItem(is("Test"))))
                .andExpect(jsonPath("$.data.content[*].projects.length()").value(contains(1, 1, 1)))
                .andExpect(jsonPath("$.data.content[*].projects[*].project_name")
                        .value(everyItem(org.hamcrest.Matchers.startsWith("Review A "))))
                .andExpect(jsonPath("$.data.content[*].projects[*].tasks[*].task_title")
                        .value(everyItem(is("Task A"))));
    }

    @Test
    @DisplayName("W-48.3: team, all and by-id replies carry employee_name, project_name and task_title")
    void otherListsCarryNames() throws Exception {
        asReporter(BASE + "/team")
                .andExpect(jsonPath("$.data.content[*].employee_name").value(everyItem(is("Test"))))
                .andExpect(jsonPath("$.data.content[0].projects[*].project_name")
                        .value(containsInAnyOrder(
                                org.hamcrest.Matchers.startsWith("Review A "),
                                org.hamcrest.Matchers.startsWith("Review B "))))
                .andExpect(jsonPath("$.data.content[0].projects[*].tasks[*].task_title")
                        .value(containsInAnyOrder("Task A", "Task B")));
        asHr(BASE)
                .andExpect(jsonPath("$.data.content[*].employee_name").value(everyItem(is("Test"))))
                .andExpect(jsonPath("$.data.content[*].projects[*].project_name")
                        .value(everyItem(org.hamcrest.Matchers.startsWith("Review "))))
                .andExpect(jsonPath("$.data.content[*].projects[*].tasks[*].task_title")
                        .value(everyItem(org.hamcrest.Matchers.startsWith("Task "))));
        asHr(BASE + "/" + sheetW1)
                .andExpect(jsonPath("$.data.employee_name").value("Test"))
                .andExpect(jsonPath("$.data.projects[*].tasks[*].task_title")
                        .value(containsInAnyOrder("Task A", "Task B")));
    }

    @Test
    @DisplayName(
            "managed shows the week's own status as it is, beside the manager's line: W3 is REJECTED, A's line APPROVED")
    void managedShowsTheWeekStatusAsIs() throws Exception {
        asM1(BASE + "/managed")
                .andExpect(jsonPath("$.data.content[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data.content[0].projects[0].status").value("APPROVED"));
        asM2(BASE + "/managed")
                .andExpect(jsonPath("$.data.content[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data.content[0].projects[0].status").value("REJECTED"));
    }

    @Test
    @DisplayName("managed for M2 also includes the other employee's week on B, and never a line on A")
    void managedForTheOtherManager() throws Exception {
        asM2(BASE + "/managed")
                .andExpect(jsonPath("$.data.total_elements").value(4))
                .andExpect(
                        jsonPath("$.data.content[*].projects[*].project_id").value(everyItem(is(projectB.toString()))))
                .andExpect(jsonPath("$.data.content[*].employee_id")
                        .value(containsInAnyOrder(empA.toString(), empA.toString(), empA.toString(), empB.toString())));
    }

    @Test
    @DisplayName("managed with a projectId M1 does not manage is 400; with one they do, it narrows the list")
    void managedProjectFilter() throws Exception {
        asM1(BASE + "/managed?projectId=" + projectB)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.projectId").exists());
        asM1(BASE + "/managed?projectId=" + projectA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(3));
    }

    @Test
    @DisplayName("A manager of no project gets an empty page, not an error")
    void managerOfNothingGetsAnEmptyPage() throws Exception {
        UUID other = UUID.randomUUID();
        com.infinevo.hrms.project.HrmsProjectTestSchema.insertMemberWithActions(
                tenant, other, "hrms.timesheet.approve");
        actAs(empB);
        mvc.perform(authed(get(BASE + "/managed"), other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(0))
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    // ── team: the reporting manager's list ───────────────────────────────────────────────────

    @Test
    @DisplayName("team for the reporting manager: E's three non-draft weeks, whole, and not the other employee's")
    void teamShowsTheWholeWeekOfDirectReports() throws Exception {
        asReporter(BASE + "/team")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(3))
                .andExpect(jsonPath("$.data.content[*].id")
                        .value(contains(sheetW3.toString(), sheetW2.toString(), sheetW1.toString())))
                .andExpect(jsonPath("$.data.content[*].projects.length()").value(contains(2, 2, 2)));
    }

    @Test
    @DisplayName("team with an employeeId who is not a direct report is 400, and nobody's weeks leak through it")
    void teamRefusesAnEmployeeWhoIsNotADirectReport() throws Exception {
        asReporter(BASE + "/team?employeeId=" + empB)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.employeeId").exists());
        asReporter(BASE + "/team?employeeId=" + empA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(3));
    }

    @Test
    @DisplayName("team is by direct line only: a manager with no direct reports sees nothing, whoever sits below")
    void noDirectReportsMeansNoWeeks() throws Exception {
        org.mockito.Mockito.reset(reportingLines);
        asReporter(BASE + "/team")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(0));
    }

    // ── all: HR's list ───────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("HR lists every non-draft week in the tenant, whole, and never a draft")
    void hrSeesEveryNonDraftWeek() throws Exception {
        asHr(BASE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(4))
                .andExpect(jsonPath("$.data.content[*].id")
                        .value(containsInAnyOrder(
                                sheetW1.toString(), sheetW2.toString(), sheetW3.toString(), sheetOfB.toString())))
                .andExpect(jsonPath("$.data.content[*].status")
                        .value(everyItem(org.hamcrest.Matchers.not(org.hamcrest.Matchers.equalTo("DRAFT")))));
    }

    @Test
    @DisplayName("Newest week first, then by employee id")
    void sortedNewestWeekFirst() throws Exception {
        asHr(BASE)
                .andExpect(jsonPath("$.data.content[0].week_start_date").value(W3.toString()))
                .andExpect(jsonPath("$.data.content[1].week_start_date").value(W2.toString()))
                .andExpect(jsonPath("$.data.content[2].week_start_date").value(W1.toString()))
                .andExpect(jsonPath("$.data.content[3].week_start_date").value(W1.toString()));
    }

    @Test
    @DisplayName("status filters in SQL: REJECTED gives W3 alone; DRAFT and CANCELLED are 400")
    void statusFilter() throws Exception {
        asHr(BASE + "?status=REJECTED")
                .andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(sheetW3.toString()));
        asHr(BASE + "?status=SUBMITTED")
                .andExpect(jsonPath("$.data.total_elements").value(2));
        asHr(BASE + "?status=DRAFT")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
        asHr(BASE + "?status=CANCELLED").andExpect(status().isBadRequest());
        asHr(BASE + "?status=NONSENSE").andExpect(status().isBadRequest());
        asM1(BASE + "/managed?status=DRAFT").andExpect(status().isBadRequest());
        asReporter(BASE + "/team?status=DRAFT").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("from and to bound the week start, both inclusive; from after to is 400")
    void weekRange() throws Exception {
        asHr(BASE + "?from=" + W2 + "&to=" + W3)
                .andExpect(jsonPath("$.data.total_elements").value(2))
                .andExpect(jsonPath("$.data.content[*].week_start_date").value(contains(W3.toString(), W2.toString())));
        asHr(BASE + "?from=" + W3 + "&to=" + W2).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("employeeId and projectId narrow HR's list; a project filter still returns the whole week")
    void hrFilters() throws Exception {
        asHr(BASE + "?employeeId=" + empB)
                .andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(sheetOfB.toString()));
        asHr(BASE + "?projectId=" + projectA)
                .andExpect(jsonPath("$.data.total_elements").value(3))
                .andExpect(jsonPath("$.data.content[*].projects.length()").value(contains(2, 2, 2)));
        asHr(BASE + "?projectId=" + projectB)
                .andExpect(jsonPath("$.data.total_elements").value(4));
    }

    // ── paging ───────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Paging returns size rows and the totals, and the next page the rest")
    void paging() throws Exception {
        asHr(BASE + "?size=3&page=0")
                .andExpect(jsonPath("$.data.content", hasSize(3)))
                .andExpect(jsonPath("$.data.size").value(3))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.total_elements").value(4))
                .andExpect(jsonPath("$.data.total_pages").value(2));
        asHr(BASE + "?size=3&page=1")
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.page").value(1));
        asHr(BASE + "?size=3&page=5").andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    @DisplayName("size is capped at 100; a negative page or a size under 1 is 400")
    void pagingBounds() throws Exception {
        asHr(BASE + "?size=500").andExpect(jsonPath("$.data.size").value(100));
        asHr(BASE + "?page=-1").andExpect(status().isBadRequest());
        asHr(BASE + "?size=0").andExpect(status().isBadRequest());
    }

    // ── by id ────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("By id: HR reads a whole submitted week, the reporting manager a direct report's, M1 only A's line")
    void byIdWidenedByTheSameThreeRules() throws Exception {
        asHr(BASE + "/" + sheetW1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects.length()").value(2));
        asReporter(BASE + "/" + sheetW2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects.length()").value(2));
        asM1(BASE + "/" + sheetW3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects.length()").value(1))
                .andExpect(jsonPath("$.data.projects[0].project_id").value(projectA.toString()));
    }

    @Test
    @DisplayName("By id: a draft is the owner's alone, and a week nobody may see is 404, as is a missing one")
    void byIdDraftsAndStrangers() throws Exception {
        asHr(BASE + "/" + sheetW4).andExpect(status().isNotFound());
        asM1(BASE + "/" + sheetW4).andExpect(status().isNotFound());
        asReporter(BASE + "/" + sheetW4).andExpect(status().isNotFound());
        asM1(BASE + "/" + sheetOfB).andExpect(status().isNotFound());
        asReporter(BASE + "/" + sheetOfB).andExpect(status().isNotFound());
        asHr(BASE + "/" + UUID.randomUUID()).andExpect(status().isNotFound());

        actAs(empA);
        mvc.perform(authed(get(BASE + "/" + sheetW4), subA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }
}
