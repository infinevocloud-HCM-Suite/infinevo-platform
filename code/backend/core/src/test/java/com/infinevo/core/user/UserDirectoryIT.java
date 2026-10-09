package com.infinevo.core.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.core.invitation.KeycloakProvisioningException;
import com.infinevo.core.invitation.KeycloakProvisioningService;
import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code GET /api/v1/users} and Disable / Enable over HTTP (W-73.4 §7): each row carries its roles and linked
 * employee; another tenant's users never appear; {@code core.user.manage} guards all three; a disabled account
 * holds no action until it is enabled again, and Keycloak is told both times.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class UserDirectoryIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private KeycloakProvisioningService keycloak;

    @Autowired
    private UserDirectoryService userDirectoryService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID tenant;
    private UUID otherTenant;
    private UUID adminSub;
    private UUID employeeSub;
    private UUID employeeSubAccount;
    private UUID employeeId;
    private String suffix;

    @BeforeEach
    void seed() throws SQLException {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        tenant = AuthzTestSchema.insertTenant("UserDir " + suffix);
        otherTenant = AuthzTestSchema.insertTenant("UserDirOther " + suffix);

        adminSub = UUID.randomUUID();
        UUID adminAccount = AuthzTestSchema.insertMember(tenant, adminSub, "admin-" + suffix + "@dir.test");
        AuthzTestSchema.grant(tenant, adminAccount, AuthzTestSchema.roleId(tenant, "tenant-admin"));

        employeeSub = UUID.randomUUID();
        employeeSubAccount = AuthzTestSchema.insertMember(tenant, employeeSub, "emp-" + suffix + "@dir.test");
        AuthzTestSchema.grant(tenant, employeeSubAccount, AuthzTestSchema.roleId(tenant, "employee"));
        AuthzTestSchema.grant(tenant, employeeSubAccount, AuthzTestSchema.roleId(tenant, "manager"));
        employeeId = AuthzTestSchema.insertEmployee(tenant, "E-" + suffix, "Asha");
        link(employeeId, employeeSubAccount);

        UUID foreign = AuthzTestSchema.insertMember(otherTenant, UUID.randomUUID(), "foreign-" + suffix + "@dir.test");
        AuthzTestSchema.grant(otherTenant, foreign, AuthzTestSchema.roleId(otherTenant, "tenant-admin"));
    }

    @Test
    @DisplayName("each row carries its roles and linked employee; another tenant's users never appear")
    void listsRolesAndLinkedEmployee_ownTenantOnly() throws Exception {
        mvc.perform(as(adminSub, get("/api/v1/users")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value("admin-" + suffix + "@dir.test"))
                .andExpect(jsonPath("$[0].roles[0].code").value("tenant-admin"))
                .andExpect(jsonPath("$[0].employeeId").doesNotExist())
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[1].email").value("emp-" + suffix + "@dir.test"))
                .andExpect(jsonPath("$[1].roles.length()").value(2))
                .andExpect(jsonPath("$[1].roles[0].code").value("employee"))
                .andExpect(jsonPath("$[1].roles[1].code").value("manager"))
                .andExpect(jsonPath("$[1].employeeId").value(employeeId.toString()))
                .andExpect(jsonPath("$[1].employeeNumber").value("E-" + suffix))
                .andExpect(jsonPath("$[1].displayName").value("Asha"))
                .andExpect(jsonPath("$[*].email")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.hasItem("foreign-" + suffix + "@dir.test"))));

        mvc.perform(as(adminSub, get("/api/v1/users").param("q", "EMP-" + suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(employeeSubAccount.toString()));
    }

    @Test
    @DisplayName("all three endpoints refuse a caller without core.user.manage")
    void requiresUserManage() throws Exception {
        mvc.perform(as(employeeSub, get("/api/v1/users"))).andExpect(status().isForbidden());
        mvc.perform(as(employeeSub, post("/api/v1/users/" + employeeSubAccount + "/disable")))
                .andExpect(status().isForbidden());
        mvc.perform(as(employeeSub, post("/api/v1/users/" + employeeSubAccount + "/enable")))
                .andExpect(status().isForbidden());
        verify(keycloak, never()).setEnabled(org.mockito.ArgumentMatchers.any(), anyBoolean());
    }

    @Test
    @DisplayName("a disabled account holds no action until enabled; Keycloak is told both times")
    void disableThenEnable() throws Exception {
        mvc.perform(as(employeeSub, get("/api/v1/navigation")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));

        mvc.perform(as(adminSub, post("/api/v1/users/" + employeeSubAccount + "/disable")))
                .andExpect(status().isNoContent());
        verify(keycloak).setEnabled(eq(keycloakIdOf(employeeSubAccount)), eq(false));
        mvc.perform(as(adminSub, get("/api/v1/users").param("q", "emp-" + suffix)))
                .andExpect(jsonPath("$[0].enabled").value(false))
                .andExpect(jsonPath("$[0].roles.length()").value(2));
        mvc.perform(as(employeeSub, get("/api/v1/navigation")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions", org.hamcrest.Matchers.empty()));

        mvc.perform(as(adminSub, post("/api/v1/users/" + employeeSubAccount + "/enable")))
                .andExpect(status().isNoContent());
        verify(keycloak).setEnabled(eq(keycloakIdOf(employeeSubAccount)), eq(true));
        mvc.perform(as(employeeSub, get("/api/v1/navigation")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    @DisplayName("Keycloak refusing leaves the account active, and another tenant's account is 404")
    void keycloakFailureRollsBack_foreignIs404() throws Exception {
        doThrow(new KeycloakProvisioningException("down"))
                .when(keycloak)
                .setEnabled(org.mockito.ArgumentMatchers.any(), eq(false));
        mvc.perform(as(adminSub, post("/api/v1/users/" + employeeSubAccount + "/disable")))
                .andExpect(status().isBadGateway());
        mvc.perform(as(adminSub, get("/api/v1/users").param("q", "emp-" + suffix)))
                .andExpect(jsonPath("$[0].enabled").value(true));

        UUID foreign = AuthzTestSchema.insertUserAccount(otherTenant, "x-" + suffix + "@dir.test");
        mvc.perform(as(adminSub, post("/api/v1/users/" + foreign + "/disable"))).andExpect(status().isNotFound());
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("one Keycloak user in two tenants: Keycloak's flag moves only when no other tenant disagrees")
    void sharedKeycloakUser_flagFollowsEveryTenant() throws Exception {
        UUID sharedSub = UUID.randomUUID();
        UUID here = AuthzTestSchema.insertMember(tenant, sharedSub, "shared-" + suffix + "@dir.test");
        UUID there = AuthzTestSchema.insertMember(otherTenant, sharedSub, "shared-" + suffix + "@dir.test");

        // Still ACTIVE in the other tenant: disabling here must not lock them out there
        mvc.perform(as(adminSub, post("/api/v1/users/" + here + "/disable"))).andExpect(status().isNoContent());
        verify(keycloak, never()).setEnabled(any(), anyBoolean());

        // Disabled in the other tenant too: enabling here must not re-open the realm-wide flag
        setStatus(there, "DISABLED");
        mvc.perform(as(adminSub, post("/api/v1/users/" + here + "/enable"))).andExpect(status().isNoContent());
        verify(keycloak, never()).setEnabled(any(), anyBoolean());

        // Disabled everywhere else: disabling here is the last one, so Keycloak is turned off
        mvc.perform(as(adminSub, post("/api/v1/users/" + here + "/disable"))).andExpect(status().isNoContent());
        verify(keycloak).setEnabled(sharedSub, false);

        // Nothing disabled elsewhere any more: enabling here turns Keycloak back on
        setStatus(there, "ACTIVE");
        mvc.perform(as(adminSub, post("/api/v1/users/" + here + "/enable"))).andExpect(status().isNoContent());
        verify(keycloak).setEnabled(sharedSub, true);
    }

    @Test
    @DisplayName("Keycloak accepted but the transaction rolled back: the flag is set back")
    void rollbackRevertsKeycloak() throws Exception {
        TenantContext.set(tenant);
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            userDirectoryService.disable(employeeSubAccount);
            tx.setRollbackOnly();
        });
        UUID kc = keycloakIdOf(employeeSubAccount);
        verify(keycloak).setEnabled(kc, false);
        verify(keycloak).setEnabled(kc, true);
        assertThat(statusOf(employeeSubAccount)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("impersonating a disabled user grants none of its actions")
    void impersonatingDisabledTargetGrantsNothing() throws SQLException {
        UUID staff = UUID.randomUUID();
        AuthzTestSchema.insertMember(PlatformTenant.DEFAULT_PLATFORM_TENANT_ID, staff, "staff-" + suffix + "@x.test");
        UUID session = UUID.randomUUID();
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.impersonation_session
                            (id, tenant_id, platform_user_id, target_user_account_id, reason, expires_at,
                             created_by, updated_by)
                        VALUES (?, ?, ?, ?, 'W-73.4 test', ?, 'test', 'test')
                        """)) {
            ps.setObject(1, session);
            ps.setObject(2, tenant);
            ps.setObject(3, staff);
            ps.setObject(4, employeeSubAccount);
            ps.setTimestamp(5, Timestamp.from(Instant.now().plus(30, ChronoUnit.MINUTES)));
            ps.executeUpdate();
        }
        assertThat(impersonationActions(session, staff)).isNotEmpty();
        setStatus(employeeSubAccount, "DISABLED");
        assertThat(impersonationActions(session, staff)).isEmpty();
    }

    private static String[] impersonationActions(UUID session, UUID staff) throws SQLException {
        try (Connection conn = AuthzTestSchema.appConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT action_codes FROM core.resolve_impersonation(?, ?)")) {
            ps.setObject(1, session);
            ps.setObject(2, staff);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return (String[]) rs.getArray(1).getArray();
            }
        }
    }

    private static void setStatus(UUID accountId, String status) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("UPDATE core.user_account SET status = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setObject(2, accountId);
            ps.executeUpdate();
        }
    }

    private static String statusOf(UUID accountId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT status FROM core.user_account WHERE id = ?")) {
            ps.setObject(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenant.toString())));
    }

    private static void link(UUID employeeId, UUID accountId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("UPDATE core.employee SET user_account_id = ? WHERE id = ?")) {
            ps.setObject(1, accountId);
            ps.setObject(2, employeeId);
            ps.executeUpdate();
        }
    }

    private static UUID keycloakIdOf(UUID accountId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT keycloak_user_id FROM core.user_account WHERE id = ?")) {
            ps.setObject(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }
}
