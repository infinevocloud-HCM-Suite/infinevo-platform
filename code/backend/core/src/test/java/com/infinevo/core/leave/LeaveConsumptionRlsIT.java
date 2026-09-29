package com.infinevo.core.leave;

import static com.infinevo.core.leave.LeaveTestSchema.TENANT_A;
import static com.infinevo.core.leave.LeaveTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test verifying Row-Level Security for leave_consumption and leave_monthly_lop (W-16.4a, spec section 7).
 */
@SpringBootTest(classes = LeaveTestApp.class)
class LeaveConsumptionRlsIT extends AbstractIntegrationTest {

    private static UUID empA;
    private static UUID empB;
    private static UUID typeA;
    private static UUID typeB;
    private static UUID allocA;
    private static UUID allocB;

    @BeforeAll
    static void setup() throws Exception {
        LeaveTestSchema.apply();
        LeaveTestSchema.seedTenants();

        empA = LeaveTestSchema.insertEmployee(TENANT_A, "EMP-CA-01", "Alice", "alice@a.test");
        empB = LeaveTestSchema.insertEmployee(TENANT_B, "EMP-CB-01", "Bob", "bob@b.test");

        try (Connection conn = LeaveTestSchema.migrationConnection()) {
            typeA = insertLeaveType(conn, TENANT_A, "AL-A", "Annual Leave A");
            typeB = insertLeaveType(conn, TENANT_B, "AL-B", "Annual Leave B");

            allocA = insertAllocation(conn, TENANT_A, empA, typeA, "2026", new BigDecimal("20.00"));
            allocB = insertAllocation(conn, TENANT_B, empB, typeB, "2026", new BigDecimal("20.00"));

            // Insert consumption in Tenant A and Tenant B
            insertConsumption(conn, TENANT_A, empA, allocA, new BigDecimal("3.00"), "2026-04");
            insertConsumption(conn, TENANT_B, empB, allocB, new BigDecimal("5.00"), "2026-04");

            // Insert monthly LOP in Tenant A and Tenant B
            insertMonthlyLop(conn, TENANT_A, empA, typeA, new BigDecimal("1.00"), "2026-04");
            insertMonthlyLop(conn, TENANT_B, empB, typeB, new BigDecimal("2.00"), "2026-04");
        }
    }

    @AfterAll
    static void cleanup() throws SQLException {
        LeaveTestSchema.clearAll();
    }

    @Test
    @DisplayName("Tenant A cannot read Tenant B's leave_consumption rows")
    void tenantACannotReadTenantBConsumption() throws SQLException {
        assertThat(LeaveTestSchema.visibleLeaveConsumptionCount(TENANT_A)).isEqualTo(1);
        assertThat(LeaveTestSchema.visibleLeaveConsumptionCount(TENANT_B)).isEqualTo(1);
    }

    @Test
    @DisplayName("Tenant A cannot read Tenant B's leave_monthly_lop rows")
    void tenantACannotReadTenantBMonthlyLop() throws SQLException {
        assertThat(LeaveTestSchema.visibleLeaveMonthlyLopCount(TENANT_A)).isEqualTo(1);
        assertThat(LeaveTestSchema.visibleLeaveMonthlyLopCount(TENANT_B)).isEqualTo(1);
    }

    private static UUID insertLeaveType(Connection conn, UUID tenantId, String code, String name) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.leave_type (id, tenant_id, code, name, is_paid, unit, allow_half_day, valid_from, is_active) "
                        + "VALUES (?, ?, ?, ?, true, 'DAYS', true, CURRENT_DATE, true)")) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, name);
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID insertAllocation(
            Connection conn, UUID tenantId, UUID employeeId, UUID leaveTypeId, String year, BigDecimal days)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.leave_allocation (id, tenant_id, employee_id, leave_type_id, leave_year, year_start_date, year_end_date, entitlement_days, pro_rate_factor, carry_forward_days) "
                        + "VALUES (?, ?, ?, ?, ?, '2026-01-01', '2026-12-31', ?, 1.0, 0)")) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, employeeId);
            ps.setObject(4, leaveTypeId);
            ps.setString(5, year);
            ps.setBigDecimal(6, days);
            ps.executeUpdate();
        }
        return id;
    }

    private static void insertConsumption(
            Connection conn, UUID tenantId, UUID empId, UUID allocId, BigDecimal days, String period)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.leave_consumption (tenant_id, employee_id, allocation_id, consumed_days, consumed_on, period) "
                        + "VALUES (?, ?, ?, ?, CURRENT_DATE, ?)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, empId);
            ps.setObject(3, allocId);
            ps.setBigDecimal(4, days);
            ps.setString(5, period);
            ps.executeUpdate();
        }
    }

    private static void insertMonthlyLop(
            Connection conn, UUID tenantId, UUID empId, UUID typeId, BigDecimal days, String period)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.leave_monthly_lop (tenant_id, employee_id, period, leave_type_id, lop_days) "
                        + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, empId);
            ps.setString(3, period);
            ps.setObject(4, typeId);
            ps.setBigDecimal(5, days);
            ps.executeUpdate();
        }
    }
}
