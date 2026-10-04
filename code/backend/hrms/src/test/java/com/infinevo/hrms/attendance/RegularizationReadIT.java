package com.infinevo.hrms.attendance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.shared.entitlement.PlatformModule;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-48.5 §7, {@code RegularizationReadIT}: {@code GET /regularizations/{id}} is visible to the owner, to HR
 * ({@code core.attendance.read}) and to the step's approver, and is {@code 404} for anyone else and for another
 * tenant's id; HR's list carries {@code employeeName}, the owner's does not.
 */
class RegularizationReadIT extends RegularizationItSupport {

    private static final String BASE = "/api/v1/hrms/attendance/regularizations";

    private UUID requestId;

    @BeforeEach
    void submitOne() throws Exception {
        JsonNode submitted = submit();
        requestId = UUID.fromString(submitted.get("id").asText());
    }

    @Test
    @DisplayName("The owner reads their own request")
    void ownerReadsOwn() throws Exception {
        actAs(employeeId);
        mvc.perform(authed(get(BASE + "/" + requestId), employeeSub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId.toString()))
                .andExpect(jsonPath("$.employeeId").value(employeeId.toString()))
                .andExpect(jsonPath("$.employeeName").doesNotExist());
    }

    @Test
    @DisplayName("HR, holding core.attendance.read, reads any request")
    void hrReadsAny() throws Exception {
        actAs(HrmsAttendanceTestSchema.insertEmployee(tenant, "REG-HR-" + UUID.randomUUID()));
        mvc.perform(authed(get(BASE + "/" + requestId), hrSub))
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
    @DisplayName("A colleague with read_own only, or a decider not on the instance, gets 404, never 403")
    void colleagueGetsNotFound() throws Exception {
        UUID colleague = HrmsAttendanceTestSchema.insertEmployee(tenant, "REG-C-" + UUID.randomUUID());
        UUID colleagueSub = UUID.randomUUID();
        HrmsAttendanceTestSchema.insertMember(tenant, colleagueSub, "employee");
        actAs(colleague);
        mvc.perform(authed(get(BASE + "/" + requestId), colleagueSub))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        UUID otherDecider = HrmsAttendanceTestSchema.insertEmployee(tenant, "REG-D-" + UUID.randomUUID());
        UUID otherDeciderSub = UUID.randomUUID();
        HrmsAttendanceTestSchema.insertMemberWithActions(tenant, otherDeciderSub, "core.approval.decide");
        actAs(otherDecider);
        mvc.perform(authed(get(BASE + "/" + requestId), otherDeciderSub)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Another tenant's id is 404, even to that tenant's HR")
    void otherTenantIsNotFound() throws Exception {
        UUID home = tenant;
        UUID other = HrmsAttendanceTestSchema.insertTenant("Regularization Other " + UUID.randomUUID());
        HrmsTestApp.ENTITLED.put(other, Set.of(PlatformModule.HRMS));
        try {
            UUID otherHrSub = UUID.randomUUID();
            HrmsAttendanceTestSchema.insertMember(other, otherHrSub, "hr");
            tenant = other;
            actAs(HrmsAttendanceTestSchema.insertEmployee(other, "REG-O-" + UUID.randomUUID()));
            mvc.perform(authed(get(BASE + "/" + requestId), otherHrSub)).andExpect(status().isNotFound());
        } finally {
            tenant = home;
            HrmsTestApp.ENTITLED.remove(other);
        }
    }

    @Test
    @DisplayName("HR's list carries employeeName; the owner's list does not")
    void allCarriesNameMineDoesNot() throws Exception {
        actAs(employeeId);
        mvc.perform(authed(get(BASE + "/mine?from=2026-10-01&to=2026-10-31"), employeeSub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeName").doesNotExist());
        mvc.perform(authed(get(BASE + "?from=2026-10-01&to=2026-10-31"), hrSub))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeName").isNotEmpty());
    }
}
