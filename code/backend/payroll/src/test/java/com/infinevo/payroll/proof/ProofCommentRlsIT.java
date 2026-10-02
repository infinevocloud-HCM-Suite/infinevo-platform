package com.infinevo.payroll.proof;

import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_A;
import static com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-34.2 — row-level security on the comment table (spec section 7).
 *
 * <p>As {@code app_user}, tenant A cannot read or insert tenant B's comments.
 */
class ProofCommentRlsIT extends ProofIntegrationTestBase {

    private static final String TABLE = "payroll.employee_proof_item_comment";

    private UUID employeeB;
    private UUID itemA;
    private UUID itemB;

    private void seedBothTenants() throws Exception {
        openWindows();
        declare();
        itemA = item(proofService.readOwn(fy), ProofSourceKind.HOUSE_RENT).id();

        TenantContext.set(TENANT_B);
        employeeB = TaxDeclarationTestSchema.seedEmployee(TENANT_B, "EMP-B", "b@globex.com", "Bob", "Globex");
        actAs(employeeB);
        openWindows();
        declare(employeeB);
        itemB = item(proofService.readOwn(fy), ProofSourceKind.HOUSE_RENT).id();

        TenantContext.set(TENANT_A);
        actAs(employeeId);

        // Seed one comment in each tenant as migration_user
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("INSERT INTO " + TABLE
                        + " (id, tenant_id, item_id, author_employee_id, author_role, body, created_by)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, TENANT_A);
            ps.setObject(3, itemA);
            ps.setObject(4, employeeId);
            ps.setString(5, "EMPLOYEE");
            ps.setString(6, "Tenant A comment");
            ps.setString(7, "employee@acme.com");
            ps.executeUpdate();

            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, TENANT_B);
            ps.setObject(3, itemB);
            ps.setObject(4, employeeB);
            ps.setString(5, "EMPLOYEE");
            ps.setString(6, "Tenant B comment");
            ps.setString(7, "b@globex.com");
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("An unbound app_user connection sees no comment rows at all")
    void unboundSeesNothing() throws Exception {
        seedBothTenants();
        try (Connection conn = TaxDeclarationTestSchema.appConnection();
                var st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM " + TABLE)) {
                rs.next();
                assertThat(rs.getInt(1)).as(TABLE).isZero();
            }
        }
    }

    @Test
    @DisplayName("Bound to tenant A, app_user sees only tenant A's comments")
    void boundSeesOnlyItsTenant() throws Exception {
        seedBothTenants();
        try (Connection conn = TaxDeclarationTestSchema.appConnection()) {
            bind(conn, TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement("SELECT DISTINCT tenant_id FROM " + TABLE);
                    ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as(TABLE + " has rows").isTrue();
                assertThat(rs.getObject(1, UUID.class)).as(TABLE).isEqualTo(TENANT_A);
                assertThat(rs.next()).as(TABLE + " shows no other tenant").isFalse();
            }
        }
    }

    @Test
    @DisplayName("Bound to tenant A, app_user cannot insert a comment for tenant B")
    void crossTenantInsertRejected() throws Exception {
        seedBothTenants();
        try (Connection conn = TaxDeclarationTestSchema.appConnection()) {
            conn.setAutoCommit(true);
            bind(conn, TENANT_A);

            assertRlsRefuses(
                    conn,
                    "INSERT INTO " + TABLE
                            + " (id, tenant_id, item_id, author_employee_id, author_role, body, created_by)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    TENANT_B,
                    itemB,
                    employeeB,
                    "EMPLOYEE",
                    "Cross-tenant comment attempt",
                    "hacker@acme.com");
        }
    }

    private static void bind(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }

    private static void assertRlsRefuses(Connection conn, String sql, Object... params) {
        assertThatThrownBy(() -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (int i = 0; i < params.length; i++) {
                            ps.setObject(i + 1, params[i]);
                        }
                        ps.executeUpdate();
                    }
                })
                .isInstanceOf(SQLException.class)
                .hasMessageMatching(".*(row-level security|violates row-level security policy|check constraint).*");
    }
}
