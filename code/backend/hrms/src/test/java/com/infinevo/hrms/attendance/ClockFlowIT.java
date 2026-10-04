package com.infinevo.hrms.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * End-to-end integration test for clock-in and clock-out lifecycle (W-40.3, spec section 7 & 8).
 *
 * <p>Proves:
 * <ul>
 *   <li>in, out, in, out -> two sessions and one {@code core.attendance} row with {@code source = CLOCK}</li>
 *   <li>second clock-in while open is {@code 409 CONFLICT}</li>
 *   <li>clock-out when not open is {@code 409 CONFLICT}</li>
 *   <li>after an admin {@code PUT} for the date, clock-out returns {@code writtenToAttendance = false} and the row stays {@code ADMIN}</li>
 *   <li>server clock authority: no request body field can set the time or the employee</li>
 *   <li>clock-out > 24 hours after clock-in is {@code 409 CONFLICT} and leaves session open</li>
 *   <li>clock-in from a new date voids an unclosed earlier session with {@code NOT_CLOCKED_OUT}</li>
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
class ClockFlowIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID tenantId;
    private UUID employeeUser;
    private UUID employeeId;
    private UUID hrUser;

    @BeforeEach
    void setUp() throws SQLException {
        tenantId = HrmsAttendanceTestSchema.insertTenant("Clock Flow Tenant " + UUID.randomUUID());
        HrmsTestApp.ENTITLED.put(tenantId, Set.of(PlatformModule.HRMS));

        employeeUser = UUID.randomUUID();
        hrUser = UUID.randomUUID();

        HrmsAttendanceTestSchema.insertMember(tenantId, employeeUser, "employee");
        HrmsAttendanceTestSchema.insertMember(tenantId, hrUser, "hr");

        employeeId = HrmsAttendanceTestSchema.insertEmployee(
                tenantId, "EMP-" + UUID.randomUUID().toString().substring(0, 8));

        EmployeeResponse employeeResponse = new EmployeeResponse(
                employeeId,
                tenantId,
                "EMP-001",
                "Test",
                null,
                "Employee",
                "MALE",
                LocalDate.of(2026, 4, 1),
                null,
                EmploymentStatus.ACTIVE,
                "emp@hrms.test",
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
        HrmsTestApp.CURRENT_EMPLOYEE.set(employeeResponse);
    }

    @AfterEach
    void tearDown() {
        HrmsTestApp.ENTITLED.remove(tenantId);
        HrmsTestApp.CURRENT_EMPLOYEE.remove();
        HrmsTestApp.CUSTOM_CLOCK.remove();
        TenantContext.clear();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, UUID sub) {
        return builder.header("X-Tenant-ID", tenantId.toString())
                .with(jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@hrms.test")));
    }

    @Test
    @DisplayName("Complete clock lifecycle: in, out, in, out creates 2 sessions and 1 core.attendance row")
    void fullClockLifecycle() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 5);

        // 1. Clock in at 09:00 IST (03:30 UTC)
        Instant t1 = Instant.parse("2026-10-05T03:30:00Z");
        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(t1, ZoneOffset.UTC));

        mvc.perform(authed(post("/api/v1/hrms/attendance/clock-in"), employeeUser))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attendanceDate").value(date.toString()))
                .andExpect(jsonPath("$.clockInAt").value(t1.toString()))
                .andExpect(jsonPath("$.clockOutAt").doesNotExist())
                .andExpect(jsonPath("$.origin").value("CLOCK"));

        // Nothing written to core.attendance at clock-in
        assertThat(HrmsAttendanceTestSchema.getAttendance(tenantId, employeeId, date))
                .isNull();

        // Second clock-in while session is open -> 409 CONFLICT
        mvc.perform(authed(post("/api/v1/hrms/attendance/clock-in"), employeeUser))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        // 2. Clock out at 13:00 IST (07:30 UTC) -> 240 minutes worked (< 270 min half-day threshold) -> ABSENT
        Instant t2 = Instant.parse("2026-10-05T07:30:00Z");
        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(t2, ZoneOffset.UTC));

        mvc.perform(authed(post("/api/v1/hrms/attendance/clock-out"), employeeUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.clockOutAt").value(t2.toString()))
                .andExpect(jsonPath("$.workedMinutes").value(240))
                .andExpect(jsonPath("$.status").value("ABSENT"))
                .andExpect(jsonPath("$.writtenToAttendance").value(true));

        // Written to core.attendance with source CLOCK and status ABSENT
        HrmsAttendanceTestSchema.AttendanceRow row1 =
                HrmsAttendanceTestSchema.getAttendance(tenantId, employeeId, date);
        assertThat(row1).isNotNull();
        assertThat(row1.status()).isEqualTo("ABSENT");
        assertThat(row1.source()).isEqualTo("CLOCK");

        // 3. Second clock-in at 14:00 IST (08:30 UTC)
        Instant t3 = Instant.parse("2026-10-05T08:30:00Z");
        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(t3, ZoneOffset.UTC));

        mvc.perform(authed(post("/api/v1/hrms/attendance/clock-in"), employeeUser))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clockInAt").value(t3.toString()));

        // 4. Second clock-out at 19:00 IST (13:30 UTC) -> +300 minutes -> 540 total min (9.0 hrs) -> PRESENT
        Instant t4 = Instant.parse("2026-10-05T13:30:00Z");
        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(t4, ZoneOffset.UTC));

        mvc.perform(authed(post("/api/v1/hrms/attendance/clock-out"), employeeUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.clockOutAt").value(t4.toString()))
                .andExpect(jsonPath("$.workedMinutes").value(540))
                .andExpect(jsonPath("$.status").value("PRESENT"))
                .andExpect(jsonPath("$.writtenToAttendance").value(true));

        // Updated in core.attendance to PRESENT, still source CLOCK
        HrmsAttendanceTestSchema.AttendanceRow row2 =
                HrmsAttendanceTestSchema.getAttendance(tenantId, employeeId, date);
        assertThat(row2).isNotNull();
        assertThat(row2.status()).isEqualTo("PRESENT");
        assertThat(row2.source()).isEqualTo("CLOCK");

        // Exactly 1 row in core.attendance and 2 rows in hrms.clock_session
        assertThat(countSessions(tenantId, employeeId, date)).isEqualTo(2);

        // Verify GET /today
        mvc.perform(authed(get("/api/v1/hrms/attendance/today"), employeeUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(date.toString()))
                .andExpect(jsonPath("$.openSession").doesNotExist())
                .andExpect(jsonPath("$.workedMinutes").value(540))
                .andExpect(jsonPath("$.sessions.length()").value(2));

        // Verify GET /sessions/mine
        // W-48.4 §4: the caller's own sessions carry no name.
        mvc.perform(authed(get("/api/v1/hrms/attendance/sessions/mine?from=2026-10-01&to=2026-10-10"), employeeUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].employeeName").doesNotExist())
                .andExpect(jsonPath("$[1].employeeName").doesNotExist());

        // Verify GET /sessions as HR
        mvc.perform(authed(
                        get("/api/v1/hrms/attendance/sessions?from=2026-10-01&to=2026-10-10&employeeId=" + employeeId),
                        hrUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // W-48.4 §4: the HR log names each row's employee, from core.employee.
                .andExpect(jsonPath("$[0].employeeName").value("Test"))
                .andExpect(jsonPath("$[1].employeeName").value("Test"));
    }

    @Test
    @DisplayName(
            "Admin precedence: when core.attendance has an ADMIN row, clock-out returns writtenToAttendance = false and leaves ADMIN row untouched")
    void adminPrecedenceRespected() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 6);

        // Pre-existing ADMIN row setting status to ABSENT
        HrmsAttendanceTestSchema.insertAdminAttendance(tenantId, employeeId, date, "ABSENT");

        // Employee clocks in at 09:00 IST (03:30 UTC)
        Instant t1 = Instant.parse("2026-10-06T03:30:00Z");
        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(t1, ZoneOffset.UTC));
        mvc.perform(authed(post("/api/v1/hrms/attendance/clock-in"), employeeUser))
                .andExpect(status().isCreated());

        // Employee clocks out at 19:00 IST (13:30 UTC) (10 hours worked = PRESENT)
        Instant t2 = Instant.parse("2026-10-06T13:30:00Z");
        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(t2, ZoneOffset.UTC));

        mvc.perform(authed(post("/api/v1/hrms/attendance/clock-out"), employeeUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workedMinutes").value(600))
                .andExpect(jsonPath("$.status").value("ABSENT"))
                .andExpect(jsonPath("$.writtenToAttendance").value(false));

        // Database row is still ADMIN and ABSENT
        HrmsAttendanceTestSchema.AttendanceRow row = HrmsAttendanceTestSchema.getAttendance(tenantId, employeeId, date);
        assertThat(row).isNotNull();
        assertThat(row.status()).isEqualTo("ABSENT");
        assertThat(row.source()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Server clock authority: payload in request body is ignored and cannot set timestamps or employee")
    void serverClockAuthorityEnforced() throws Exception {
        LocalDate expectedDate = LocalDate.of(2026, 10, 7);
        Instant serverTime = Instant.parse("2026-10-07T04:00:00Z");
        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(serverTime, ZoneOffset.UTC));

        // Attacker attempts to provide a forged body with ancient time and fake employee
        String spoofBody = "{\"clockInAt\":\"2020-01-01T00:00:00Z\",\"employeeId\":\"" + UUID.randomUUID() + "\"}";

        mvc.perform(authed(
                        post("/api/v1/hrms/attendance/clock-in")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(spoofBody),
                        employeeUser))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attendanceDate").value(expectedDate.toString()))
                .andExpect(jsonPath("$.clockInAt").value(serverTime.toString()))
                .andExpect(jsonPath("$.employeeId").value(employeeId.toString()));
    }

    @Test
    @DisplayName("Clock-out with no open session returns 409 CONFLICT")
    void clockOutWithoutOpenSessionIsConflict() throws Exception {
        mvc.perform(authed(post("/api/v1/hrms/attendance/clock-out"), employeeUser))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("Clock-out more than 24 hours after clock-in returns 409 CONFLICT and leaves session open")
    void clockOutAfter24HoursIsConflictAndLeavesSessionOpen() throws Exception {
        // Clock in at Day 1 09:00 IST
        Instant t1 = Instant.parse("2026-10-08T03:30:00Z");
        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(t1, ZoneOffset.UTC));

        mvc.perform(authed(post("/api/v1/hrms/attendance/clock-in"), employeeUser))
                .andExpect(status().isCreated());

        // Clock out at Day 2 10:00 IST (> 24 hours later)
        Instant t2 = Instant.parse("2026-10-09T04:30:00Z");
        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(t2, ZoneOffset.UTC));

        mvc.perform(authed(post("/api/v1/hrms/attendance/clock-out"), employeeUser))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        // Verify session in database is still open
        try (Connection conn = HrmsAttendanceTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT clock_out_at, voided_at FROM hrms.clock_session WHERE tenant_id = ? AND employee_id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getTimestamp("clock_out_at")).isNull();
                assertThat(rs.getTimestamp("voided_at")).isNull();
            }
        }
    }

    @Test
    @DisplayName("Clock-in on a new date voids an unclosed session from an earlier date with NOT_CLOCKED_OUT")
    void clockInOnNewDateVoidsEarlierUnclosedSession() throws Exception {
        // Day 1: Clock in at 09:00 IST, forget to clock out
        Instant t1 = Instant.parse("2026-10-08T03:30:00Z");
        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(t1, ZoneOffset.UTC));

        mvc.perform(authed(post("/api/v1/hrms/attendance/clock-in"), employeeUser))
                .andExpect(status().isCreated());

        // Day 2: Clock in at 09:00 IST (2026-10-09)
        Instant t2 = Instant.parse("2026-10-09T03:30:00Z");
        HrmsTestApp.CUSTOM_CLOCK.set(Clock.fixed(t2, ZoneOffset.UTC));

        mvc.perform(authed(post("/api/v1/hrms/attendance/clock-in"), employeeUser))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attendanceDate").value("2026-10-09"));

        // Verify earlier session was voided with NOT_CLOCKED_OUT
        try (Connection conn = HrmsAttendanceTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT attendance_date, void_reason, voided_at FROM hrms.clock_session "
                                + "WHERE tenant_id = ? AND employee_id = ? ORDER BY attendance_date ASC")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                // First session (Day 1)
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDate("attendance_date").toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 8));
                assertThat(rs.getString("void_reason")).isEqualTo("NOT_CLOCKED_OUT");
                assertThat(rs.getTimestamp("voided_at")).isNotNull();

                // Second session (Day 2)
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDate("attendance_date").toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 9));
                assertThat(rs.getString("void_reason")).isNull();
                assertThat(rs.getTimestamp("voided_at")).isNull();
            }
        }
    }

    private static int countSessions(UUID tenantId, UUID employeeId, LocalDate date) throws SQLException {
        try (Connection conn = HrmsAttendanceTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM hrms.clock_session WHERE tenant_id = ? AND employee_id = ? AND attendance_date = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            ps.setDate(3, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getInt(1);
            }
        }
    }
}
