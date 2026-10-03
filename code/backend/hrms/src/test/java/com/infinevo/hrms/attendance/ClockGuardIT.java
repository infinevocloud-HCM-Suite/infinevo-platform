package com.infinevo.hrms.attendance;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Integration test for authorization and module entitlement guards on clock endpoints (W-40.3, spec section 7).
 *
 * <p>Proves:
 * <ul>
 *   <li>{@code 403 FORBIDDEN} without {@code hrms.attendance.mark} on clock-in, clock-out, today</li>
 *   <li>{@code /sessions} is {@code 403 FORBIDDEN} for an employee without {@code core.attendance.read}</li>
 *   <li>{@code 403 MODULE_NOT_ENTITLED} on all endpoints for a tenant without {@link PlatformModule#HRMS}</li>
 * </ul>
 */
@SpringBootTest(classes = HrmsTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            HrmsAttendanceTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class ClockGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private UUID tenantId;
    private UUID hrUser;
    private UUID employeeUser;
    private UUID unprivilegedUser;

    @BeforeEach
    void setUp() throws SQLException {
        tenantId = HrmsAttendanceTestSchema.insertTenant("Clock Guard Tenant " + UUID.randomUUID());
        HrmsTestApp.ENTITLED.put(tenantId, Set.of(PlatformModule.HRMS));

        hrUser = UUID.randomUUID();
        employeeUser = UUID.randomUUID();
        unprivilegedUser = UUID.randomUUID();

        HrmsAttendanceTestSchema.insertMember(tenantId, hrUser, "hr");
        HrmsAttendanceTestSchema.insertMember(tenantId, employeeUser, "employee");
        // user with no actions/roles
        HrmsAttendanceTestSchema.insertMemberWithActions(tenantId, unprivilegedUser);
    }

    @AfterEach
    void tearDown() {
        HrmsTestApp.ENTITLED.remove(tenantId);
        HrmsTestApp.CURRENT_EMPLOYEE.remove();
        TenantContext.clear();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, UUID sub) {
        return builder.header("X-Tenant-ID", tenantId.toString())
                .with(jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@hrms.test")));
    }

    @Test
    @DisplayName("Clock-in without hrms.attendance.mark returns 403 FORBIDDEN")
    void clockInWithoutMarkActionIsForbidden() throws Exception {
        mvc.perform(authed(post("/api/v1/hrms/attendance/clock-in"), unprivilegedUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Clock-out without hrms.attendance.mark returns 403 FORBIDDEN")
    void clockOutWithoutMarkActionIsForbidden() throws Exception {
        mvc.perform(authed(post("/api/v1/hrms/attendance/clock-out"), unprivilegedUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Today summary without hrms.attendance.mark returns 403 FORBIDDEN")
    void todayWithoutMarkActionIsForbidden() throws Exception {
        mvc.perform(authed(get("/api/v1/hrms/attendance/today"), unprivilegedUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Tenant sessions query /sessions is 403 FORBIDDEN for employee role without core.attendance.read")
    void allSessionsForbiddenForEmployeeRole() throws Exception {
        mvc.perform(authed(get("/api/v1/hrms/attendance/sessions?from=2026-10-01&to=2026-10-02"), employeeUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Tenant without HRMS entitlement receives 403 MODULE_NOT_ENTITLED on every endpoint")
    void tenantWithoutHrmsModuleIsRefused() throws Exception {
        // Switch tenant to Payroll only
        HrmsTestApp.ENTITLED.put(tenantId, Set.of(PlatformModule.PAYROLL));

        mvc.perform(authed(post("/api/v1/hrms/attendance/clock-in"), employeeUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));

        mvc.perform(authed(post("/api/v1/hrms/attendance/clock-out"), employeeUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));

        mvc.perform(authed(get("/api/v1/hrms/attendance/today"), employeeUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));

        mvc.perform(authed(get("/api/v1/hrms/attendance/sessions/mine?from=2026-10-01&to=2026-10-02"), employeeUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));

        mvc.perform(authed(get("/api/v1/hrms/attendance/sessions?from=2026-10-01&to=2026-10-02"), hrUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
    }
}
