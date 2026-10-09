package com.infinevo.core.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.document.DocumentLinkService;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.core.tenant.TenantProfileService;
import com.infinevo.shared.impersonation.ImpersonationController;
import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-73.1 spec section 7 — the navigation feed carries the bound tenant's name, tagline and logo link, read
 * under that tenant's row-level security: two callers in two tenants get their own, and never each other's.
 *
 * <p>Acting as a customer user (W-65.2), the feed is that tenant's branding, not the platform's: the session
 * is opened through {@link ImpersonationController} by real platform staff, as {@code ImpersonationIT} does.
 *
 * <p>{@link PermissionGuardTestApp} holds no document store, so the link service here is a stub that signs
 * nothing: what is proven is that the feed asks for a link to the tenant's own logo document, for the day the
 * spec names, and carries back what it was given. The real signature is {@code DocumentLinkServiceTest}'s.
 */
@SpringBootTest(classes = {PermissionGuardTestApp.class, NavigationBrandingIT.StubLinks.class})
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class NavigationBrandingIT extends AbstractIntegrationTest {

    @TestConfiguration
    @Import(ImpersonationController.class)
    static class StubLinks {
        @Bean
        DocumentLinkService documentLinkService() {
            return new DocumentLinkService() {
                @Override
                public SignedLink signedLink(UUID documentId) {
                    return signedLink(documentId, INTERACTIVE_TTL);
                }

                @Override
                public SignedLink signedLink(UUID documentId, Duration ttl) {
                    assertThat(ttl).as("the header's link lives a day").isEqualTo(TenantProfileService.LOGO_LINK_TTL);
                    return new SignedLink(
                            "/api/v1/documents/download?t=stub." + documentId,
                            Instant.now().plus(ttl));
                }

                @Override
                public Optional<LinkClaims> verify(String token) {
                    return Optional.empty();
                }
            };
        }
    }

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private UUID acmeTenant;
    private UUID acmeSub;
    private UUID acmeLogo;
    private UUID globexTenant;
    private UUID globexSub;

    @BeforeEach
    void setUp() throws SQLException {
        acmeTenant = AuthzTestSchema.insertTenant("Acme Branding " + UUID.randomUUID());
        acmeSub = UUID.randomUUID();
        UUID acmeAccount = AuthzTestSchema.insertMember(acmeTenant, acmeSub, "employee@acme.branding.test");
        AuthzTestSchema.grant(acmeTenant, acmeAccount, AuthzTestSchema.roleId(acmeTenant, "employee"));
        acmeLogo = AuthzTestSchema.insertDocumentRow(acmeTenant, "TENANT_LOGO", "image/png", 40_000);
        brand(acmeTenant, "People first", acmeLogo);

        globexTenant = AuthzTestSchema.insertTenant("Globex Branding " + UUID.randomUUID());
        globexSub = UUID.randomUUID();
        UUID globexAccount = AuthzTestSchema.insertMember(globexTenant, globexSub, "employee@globex.branding.test");
        AuthzTestSchema.grant(globexTenant, globexAccount, AuthzTestSchema.roleId(globexTenant, "employee"));
    }

    @Test
    @DisplayName("The feed carries the bound tenant's name, tagline and a link to its logo")
    void feedCarriesBranding() throws Exception {
        NavigationResponse acme = feedFor(acmeTenant, acmeSub);

        assertThat(acme.tenantName()).startsWith("Acme Branding ");
        assertThat(acme.tagline()).isEqualTo("People first");
        assertThat(acme.tenantLogoUrl()).isEqualTo("/api/v1/documents/download?t=stub." + acmeLogo);
    }

    @Test
    @DisplayName("A tenant with no logo and no tagline gets nulls, never another tenant's")
    void unbrandedTenantGetsNulls() throws Exception {
        NavigationResponse globex = feedFor(globexTenant, globexSub);

        assertThat(globex.tenantName()).startsWith("Globex Branding ");
        assertThat(globex.tagline()).isNull();
        assertThat(globex.tenantLogoUrl()).isNull();
    }

    @Test
    @DisplayName("Acting as a customer user, the feed carries that tenant's branding, not the platform's")
    void actingAsReturnsTheTargetTenantsBranding() throws Exception {
        UUID platform = PlatformTenant.DEFAULT_PLATFORM_TENANT_ID;
        UUID staffSub = UUID.randomUUID();
        String staffEmail = "staff-" + UUID.randomUUID() + "@infinevo.local";
        UUID staffAccount = AuthzTestSchema.insertMember(platform, staffSub, staffEmail);
        AuthzTestSchema.grant(platform, staffAccount, AuthzTestSchema.roleId(platform, "platform-admin"));

        NavigationResponse own = feedFor(platform, staffSub);
        assertThat(own.tenantName())
                .as("the staff member's own feed is the platform's")
                .isEqualTo("Infinevo");

        String opened = mvc.perform(
                        asStaff(staffSub, staffEmail, post("/api/v1/tenants/{id}/impersonations", acmeTenant))
                                .content(json.writeValueAsString(
                                        Map.of("email", "employee@acme.branding.test", "reason", "W-73.1 branding"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String session = json.readTree(opened).path("sessionId").asText();

        MvcResult result = mvc.perform(
                        asStaff(staffSub, staffEmail, get("/api/v1/navigation")).header("X-Impersonation", session))
                .andExpect(status().isOk())
                .andReturn();
        NavigationResponse acting = json.readValue(result.getResponse().getContentAsString(), NavigationResponse.class);

        assertThat(acting.tenantName()).startsWith("Acme Branding ");
        assertThat(acting.tagline()).isEqualTo("People first");
        assertThat(acting.tenantLogoUrl()).isEqualTo("/api/v1/documents/download?t=stub." + acmeLogo);
    }

    private static MockHttpServletRequestBuilder asStaff(
            UUID sub, String email, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON).with(jwt().jwt(token -> token.subject(sub.toString())
                .claim("tenant_id", PlatformTenant.DEFAULT_PLATFORM_TENANT_ID.toString())
                .claim("email", email)));
    }

    private NavigationResponse feedFor(UUID tenantId, UUID sub) throws Exception {
        MvcResult result = mvc.perform(as(tenantId, sub, get("/api/v1/navigation")))
                .andExpect(status().isOk())
                .andReturn();
        return json.readValue(result.getResponse().getContentAsString(), NavigationResponse.class);
    }

    /** Written as the schema owner: the profile endpoint's own write is {@code TenantProfileIT}'s. */
    private static void brand(UUID tenantId, String tagline, UUID logo) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE core.tenant SET tagline = ?, logo_document_id = ? WHERE tenant_id = ?")) {
            ps.setString(1, tagline);
            ps.setObject(2, logo);
            ps.setObject(3, tenantId);
            ps.executeUpdate();
        }
    }

    private static MockHttpServletRequestBuilder as(UUID tenantId, UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenantId.toString())));
    }
}
