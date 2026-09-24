package com.infinevo.core.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.cache.CacheService;
import com.infinevo.shared.cache.TenantCacheKeyGenerator;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-11.2 over HTTP — {@code @RequiresAction} on the core controllers, checked through the real chain:
 * Spring Security, {@code TenantContextFilter} (membership in {@code core.user_tenant}), the aspect,
 * {@code PermissionService}, the Redis-backed {@code PermissionCache}, and on a miss
 * {@code PermissionReadServiceImpl} as {@code app_user} under row-level security.
 *
 * <p>Three members of one fresh tenant, each holding the seeded system roles a real user would:
 * {@code tenant-admin}; {@code hr} plus {@code employee} (every member of staff holds
 * {@code employee}, {@code V022}); and {@code employee} alone.
 *
 * <p>The grant and role-change tests are the ticket end to end: the refused user's action set is
 * cached first, the change is made <em>through the API</em>, and the very next request sees it — no
 * restart, no eviction call, only the version bump {@code RoleServiceImpl} makes after commit.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class PermissionGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CacheService cacheService;

    private final ObjectMapper json = new ObjectMapper();

    private UUID tenant;
    private UUID adminSub;
    private UUID hrSub;
    private UUID employeeSub;
    private UUID employeeAccount;
    private UUID employeeRole;
    private UUID hrRole;

    @BeforeEach
    void seed() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("Guard " + UUID.randomUUID());
        employeeRole = AuthzTestSchema.roleId(tenant, "employee");
        hrRole = AuthzTestSchema.roleId(tenant, "hr");

        adminSub = UUID.randomUUID();
        UUID adminAccount = AuthzTestSchema.insertMember(tenant, adminSub, "admin@guard.test");
        AuthzTestSchema.grant(tenant, adminAccount, AuthzTestSchema.roleId(tenant, "tenant-admin"));

        hrSub = UUID.randomUUID();
        UUID hrAccount = AuthzTestSchema.insertMember(tenant, hrSub, "hr@guard.test");
        AuthzTestSchema.grant(tenant, hrAccount, hrRole);
        AuthzTestSchema.grant(tenant, hrAccount, employeeRole);

        employeeSub = UUID.randomUUID();
        employeeAccount = AuthzTestSchema.insertMember(tenant, employeeSub, "employee@guard.test");
        AuthzTestSchema.grant(tenant, employeeAccount, employeeRole);
    }

    // ── refused

    @Test
    @DisplayName("employee alone: 403 on a role write, and no role is created")
    void employeeCannotWriteRoles() throws Exception {
        mvc.perform(as(employeeSub, post("/api/v1/roles"))
                        .content(body(new RoleCreateRequest("sneaky", "Sneaky", List.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(containsString("core.role.manage")));

        assertThat(roleExists("sneaky")).isFalse();
    }

    @Test
    @DisplayName("employee alone: 403 granting themselves tenant-admin - the self-grant gap is closed")
    void employeeCannotGrantThemselvesRoles() throws Exception {
        UUID tenantAdmin = AuthzTestSchema.roleId(tenant, "tenant-admin");

        mvc.perform(as(employeeSub, put("/api/v1/users/" + employeeAccount + "/roles"))
                        .content(body(new UserRolesRequest(List.of(employeeRole, tenantAdmin)))))
                .andExpect(status().isForbidden());

        assertThat(AuthzTestSchema.rolesOfUser(employeeAccount)).containsExactly(employeeRole.toString());
    }

    @Test
    @DisplayName("employee alone: 403 on an hr-only employee endpoint; hr gets through to the service")
    void employeeEndpointIsHrOnly() throws Exception {
        UUID nobody = UUID.randomUUID();

        mvc.perform(as(employeeSub, get("/api/v1/employees/" + nobody))).andExpect(status().isForbidden());

        // Through the guard: the service answers, and there is no such employee.
        mvc.perform(as(hrSub, get("/api/v1/employees/" + nobody)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("hr reads roles but cannot change them; tenant-admin can")
    void roleManagementIsTheAdminsAlone() throws Exception {
        mvc.perform(as(hrSub, get("/api/v1/roles"))).andExpect(status().isOk());
        mvc.perform(as(hrSub, post("/api/v1/roles")).content(body(new RoleCreateRequest(null, "By HR", List.of()))))
                .andExpect(status().isForbidden());

        mvc.perform(as(adminSub, post("/api/v1/roles"))
                        .content(body(new RoleCreateRequest(null, "By Admin", List.of()))))
                .andExpect(status().isCreated());
        assertThat(roleExists("by-hr")).isFalse();
        assertThat(roleExists("by-admin")).isTrue();
    }

    @Test
    @DisplayName("A user with no core.user_tenant row never reaches the guard")
    void nonMemberIsRefusedAtTheEdge() throws Exception {
        mvc.perform(as(UUID.randomUUID(), get("/api/v1/roles"))).andExpect(status().isForbidden());
    }

    // ── the bump, end to end

    @Test
    @DisplayName("A grant through the API takes effect on the next request - cached refusal, bump, reload")
    void grantTakesEffectWithoutRestart() throws Exception {
        UUID nobody = UUID.randomUUID();
        mvc.perform(as(employeeSub, get("/api/v1/employees/" + nobody))).andExpect(status().isForbidden());
        String before = version().orElseThrow(() -> new AssertionError("the refusal did not go through the cache"));

        mvc.perform(as(adminSub, put("/api/v1/users/" + employeeAccount + "/roles"))
                        .content(body(new UserRolesRequest(List.of(employeeRole, hrRole)))))
                .andExpect(status().isOk());

        assertThat(version())
                .as("the grant bumped the tenant's version")
                .isPresent()
                .get()
                .isNotEqualTo(before);
        mvc.perform(as(employeeSub, get("/api/v1/employees/" + nobody))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Changing a role's actions through the API takes effect for its holders, both ways")
    void roleUpdateTakesEffectWithoutRestart() throws Exception {
        mvc.perform(as(employeeSub, get("/api/v1/roles"))).andExpect(status().isForbidden());

        String created = mvc.perform(as(adminSub, post("/api/v1/roles"))
                        .content(body(new RoleCreateRequest(null, "Auditor", List.of("core.role.read")))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID auditor = UUID.fromString(json.readTree(created).get("id").asText());
        mvc.perform(as(adminSub, put("/api/v1/users/" + employeeAccount + "/roles"))
                        .content(body(new UserRolesRequest(List.of(employeeRole, auditor)))))
                .andExpect(status().isOk());

        mvc.perform(as(employeeSub, get("/api/v1/roles"))).andExpect(status().isOk());

        // Revocation matters more than a grant: a stale cache here keeps a power someone lost.
        mvc.perform(as(adminSub, put("/api/v1/roles/" + auditor))
                        .content(body(new RoleUpdateRequest("Auditor", List.of()))))
                .andExpect(status().isOk());
        mvc.perform(as(employeeSub, get("/api/v1/roles"))).andExpect(status().isForbidden());

        mvc.perform(as(adminSub, put("/api/v1/users/" + employeeAccount + "/roles"))
                        .content(body(new UserRolesRequest(List.of(employeeRole)))))
                .andExpect(status().isOk());
        mvc.perform(as(adminSub, delete("/api/v1/roles/" + auditor))).andExpect(status().isNoContent());
        assertThat(roleExists("auditor")).isFalse();
    }

    // ── helpers

    /** A request from {@code sub}, carrying this test's tenant as the token claim the filter reads. */
    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenant.toString())));
    }

    private String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    /** The tenant's permission version in Redis — the key {@code PermissionCache} keeps it under. */
    private Optional<String> version() {
        return cacheService.get(TenantCacheKeyGenerator.tenantKey(tenant, "authz", "version"), String.class);
    }

    private boolean roleExists(String code) throws SQLException {
        try {
            AuthzTestSchema.roleId(tenant, code);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
