package com.infinevo.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.test.EnabledIfDockerAvailable;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D-144 ({@code payroll/V221}): the backfill that applies "closed at Approved" to months already
 * there. Migrates to {@code V220} (the stage column, every lock LOCKED), seeds one tenant with each
 * case, applies the rest, and reads what V221 left - the {@code PreExistingTenantCatalogueIT} pattern.
 */
@EnabledIfDockerAvailable
class PayInputLockStageBackfillIT {

    private static final String DATABASE = "infinevo_lock_stage_backfill";

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();
    private static final String PAID_MONTH = "2026-01";
    private static final String APPROVED_MONTH = "2026-02";
    private static final String CANCELLED_MONTH = "2026-03";
    private static final String DRAFT_MONTH = "2026-04";
    private static final String NO_RUN_MONTH = "2026-05";

    private static UUID offCycleRun;
    private static String jdbcUrl;

    @BeforeAll
    static void migrateSeedAndBackfill() throws SQLException {
        jdbcUrl = PostgresTestContainerInitializer.provisionAdditionalDatabase(DATABASE);
        launch("--spring.flyway.target=220");

        try (Connection conn = migrationUser()) {
            tenant(conn, TENANT, "Backfill");
            tenant(conn, OTHER_TENANT, "Backfill other");

            run(conn, TENANT, PAID_MONTH, "REGULAR", "PAID");
            periodLock(conn, TENANT, PAID_MONTH);

            run(conn, TENANT, APPROVED_MONTH, "REGULAR", "APPROVED");
            periodLock(conn, TENANT, APPROVED_MONTH);

            run(conn, TENANT, CANCELLED_MONTH, "REGULAR", "CANCELLED");
            run(conn, TENANT, CANCELLED_MONTH, "REGULAR", "CANCELLED");
            periodLock(conn, TENANT, CANCELLED_MONTH);

            run(conn, TENANT, DRAFT_MONTH, "REGULAR", "DRAFT");
            periodLock(conn, TENANT, DRAFT_MONTH);

            // A month locked with no run at all (by an administrator, through core) is not D-138's.
            periodLock(conn, TENANT, NO_RUN_MONTH);

            // An approved off-cycle run in the approved month, with its own run lock.
            offCycleRun = run(conn, TENANT, APPROVED_MONTH, "OFF_CYCLE", "APPROVED");
            runLock(conn, TENANT, APPROVED_MONTH, offCycleRun);

            // Another tenant's lock for the paid month, with no run of its own: untouched.
            periodLock(conn, OTHER_TENANT, PAID_MONTH);
        }

        launch();
    }

    @Test
    @DisplayName("A month whose regular run is PAID becomes CLOSED")
    void paidMonthIsClosed() throws SQLException {
        assertThat(periodLockStage(TENANT, PAID_MONTH)).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("A month whose regular run is APPROVED becomes CLOSED")
    void approvedMonthIsClosed() throws SQLException {
        assertThat(periodLockStage(TENANT, APPROVED_MONTH)).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("A month whose regular runs were all CANCELLED loses its lock (D-138)")
    void cancelledMonthLosesItsLock() throws SQLException {
        assertThat(periodLockStage(TENANT, CANCELLED_MONTH)).isNull();
    }

    @Test
    @DisplayName("A month with a live DRAFT run keeps its lock, LOCKED")
    void draftMonthKeepsItsLock() throws SQLException {
        assertThat(periodLockStage(TENANT, DRAFT_MONTH)).isEqualTo("LOCKED");
    }

    @Test
    @DisplayName("A month locked with no run at all keeps its lock, LOCKED")
    void lockWithNoRunIsKept() throws SQLException {
        assertThat(periodLockStage(TENANT, NO_RUN_MONTH)).isEqualTo("LOCKED");
    }

    @Test
    @DisplayName("An off-cycle run's own lock is untouched: still there, still LOCKED")
    void offCycleRunLockIsUntouched() throws SQLException {
        try (Connection conn = migrationUser();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT stage, updated_by FROM core.pay_input_period_lock WHERE tenant_id = ? AND run_ref = ?")) {
            ps.setObject(1, TENANT);
            ps.setObject(2, offCycleRun);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("LOCKED");
                assertThat(rs.getString(2)).isEqualTo("test");
            }
        }
    }

    @Test
    @DisplayName("Another tenant's lock is judged by its own runs only")
    void otherTenantUntouched() throws SQLException {
        assertThat(periodLockStage(OTHER_TENANT, PAID_MONTH)).isEqualTo("LOCKED");
    }

    private static void launch(String... extra) {
        String[] base = {
            "--DB_URL=" + jdbcUrl,
            "--DB_MIGRATION_USERNAME=" + PostgresTestContainerInitializer.MIGRATION_USER,
            "--DB_MIGRATION_PASSWORD=" + PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD
        };
        String[] args = new String[base.length + extra.length];
        System.arraycopy(base, 0, args, 0, base.length);
        System.arraycopy(extra, 0, args, base.length, extra.length);
        MigrationApplication.launch(args);
    }

    /** The stage of the tenant's period lock (run_ref null) for {@code period}, or null when there is none. */
    private static String periodLockStage(UUID tenant, String period) throws SQLException {
        try (Connection conn = migrationUser();
                PreparedStatement ps = conn.prepareStatement("SELECT stage FROM core.pay_input_period_lock"
                        + " WHERE tenant_id = ? AND period = ? AND run_ref IS NULL")) {
            ps.setObject(1, tenant);
            ps.setString(2, period);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static void tenant(Connection conn, UUID tenant, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)")) {
            ps.setObject(1, tenant);
            ps.setString(2, name + " " + tenant);
            ps.executeUpdate();
        }
    }

    private static UUID run(Connection conn, UUID tenant, String period, String type, String status)
            throws SQLException {
        YearMonth month = YearMonth.parse(period);
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO payroll.payrun"
                + " (tenant_id, period, period_start, period_end, cutoff_date, pay_date, run_type, status, paid_on)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id")) {
            ps.setObject(1, tenant);
            ps.setString(2, period);
            ps.setDate(3, Date.valueOf(start));
            ps.setDate(4, Date.valueOf(end));
            ps.setDate(5, Date.valueOf(start.plusDays(24)));
            ps.setDate(6, Date.valueOf(end));
            ps.setString(7, type);
            ps.setString(8, status);
            ps.setDate(9, "PAID".equals(status) ? Date.valueOf(end) : null);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private static void periodLock(Connection conn, UUID tenant, String period) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.pay_input_period_lock (tenant_id, period, locked_by, updated_by) VALUES (?, ?, 'test', 'test')")) {
            ps.setObject(1, tenant);
            ps.setString(2, period);
            ps.executeUpdate();
        }
    }

    private static void runLock(Connection conn, UUID tenant, String period, UUID runRef) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.pay_input_period_lock"
                + " (tenant_id, period, run_ref, locked_by, updated_by) VALUES (?, ?, ?, 'test', 'test')")) {
            ps.setObject(1, tenant);
            ps.setString(2, period);
            ps.setObject(3, runRef);
            ps.executeUpdate();
        }
    }

    private static Connection migrationUser() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }
}
