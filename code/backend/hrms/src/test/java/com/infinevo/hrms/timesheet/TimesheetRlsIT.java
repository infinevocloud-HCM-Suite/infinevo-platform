package com.infinevo.hrms.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.hrms.project.HrmsProjectTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-42.1 §7, {@code TimesheetRlsIT}: row-level security on all four timesheet tables, as {@code app_user}, the role
 * the application connects as. Tenant A has a timesheet with a project, task and day entry; tenant B has nothing of A's
 * in reach.
 */
class TimesheetRlsIT extends TimesheetItSupport {

    private static final List<String> TABLES =
            List.of("timesheet", "timesheet_project_entry", "timesheet_task_entry", "timesheet_day_entry");

    private UUID tenantB;
    private UUID timesheetOfA;

    @BeforeEach
    void seedTenantA() throws SQLException {
        tenantB = HrmsProjectTestSchema.insertTenant("Timesheet RLS B " + UUID.randomUUID());
        HrmsProjectTestSchema.insertEmployee(tenantB, "EMP-B-" + UUID.randomUUID());
        inTenant();
        timesheetOfA = timesheets.create(simple(WEEK)).id();
        TenantContext.clear();
    }

    @Test
    @DisplayName("A connection with no tenant bound sees zero rows in all four tables")
    void noTenantSeesNothing() throws SQLException {
        try (Connection conn = HrmsProjectTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            for (String table : TABLES) {
                assertThat(count(conn, "SELECT count(*) FROM hrms." + table))
                        .as(table)
                        .isZero();
            }
        }
    }

    @Test
    @DisplayName("Bound to tenant B, none of tenant A's rows is visible in any table; bound to A, each is")
    void tenantBCannotReadTenantA() throws SQLException {
        try (Connection conn = HrmsProjectTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            HrmsProjectTestSchema.bindTenant(conn, tenantB);
            for (String table : TABLES) {
                assertThat(count(conn, "SELECT count(*) FROM hrms." + table + " WHERE tenant_id = ?", tenant))
                        .as("B reads A's %s", table)
                        .isZero();
                assertThat(count(conn, "SELECT count(*) FROM hrms." + table))
                        .as("B sees any %s", table)
                        .isZero();
            }
        }
        try (Connection conn = HrmsProjectTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            HrmsProjectTestSchema.bindTenant(conn, tenant);
            for (String table : TABLES) {
                assertThat(count(conn, "SELECT count(*) FROM hrms." + table))
                        .as("A reads its %s", table)
                        .isPositive();
            }
        }
    }

    @Test
    @DisplayName("Bound to tenant B, updating or deleting tenant A's rows affects 0 rows, in every table")
    void tenantBCannotWriteTenantA() throws SQLException {
        try (Connection conn = HrmsProjectTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            HrmsProjectTestSchema.bindTenant(conn, tenantB);
            assertThat(execute(conn, "UPDATE hrms.timesheet SET status = 'APPROVED' WHERE id = ?", timesheetOfA))
                    .isZero();
            for (String table : TABLES) {
                assertThat(execute(conn, "DELETE FROM hrms." + table + " WHERE tenant_id = ?", tenant))
                        .as("delete from %s", table)
                        .isZero();
            }
            conn.rollback();
        }
        assertThat(rows("timesheet_day_entry"))
                .as("A's rows are all still there")
                .isEqualTo(1);
        assertThat(HrmsProjectTestSchema.count(
                        "SELECT count(*) FROM hrms.timesheet WHERE status = 'DRAFT' AND id = ?", timesheetOfA))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Bound to tenant B, inserting a row that names tenant A is refused by the policy")
    void insertNamingAnotherTenantIsRefused() throws SQLException {
        try (Connection conn = HrmsProjectTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            HrmsProjectTestSchema.bindTenant(conn, tenantB);
            assertThatThrownBy(() -> execute(
                            conn,
                            "INSERT INTO hrms.timesheet (tenant_id, employee_id, week_start_date, week_end_date, status)"
                                    + " VALUES (?, ?, DATE '2026-10-12', DATE '2026-10-18', 'DRAFT')",
                            tenant,
                            empA))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("row-level security");
        }
    }

    @Test
    @DisplayName("The service writes under the caller's tenant: the timesheet and every line under it carry it")
    void everyLineCarriesTheCallersTenant() throws SQLException {
        assertThat(HrmsProjectTestSchema.count(
                        "SELECT count(*) FROM hrms.timesheet WHERE id = ? AND tenant_id = ?", timesheetOfA, tenant))
                .isEqualTo(1);
        assertThat(HrmsProjectTestSchema.count(
                        """
                        SELECT count(*) FROM hrms.timesheet t
                          JOIN hrms.timesheet_project_entry pe ON pe.timesheet_id = t.id
                          JOIN hrms.timesheet_task_entry te ON te.project_entry_id = pe.id
                          JOIN hrms.timesheet_day_entry de ON de.task_entry_id = te.id
                         WHERE t.id = ?
                           AND (pe.tenant_id <> t.tenant_id OR te.tenant_id <> t.tenant_id OR de.tenant_id <> t.tenant_id)
                        """,
                        timesheetOfA))
                .as("a line under tenant A's timesheet that names another tenant")
                .isZero();
    }

    private static long count(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static int execute(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps.executeUpdate();
        }
    }
}
