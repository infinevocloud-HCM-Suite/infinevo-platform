package com.infinevo.payroll.proof;

import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_A;
import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_B;
import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.executeResource;
import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.functionExists;
import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.migrationConnection;
import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.tableExists;

import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Schema and seeding for the proof-of-investment integration tests (W-34.1).
 *
 * <p>It builds on {@link TaxDeclarationTestSchema}, which brings the declaration tables and the tenants,
 * and adds what a proof references: {@code core.document} and the approval tables (V112 has a foreign key
 * to {@code approval_instance}), then V110 to V114 themselves. Every step is guarded, so it can run on the
 * shared database more than once.
 *
 * <p>The proof tables reference declarations, employees and documents with no cascade, and other
 * integration tests delete those rows. So every test here must call {@link #clearProofs()} before its
 * own teardown ends, or it would leave rows that make those other tests fail on a foreign key.
 */
final class ProofTestSchema {

    private ProofTestSchema() {}

    static void apply() throws Exception {
        TaxDeclarationTestSchema.apply();
        try (Connection conn = migrationConnection()) {
            if (!tableExists(conn, "core", "document")) {
                executeResource(conn, "db/migration/core/V037__document.sql");
            }
            if (!tableExists(conn, "core", "approval_definition")) {
                executeResource(conn, "db/migration/core/V089__approval_definition.sql");
            }
            if (!tableExists(conn, "core", "approval_instance")) {
                executeResource(conn, "db/migration/core/V090__approval_instance.sql");
            }
            if (!tableExists(conn, "core", "approval_step")) {
                executeResource(conn, "db/migration/core/V091__approval_step.sql");
            }
            if (!tableExists(conn, "core", "approval_delegation")) {
                executeResource(conn, "db/migration/core/V092__approval_delegation.sql");
            }
            if (!functionExists(conn, "core", "seed_proof_roles")) {
                executeResource(conn, "db/migration/reference/V110__proof_actions.sql");
            }
            if (!columnExists(conn, "income_tax_declaration", "poi_opens_on")) {
                executeResource(conn, "db/migration/payroll/V111__proof_window_columns.sql");
            }
            if (!tableExists(conn, "payroll", "employee_proof_of_investment")) {
                executeResource(conn, "db/migration/payroll/V112__employee_proof_of_investment.sql");
            }
            if (!tableExists(conn, "payroll", "employee_proof_item")) {
                executeResource(conn, "db/migration/payroll/V113__employee_proof_item.sql");
            }
            if (!tableExists(conn, "payroll", "employee_proof_item_document")) {
                executeResource(conn, "db/migration/payroll/V114__employee_proof_item_document.sql");
            }
            if (!tableExists(conn, "payroll", "employee_proof_item_comment")) {
                executeResource(conn, "db/migration/payroll/V115__employee_proof_item_comment.sql");
            }
            try (Statement st = conn.createStatement()) {
                st.execute("GRANT USAGE ON SCHEMA core, payroll, reference TO app_user");
                st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA core TO app_user");
                st.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA payroll TO app_user");
                st.execute("GRANT SELECT ON ALL TABLES IN SCHEMA reference TO app_user");
            }
        }
    }

    /** The two tenants, with their roles and the proof grants, whether or not they were seeded before. */
    static void seedTenants() throws SQLException {
        TaxDeclarationTestSchema.seedTenants();
        try (Connection conn = migrationConnection();
                Statement st = conn.createStatement()) {
            st.execute("SELECT core.seed_proof_roles('" + TENANT_A + "')");
            st.execute("SELECT core.seed_proof_roles('" + TENANT_B + "')");
        }
    }

    /**
     * Deletes every proof row, link and document of the two test tenants, in foreign-key order. Call it
     * before {@code TaxDeclarationTestSchema.clearDeclarations()}, which deletes the declarations and
     * employees these rows point at.
     */
    static void clearProofs() throws SQLException {
        try (Connection conn = migrationConnection();
                Statement st = conn.createStatement()) {
            if (tableExists(conn, "payroll", "employee_proof_item_comment")) {
                st.execute("DELETE FROM payroll.employee_proof_item_comment");
            }
            if (tableExists(conn, "payroll", "employee_proof_item_document")) {
                st.execute("DELETE FROM payroll.employee_proof_item_document");
            }
            if (tableExists(conn, "payroll", "employee_proof_item")) {
                st.execute("DELETE FROM payroll.employee_proof_item");
            }
            if (tableExists(conn, "payroll", "employee_proof_of_investment")) {
                st.execute("DELETE FROM payroll.employee_proof_of_investment");
            }
            // W-34.2: every submit starts an approval instance whose subject is the employee. Left behind,
            // those rows make the teardown that deletes the employees fail on approval_instance's foreign
            // key. The proofs are gone by now, so nothing still points at the instances.
            if (tableExists(conn, "core", "approval_step")) {
                st.execute(
                        "DELETE FROM core.approval_step WHERE tenant_id IN ('" + TENANT_A + "', '" + TENANT_B + "')");
            }
            if (tableExists(conn, "core", "approval_instance")) {
                st.execute("DELETE FROM core.approval_instance WHERE tenant_id IN ('" + TENANT_A + "', '" + TENANT_B
                        + "')");
            }
            if (tableExists(conn, "core", "document")) {
                st.execute("DELETE FROM core.document WHERE tenant_id IN ('" + TENANT_A + "', '" + TENANT_B + "')");
            }
        }
    }

    static long count(String table, java.util.UUID tenantId) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM " + table + " WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM information_schema.columns"
                + " WHERE table_schema = 'payroll' AND table_name = ? AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
