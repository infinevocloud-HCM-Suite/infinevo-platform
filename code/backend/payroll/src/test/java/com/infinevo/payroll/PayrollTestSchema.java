package com.infinevo.payroll;

import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Schema, seed data and connection helpers for payroll integration tests (W-26.1).
 */
public final class PayrollTestSchema {

    public static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private PayrollTestSchema() {}

    public static Connection migrationConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    public static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }

    public static void apply() throws Exception {
        try (Connection conn = migrationConnection()) {
            if (!tableExists(conn, "core", "tenant")) {
                executeResource(conn, "db/migration/core/V001__tenant.sql");
            }
            if (!tableExists(conn, "core", "employee")) {
                executeResource(conn, "db/migration/core/V010__employee.sql");
            }
            if (!tableExists(conn, "core", "employee_personal")) {
                executeResource(conn, "db/migration/core/V015__employee_personal.sql");
            } else {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE core.employee_personal NO FORCE ROW LEVEL SECURITY");
                }
            }
            if (!tableExists(conn, "payroll", "earning")) {
                executeResource(conn, "db/migration/payroll/V042__earning.sql");
            }
            if (!tableExists(conn, "payroll", "deduction")) {
                executeResource(conn, "db/migration/payroll/V043__deduction.sql");
            }
            if (!tableExists(conn, "payroll", "benefit")) {
                executeResource(conn, "db/migration/payroll/V044__benefit.sql");
            }
            if (!tableExists(conn, "payroll", "reimbursement")) {
                executeResource(conn, "db/migration/payroll/V045__reimbursement.sql");
            }
            if (!tableExists(conn, "payroll", "ctc_structure")) {
                executeResource(conn, "db/migration/payroll/V046__ctc_structure.sql");
            }
            if (!tableExists(conn, "payroll", "employee_earning")) {
                executeResource(conn, "db/migration/payroll/V047__employee_earning.sql");
            }
            if (!tableExists(conn, "payroll", "employee_benefit")) {
                executeResource(conn, "db/migration/payroll/V048__employee_benefit.sql");
            }
            if (!tableExists(conn, "payroll", "employee_reimbursement")) {
                executeResource(conn, "db/migration/payroll/V049__employee_reimbursement.sql");
            }
            if (!tableExists(conn, "payroll", "employee_statutory_profile")) {
                executeResource(conn, "db/migration/payroll/V050__employee_statutory_profile.sql");
            }
            if (!tableExists(conn, "payroll", "fbp")) {
                executeResource(conn, "db/migration/payroll/V051__fbp.sql");
            }
            if (!tableExists(conn, "payroll", "employee_fbp_component")) {
                executeResource(conn, "db/migration/payroll/V053__employee_fbp_component.sql");
            }
            if (!tableExists(conn, "payroll", "epf_setting")) {
                executeResource(conn, "db/migration/payroll/V062__epf_setting.sql");
            } else {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE payroll.epf_setting NO FORCE ROW LEVEL SECURITY");
                }
            }
            if (!tableExists(conn, "payroll", "esi_setting")) {
                executeResource(conn, "db/migration/payroll/V063__esi_setting.sql");
            } else {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE payroll.esi_setting NO FORCE ROW LEVEL SECURITY");
                }
            }
            if (!tableExists(conn, "reference", "state")) {
                executeResource(conn, "db/migration/reference/V003__reference_lookups.sql");
            }
            if (!tableExists(conn, "core", "work_location")) {
                executeResource(conn, "db/migration/core/V013__work_location.sql");
            }
            if (!tableExists(conn, "reference", "pt_state")) {
                executeResource(conn, "db/migration/reference/V064__pt_state_and_slab.sql");
            }
            if (!tableExists(conn, "payroll", "org_pt_override")) {
                executeResource(conn, "db/migration/payroll/V065__org_pt_override.sql");
            } else {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE payroll.org_pt_override NO FORCE ROW LEVEL SECURITY");
                }
            }
            if (!tableExists(conn, "payroll", "org_pt_override_slab")) {
                executeResource(conn, "db/migration/payroll/V066__org_pt_override_slab.sql");
            } else {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE payroll.org_pt_override_slab NO FORCE ROW LEVEL SECURITY");
                }
            }
            if (!tableExists(conn, "payroll", "pt_history")) {
                executeResource(conn, "db/migration/payroll/V067__pt_history.sql");
            } else {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE payroll.pt_history NO FORCE ROW LEVEL SECURITY");
                }
            }
            if (!tableExists(conn, "payroll", "ctc_epf_component")) {
                executeResource(conn, "db/migration/payroll/V068__ctc_epf_component.sql");
            } else {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE payroll.ctc_epf_component NO FORCE ROW LEVEL SECURITY");
                }
            }
            if (!tableExists(conn, "payroll", "ctc_esi_component")) {
                executeResource(conn, "db/migration/payroll/V069__ctc_esi_component.sql");
            } else {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE payroll.ctc_esi_component NO FORCE ROW LEVEL SECURITY");
                }
            }
            try (Statement st = conn.createStatement()) {
                st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA core TO app_user");
                st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA payroll TO app_user");
                st.execute("GRANT SELECT ON ALL TABLES IN SCHEMA reference TO app_user");
            }
        }
    }

    public static void seedTenants() throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, TENANT_A);
            ps.setString(2, "Acme Manufacturing");
            ps.executeUpdate();
            ps.setObject(1, TENANT_B);
            ps.setString(2, "Globex Corporation");
            ps.executeUpdate();
        }
    }

    public static void cleanTables() throws SQLException {
        try (Connection conn = migrationConnection();
                Statement st = conn.createStatement()) {
            st.execute(
                    "TRUNCATE TABLE payroll.employee_fbp_component, payroll.fbp, payroll.ctc_structure, payroll.employee_statutory_profile, "
                            + "payroll.earning, payroll.deduction, payroll.benefit, payroll.reimbursement CASCADE");
            if (tableExists(conn, "payroll", "ctc_epf_component")) {
                st.execute("DELETE FROM payroll.ctc_epf_component");
            }
            if (tableExists(conn, "payroll", "ctc_esi_component")) {
                st.execute("DELETE FROM payroll.ctc_esi_component");
            }
            if (tableExists(conn, "payroll", "epf_setting")) {
                st.execute("DELETE FROM payroll.epf_setting");
            }
            if (tableExists(conn, "payroll", "esi_setting")) {
                st.execute("DELETE FROM payroll.esi_setting");
            }
            if (tableExists(conn, "payroll", "org_pt_override_slab")) {
                st.execute("DELETE FROM payroll.org_pt_override_slab");
            }
            if (tableExists(conn, "payroll", "org_pt_override")) {
                st.execute("DELETE FROM payroll.org_pt_override");
            }
            if (tableExists(conn, "payroll", "pt_history")) {
                st.execute("DELETE FROM payroll.pt_history");
            }
            if (tableExists(conn, "core", "attendance")) {
                st.execute("DELETE FROM core.attendance");
            }
            if (tableExists(conn, "core", "work_location")) {
                st.execute("DELETE FROM core.work_location");
            }
            if (tableExists(conn, "core", "employee_personal")) {
                st.execute("DELETE FROM core.employee_personal");
            }
            st.execute("DELETE FROM core.employee");
        }
    }

    public static void bindTenant(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement bind = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, false)")) {
            bind.setString(1, tenantId.toString());
            bind.execute();
        }
    }

    public static void clearTenant(Connection conn) throws SQLException {
        try (PreparedStatement clear = conn.prepareStatement("SELECT set_config('app.current_tenant_id', '', false)")) {
            clear.execute();
        }
    }

    public static boolean tableExists(Connection conn, String schema, String table) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT 1 FROM pg_tables WHERE schemaname = ? AND tablename = ?")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void executeResource(Connection conn, String resourcePath) throws Exception {
        try (InputStream is = PayrollTestSchema.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Migration script not found on test classpath: " + resourcePath);
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }
}
