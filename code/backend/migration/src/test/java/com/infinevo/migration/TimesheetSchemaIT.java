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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/**
 * W-42.1 §6 and §8: the four timesheet tables as the shipped scripts create them, through Flyway. What the spec's
 * verification lists is asserted here: row-level security on all four, {@code hours} as {@code numeric(4,2)}, the
 * seven named indexes beside the four primary keys, and the database's own CHECKs and cascades.
 */
@EnabledIfDockerAvailable
class TimesheetSchemaIT {

    private static final String DATABASE = "infinevo_timesheet_schema";
    private static final String CHECK_VIOLATION = "23514";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final List<String> TABLES =
            List.of("timesheet", "timesheet_project_entry", "timesheet_task_entry", "timesheet_day_entry");

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
    @DisplayName("Row-level security is on for all four tables, each with a tenant_isolation policy")
    void rowLevelSecurity() throws SQLException {
        try (Connection conn = connection()) {
            for (String table : TABLES) {
                assertThat(strings(
                                conn,
                                "SELECT relrowsecurity::text FROM pg_class WHERE oid = 'hrms." + table + "'::regclass"))
                        .as("RLS on hrms.%s", table)
                        .containsExactly("true");
                assertThat(strings(
                                conn,
                                "SELECT policyname FROM pg_policies WHERE schemaname = 'hrms' AND tablename = '" + table
                                        + "'"))
                        .as("policy on hrms.%s", table)
                        .containsExactly("tenant_isolation");
            }
        }
    }

