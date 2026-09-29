package com.infinevo.core.lop;

import static com.infinevo.core.lop.LopTestSchema.TENANT_A;
import static com.infinevo.core.lop.LopTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-18.1 — Row-level security: tenant A cannot read or edit tenant B's loss-of-pay policies.
 */
@SpringBootTest(classes = LopTestApp.class, properties = "spring.main.allow-bean-definition-overriding=true")
class LopPolicyRlsIT extends AbstractIntegrationTest {

    private UUID policyAId;
    private UUID policyBId;

    @BeforeAll
    static void applySchema() throws Exception {
        LopTestSchema.apply();
        LopTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        LopTestSchema.clearAll();
    }

    @BeforeEach
    void seedData() throws SQLException {
        LopTestSchema.clearAll();

        policyAId = UUID.randomUUID();
        policyBId = UUID.randomUUID();

        try (Connection conn = LopTestSchema.migrationConnection()) {
            // Seed policy for Tenant A
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.lop_policy (id, tenant_id, working_day_basis, configured_days_per_month,"
                            + " weekends_payable, holidays_payable, lop_rounding, effective_from) VALUES (?, ?,"
                            + " 'ACTUAL_DAYS', NULL, true, true, 'HALF_UP_2', '2026-01-01')")) {
                ps.setObject(1, policyAId);
                ps.setObject(2, TENANT_A);
                ps.executeUpdate();
            }

            // Seed policy for Tenant B
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.lop_policy (id, tenant_id, working_day_basis, configured_days_per_month,"
                            + " weekends_payable, holidays_payable, lop_rounding, effective_from) VALUES (?, ?,"
                            + " 'FIXED_30', NULL, true, true, 'HALF_UP_2', '2026-01-01')")) {
                ps.setObject(1, policyBId);
                ps.setObject(2, TENANT_B);
                ps.executeUpdate();
            }
        }
    }

    @Test
    @DisplayName("app_user bound to Tenant A sees only Tenant A loss-of-pay policy")
    void appUserBoundToTenantASeesOnlyTenantA() throws SQLException {
        try (Connection conn = LopTestSchema.appConnection()) {
            setTenantContext(conn, TENANT_A);

            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT id, tenant_id FROM core.lop_policy")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    assertThat((UUID) rs.getObject("id")).isEqualTo(policyAId);
                    assertThat((UUID) rs.getObject("tenant_id")).isEqualTo(TENANT_A);
                }
                assertThat(count).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("app_user bound to Tenant B sees only Tenant B loss-of-pay policy")
    void appUserBoundToTenantBSeesOnlyTenantB() throws SQLException {
        try (Connection conn = LopTestSchema.appConnection()) {
            setTenantContext(conn, TENANT_B);

            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT id, tenant_id FROM core.lop_policy")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    assertThat((UUID) rs.getObject("id")).isEqualTo(policyBId);
                    assertThat((UUID) rs.getObject("tenant_id")).isEqualTo(TENANT_B);
                }
                assertThat(count).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("Tenant A cannot update Tenant B's loss-of-pay policy under RLS")
    void tenantACannotUpdateTenantBPolicy() throws SQLException {
        try (Connection conn = LopTestSchema.appConnection()) {
            setTenantContext(conn, TENANT_A);

            try (PreparedStatement ps =
                    conn.prepareStatement("UPDATE core.lop_policy SET working_day_basis = 'FIXED_30' WHERE id = ?")) {
                ps.setObject(1, policyBId);
                int rowsUpdated = ps.executeUpdate();
                assertThat(rowsUpdated).isEqualTo(0);
            }
        }
    }

    @Test
    @DisplayName("unbound app_user sees 0 rows under RLS")
    void unboundAppUserSeesZeroRows() throws SQLException {
        try (Connection conn = LopTestSchema.appConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS c FROM core.lop_policy")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong("c")).isEqualTo(0L);
        }
    }

    private void setTenantContext(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }
}
