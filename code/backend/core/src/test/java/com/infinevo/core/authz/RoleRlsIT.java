package com.infinevo.core.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-11.1 — tenant A cannot read or grant tenant B's roles, as {@code app_user} (spec section 7).
 *
 * <p>Asserted at two levels, because they fail independently: through the services, which is how the
 * application reaches the rows, and on a raw {@code app_user} connection, which is the
 * {@code tenant_isolation} policy of {@code V021}-{@code V023} with no Java in the way. Every service
 * method here names the tenant in its query, so a service-level "not found" alone could not tell an
 * enforced policy from a {@code WHERE} clause.
 *
 * <p>Each test gets two fresh tenants, each with a user and a tenant role, and the seven system roles
 * the {@code core.tenant} trigger gave them.
 */
@SpringBootTest(classes = AuthzTestApp.class)
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
class RoleRlsIT extends AbstractIntegrationTest {

    @Autowired
    private RoleService roleService;

    @Autowired
    private PermissionReadService permissionReadService;

    @Autowired
    private DataSource dataSource;

    private UUID tenantA;
    private UUID tenantB;
    private UUID userOfA;
    private UUID userOfB;
    private UUID roleOfA;
    private UUID roleOfB;

    @BeforeEach
    void seed() throws SQLException {
        TenantContext.clear();
        tenantA = AuthzTestSchema.insertTenant("Rls Alpha");
        tenantB = AuthzTestSchema.insertTenant("Rls Beta");
        userOfA = AuthzTestSchema.insertUserAccount(tenantA, "a@alpha.test");
        userOfB = AuthzTestSchema.insertUserAccount(tenantB, "b@beta.test");
        roleOfA = AuthzTestSchema.insertRole(tenantA, "reviewer", "Reviewer", "core.role.read");
        roleOfB = AuthzTestSchema.insertRole(tenantB, "reviewer", "Revisor", "core.role.read", "core.org.read");
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("The context talks to the W-11.1 database as app_user — the control for everything below")
    void contextUsesTheDedicatedDatabaseAsAppUser() throws SQLException {
        try (Connection conn = dataSource.getConnection();
                ResultSet rs = conn.createStatement().executeQuery("SELECT current_database(), current_user")) {
            rs.next();
            assertThat(rs.getString(1)).isEqualTo(AuthzTestSchema.DATABASE);
            assertThat(rs.getString(2)).isEqualTo(PostgresTestContainerInitializer.APP_USER);
        }
    }

    // ── read

    @Test
    @DisplayName("Row-level security alone hides tenant B's roles, actions and grants, on a raw app_user connection")
    void rlsHidesTheOtherTenantOnARawConnection() throws SQLException {
        AuthzTestSchema.grant(tenantB, userOfB, roleOfB);

        assertThat(visibleAsApp(tenantA, "SELECT count(*) FROM core.role WHERE tenant_id = ?", tenantB))
                .as("tenant B roles visible to app_user bound to A")
                .isZero();
        assertThat(visibleAsApp(tenantA, "SELECT count(*) FROM core.role_action WHERE tenant_id = ?", tenantB))
                .as("tenant B role actions visible to app_user bound to A")
                .isZero();
        assertThat(visibleAsApp(tenantA, "SELECT count(*) FROM core.user_role WHERE tenant_id = ?", tenantB))
                .as("tenant B grants visible to app_user bound to A")
                .isZero();
        assertThat(visibleAsApp(tenantA, "SELECT count(*) FROM core.role WHERE id = ?", roleOfB))
                .isZero();

        // The control: the same reads with B bound see B's rows, so they were there to be hidden.
        assertThat(visibleAsApp(tenantB, "SELECT count(*) FROM core.role WHERE id = ?", roleOfB))
                .isEqualTo(1);
        assertThat(visibleAsApp(tenantB, "SELECT count(*) FROM core.user_role WHERE tenant_id = ?", tenantB))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Bound to A, the role list is A's seven system roles and A's own role, none of B's")
    void listSeesOnlyTheBoundTenant() {
        TenantContext.set(tenantA);

        List<RoleResponse> roles = roleService.list();

        assertThat(roles).allSatisfy(r -> assertThat(r.tenantId()).isEqualTo(tenantA));
        assertThat(roles).extracting(RoleResponse::id).doesNotContain(roleOfB).contains(roleOfA);
        assertThat(roles).filteredOn(RoleResponse::system).hasSize(7);
        assertThat(roles).hasSize(8);
    }

    // ── change

    @Test
    @DisplayName("Tenant A cannot update or delete tenant B's role, and the row is untouched")
    void cannotChangeTheOtherTenantsRole() throws SQLException {
        TenantContext.set(tenantA);

        assertThatThrownBy(() -> roleService.update(roleOfB, new RoleUpdateRequest("Mallory", List.of())))
                .isInstanceOf(RoleService.NotFoundException.class);
        assertThatThrownBy(() -> roleService.delete(roleOfB)).isInstanceOf(RoleService.NotFoundException.class);

        assertThat(AuthzTestSchema.readColumn("role", roleOfB, "name")).isEqualTo("Revisor");
        assertThat(AuthzTestSchema.actionsOfRole(roleOfB)).containsExactlyInAnyOrder("core.role.read", "core.org.read");
    }

    // ── grant

    @Test
    @DisplayName("Tenant A cannot grant tenant B's role to A's user")
    void cannotGrantTheOtherTenantsRole() throws SQLException {
        TenantContext.set(tenantA);

        assertThatThrownBy(() -> roleService.replaceUserRoles(userOfA, new UserRolesRequest(List.of(roleOfB))))
                .isInstanceOf(RoleService.NotFoundException.class)
                .hasMessageContaining(roleOfB.toString());

        assertThat(AuthzTestSchema.rolesOfUser(userOfA)).isEmpty();
    }

    @Test
    @DisplayName("Tenant A cannot grant its own role to tenant B's user")
    void cannotGrantToTheOtherTenantsUser() throws SQLException {
        TenantContext.set(tenantA);

        assertThatThrownBy(() -> roleService.replaceUserRoles(userOfB, new UserRolesRequest(List.of(roleOfA))))
                .isInstanceOf(RoleService.NotFoundException.class)
                .hasMessageContaining("user");

        assertThat(AuthzTestSchema.rolesOfUser(userOfB)).isEmpty();
    }

    @Test
    @DisplayName(
            "A raw grant across the boundary fails in the database too — policy for the tenant, foreign key for the role")
    void rawCrossTenantGrantsAreRefusedByTheDatabase() throws SQLException {
        // A row claiming tenant B, written with A bound: the policy's USING clause is the insert check.
        assertThatThrownBy(() -> insertGrantAsApp(tenantA, tenantB, userOfB, roleOfB))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("row-level security");
        // A row in tenant A pointing at B's role: the composite (tenant_id, role_id) key has no match.
        assertThatThrownBy(() -> insertGrantAsApp(tenantA, tenantA, userOfA, roleOfB))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("user_role_role_fkey");

        assertThat(AuthzTestSchema.rolesOfUser(userOfA)).isEmpty();
        assertThat(AuthzTestSchema.rolesOfUser(userOfB)).isEmpty();
    }

    // ── actionsOf

    @Test
    @DisplayName("actionsOf returns only the bound tenant's actions")
    void actionsOfIsTenantScoped() throws SQLException {
        UUID employeeOfA = AuthzTestSchema.roleId(tenantA, "employee");
        UUID hrOfB = AuthzTestSchema.roleId(tenantB, "hr");
        AuthzTestSchema.grant(tenantA, userOfA, employeeOfA);
        AuthzTestSchema.grant(tenantA, userOfA, roleOfA);
        AuthzTestSchema.grant(tenantB, userOfB, hrOfB);

        TenantContext.set(tenantA);
        Set<String> expected = new HashSet<>(AuthzTestSchema.actionsOfRole(employeeOfA));
        expected.addAll(AuthzTestSchema.actionsOfRole(roleOfA));

        assertThat(permissionReadService.actionsOf(userOfA)).isNotEmpty().isEqualTo(expected);
        assertThat(permissionReadService.actionsOf(userOfB))
                .as("B's user holds hr in B, which bound to A is nothing")
                .isEmpty();

        TenantContext.set(tenantB);
        assertThat(permissionReadService.actionsOf(userOfB)).isEqualTo(AuthzTestSchema.actionsOfRole(hrOfB));
        assertThat(permissionReadService.actionsOf(userOfA)).isEmpty();
    }

    // ── the own-tenant control: the whole lifecycle works when nothing crosses a boundary

    @Test
    @DisplayName("Within its own tenant a role is created, edited, granted, refused deletion while held, then deleted")
    void ownTenantLifecycle() throws SQLException {
        TenantContext.set(tenantA);

        RoleResponse created = roleService.create(
                new RoleCreateRequest(null, "Leave Clerk", List.of("hrms.leave.read", "hrms.leave.approve")));
        assertThat(created.code()).isEqualTo("leave-clerk");
        assertThat(AuthzTestSchema.actionsOfRole(created.id()))
                .containsExactlyInAnyOrder("hrms.leave.read", "hrms.leave.approve");

        assertThatThrownBy(() -> roleService.create(new RoleCreateRequest("leave-clerk", "Again", List.of())))
                .isInstanceOf(RoleService.DuplicateCodeException.class);
        assertThatThrownBy(() -> roleService.create(new RoleCreateRequest(null, "Bad", List.of("hrms.leave.invent"))))
                .isInstanceOf(RoleService.ValidationException.class)
                .hasMessageContaining("hrms.leave.invent");

        RoleResponse updated = roleService.update(
                created.id(), new RoleUpdateRequest("Leave Officer", List.of("hrms.leave.read", "hrms.holiday.read")));
        assertThat(updated.name()).isEqualTo("Leave Officer");
        assertThat(AuthzTestSchema.actionsOfRole(created.id()))
                .containsExactlyInAnyOrder("hrms.leave.read", "hrms.holiday.read");

        UUID hr = AuthzTestSchema.roleId(tenantA, "hr");
        assertThatThrownBy(() -> roleService.update(hr, new RoleUpdateRequest("People", List.of())))
                .isInstanceOf(RoleService.SystemRoleException.class);
        assertThatThrownBy(() -> roleService.delete(hr)).isInstanceOf(RoleService.SystemRoleException.class);

        UserRolesResponse granted =
                roleService.replaceUserRoles(userOfA, new UserRolesRequest(List.of(created.id(), hr)));
        assertThat(granted.roles()).extracting(RoleResponse::code).containsExactly("hr", "leave-clerk");
        assertThat(AuthzTestSchema.rolesOfUser(userOfA))
                .containsExactlyInAnyOrder(created.id().toString(), hr.toString());

        assertThatThrownBy(() -> roleService.delete(created.id())).isInstanceOf(RoleService.RoleInUseException.class);

        roleService.replaceUserRoles(userOfA, new UserRolesRequest(List.of(hr)));
        assertThat(AuthzTestSchema.rolesOfUser(userOfA)).containsExactly(hr.toString());

        roleService.delete(created.id());
        assertThat(AuthzTestSchema.readColumn("role", created.id(), "id")).isNull();
        assertThat(AuthzTestSchema.actionsOfRole(created.id())).isEmpty();
    }

    /** One count as {@code app_user} with {@code tenantId} bound. */
    private static int visibleAsApp(UUID tenantId, String sql, UUID param) throws SQLException {
        try (Connection conn = AuthzTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try {
                AuthzTestSchema.bindTenant(conn, tenantId);
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, param);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        return rs.getInt(1);
                    }
                }
            } finally {
                conn.rollback();
            }
        }
    }

    /** Inserts a grant as {@code app_user} with {@code boundTenant} bound, rolled back whatever happens. */
    private static void insertGrantAsApp(UUID boundTenant, UUID rowTenant, UUID userAccountId, UUID roleId)
            throws SQLException {
        try (Connection conn = AuthzTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try {
                AuthzTestSchema.bindTenant(conn, boundTenant);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.user_role (tenant_id, user_account_id, role_id) VALUES (?, ?, ?)")) {
                    ps.setObject(1, rowTenant);
                    ps.setObject(2, userAccountId);
                    ps.setObject(3, roleId);
                    ps.executeUpdate();
                }
            } finally {
                conn.rollback();
            }
        }
    }
}
