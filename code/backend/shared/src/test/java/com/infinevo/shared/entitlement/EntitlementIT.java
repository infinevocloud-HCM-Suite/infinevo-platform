package com.infinevo.shared.entitlement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.shared.authz.FakeCacheService;
import com.infinevo.shared.authz.PermissionCache;
import com.infinevo.shared.cache.CacheService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test for entitlement enforcement (W-12.2, spec section 7 & 8).
 *
 * <p>Verifies:
 * <ul>
 *   <li>Acme (Payroll only) gets 403 MODULE_NOT_ENTITLED on HRMS test endpoint.
 *   <li>Globex gets 200 on the same endpoint.
 *   <li>Globex with status suspended gets 403 TENANT_SUSPENDED.
 *   <li>A module change through W-12.1 is seen on the next request without waiting for TTL.
 *   <li>A revoked module returns 200 on GET and 403 MODULE_NOT_ENTITLED on POST.
 * </ul>
 */
@SpringBootTest(classes = EntitlementIT.TestApp.class)
@AutoConfigureMockMvc(addFilters = false)
class EntitlementIT {

    private final UUID acmeTenant = UUID.randomUUID();
    private final UUID globexTenant = UUID.randomUUID();
    private final UUID downgradedTenant = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StubEntitlementSource source;

    @Autowired
    private PermissionCache permissionCache;

    @BeforeEach
    void setUp() {
        source.grant(acmeTenant, PlatformModule.PAYROLL);
        source.grant(globexTenant, PlatformModule.HRMS, PlatformModule.PAYROLL);
        source.revoke(downgradedTenant, PlatformModule.HRMS);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Acme (Payroll only) gets 403 MODULE_NOT_ENTITLED on HRMS endpoint")
    void acmeGets403ModuleNotEntitled() throws Exception {
        TenantContext.set(acmeTenant);

        mvc.perform(get("/api/v1/test/hrms-guard"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
    }

    @Test
    @DisplayName("Globex (HRMS + Payroll) gets 200 on HRMS endpoint")
    void globexGets200() throws Exception {
        TenantContext.set(globexTenant);

        mvc.perform(get("/api/v1/test/hrms-guard"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    @DisplayName("Globex with status suspended gets 403 TENANT_SUSPENDED")
    void globexSuspendedGets403TenantSuspended() throws Exception {
        source.setSuspended(globexTenant, true);
        permissionCache.bumpVersion(globexTenant);
        TenantContext.set(globexTenant);

        mvc.perform(get("/api/v1/test/hrms-guard"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));
    }

    @Test
    @DisplayName("Module change through W-12.1 is seen on next request without waiting for TTL")
    void moduleChangeSeenImmediatelyWithoutWaitingForTtl() throws Exception {
        TenantContext.set(acmeTenant);

        // Before: refused
        mvc.perform(get("/api/v1/test/hrms-guard"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));

        // Upgrade: grant HRMS and bump version as W-12.1 SubscriptionService does
        source.grant(acmeTenant, PlatformModule.HRMS, PlatformModule.PAYROLL);
        permissionCache.bumpVersion(acmeTenant);

        // After: immediately 200 OK
        mvc.perform(get("/api/v1/test/hrms-guard"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    @DisplayName("Revoked module returns 200 on GET and 403 on POST")
    void revokedModule_200OnGet_403OnPost() throws Exception {
        TenantContext.set(downgradedTenant);

        // Historical read passes under read-only mode
        mvc.perform(get("/api/v1/test/hrms-guard"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        // Mutation is refused
        mvc.perform(post("/api/v1/test/hrms-guard"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_NOT_ENTITLED"));
    }

    @SpringBootApplication(
            scanBasePackages = {"com.infinevo.shared.entitlement", "com.infinevo.shared.authz"},
            exclude = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class
            })
    @EnableAspectJAutoProxy
    static class TestApp {

        @Bean
        CacheService cacheService() {
            return new FakeCacheService();
        }

        @Bean
        StubEntitlementSource stubEntitlementSource() {
            return new StubEntitlementSource();
        }
    }
}
