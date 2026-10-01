package com.infinevo.payroll.taxdeclaration;

import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Shared schema management, migrations, and test data seeding for tax declaration tests (W-32.1).
 */
public final class TaxDeclarationTestSchema {

    public static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private TaxDeclarationTestSchema() {}

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
            if (!tableExists(conn, "reference", "action")) {
                executeResource(conn, "db/migration/reference/V020__action.sql");
            }
            if (!tableExists(conn, "core", "role")) {
                executeResource(conn, "db/migration/core/V021__role.sql");
            }
            if (!tableExists(conn, "core", "role_action")) {
                executeResource(conn, "db/migration/core/V022__role_action.sql");
            }
            if (!actionExists(conn, "core.leave.apply")) {
                executeResource(conn, "db/migration/core/V025__catalogue_correction.sql");
            }
            if (!tableExists(conn, "core", "employee")) {
                executeResource(conn, "db/migration/core/V010__employee.sql");
            }
            if (!tableExists(conn, "reference", "hra_rule_master")) {
                executeResource(conn, "db/migration/reference/V004__reference_tax_masters.sql");
                executeResource(conn, "db/migration/reference/V005__reference_tax_seed.sql");
            }
            if (!taxSlabAgeCategoryExists(conn, "SENIOR")) {
                executeResource(conn, "db/migration/reference/V027__tax_slab_age_categories.sql");
            }
            if (!ruleForYearExists(conn, "hra_rule_master", "2026-2027")) {
                executeResource(conn, "db/migration/reference/V105__fy_2026_27_reference_rules.sql");
            }
            if (!ruleForYearExists(conn, "home_loan_rule_master", "2026-2027")) {
                executeResource(conn, "db/migration/reference/V106__fy_2026_27_w33_reference_rules.sql");
            }
            if (!functionExists(conn, "core", "seed_tax_declaration_roles")) {
                executeResource(conn, "db/migration/reference/V070__tax_declaration_actions.sql");
            }
            if (!tableExists(conn, "payroll", "income_tax_declaration")) {
                executeResource(conn, "db/migration/payroll/V071__income_tax_declaration.sql");
            }
            if (!tableExists(conn, "payroll", "employee_investment_declaration")) {
                executeResource(conn, "db/migration/payroll/V072__employee_investment_declaration.sql");
            }
            if (!tableExists(conn, "payroll", "employee_inv_house_rent")) {
                executeResource(conn, "db/migration/payroll/V073__employee_inv_house_rent.sql");
            }
            if (!tableExists(conn, "payroll", "employee_inv_home_loan")) {
                executeResource(conn, "db/migration/payroll/V074__employee_inv_home_loan.sql");
            }
            if (!tableExists(conn, "payroll", "employee_inv_let_out_property")) {
                executeResource(conn, "db/migration/payroll/V075__employee_inv_let_out_property.sql");
            }
            if (!tableExists(conn, "payroll", "employee_inv_let_out_property_line")) {
                executeResource(conn, "db/migration/payroll/V076__employee_inv_let_out_property_line.sql");
            }
            if (!tableExists(conn, "payroll", "employee_inv_section6a")) {
                executeResource(conn, "db/migration/payroll/V077__employee_inv_section6a.sql");
            }
            if (!tableExists(conn, "payroll", "employee_inv_pre_tax_deduction")) {
                executeResource(conn, "db/migration/payroll/V078__employee_inv_pre_tax_deduction.sql");
            }
            if (!tableExists(conn, "payroll", "employee_inv_prev_employment")) {
                executeResource(conn, "db/migration/payroll/V079__employee_inv_prev_employment.sql");
            }
            if (!tableExists(conn, "payroll", "employee_inv_other_income")) {
                executeResource(conn, "db/migration/payroll/V080__employee_inv_other_income.sql");
            }
            if (!tableExists(conn, "payroll", "employee_inv_tax_summary")) {
                executeResource(conn, "db/migration/payroll/V081__employee_inv_tax_summary.sql");
            }
            try (Statement st = conn.createStatement()) {
                st.execute("GRANT USAGE ON SCHEMA core, payroll, reference TO app_user");
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
        try (Connection conn = migrationConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT core.seed_system_roles('" + TENANT_A + "')");
            stmt.execute("SELECT core.seed_system_roles('" + TENANT_B + "')");
            stmt.execute("SELECT core.seed_tax_declaration_roles('" + TENANT_A + "')");
            stmt.execute("SELECT core.seed_tax_declaration_roles('" + TENANT_B + "')");
        }
    }

