package com.infinevo.hrms.attendance;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
 * End-to-end integration test for attendance preferences API lifecycle (W-40.1, spec section 7 & 8).
 *
 * <p>Proves:
 * <ul>
 *   <li>{@code GET} before any {@code PUT} returns {@code 200} with system defaults and {@code isDefault: true}</li>
 *   <li>{@code PUT} then {@code GET} round-trips every field at scale 2 with {@code isDefault: false}</li>
 *   <li>A second {@code PUT} updates the existing row so the database row count remains 1</li>
 *   <li>An audit row is written to {@code core.audit_log}</li>
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
class AttendancePreferenceIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID tenantId;
    private UUID hrUser;

    @BeforeEach
    void setUp() throws SQLException {
        tenantId = HrmsAttendanceTestSchema.insertTenant("Attendance Pref Tenant " + UUID.randomUUID());
        HrmsTestApp.ENTITLED.put(tenantId, Set.of(PlatformModule.HRMS));

        hrUser = UUID.randomUUID();
        HrmsAttendanceTestSchema.insertMember(tenantId, hrUser, "hr");
    }

    @AfterEach
    void tearDown() {
        HrmsTestApp.ENTITLED.remove(tenantId);
        TenantContext.clear();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Tenant-ID", tenantId.toString())
                .with(jwt().jwt(j -> j.subject(hrUser.toString()).claim("email", hrUser + "@hrms.test")));
    }

    @Test
    @DisplayName("GET before any PUT returns 200 with system defaults and isDefault = true")
    void getBeforePutReturnsDefaults() throws Exception {
        mvc.perform(authed(get("/api/v1/hrms/attendance/preferences")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.hoursCalculation").value("EVERY_SESSION"))
                .andExpect(jsonPath("$.fullDayMinimumHours").value(9.00))
                .andExpect(jsonPath("$.halfDayMinimumHours").value(4.50))
                .andExpect(jsonPath("$.regularizationWindowDays").doesNotExist())
                .andExpect(jsonPath("$.maxRegularizationsPerMonth").doesNotExist())
                .andExpect(jsonPath("$.allowRegularizationWithoutSession").value(true));
    }

    @Test
    @DisplayName("PUT then GET round-trips every field at scale 2, leaves 1 row on second PUT, and audits")
    void putAndGetRoundTripsAndAudits() throws Exception {
        AttendancePreferenceRequest initialRequest = new AttendancePreferenceRequest(
                HoursCalculation.FIRST_IN_LAST_OUT, new BigDecimal("8.50"), new BigDecimal("4.25"), 14, 3, true);

        mvc.perform(authed(put("/api/v1/hrms/attendance/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialRequest))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(false))
                .andExpect(jsonPath("$.hoursCalculation").value("FIRST_IN_LAST_OUT"))
                .andExpect(jsonPath("$.fullDayMinimumHours").value(8.50))
                .andExpect(jsonPath("$.halfDayMinimumHours").value(4.25))
                .andExpect(jsonPath("$.regularizationWindowDays").value(14))
                .andExpect(jsonPath("$.maxRegularizationsPerMonth").value(3))
                .andExpect(jsonPath("$.allowRegularizationWithoutSession").value(true));

        // GET confirms persisted values
        mvc.perform(authed(get("/api/v1/hrms/attendance/preferences")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(false))
                .andExpect(jsonPath("$.hoursCalculation").value("FIRST_IN_LAST_OUT"))
                .andExpect(jsonPath("$.fullDayMinimumHours").value(8.50))
                .andExpect(jsonPath("$.halfDayMinimumHours").value(4.25))
                .andExpect(jsonPath("$.regularizationWindowDays").value(14))
                .andExpect(jsonPath("$.maxRegularizationsPerMonth").value(3))
                .andExpect(jsonPath("$.allowRegularizationWithoutSession").value(true));

        // Second PUT modifies existing record
        AttendancePreferenceRequest secondRequest = new AttendancePreferenceRequest(
                HoursCalculation.EVERY_SESSION, new BigDecimal("8.00"), new BigDecimal("4.00"), 30, 5, false);

        mvc.perform(authed(put("/api/v1/hrms/attendance/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(false))
                .andExpect(jsonPath("$.hoursCalculation").value("EVERY_SESSION"))
                .andExpect(jsonPath("$.fullDayMinimumHours").value(8.00))
                .andExpect(jsonPath("$.halfDayMinimumHours").value(4.00))
                .andExpect(jsonPath("$.regularizationWindowDays").value(30))
                .andExpect(jsonPath("$.maxRegularizationsPerMonth").value(5))
                .andExpect(jsonPath("$.allowRegularizationWithoutSession").value(false));

        // Proves single row in database
        assertThat(countPreferenceRows(tenantId)).isEqualTo(1);

        // Proves audit row is written to core.audit_log
        int auditCount = HrmsAttendanceTestSchema.countAuditRows(tenantId, "attendance_preference");
        assertThat(auditCount).isGreaterThanOrEqualTo(1);
    }

    private int countPreferenceRows(UUID tenantId) throws SQLException {
        try (Connection conn = HrmsAttendanceTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM hrms.attendance_preference WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
