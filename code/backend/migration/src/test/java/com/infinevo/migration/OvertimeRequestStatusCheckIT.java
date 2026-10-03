package com.infinevo.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.test.EnabledIfDockerAvailable;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/**
 * W-40.5 ({@code V120}): {@code core.overtime_request.status} holds {@code PENDING}, {@code APPROVED},
 * {@code REJECTED}, and {@code CANCELLED}. Run against the shipped scripts through Flyway.
 */
@EnabledIfDockerAvailable
class OvertimeRequestStatusCheckIT {

    private static final String DATABASE = "infinevo_overtime_status";
    private static final String CHECK_VIOLATION = "23514";

    private static String jdbcUrl;

    @BeforeAll
    static void migrate() {
        jdbcUrl = PostgresTestContainerInitializer.provisionAdditionalDatabase(DATABASE);
        MigrationApplication.launch(
                "--DB_URL=" + jdbcUrl,
                "--DB_MIGRATION_USERNAME=" + PostgresTestContainerInitializer.MIGRATION_USER,
                "--DB_MIGRATION_PASSWORD=" + PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    @Test
    @DisplayName("A row with status = 'PENDING' inserts successfully")
    void pendingStatusInserts() throws SQLException {
        try (Connection conn = connection()) {
            UUID tenant = insertTenant(conn);
            UUID employee = insertEmployee(conn, tenant);
            insertOvertime(conn, tenant, employee, "PENDING");

            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT status FROM core.overtime_request WHERE tenant_id = ?")) {
                ps.setObject(1, tenant);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("PENDING");
                }
            }
        }
    }

    @Test
    @DisplayName("All four valid statuses ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED') are accepted")
    void allFourStatusesAccepted() throws SQLException {
        try (Connection conn = connection()) {
            UUID tenant = insertTenant(conn);
            UUID employee = insertEmployee(conn, tenant);

            for (String status : new String[] {"PENDING", "APPROVED", "REJECTED", "CANCELLED"}) {
                insertOvertime(conn, tenant, employee, status);
            }

            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM core.overtime_request WHERE tenant_id = ?")) {
                ps.setObject(1, tenant);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getLong(1)).isEqualTo(4L);
                }
            }
        }
    }

    @Test
    @DisplayName("status = 'WAITING' is refused by the CHECK constraint")
    void waitingStatusRefused() throws SQLException {
        try (Connection conn = connection()) {
            UUID tenant = insertTenant(conn);
            UUID employee = insertEmployee(conn, tenant);

            assertThatThrownBy(() -> insertOvertime(conn, tenant, employee, "WAITING"))
                    .as("insert 'WAITING'")
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(CHECK_VIOLATION);
        }
    }

    @Test
    @DisplayName("Unknown or lowercase statuses are refused by the CHECK constraint")
    void invalidStatusesRefused() throws SQLException {
        try (Connection conn = connection()) {
            UUID tenant = insertTenant(conn);
            UUID employee = insertEmployee(conn, tenant);

            for (String bad : new String[] {"pending", "approved", "SUBMITTED", "COMPLETED", ""}) {
                assertThatThrownBy(() -> insertOvertime(conn, tenant, employee, bad))
                        .as("insert '%s'", bad)
                        .isInstanceOf(PSQLException.class)
                        .extracting(e -> ((PSQLException) e).getSQLState())
                        .isEqualTo(CHECK_VIOLATION);
            }
        }
    }

    private static UUID insertTenant(Connection conn) throws SQLException {
        UUID tenant = UUID.randomUUID();
        try (PreparedStatement ps =
                conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, 'OT Status Tenant')")) {
            ps.setObject(1, tenant);
            ps.executeUpdate();
        }
        return tenant;
    }

    private static UUID insertEmployee(Connection conn, UUID tenant) throws SQLException {
        UUID empId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.employee (id, tenant_id, employee_number, first_name, work_email, date_of_joining, status) "
                        + "VALUES (?, ?, ?, 'Test', ?, DATE '2026-04-01', 'ACTIVE')")) {
            ps.setObject(1, empId);
            ps.setObject(2, tenant);
            ps.setString(3, "EMP-" + empId.toString().substring(0, 8));
            ps.setString(4, "emp-" + empId.toString().substring(0, 8) + "@test.com");
            ps.executeUpdate();
        }
        return empId;
    }

    private static void insertOvertime(Connection conn, UUID tenant, UUID employee, String status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.overtime_request (tenant_id, employee_id, overtime_date, hours, status, source) "
                        + "VALUES (?, ?, CURRENT_DATE, 2.00, ?, 'REQUEST')")) {
            ps.setObject(1, tenant);
            ps.setObject(2, employee);
            ps.setString(3, status);
            ps.executeUpdate();
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }
}