    public static UUID seedEmployee(UUID tenantId, String code, String email, String firstName, String lastName)
            throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.employee (
                            tenant_id, employee_number, work_email, first_name, last_name, date_of_joining, status,
                            created_by, updated_by
                        ) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', 'test', 'test')
                        ON CONFLICT (tenant_id, employee_number) DO UPDATE
                            SET work_email = EXCLUDED.work_email,
                                first_name = EXCLUDED.first_name,
                                last_name = EXCLUDED.last_name
                        RETURNING id
                        """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, code);
            ps.setString(3, email);
            ps.setString(4, firstName);
            ps.setString(5, lastName);
            ps.setObject(6, LocalDate.of(2024, 1, 1));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    public static void clearDeclarations() throws SQLException {
        try (Connection conn = migrationConnection();
                Statement stmt = conn.createStatement()) {
            if (tableExists(conn, "payroll", "employee_inv_tax_summary")) {
                stmt.execute("DELETE FROM payroll.employee_inv_tax_summary");
            }
            if (tableExists(conn, "payroll", "employee_inv_other_income")) {
                stmt.execute("DELETE FROM payroll.employee_inv_other_income");
            }
            if (tableExists(conn, "payroll", "employee_inv_prev_employment")) {
                stmt.execute("DELETE FROM payroll.employee_inv_prev_employment");
            }
            if (tableExists(conn, "payroll", "employee_inv_pre_tax_deduction")) {
                stmt.execute("DELETE FROM payroll.employee_inv_pre_tax_deduction");
            }
            if (tableExists(conn, "payroll", "employee_inv_section6a")) {
                stmt.execute("DELETE FROM payroll.employee_inv_section6a");
            }
            if (tableExists(conn, "payroll", "employee_inv_let_out_property_line")) {
                stmt.execute("DELETE FROM payroll.employee_inv_let_out_property_line");
            }
            if (tableExists(conn, "payroll", "employee_inv_let_out_property")) {
                stmt.execute("DELETE FROM payroll.employee_inv_let_out_property");
            }
            if (tableExists(conn, "payroll", "employee_inv_home_loan")) {
                stmt.execute("DELETE FROM payroll.employee_inv_home_loan");
            }
            if (tableExists(conn, "payroll", "employee_inv_house_rent")) {
                stmt.execute("DELETE FROM payroll.employee_inv_house_rent");
            }
            if (tableExists(conn, "payroll", "employee_investment_declaration")) {
                stmt.execute("DELETE FROM payroll.employee_investment_declaration");
            }
            if (tableExists(conn, "payroll", "income_tax_declaration")) {
                stmt.execute("DELETE FROM payroll.income_tax_declaration");
            }
            if (tableExists(conn, "payroll", "ctc_structure")) {
                stmt.execute("DELETE FROM payroll.ctc_structure");
            }
            if (tableExists(conn, "payroll", "employee_statutory_profile")) {
                stmt.execute("DELETE FROM payroll.employee_statutory_profile");
            }
            if (tableExists(conn, "core", "employee")) {
                stmt.execute("DELETE FROM core.employee WHERE tenant_id IN ('" + TENANT_A + "', '" + TENANT_B + "')");
            }
        }
    }

    public static void clearAll() throws SQLException {
        try (Connection conn = migrationConnection();
                Statement stmt = conn.createStatement()) {
            clearDeclarations();
            if (tableExists(conn, "core", "employee")) {
                stmt.execute("DELETE FROM core.employee");
            }
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

    public static boolean actionExists(Connection conn, String code) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM reference.action WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static boolean functionExists(Connection conn, String schema, String function) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace "
                        + "WHERE n.nspname = ? AND p.proname = ?")) {
            ps.setString(1, schema);
            ps.setString(2, function);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static boolean ruleForYearExists(Connection conn, String table, String fy) throws SQLException {
        if (!tableExists(conn, "reference", table)) {
            return false;
        }
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT 1 FROM reference." + table + " WHERE financial_year = ? LIMIT 1")) {
            ps.setString(1, fy);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static boolean taxSlabAgeCategoryExists(Connection conn, String ageCategory) throws SQLException {
        if (!tableExists(conn, "reference", "tax_slab_master")) {
            return false;
        }
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT 1 FROM reference.tax_slab_master WHERE age_category = ? LIMIT 1")) {
            ps.setString(1, ageCategory);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void executeResource(Connection conn, String resourcePath) throws Exception {
        try (InputStream is = TaxDeclarationTestSchema.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("migration script not found on classpath: " + resourcePath);
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }
}
