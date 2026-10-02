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
 * W-65.1 review ({@code V138}): {@code core.subscription.status} holds only the four {@code SubscriptionStatus}
 * names. Run against the shipped scripts through Flyway, so what is read back is the row {@code V082} wrote and
 * {@code V138} then corrected.
 */
@EnabledIfDockerAvailable
class SubscriptionStatusCheckIT {

    private static final String DATABASE = "infinevo_subscription_status";
    private static final UUID PLATFORM = UUID.fromString("00000000-0000-0000-0000-000000000001");
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
    @DisplayName("The platform tenant's subscription, written by V082, reads ACTIVE once V138 has run")
    void platformSubscriptionIsActive() throws SQLException {
        try (Connection conn = connection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT status FROM core.subscription WHERE tenant_id = ?")) {
            ps.setObject(1, PLATFORM);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("the platform tenant has a subscription")
                        .isTrue();
                assertThat(rs.getString(1)).isEqualTo("ACTIVE");
            }
        }
    }

    @Test
    @DisplayName("No subscription anywhere holds a status that is not one of the four names")
    void everyStatusIsAKnownName() throws SQLException {
        try (Connection conn = connection();
                PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM core.subscription"
                        + " WHERE status NOT IN ('ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED')");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            assertThat(rs.getLong(1)).isZero();
        }
    }

    @Test
    @DisplayName("A lower-case or unknown status is refused, whether inserted or set through the function")
    void badStatusIsRefused() throws SQLException {
        try (Connection conn = connection()) {
            UUID tenant = tenant(conn);
            for (String bad : new String[] {"active", "Active", "EXPIRED", ""}) {
                assertThatThrownBy(() -> insert(conn, tenant, bad))
                        .as("insert '%s'", bad)
                        .isInstanceOf(PSQLException.class)
                        .extracting(e -> ((PSQLException) e).getSQLState())
                        .isEqualTo(CHECK_VIOLATION);
            }

            insert(conn, tenant, "ACTIVE");
            assertThatThrownBy(() -> setThroughFunction(conn, tenant, "suspended"))
                    .as("core.set_subscription_status with a lower-case status")
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(CHECK_VIOLATION);
        }
    }

    @Test
    @DisplayName("All four real statuses are accepted")
    void everyRealStatusIsAccepted() throws SQLException {
        try (Connection conn = connection()) {
            UUID tenant = tenant(conn);
            insert(conn, tenant, "ACTIVE");
            for (String status : new String[] {"PAST_DUE", "SUSPENDED", "CANCELLED", "ACTIVE"}) {
                setThroughFunction(conn, tenant, status);
                try (PreparedStatement ps =
                        conn.prepareStatement("SELECT status FROM core.subscription WHERE tenant_id = ?")) {
                    ps.setObject(1, tenant);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertThat(rs.getString(1)).isEqualTo(status);
                    }
                }
            }
        }
    }

    private static UUID tenant(Connection conn) throws SQLException {
        UUID tenant = UUID.randomUUID();
        try (PreparedStatement ps =
                conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, 'Status Check')")) {
            ps.setObject(1, tenant);
            ps.executeUpdate();
        }
        // The tenant insert gives it no subscription; the function and the inserts below act on this one.
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM core.subscription WHERE tenant_id = ?")) {
            ps.setObject(1, tenant);
            ps.executeUpdate();
        }
        return tenant;
    }

    private static void insert(Connection conn, UUID tenant, String status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.subscription (tenant_id, status) VALUES (?, ?) ON CONFLICT (tenant_id) DO NOTHING")) {
            ps.setObject(1, tenant);
            ps.setString(2, status);
            ps.executeUpdate();
        }
    }

    private static void setThroughFunction(Connection conn, UUID tenant, String status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT core.set_subscription_status(?, ?)")) {
            ps.setObject(1, tenant);
            ps.setString(2, status);
            ps.execute();
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }
}
