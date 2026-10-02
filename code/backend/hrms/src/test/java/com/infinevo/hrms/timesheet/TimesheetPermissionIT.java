package com.infinevo.hrms.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.hrms.project.HrmsProjectTestSchema;
import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-42.1 §7, {@code TimesheetPermissionIT}: who can reach a timesheet, over HTTP through the real filter chain, the
 * real permission check and the module guard. Plus the API contract: status codes, headers and field names.
 */
class TimesheetPermissionIT extends TimesheetItSupport {

    private static final String BASE = "/api/v1/hrms/timesheets";

    @BeforeEach
    void bind() {
        inTenant();
    }

    // ── the contract ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST is 201 with a Location and the draft in snake_case; the spec's camelCase names are accepted too")
    void postCreatesAndAcceptsBothSpellings() throws Exception {
        mvc.perform(authed(post(BASE), subA).content(body(simple(WEEK))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(BASE + "/")))
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.employee_id").value(empA.toString()))
                .andExpect(jsonPath("$.data.week_start_date").value(WEEK.toString()))
                .andExpect(
                        jsonPath("$.data.week_end_date").value(WEEK.plusDays(6).toString()))
                .andExpect(jsonPath("$.data.projects[0].project_id").value(projectP.toString()))
                .andExpect(jsonPath("$.data.projects[0].tasks[0].task_id").value(taskP1.toString()))
                .andExpect(jsonPath("$.data.projects[0].tasks[0].days[0].hours").value(8.0));

        String camel = "{\"weekStartDate\":\"" + WEEK.plusDays(7) + "\",\"projects\":[{\"projectId\":\"" + projectP
                + "\",\"tasks\":[{\"taskId\":\"" + taskP1 + "\",\"days\":[{\"date\":\"" + WEEK.plusDays(7)
                + "\",\"hours\":6.5}]}]}]}";
        mvc.perform(authed(post(BASE), subA).content(camel))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.projects[0].tasks[0].days[0].hours").value(6.5));
    }

