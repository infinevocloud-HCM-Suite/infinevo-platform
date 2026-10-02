package com.infinevo.hrms.timesheet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.hrms.project.HrmsProjectTestSchema;
import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-42.4 §7, {@code TimesheetReviewPermissionIT}: who may call the review endpoints, through the real filter chain,
 * permission check and module guard.
 */
class TimesheetReviewPermissionIT extends TimesheetReviewSupport {

    private static final String BASE = "/api/v1/hrms/timesheets";

    @Test
    @DisplayName("An employee holding only submit and read_own gets 403 on all three lists")
    void anEmployeeCannotUseTheReviewLists() throws Exception {
        actAs(empA);
        for (String path : new String[] {BASE + "/managed", BASE + "/team", BASE}) {
            mvc.perform(authed(get(path), subA)).andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Each list needs its own action: approve, read_team and read do not stand in for one another")
    void eachListNeedsItsOwnAction() throws Exception {
        actAs(m1);
        mvc.perform(authed(get(BASE + "/team"), subM1)).andExpect(status().isForbidden());
        mvc.perform(authed(get(BASE), subM1)).andExpect(status().isForbidden());
        actAs(reporter);
        mvc.perform(authed(get(BASE + "/managed"), subReporter)).andExpect(status().isForbidden());
        mvc.perform(authed(get(BASE), subReporter)).andExpect(status().isForbidden());
        actAs(hrEmp);
        mvc.perform(authed(get(BASE + "/managed"), subHr)).andExpect(status().isForbidden());
        mvc.perform(authed(get(BASE + "/team"), subHr)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("The reporting manager reading a week of someone who is not their report, by id, gets 404")
    void aNonReportsWeekIsNotFound() throws Exception {
        actAs(reporter);
        mvc.perform(authed(get(BASE + "/" + sheetOfB), subReporter)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A caller with none of the four read actions is refused the by-id read, whoever's week it is")
    void byIdNeedsAReadAction() throws Exception {
        UUID nothing = UUID.randomUUID();
        HrmsProjectTestSchema.insertMemberWithActions(tenant, nothing, "hrms.timesheet.submit");
        actAs(empA);
        mvc.perform(authed(get(BASE + "/" + sheetW1), nothing)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A login with no employee record is 403 on the lists, never a server error")
    void noEmployeeIsRefused() throws Exception {
        HrmsTestApp.CURRENT_EMPLOYEE.remove();
        mvc.perform(authed(get(BASE), subHr)).andExpect(status().isForbidden());
        mvc.perform(authed(get(BASE + "/managed"), subM1)).andExpect(status().isForbidden());
        mvc.perform(authed(get(BASE + "/team"), subReporter)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A tenant that bought only Payroll gets the module refusal on every list")
    void payrollOnlyTenantIsRefused() throws Exception {
        HrmsTestApp.ENTITLED.put(tenant, Set.of(PlatformModule.PAYROLL));
        actAs(hrEmp);
        mvc.perform(authed(get(BASE), subHr))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
        mvc.perform(authed(get(BASE + "/managed"), subM1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
        mvc.perform(authed(get(BASE + "/team"), subReporter))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
    }
}
