package com.infinevo.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.test.EnabledIfDockerAvailable;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W-11.3 §6 steps 3, 6 and 7 — a tenant that existed before {@code V025__catalogue_correction.sql}.
 *
 * <p>{@link RoleCatalogueIT} inserts its tenants after every script has run, so it proves the
 * replaced seed function but never the move of existing grants. Here the shipped tree is migrated
 * only to V024, a tenant is inserted (the V022 trigger seeds it with the old {@code hrms.*} codes and
 * {@code core.tenant.provision} on {@code platform-admin}), and then the rest is applied. The
 * pre-existing tenant must end up with exactly the grants a tenant created after V025 gets.
 */
@EnabledIfDockerAvailable
class PreExistingTenantCatalogueIT {

    private static final String DATABASE = "infinevo_catalogue_upgrade";

    private static final UUID TENANT_BEFORE = UUID.randomUUID();
    private static final UUID TENANT_AFTER = UUID.randomUUID();

    private static String jdbcUrl;

    @BeforeAll
    static void migrateToV024_insertTenant_thenMigrateToLatest() throws SQLException {
        jdbcUrl = PostgresTestContainerInitializer.provisionAdditionalDatabase(DATABASE);

        MigrationApplication.launch(
                "--DB_URL=" + jdbcUrl,
                "--DB_MIGRATION_USERNAME=" + PostgresTestContainerInitializer.MIGRATION_USER,
                "--DB_MIGRATION_PASSWORD=" + PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD,
                "--spring.flyway.target=024");

        insertTenant(TENANT_BEFORE, "Before V025");
        try (Connection conn = migrationUserConnection()) {
            // Precondition: the tenant really was seeded with the old shape.
            assertThat(holds(conn, TENANT_BEFORE, "hr", "hrms.leave.read")).isTrue();
            assertThat(holds(conn, TENANT_BEFORE, "platform-admin", "core.tenant.provision"))
                    .isTrue();
        }

        MigrationApplication.launch(
                "--DB_URL=" + jdbcUrl,
                "--DB_MIGRATION_USERNAME=" + PostgresTestContainerInitializer.MIGRATION_USER,
                "--DB_MIGRATION_PASSWORD=" + PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);

        insertTenant(TENANT_AFTER, "After V025");
    }

    @Test
    void hr_holdsTheRenamedLeaveRead_andNotTheOldCode() throws SQLException {
        try (Connection conn = migrationUserConnection()) {
            assertThat(holds(conn, TENANT_BEFORE, "hr", "core.leave.read")).isTrue();
            assertThat(holds(conn, TENANT_BEFORE, "hr", "hrms.leave.read")).isFalse();
        }
    }

    @Test
    void employee_holdsTheRenamedLeaveApply_andKeepsAttendanceMark() throws SQLException {
        try (Connection conn = migrationUserConnection()) {
            assertThat(holds(conn, TENANT_BEFORE, "employee", "core.leave.apply"))
                    .isTrue();
            assertThat(holds(conn, TENANT_BEFORE, "employee", "hrms.attendance.mark"))
                    .isTrue();
        }
    }

    @Test
    void platformAdmin_noLongerHoldsTenantProvision() throws SQLException {
        try (Connection conn = migrationUserConnection()) {
            assertThat(holds(conn, TENANT_BEFORE, "platform-admin", "core.tenant.provision"))
                    .isFalse();
        }
    }

    @Test
    void preExistingTenant_endsWithTheSameGrantsAsATenantCreatedAfterV025() throws SQLException {
        try (Connection conn = migrationUserConnection()) {
            assertThat(count(conn, TENANT_BEFORE))
                    .as("role_action rows: moved + backfilled vs freshly seeded")
                    .isEqualTo(count(conn, TENANT_AFTER));
            assertThat(grants(conn, TENANT_BEFORE))
                    .as("role:action pairs")
                    .containsExactlyElementsOf(grants(conn, TENANT_AFTER));
        }
    }

    // ── helpers

    private static void insertTenant(UUID tenant, String name) throws SQLException {
        try (Connection conn = migrationUserConnection();
                PreparedStatement ps =
                        conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)")) {
            ps.setObject(1, tenant);
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    private static boolean holds(Connection conn, UUID tenant, String role, String action) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM core.role_action ra"
                + " JOIN core.role r ON r.id = ra.role_id AND r.tenant_id = ra.tenant_id"
                + " WHERE r.tenant_id = ? AND r.code = ? AND r.is_system AND ra.action_code = ?")) {
            ps.setObject(1, tenant);
            ps.setString(2, role);
            ps.setString(3, action);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getInt(1) == 1;
            }
        }
    }

    private static int count(Connection conn, UUID tenant) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT count(*) FROM core.role_action WHERE tenant_id = ?")) {
            ps.setObject(1, tenant);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getInt(1);
            }
        }
    }

    private static List<String> grants(Connection conn, UUID tenant) throws SQLException {
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT r.code || ':' || ra.action_code FROM core.role_action ra"
                        + " JOIN core.role r ON r.id = ra.role_id AND r.tenant_id = ra.tenant_id"
                        + " WHERE ra.tenant_id = ? ORDER BY 1")) {
            ps.setObject(1, tenant);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            }
        }
        return result;
    }

    private static Connection migrationUserConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }
}
