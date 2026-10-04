package com.infinevo.hrms.overtime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * W-48.5 §7, {@code OvertimeRequestReadIT}: {@code GET /overtime-requests/{id}} is visible to the owner, to a holder
 * of {@code core.overtime.read} and to a step's approver, and is {@code 404} for anyone else and for another tenant's
 * id.
 */
class OvertimeRequestReadIT extends OvertimeRequestItSupport {

    private UUID requestId;

    @BeforeEach
    void submitOne() throws Exception {
        requestId =
                UUID.fromString(submitAs(employeeId, employeeSub, "2").get("id").asText());
    }

    @Test
    @DisplayName("The owner reads their own request")
    void ownerReadsOwn() throws Exception {
        actAs(employeeId);
        mvc.perform(authed(get(BASE + "/" + requestId), employeeSub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.employee_id").value(employeeId.toString()));
    }

    @Test
    @DisplayName("A holder of core.overtime.read reads any request")
    void readerReadsAny() throws Exception {
        UUID reader = HrmsProjectTestSchema.insertEmployee(tenant, "OT-R-" + UUID.randomUUID());
        UUID readerSub = UUID.randomUUID();
        HrmsProjectTestSchema.insertMemberWithActions(tenant, readerSub, "core.overtime.read");
        actAs(reader);
        mvc.perform(authed(get(BASE + "/" + requestId), readerSub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()));
    }

    @Test
    @DisplayName("The step's approver reads it")
    void approverReads() throws Exception {
        actAs(managerId);
        mvc.perform(authed(get(BASE + "/" + requestId), managerSub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()));
    }

    @Test
    @DisplayName("A colleague with no read, or a decider not on the instance, gets 404, never 403")
    void othersGetNotFound() throws Exception {
        actAs(colleagueId);
        mvc.perform(authed(get(BASE + "/" + requestId), colleagueSub))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        UUID otherDecider = HrmsProjectTestSchema.insertEmployee(tenant, "OT-D-" + UUID.randomUUID());
        UUID otherDeciderSub = UUID.randomUUID();
        HrmsProjectTestSchema.insertMemberWithActions(
                tenant, otherDeciderSub, "core.approval.decide", "hrms.overtime.request");
        actAs(otherDecider);
        mvc.perform(authed(get(BASE + "/" + requestId), otherDeciderSub))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Another tenant's id is 404, even to that tenant's reader")
    void otherTenantIsNotFound() throws Exception {
        UUID home = tenant;
        UUID other = HrmsProjectTestSchema.insertTenant("Overtime Other " + UUID.randomUUID());
        HrmsTestApp.ENTITLED.put(other, Set.of(PlatformModule.HRMS));
        try {
            UUID otherSub = UUID.randomUUID();
            HrmsProjectTestSchema.insertMemberWithActions(other, otherSub, "core.overtime.read");
            tenant = other;
            actAs(HrmsProjectTestSchema.insertEmployee(other, "OT-O-" + UUID.randomUUID()));
            mvc.perform(authed(get(BASE + "/" + requestId), otherSub)).andExpect(status().isNotFound());
        } finally {
            tenant = home;
            HrmsTestApp.ENTITLED.remove(other);
        }
    }
}
