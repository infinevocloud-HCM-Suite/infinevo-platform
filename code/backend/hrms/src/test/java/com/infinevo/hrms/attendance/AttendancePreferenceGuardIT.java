package com.infinevo.hrms.attendance;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Integration test for authorization and module entitlement guards on attendance preferences (W-40.1, spec section 7).
 *
 * <p>Proves:
 * <ul>
 *   <li>{@code 403} on {@code PUT} without {@code core.attendance.manage} action</li>
 *   <li>{@code 403 MODULE_NOT_ENTITLED} on both {@code GET} and {@code PUT} for a tenant without HRMS</li>
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
class AttendancePreferenceGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID tenantId;
    private UUID hrUser;
    private UUID employeeUser;

    @BeforeEach
    void setUp() throws SQLException {
        tenantId = HrmsAttendanceTestSchema.insertTenant("Guard Tenant " + UUID.randomUUID());
        HrmsTestApp.ENTITLED.put(tenantId, Set.of(PlatformModule.HRMS));

        hrUser = UUID.randomUUID();
        employeeUser = UUID.randomUUID();

        HrmsAttendanceTestSchema.insertMember(tenantId, hrUser, "hr");
        HrmsAttendanceTestSchema.insertMember(tenantId, employeeUser, "employee");
    }

    @AfterEach
    void tearDown() {
        HrmsTestApp.ENTITLED.remove(tenantId);
        TenantContext.clear();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, UUID sub) {
        return builder.header("X-Tenant-ID", tenantId.toString())
                .with(jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@hrms.test")));
    }

    @Test
    @DisplayName("PUT without core.attendance.manage returns 403 FORBIDDEN")
    void putWithoutManageActionIsForbidden() throws Exception {
        AttendancePreferenceRequest request = new AttendancePreferenceRequest(
                HoursCalculation.EVERY_SESSION, BigDecimal.valueOf(9.00), BigDecimal.valueOf(4.50), null, null, true);

        mvc.perform(authed(
                        put("/api/v1/hrms/attendance/preferences")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)),
                        employeeUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("A tenant without HRMS module entitlement receives 403 MODULE_NOT_ENTITLED on both GET and PUT")
    void tenantWithoutHrmsModuleIsRefused() throws Exception {
        // Switch tenant to Payroll only
        HrmsTestApp.ENTITLED.put(tenantId, Set.of(PlatformModule.PAYROLL));

        AttendancePreferenceRequest request = new AttendancePreferenceRequest(
                HoursCalculation.EVERY_SESSION, BigDecimal.valueOf(9.00), BigDecimal.valueOf(4.50), null, null, true);

        mvc.perform(authed(get("/api/v1/hrms/attendance/preferences"), hrUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));

        mvc.perform(authed(
                        put("/api/v1/hrms/attendance/preferences")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)),
                        hrUser))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
    }
}
