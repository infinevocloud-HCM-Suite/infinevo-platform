package com.infinevo.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-65.1 spec §7: as app_user bound to Acme, SELECT * FROM core.tenant returns one row and
 * SELECT * FROM core.list_tenants() returns all — proving the function is the only door.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class PlatformTenantRlsIT extends AbstractIntegrationTest {

    private UUID acmeTenant;
    private UUID globexTenant;

    @BeforeEach
    void setUp() throws SQLException {
        acmeTenant = AuthzTestSchema.insertTenant("Acme RLS " + UUID.randomUUID());
        globexTenant = AuthzTestSchema.insertTenant("Globex RLS " + UUID.randomUUID());
    }

    @Test
    @DisplayName("app_user bound to Acme sees only Acme in core.tenant table due to RLS")
    void appUser_boundToAcme_tableSelectReturnsOnlyBoundTenant() throws SQLException {
        try (Connection conn = AuthzTestSchema.appConnection()) {
            bindTenant(conn, acmeTenant);

            try (PreparedStatement ps = conn.prepareStatement("SELECT tenant_id FROM core.tenant");
                    ResultSet rs = ps.executeQuery()) {
                List<UUID> visibleTenants = new ArrayList<>();
                while (rs.next()) {
                    visibleTenants.add(rs.getObject("tenant_id", UUID.class));
                }

                assertThat(visibleTenants).containsExactly(acmeTenant);
            }
        }
    }

    @Test
    @DisplayName("app_user bound to Acme sees all tenants via core.list_tenants() SECURITY DEFINER function")
    void appUser_boundToAcme_listTenantsFunctionReturnsAllTenants() throws SQLException {
        try (Connection conn = AuthzTestSchema.appConnection()) {
            bindTenant(conn, acmeTenant);

            try (PreparedStatement ps = conn.prepareStatement("SELECT tenant_id FROM core.list_tenants()");
                    ResultSet rs = ps.executeQuery()) {
                List<UUID> returnedTenants = new ArrayList<>();
                while (rs.next()) {
                    returnedTenants.add(rs.getObject("tenant_id", UUID.class));
                }

                assertThat(returnedTenants).contains(acmeTenant, globexTenant);
            }
        }
    }

    @Test
    @DisplayName("app_user bound to Acme can read other tenant via core.get_tenant_overview() function")
    void appUser_boundToAcme_getTenantOverviewReturnsTargetTenant() throws SQLException {
        try (Connection conn = AuthzTestSchema.appConnection()) {
            bindTenant(conn, acmeTenant);

            try (PreparedStatement ps = conn.prepareStatement("SELECT tenant_id FROM core.get_tenant_overview(?)")) {
                ps.setObject(1, globexTenant);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getObject("tenant_id", UUID.class)).isEqualTo(globexTenant);
                }

                ps.setObject(1, UUID.randomUUID());
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isFalse();
                }
            }
        }
    }

    /**
     * Session-level, not transaction-local: the connection is in autocommit, so a local setting would
     * be gone by the next statement and RLS would see no tenant at all. The connection is closed after
     * each test, which ends the session.
     */
    private void bindTenant(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }
}