    @Test
    @DisplayName("hours is numeric(4,2), never a float")
    void hoursIsNumeric42() throws SQLException {
        try (Connection conn = connection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT data_type, numeric_precision, numeric_scale FROM information_schema.columns"
                                + " WHERE table_schema = 'hrms' AND table_name = 'timesheet_day_entry'"
                                + " AND column_name = 'hours'");
                ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("numeric");
            assertThat(rs.getInt(2)).isEqualTo(4);
            assertThat(rs.getInt(3)).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("The seven indexes of the spec exist beside the four primary keys, and nothing else")
    void indexes() throws SQLException {
        try (Connection conn = connection()) {
            assertThat(strings(
                            conn,
                            "SELECT indexname FROM pg_indexes WHERE schemaname = 'hrms' AND tablename LIKE 'timesheet%'"
                                    + " ORDER BY 1"))
                    .containsExactly(
                            "idx_timesheet_tenant_status",
                            "idx_tpe_tenant_project_status",
                            "idx_tte_tenant_task",
                            "timesheet_day_entry_pkey",
                            "timesheet_pkey",
                            "timesheet_project_entry_pkey",
                            "timesheet_task_entry_pkey",
                            "uk_tde_tenant_entry_date",
                            "uk_timesheet_tenant_employee_week",
                            "uk_tpe_tenant_timesheet_project",
                            "uk_tte_tenant_entry_task");
        }
    }

    @Test
    @DisplayName("The database refuses a week that does not start on a Monday or does not last seven days")
    void weekChecks() throws SQLException {
        try (Connection conn = connection()) {
            Fixture f = Fixture.create(conn);
            assertThatThrownBy(() -> f.insertTimesheet("2026-10-06", "2026-10-12", "DRAFT"))
                    .as("a Tuesday")
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(CHECK_VIOLATION);
            assertThatThrownBy(() -> f.insertTimesheet("2026-10-05", "2026-10-10", "DRAFT"))
                    .as("six days")
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(CHECK_VIOLATION);
            assertThatThrownBy(() -> f.insertTimesheet("2026-10-05", "2026-10-11", "DONE"))
                    .as("an unknown status")
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(CHECK_VIOLATION);
            f.insertTimesheet("2026-10-05", "2026-10-11", "DRAFT");
        }
    }

    @Test
    @DisplayName("One live timesheet per employee per week; a cancelled one does not hold the week")
    void oneLiveTimesheetPerWeek() throws SQLException {
        try (Connection conn = connection()) {
            Fixture f = Fixture.create(conn);
            UUID first = f.insertTimesheet("2026-10-05", "2026-10-11", "DRAFT");

            assertThatThrownBy(() -> f.insertTimesheet("2026-10-05", "2026-10-11", "DRAFT"))
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(UNIQUE_VIOLATION);

            f.setStatus(first, "CANCELLED");
            f.insertTimesheet("2026-10-05", "2026-10-11", "DRAFT");
        }
    }

    @Test
    @DisplayName("hours of 0, below 0 and above 24 are refused; 24 and 0.01 are accepted; a date is once per task")
    void hoursChecksAndDateUniqueness() throws SQLException {
        try (Connection conn = connection()) {
            Fixture f = Fixture.create(conn);
            UUID sheet = f.insertTimesheet("2026-10-05", "2026-10-11", "DRAFT");
            UUID task = f.insertTaskEntry(f.insertProjectEntry(sheet));

            for (String bad : new String[] {"0", "-1", "24.01"}) {
                assertThatThrownBy(() -> f.insertDay(task, "2026-10-05", bad))
                        .as("hours %s", bad)
                        .isInstanceOf(PSQLException.class)
                        .extracting(e -> ((PSQLException) e).getSQLState())
                        .isEqualTo(CHECK_VIOLATION);
            }
            f.insertDay(task, "2026-10-05", "24");
            f.insertDay(task, "2026-10-06", "0.01");
            assertThatThrownBy(() -> f.insertDay(task, "2026-10-05", "1"))
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(UNIQUE_VIOLATION);
        }
    }

    @Test
    @DisplayName("Deleting a timesheet takes its project, task and day entries with it")
    void deleteCascades() throws SQLException {
        try (Connection conn = connection()) {
            Fixture f = Fixture.create(conn);
            UUID sheet = f.insertTimesheet("2026-10-05", "2026-10-11", "DRAFT");
            UUID projectEntry = f.insertProjectEntry(sheet);
            f.insertDay(f.insertTaskEntry(projectEntry), "2026-10-05", "8");

            f.deleteTimesheet(sheet);

            for (String table : TABLES) {
                assertThat(count(conn, "SELECT count(*) FROM hrms." + table + " WHERE tenant_id = '" + f.tenant + "'"))
                        .as(table)
                        .isZero();
            }
        }
    }

    /** A tenant with an employee, a project and a task, and helpers that insert the rows under them. */
    private static final class Fixture {
        final Connection conn;
        final UUID tenant = UUID.randomUUID();
        final UUID employee;
        final UUID project;
        final UUID task;

        private Fixture(Connection conn) throws SQLException {
            this.conn = conn;
            run("INSERT INTO core.tenant (tenant_id, name) VALUES ('" + tenant + "', 'Timesheet Schema')");
            employee = id(
                    "INSERT INTO core.employee (tenant_id, employee_number, first_name, work_email, date_of_joining, status)"
                            + " VALUES ('" + tenant
                            + "', 'E-1', 'T', 'e1@ts.test', DATE '2026-04-01', 'ACTIVE') RETURNING id");
            project = id("INSERT INTO hrms.project (tenant_id, name, priority, status) VALUES ('" + tenant
                    + "', 'P', 'LOW', 'STARTED') RETURNING id");
            task = id("INSERT INTO hrms.task (tenant_id, project_id, title, priority, status) VALUES ('" + tenant
                    + "', '" + project + "', 'T', 'LOW', 'TODO') RETURNING id");
        }

        static Fixture create(Connection conn) throws SQLException {
            return new Fixture(conn);
        }

        UUID insertTimesheet(String start, String end, String status) throws SQLException {
            return id("INSERT INTO hrms.timesheet (tenant_id, employee_id, week_start_date, week_end_date, status)"
                    + " VALUES ('" + tenant + "', '" + employee + "', DATE '" + start + "', DATE '" + end + "', '"
                    + status + "') RETURNING id");
        }

        UUID insertProjectEntry(UUID timesheet) throws SQLException {
            return id("INSERT INTO hrms.timesheet_project_entry (tenant_id, timesheet_id, project_id, status)"
                    + " VALUES ('" + tenant + "', '" + timesheet + "', '" + project + "', 'DRAFT') RETURNING id");
        }

        UUID insertTaskEntry(UUID projectEntry) throws SQLException {
            return id("INSERT INTO hrms.timesheet_task_entry (tenant_id, project_entry_id, task_id) VALUES ('" + tenant
                    + "', '" + projectEntry + "', '" + task + "') RETURNING id");
        }

        void insertDay(UUID taskEntry, String date, String hours) throws SQLException {
            run("INSERT INTO hrms.timesheet_day_entry (tenant_id, task_entry_id, work_date, hours) VALUES ('" + tenant
                    + "', '" + taskEntry + "', DATE '" + date + "', " + hours + ")");
        }

        void setStatus(UUID timesheet, String status) throws SQLException {
            run("UPDATE hrms.timesheet SET status = '" + status + "' WHERE id = '" + timesheet + "'");
        }

        void deleteTimesheet(UUID timesheet) throws SQLException {
            run("DELETE FROM hrms.timesheet WHERE id = '" + timesheet + "'");
        }

        private void run(String sql) throws SQLException {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.execute();
            }
        }

        private UUID id(String sql) throws SQLException {
            try (PreparedStatement ps = conn.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private static List<String> strings(Connection conn, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
    }

    private static long count(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }
}
