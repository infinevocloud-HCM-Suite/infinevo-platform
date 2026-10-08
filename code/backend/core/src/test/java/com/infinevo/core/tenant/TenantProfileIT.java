package com.infinevo.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
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
 * W-73.1 spec section 7 — {@code /api/v1/tenants/current/profile} over HTTP, through the shipped filter
 * chain, the real permission check and a real PostgreSQL with row-level security: {@code PUT} needs
 * {@code core.tenant.manage}; a logo document from another tenant is refused; the written row is the
 * bound tenant's and nobody else's.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class TenantProfileIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private UUID acmeTenant;
    private UUID acmeAdminSub;
    private UUID acmeHrSub;
    private UUID globexTenant;

    @BeforeEach
    void setUp() throws SQLException {
        acmeTenant = AuthzTestSchema.insertTenant("Acme Profile " + UUID.randomUUID());
        acmeAdminSub = UUID.randomUUID();
        UUID adminAccount = AuthzTestSchema.insertMember(acmeTenant, acmeAdminSub, "admin@acme.profile.test");
        AuthzTestSchema.grant(acmeTenant, adminAccount, AuthzTestSchema.roleId(acmeTenant, "tenant-admin"));

        // hr holds core.tenant.read (V158 seed) and not core.tenant.manage.
        acmeHrSub = UUID.randomUUID();
        UUID hrAccount = AuthzTestSchema.insertMember(acmeTenant, acmeHrSub, "hr@acme.profile.test");
        AuthzTestSchema.grant(acmeTenant, hrAccount, AuthzTestSchema.roleId(acmeTenant, "hr"));

        globexTenant = AuthzTestSchema.insertTenant("Globex Profile " + UUID.randomUUID());
    }

    @Test
    @DisplayName("A fresh tenant: the name, no tagline, no logo - and hr may read it")
    void freshTenantProfile() throws Exception {
        mvc.perform(as(acmeTenant, acmeHrSub, get("/api/v1/tenants/current/profile")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(org.hamcrest.Matchers.startsWith("Acme Profile ")))
                .andExpect(jsonPath("$.tagline").doesNotExist())
                .andExpect(jsonPath("$.logoDocumentId").doesNotExist())
                .andExpect(jsonPath("$.logoUrl").doesNotExist());
    }

    @Test
    @DisplayName("PUT needs core.tenant.manage: hr is refused 403 and the row is untouched")
    void putNeedsTenantManage() throws Exception {
        mvc.perform(as(acmeTenant, acmeHrSub, put("/api/v1/tenants/current/profile"))
                        .content(json.writeValueAsString(Map.of("tagline", "People first"))))
                .andExpect(status().isForbidden());

        assertThat(tagline(acmeTenant)).isNull();
    }

    @Test
    @DisplayName("The admin sets a tagline and a logo; the row is written; a blank tagline clears it")
    void adminUpdatesProfile() throws Exception {
        UUID logo = AuthzTestSchema.insertDocumentRow(acmeTenant, "TENANT_LOGO", "image/png", 40_000);

        mvc.perform(as(acmeTenant, acmeAdminSub, put("/api/v1/tenants/current/profile"))
                        .content(json.writeValueAsString(
                                Map.of("tagline", "  People first  ", "logoDocumentId", logo.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagline").value("People first"))
                .andExpect(jsonPath("$.logoDocumentId").value(logo.toString()));

        assertThat(tagline(acmeTenant)).isEqualTo("People first");
        assertThat(logoDocumentId(acmeTenant)).isEqualTo(logo);
        assertThat(tagline(globexTenant))
                .as("the other tenant's row is untouched")
                .isNull();

        mvc.perform(as(acmeTenant, acmeAdminSub, put("/api/v1/tenants/current/profile"))
                        .content(json.writeValueAsString(Map.of("tagline", "   "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagline").doesNotExist())
                .andExpect(jsonPath("$.logoDocumentId").doesNotExist());
        assertThat(logoDocumentId(acmeTenant)).isNull();
    }

    @Test
    @DisplayName("A logo document from another tenant is refused (row-level security): 400, nothing written")
    void otherTenantsLogoIsRefused() throws Exception {
        UUID theirLogo = AuthzTestSchema.insertDocumentRow(globexTenant, "TENANT_LOGO", "image/png", 40_000);

        mvc.perform(as(acmeTenant, acmeAdminSub, put("/api/v1/tenants/current/profile"))
                        .content(json.writeValueAsString(Map.of("logoDocumentId", theirLogo.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No document " + theirLogo + " in this tenant"));

        assertThat(logoDocumentId(acmeTenant)).isNull();
    }

    @Test
    @DisplayName("A document that is not a TENANT_LOGO, not an image, or too big is refused 400")
    void wrongKindTypeOrSizeIsRefused() throws Exception {
        UUID notALogo = AuthzTestSchema.insertDocumentRow(acmeTenant, "EMPLOYEE_DOCUMENT", "image/png", 40_000);
        UUID notAnImage = AuthzTestSchema.insertDocumentRow(acmeTenant, "TENANT_LOGO", "application/pdf", 40_000);
        UUID tooBig = AuthzTestSchema.insertDocumentRow(acmeTenant, "TENANT_LOGO", "image/png", 512L * 1024 + 1);

        for (UUID id : new UUID[] {notALogo, notAnImage, tooBig}) {
            mvc.perform(as(acmeTenant, acmeAdminSub, put("/api/v1/tenants/current/profile"))
                            .content(json.writeValueAsString(Map.of("logoDocumentId", id.toString()))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(logoDocumentId(acmeTenant)).isNull();
    }

    @Test
    @DisplayName("A tagline over 80 characters is refused 400")
    void longTaglineIsRefused() throws Exception {
        mvc.perform(as(acmeTenant, acmeAdminSub, put("/api/v1/tenants/current/profile"))
                        .content(json.writeValueAsString(Map.of("tagline", "x".repeat(81)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("tagline must be at most 80 characters"));

        assertThat(tagline(acmeTenant)).isNull();
    }

    private static String tagline(UUID tenantId) throws SQLException {
        Object value = tenantColumn(tenantId, "tagline");
        return value == null ? null : value.toString();
    }

    private static UUID logoDocumentId(UUID tenantId) throws SQLException {
        return (UUID) tenantColumn(tenantId, "logo_document_id");
    }

    /** Read as the schema owner, so row-level security does not hide the row. */
    private static Object tenantColumn(UUID tenantId, String column) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT " + column + " FROM core.tenant WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1) : null;
            }
        }
    }

    private static MockHttpServletRequestBuilder as(UUID tenantId, UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenantId.toString())));
    }
}
