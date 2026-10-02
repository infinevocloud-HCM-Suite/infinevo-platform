package com.infinevo.hrms.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.hrms.project.HrmsProjectTestSchema;
import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.shared.entitlement.PlatformModule;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * W-42.4 §7, {@code TimesheetReviewRlsIT}: tenant isolation of the review reads. Tenant A has weeks in every status;
 * tenant B's HR sees none of them, by list or by id, and a connection with no tenant bound sees no row at all.
 */
class TimesheetReviewRlsIT extends TimesheetReviewSupport {

    private static final String BASE = "/api/v1/hrms/timesheets";

    @Test
    @DisplayName("Tenant B's HR sees none of tenant A's weeks: the list is empty and A's ids are 404")
    void anotherTenantsHrSeesNothing() throws Exception {
        UUID tenantB = HrmsProjectTestSchema.insertTenant("Review RLS B " + UUID.randomUUID());
        HrmsTestApp.ENTITLED.put(tenantB, Set.of(PlatformModule.HRMS));
        UUID hrB = HrmsProjectTestSchema.insertEmployee(tenantB, "HR-B-" + UUID.randomUUID());
        UUID subHrB = UUID.randomUUID();
        HrmsProjectTestSchema.insertMemberWithActions(tenantB, subHrB, "hrms.timesheet.read");
        actAs(hrB);
        try {
            mvc.perform(get(BASE)
                            .header("X-Tenant-ID", tenantB.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .with(jwt().jwt(j -> j.subject(subHrB.toString()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total_elements").value(0));
            mvc.perform(get(BASE + "/" + sheetW1)
                            .header("X-Tenant-ID", tenantB.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .with(jwt().jwt(j -> j.subject(subHrB.toString()))))
                    .andExpect(status().isNotFound());
            mvc.perform(get(BASE + "?projectId=" + projectA)
                            .header("X-Tenant-ID", tenantB.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .with(jwt().jwt(j -> j.subject(subHrB.toString()))))
                    .andExpect(jsonPath("$.data.total_elements").value(0));
        } finally {
            HrmsTestApp.ENTITLED.remove(tenantB);
        }
    }

    @Test
    @DisplayName(
            "The same HR in tenant A sees the weeks, so the empty answer above is isolation and not an empty fixture")
    void tenantAHrSeesThem() throws Exception {
        actAs(hrEmp);
        mvc.perform(authed(get(BASE), subHr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_elements").value(4));
    }

    @Test
    @DisplayName("A connection with no tenant bound sees zero rows in the timesheet tables")
    void noTenantSeesNothing() throws Exception {
        try (Connection conn = HrmsProjectTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            for (String table :
                    new String[] {"timesheet", "timesheet_project_entry", "timesheet_task_entry", "timesheet_day_entry"
                    }) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM hrms." + table);
                        ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getLong(1)).as(table).isZero();
                }
            }
        }
    }
}
