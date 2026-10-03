package com.infinevo.hrms.attendance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-40.4 §7, {@code RegularizationGuardIT}: {@code 403 MODULE_NOT_ENTITLED} for a Payroll-only tenant on all three
 * endpoints; {@code employee} is {@code 403} on the all-requests {@code GET}.
 */
class RegularizationGuardIT extends RegularizationItSupport {

    private static final String RANGE = "?from=2026-10-01&to=2026-10-31";

    @Test
    @DisplayName("A Payroll-only tenant gets 403 MODULE_NOT_ENTITLED on every endpoint")
    void payrollOnlyTenantRefused() throws Exception {
        HrmsTestApp.ENTITLED.put(tenant, Set.of(PlatformModule.PAYROLL));

        mvc.perform(authed(post("/api/v1/hrms/attendance/regularizations"), employeeSub)
                        .content(body(DAY, IN_AT, OUT_AT, "x")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
        mvc.perform(authed(get("/api/v1/hrms/attendance/regularizations/mine" + RANGE), employeeSub))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
        mvc.perform(authed(get("/api/v1/hrms/attendance/regularizations" + RANGE), hrSub))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
    }

    @Test
    @DisplayName("employee is 403 on the all-requests GET, and allowed on submit and mine")
    void employeeCannotReadTheTenants() throws Exception {
        mvc.perform(authed(get("/api/v1/hrms/attendance/regularizations" + RANGE), employeeSub))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(authed(get("/api/v1/hrms/attendance/regularizations/mine" + RANGE), employeeSub))
                .andExpect(status().isOk());
        mvc.perform(authed(get("/api/v1/hrms/attendance/regularizations" + RANGE), hrSub))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A span over 93 days is 400 VALIDATION_FAILED")
    void spanTooLong() throws Exception {
        mvc.perform(authed(
                        get("/api/v1/hrms/attendance/regularizations/mine?from=2026-01-01&to=2026-10-01"), employeeSub))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
