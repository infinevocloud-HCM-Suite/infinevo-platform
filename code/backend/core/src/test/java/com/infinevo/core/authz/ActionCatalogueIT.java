package com.infinevo.core.authz;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-11.1 — every action code a seeded system role holds exists in the catalogue (spec section 7), and
 * a new tenant gets the seven system roles (decision 2).
 *
 * <p>The foreign key on {@code role_action.action_code} makes an orphan impossible to store, so the
 * containment assertion alone could pass vacuously if the seed silently granted nothing. Hence the
 * non-empty checks on every one of the seven: the risk this test exists for is a seeded role that
 * drifted from the catalogue, and "holds nothing" is the silent form of that drift.
 */
@SpringBootTest(classes = AuthzTestApp.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class ActionCatalogueIT extends AbstractIntegrationTest {

    @Autowired
    private RoleService roleService;

    private UUID tenant;

    @BeforeEach
    void newTenant() throws SQLException {
        TenantContext.clear();
        tenant = AuthzTestSchema.insertTenant("Catalogue Gamma");
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A new tenant has exactly the seven system roles, each holding at least one action")
    void newTenantHasTheSevenSystemRoles() throws SQLException {
        Map<String, Set<String>> held = systemRoleActions(tenant);

        assertThat(held.keySet()).containsExactlyInAnyOrderElementsOf(AuthzTestSchema.SYSTEM_ROLES);
        assertThat(held)
                .allSatisfy((role, actions) ->
                        assertThat(actions).as("actions held by " + role).isNotEmpty());
    }

    @Test
    @DisplayName("Every action code a seeded system role holds exists in reference.action")
    void everySeededActionIsInTheCatalogue() throws SQLException {
        Set<String> catalogue = catalogue();
        Set<String> seeded = systemRoleActions(tenant).values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(catalogue).isNotEmpty();
        assertThat(seeded).isNotEmpty();
        assertThat(catalogue).containsAll(seeded);
    }

    @Test
    @DisplayName("platform-admin and tenant-admin each hold the whole catalogue but tenant provisioning")
    void adminRolesCoverTheCatalogue() throws SQLException {
        Set<String> catalogue = catalogue();
        Map<String, Set<String>> held = systemRoleActions(tenant);

        assertThat(catalogue).contains("core.tenant.provision");
        Set<String> expected = new TreeSet<>(catalogue);
        expected.remove("core.tenant.provision");
        assertThat(held.get("platform-admin")).isEqualTo(expected).hasSize(catalogue.size() - 1);
        assertThat(held.get("tenant-admin")).isEqualTo(expected).hasSize(catalogue.size() - 1);
    }

    @Test
    @DisplayName(
            "W-11.3: a tenant inserted after V025 has no provisioning grant on any role, and hr holds core.leave.read")
    void newTenantGetsTheCorrectedGrants() throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        SELECT count(*)
                          FROM core.role_action
                         WHERE tenant_id = ? AND action_code = 'core.tenant.provision'
                        """)) {
            ps.setObject(1, tenant);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }

        Map<String, Set<String>> held = systemRoleActions(tenant);
        assertThat(held.get("hr")).contains("core.leave.read").doesNotContain("hrms.leave.read");
        assertThat(held.get("employee")).contains("core.leave.apply", "hrms.attendance.mark");
    }

    @Test
    @DisplayName(
            "Through the service: the catalogue is the reference table, and the tenant's roles are the seeded ones")
    void serviceReadsMatchTheDatabase() throws SQLException {
        TenantContext.set(tenant);

        List<ActionResponse> actions = roleService.listActions();
        assertThat(actions).extracting(ActionResponse::code).containsExactlyInAnyOrderElementsOf(catalogue());
        assertThat(actions).extracting(ActionResponse::module).containsOnly("core", "hrms", "payroll");

        List<RoleResponse> roles = roleService.list();
        Map<String, Set<String>> held = systemRoleActions(tenant);
        assertThat(roles).allSatisfy(r -> assertThat(r.system()).isTrue());
        assertThat(roles).hasSize(7).allSatisfy(r -> assertThat(Set.copyOf(r.actionCodes()))
                .as(r.code())
                .isEqualTo(held.get(r.code())));
    }

    @Test
    @DisplayName("app_user reads the catalogue with no tenant bound — it is not tenant data")
    void catalogueNeedsNoTenant() throws SQLException {
        try (Connection conn = AuthzTestSchema.appConnection();
                ResultSet rs = conn.createStatement().executeQuery("SELECT count(*) FROM reference.action")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(catalogue().size());
        }
    }

    /** Every code in {@code reference.action}, sorted, as the schema owner. */
    private static Set<String> catalogue() throws SQLException {
        Set<String> codes = new TreeSet<>();
        try (Connection conn = AuthzTestSchema.migrationConnection();
                ResultSet rs = conn.createStatement().executeQuery("SELECT code FROM reference.action ORDER BY code")) {
            while (rs.next()) {
                codes.add(rs.getString(1));
            }
        }
        return codes;
    }

    /** System role code to the action codes it holds, in one tenant, as the schema owner. */
    private static Map<String, Set<String>> systemRoleActions(UUID tenantId) throws SQLException {
        Map<String, Set<String>> held = new HashMap<>();
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        SELECT r.code, ra.action_code
                          FROM core.role r
                          LEFT JOIN core.role_action ra ON ra.tenant_id = r.tenant_id AND ra.role_id = r.id
                         WHERE r.tenant_id = ? AND r.is_system
                        """)) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Set<String> actions = held.computeIfAbsent(rs.getString(1), k -> new TreeSet<>());
                    String action = rs.getString(2);
                    if (action != null) {
                        actions.add(action);
                    }
                }
            }
        }
        return held;
    }
}
