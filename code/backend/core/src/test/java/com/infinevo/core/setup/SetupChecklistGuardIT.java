package com.infinevo.core.setup;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.SQLException;
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
 * W-24.1 — Authorization guard tests for setup checklist (spec §7).
 *
 * <p>GET without core.tenant.read is 403; skip with core.tenant.read only is 403;
 * with core.tenant.manage is 200.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class SetupChecklistGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private UUID tenant;
    private UUID noPermSub;
    private UUID readerSub;
    private UUID managerSub;

    @BeforeEach
    void setUp() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("SetupGuard " + UUID.randomUUID());

        // 1. User with no tenant actions (employee role only)
        noPermSub = UUID.randomUUID();
        UUID noPermAccount = AuthzTestSchema.insertMember(tenant, noPermSub, "noperm@guard.test");
        AuthzTestSchema.grant(tenant, noPermAccount, AuthzTestSchema.roleId(tenant, "employee"));

        // 2. User holding core.tenant.read only
        readerSub = UUID.randomUUID();
        UUID readerAccount = AuthzTestSchema.insertMember(tenant, readerSub, "reader@guard.test");
        UUID readerRole = AuthzTestSchema.insertRole(tenant, "tenant-reader", "Reader", "core.tenant.read");
        AuthzTestSchema.grant(tenant, readerAccount, readerRole);

        // 3. User holding core.tenant.manage (and read)
        managerSub = UUID.randomUUID();
        UUID managerAccount = AuthzTestSchema.insertMember(tenant, managerSub, "manager@guard.test");
        UUID managerRole = AuthzTestSchema.insertRole(
                tenant, "tenant-manager", "Manager", "core.tenant.read", "core.tenant.manage");
        AuthzTestSchema.grant(tenant, managerAccount, managerRole);
    }

    @Test
    @DisplayName("GET /api/v1/setup-checklist without core.tenant.read is 403")
    void getChecklist_withoutRead_forbidden() throws Exception {
        mvc.perform(as(noPermSub, get("/api/v1/setup-checklist")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/v1/setup-checklist with core.tenant.read is 200")
    void getChecklist_withRead_ok() throws Exception {
        mvc.perform(as(readerSub, get("/api/v1/setup-checklist")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/setup-checklist/{stepCode}/skip with core.tenant.read only is 403")
    void skipStep_withReadOnly_forbidden() throws Exception {
        SkipStepRequest req = new SkipStepRequest("Not needed");
        mvc.perform(as(readerSub, post("/api/v1/setup-checklist/WORK_LOCATION/skip"))
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /api/v1/setup-checklist/{stepCode}/skip with core.tenant.manage is 200")
    void skipStep_withManage_ok() throws Exception {
        SkipStepRequest req = new SkipStepRequest("Exempt");
        mvc.perform(as(managerSub, post("/api/v1/setup-checklist/WORK_LOCATION/skip"))
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WORK_LOCATION"))
                .andExpect(jsonPath("$.skipped").value(true))
                .andExpect(jsonPath("$.skipReason").value("Exempt"));
    }

    private MockHttpServletRequestBuilder as(UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenant.toString())));
    }
}
