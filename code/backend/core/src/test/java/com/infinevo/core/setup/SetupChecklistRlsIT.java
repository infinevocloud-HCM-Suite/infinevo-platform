package com.infinevo.core.setup;

import static com.infinevo.core.setup.SetupChecklistTestSchema.TENANT_A;
import static com.infinevo.core.setup.SetupChecklistTestSchema.TENANT_B;
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
 * W-24.1 — tenant A cannot read tenant B's checklist under row-level security (spec §7).
 */
@SpringBootTest(classes = SetupChecklistTestApp.class)
class SetupChecklistRlsIT extends AbstractIntegrationTest {

    @BeforeAll
    static void applySchema() throws Exception {
        SetupChecklistTestSchema.apply();
        SetupChecklistTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        SetupChecklistTestSchema.clearAll();
    }

    @BeforeEach
    void seedSteps() throws SQLException {
        SetupChecklistTestSchema.clearAll();

        try (Connection conn = SetupChecklistTestSchema.migrationConnection()) {
            // Seed a step for Tenant A
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.tenant_setup_step (id, tenant_id, step_code, display_order) VALUES (?, ?, 'WORK_LOCATION', 1)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT_A);
                ps.executeUpdate();
            }

            // Seed a step for Tenant B
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.tenant_setup_step (id, tenant_id, step_code, display_order) VALUES (?, ?, 'WORK_LOCATION', 1)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT_B);
                ps.executeUpdate();
            }
        }
    }

    @Test
    @DisplayName("app_user bound to Tenant A sees only Tenant A rows")
    void appUserBoundToTenantASeesOnlyTenantA() throws SQLException {
        try (Connection conn = SetupChecklistTestSchema.appConnection()) {
            setTenantContext(conn, TENANT_A);

            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT tenant_id FROM core.tenant_setup_step")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    UUID found = (UUID) rs.getObject("tenant_id");
                    assertThat(found).isEqualTo(TENANT_A);
                }
                assertThat(count).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("app_user bound to Tenant B sees only Tenant B rows")
    void appUserBoundToTenantBSeesOnlyTenantB() throws SQLException {
        try (Connection conn = SetupChecklistTestSchema.appConnection()) {
            setTenantContext(conn, TENANT_B);

            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT tenant_id FROM core.tenant_setup_step")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    UUID found = (UUID) rs.getObject("tenant_id");
                    assertThat(found).isEqualTo(TENANT_B);
                }
                assertThat(count).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("unbound app_user sees 0 rows under RLS")
    void unboundAppUserSeesZeroRows() throws SQLException {
        try (Connection conn = SetupChecklistTestSchema.appConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.tenant_setup_step")) {
            rs.next();
            assertThat(rs.getLong(1)).isZero();
        }
    }

    private void setTenantContext(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }
}
