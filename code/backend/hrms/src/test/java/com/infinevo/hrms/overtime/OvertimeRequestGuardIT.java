package com.infinevo.hrms.overtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.hrms.project.HrmsProjectTestSchema;
import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * W-40.6 §7, {@code OvertimeRequestGuardIT}: {@code 403} without {@code hrms.overtime.request}; {@code 403
 * MODULE_NOT_ENTITLED} for a Payroll-only tenant; an employee id in the body is ignored; "mine" never returns a
 * colleague's row.
 */
class OvertimeRequestGuardIT extends OvertimeRequestItSupport {

    private String range() {
        return "?from=" + DAY.minusDays(5) + "&to=" + DAY.plusDays(1);
    }

    @Test
    @DisplayName("Without hrms.overtime.request both endpoints are 403 FORBIDDEN")
    void withoutTheCodeIsForbidden() throws Exception {
        UUID outsiderSub = UUID.randomUUID();
        HrmsProjectTestSchema.insertMemberWithActions(tenant, outsiderSub, "core.approval.decide");

        mvc.perform(authed(post(BASE), outsiderSub).content(body(DAY, "2", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(authed(get(BASE + "/mine" + range()), outsiderSub))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(requestCount()).isZero();
    }

    @Test
    @DisplayName("A Payroll-only tenant gets 403 MODULE_NOT_ENTITLED on both endpoints")
    void payrollOnlyTenantRefused() throws Exception {
        HrmsTestApp.ENTITLED.put(tenant, Set.of(PlatformModule.PAYROLL));

        mvc.perform(authed(post(BASE), employeeSub).content(body(DAY, "2", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
        mvc.perform(authed(get(BASE + "/mine" + range()), employeeSub))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
        assertThat(requestCount()).isZero();
    }

    @Test
    @DisplayName("An employee id in the body is ignored: the row is the caller's")
    void bodyEmployeeIdIgnored() throws Exception {
        actAs(employeeId);
        String content = "{\"employeeId\":\"" + colleagueId + "\",\"employee_id\":\"" + colleagueId
                + "\",\"overtimeDate\":\"" + DAY + "\",\"hours\":2}";
        MvcResult result = mvc.perform(authed(post(BASE), employeeSub).content(content))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = json.readTree(result.getResponse().getContentAsString());
        assertThat(created.get("employee_id").asText()).isEqualTo(employeeId.toString());
        assertThat(HrmsProjectTestSchema.count(
                        "SELECT count(*) FROM core.overtime_request WHERE tenant_id = ? AND employee_id = ?",
                        tenant,
                        colleagueId))
                .isZero();
    }

    @Test
    @DisplayName("mine returns only the caller's rows, never a colleague's")
    void mineIsOnlyMine() throws Exception {
        JsonNode mine = submitAs(employeeId, employeeSub, "2");
        JsonNode theirs = submitAs(colleagueId, colleagueSub, "4");

        actAs(employeeId);
        mvc.perform(authed(get(BASE + "/mine" + range()), employeeSub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine.get("id").asText()))
                .andExpect(jsonPath("$[0].employee_id").value(employeeId.toString()));

        actAs(colleagueId);
        mvc.perform(authed(get(BASE + "/mine" + range()), colleagueSub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(theirs.get("id").asText()));
    }

    @Test
    @DisplayName("A span over 93 days is 400 VALIDATION_FAILED")
    void spanTooLong() throws Exception {
        mvc.perform(authed(get(BASE + "/mine?from=" + DAY.minusDays(120) + "&to=" + DAY), employeeSub))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("A login not linked to an employee is 403")
    void unlinkedLoginRefused() throws Exception {
        HrmsTestApp.CURRENT_EMPLOYEE.remove();
        mvc.perform(authed(post(BASE), employeeSub).content(body(DAY, "2", null)))
                .andExpect(status().isForbidden());
        assertThat(requestCount()).isZero();
    }
}