    @Test
    @DisplayName("PUT replaces (200), GET reads it, DELETE removes it (204) and it is then 404")
    void putGetDelete() throws Exception {
        UUID id = timesheets.create(simple(WEEK)).id();

        mvc.perform(authed(put(BASE + "/" + id), subA)
                        .content(body(request(WEEK, projectP, taskP1, day(WEEK, "3"), day(WEEK.plusDays(1), "4")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects[0].tasks[0].days.length()").value(2));
        mvc.perform(authed(get(BASE + "/" + id), subA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
        mvc.perform(authed(get(BASE + "/mine?status=DRAFT&from=" + WEEK + "&to=" + WEEK), subA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(authed(delete(BASE + "/" + id), subA)).andExpect(status().isNoContent());
        mvc.perform(authed(get(BASE + "/" + id), subA)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A broken rule is 400 with the field named; a taken week and a locked draft are 409")
    void errorStatuses() throws Exception {
        mvc.perform(authed(post(BASE), subA).content(body(request(WEEK, projectP, taskP1, day(WEEK, "24.5")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors['projects[0].tasks[0].days[0].hours']")
                        .exists());
        mvc.perform(authed(post(BASE), subA).content(body(request(WEEK, projectQ, taskQ1, day(WEEK, "8")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['projects[0].project_id']").exists());

        // The request filter clears the thread's tenant when a request ends, so bind it again for a direct call.
        inTenant();
        UUID id = timesheets.create(simple(WEEK)).id();
        mvc.perform(authed(post(BASE), subA).content(body(simple(WEEK)))).andExpect(status().isConflict());
        HrmsProjectTestSchema.update("UPDATE hrms.timesheet SET status = 'SUBMITTED' WHERE id = ?", id);
        mvc.perform(authed(put(BASE + "/" + id), subA).content(body(simple(WEEK))))
                .andExpect(status().isConflict());
        mvc.perform(authed(delete(BASE + "/" + id), subA)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Malformed JSON, a non-date, a non-UUID id and an unknown status are 400, not a server error")
    void unreadableRequests() throws Exception {
        mvc.perform(authed(post(BASE), subA).content("{not json")).andExpect(status().isBadRequest());
        mvc.perform(authed(post(BASE), subA).content("{\"week_start_date\":\"05/10/2026\",\"projects\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(authed(get(BASE + "/not-a-uuid"), subA)).andExpect(status().isBadRequest());
        mvc.perform(authed(get(BASE + "/mine?status=NOPE"), subA)).andExpect(status().isBadRequest());
        mvc.perform(authed(get(BASE + "/mine?from=yesterday"), subA)).andExpect(status().isBadRequest());
    }

    // ── who can reach what ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Another employee's timesheet is 404 on GET, PUT and DELETE, and it is left untouched")
    void anotherEmployeesTimesheetIs404() throws Exception {
        UUID mine = timesheets.create(simple(WEEK)).id();
        actAs(empB);

        mvc.perform(authed(get(BASE + "/" + mine), subB)).andExpect(status().isNotFound());
        mvc.perform(authed(put(BASE + "/" + mine), subB).content(body(simple(WEEK))))
                .andExpect(status().isNotFound());
        mvc.perform(authed(delete(BASE + "/" + mine), subB)).andExpect(status().isNotFound());

        assertThat(rows("timesheet")).as("still there").isEqualTo(1);
        assertThat(HrmsProjectTestSchema.count(
                        "SELECT count(*) FROM hrms.timesheet WHERE id = ? AND employee_id = ?", mine, empA))
                .isEqualTo(1);
        mvc.perform(authed(get(BASE + "/mine"), subB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("A login with no employee record is 403 on every path, not 500")
    void loginWithNoEmployeeIs403() throws Exception {
        UUID id = UUID.randomUUID();
        HrmsTestApp.CURRENT_EMPLOYEE.remove();

        mvc.perform(authed(post(BASE), subA).content(body(simple(WEEK)))).andExpect(status().isForbidden());
        mvc.perform(authed(put(BASE + "/" + id), subA).content(body(simple(WEEK))))
                .andExpect(status().isForbidden());
        mvc.perform(authed(delete(BASE + "/" + id), subA)).andExpect(status().isForbidden());
        mvc.perform(authed(get(BASE + "/" + id), subA)).andExpect(status().isForbidden());
        mvc.perform(authed(get(BASE + "/mine"), subA)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A tenant that bought only Payroll gets the module refusal, not the timesheet")
    void payrollOnlyTenantIsRefused() throws Exception {
        HrmsTestApp.ENTITLED.put(tenant, Set.of(PlatformModule.PAYROLL));

        mvc.perform(authed(get(BASE + "/mine"), subA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
        mvc.perform(authed(post(BASE), subA).content(body(simple(WEEK))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
    }

    @Test
    @DisplayName(
            "Writing needs hrms.timesheet.submit and reading needs hrms.timesheet.read_own; neither implies the other")
    void actionGuards() throws Exception {
        UUID readOnly = UUID.randomUUID();
        UUID writeOnly = UUID.randomUUID();
        UUID neither = UUID.randomUUID();
        HrmsProjectTestSchema.insertMemberWithActions(tenant, readOnly, "hrms.timesheet.read_own");
        HrmsProjectTestSchema.insertMemberWithActions(tenant, writeOnly, "hrms.timesheet.submit");
        HrmsProjectTestSchema.insertMemberWithActions(tenant, neither, "core.employee.read");

        mvc.perform(authed(get(BASE + "/mine"), readOnly)).andExpect(status().isOk());
        mvc.perform(authed(post(BASE), readOnly).content(body(simple(WEEK)))).andExpect(status().isForbidden());

        mvc.perform(authed(post(BASE), writeOnly).content(body(simple(WEEK)))).andExpect(status().isCreated());
        mvc.perform(authed(get(BASE + "/mine"), writeOnly)).andExpect(status().isForbidden());

        mvc.perform(authed(get(BASE + "/mine"), neither)).andExpect(status().isForbidden());
        mvc.perform(authed(post(BASE), neither).content(body(simple(WEEK.plusDays(7)))))
                .andExpect(status().isForbidden());
        assertThat(rows("timesheet")).as("only the permitted write landed").isEqualTo(1);
    }
}
