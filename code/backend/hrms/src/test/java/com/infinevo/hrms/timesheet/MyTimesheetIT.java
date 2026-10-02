package com.infinevo.hrms.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.hrms.project.AssignmentRequest;
import com.infinevo.hrms.project.HrmsProjectTestSchema;
import com.infinevo.hrms.project.HrmsTestApp;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-42.1 §7, {@code MyTimesheetIT}: {@code GET /api/v1/me/timesheet}, which replaced the W-25 placeholder. The
 * caller's timesheet for a week, or {@code data: null}.
 */
class MyTimesheetIT extends TimesheetItSupport {

    private static final String ME = "/api/v1/me/timesheet";

    /** The Monday of the real current week: what the endpoint defaults to. */
    private final LocalDate thisWeek = TimesheetRules.mondayOf(LocalDate.now());

    @BeforeEach
    void bind() {
        inTenant();
    }

    @Test
    @DisplayName("With no weekStart it returns this week's timesheet, and it is no longer the placeholder")
    void returnsThisWeek() throws Exception {
        UUID id = timesheets.create(simple(thisWeek)).id();
        timesheets.create(simple(thisWeek.minusDays(7)));

        mvc.perform(authed(get(ME), subA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.week_start_date").value(thisWeek.toString()))
                .andExpect(jsonPath("$.data.projects[0].project_id").value(projectP.toString()));
    }

    @Test
    @DisplayName("With a weekStart it returns that week's")
    void returnsTheAskedWeek() throws Exception {
        UUID last = timesheets.create(simple(thisWeek.minusDays(7))).id();

        mvc.perform(authed(get(ME + "?weekStart=" + thisWeek.minusDays(7)), subA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(last.toString()));
    }

    @Test
    @DisplayName(
            "With none it is 200 and data is null, written out, so a panel can tell nothing from a malformed answer")
    void nothingIsNullData() throws Exception {
        String body = mvc.perform(authed(get(ME), subA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("\"data\":null");
    }

    @Test
    @DisplayName("Another employee's timesheet for the week is not returned: it is the caller's or null")
    void neverAnotherEmployees() throws Exception {
        assignments.assign(projectP, new AssignmentRequest(empB, WEEK));
        actAs(empB);
        timesheets.create(simple(thisWeek));
        actAs(empA);

        String body = mvc.perform(authed(get(ME), subA))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("\"data\":null");
    }

    @Test
    @DisplayName("A cancelled timesheet is not this week's")
    void cancelledIsNotReturned() throws Exception {
        UUID id = timesheets.create(simple(thisWeek)).id();
        HrmsProjectTestSchema.update("UPDATE hrms.timesheet SET status = 'CANCELLED' WHERE id = ?", id);

        assertThat(mvc.perform(authed(get(ME), subA)).andReturn().getResponse().getContentAsString())
                .contains("\"data\":null");
    }

    @Test
    @DisplayName("A weekStart that is not a Monday, or not a date, is 400")
    void badWeekStart() throws Exception {
        mvc.perform(authed(get(ME + "?weekStart=" + thisWeek.plusDays(2)), subA))
                .andExpect(status().isBadRequest());
        mvc.perform(authed(get(ME + "?weekStart=soon"), subA)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A login with no employee record is 403, not the 500 W-25 gave on /me/employee")
    void noEmployeeIs403() throws Exception {
        HrmsTestApp.CURRENT_EMPLOYEE.remove();

        mvc.perform(authed(get(ME), subA)).andExpect(status().isForbidden());
    }
}
