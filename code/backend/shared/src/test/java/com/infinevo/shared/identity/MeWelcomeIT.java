package com.infinevo.shared.identity;

import static com.infinevo.shared.identity.IdentityTestSchema.TENANT_ACME;
import static com.infinevo.shared.identity.IdentityTestSchema.TENANT_GLOBEX;
import static com.infinevo.shared.identity.IdentityTestSchema.USER_ADMIN_ACME;
import static com.infinevo.shared.identity.IdentityTestSchema.USER_ADMIN_GLOBEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-73.8 — {@code PUT /api/v1/me/welcome-seen} stamps {@code welcome_seen_at} once, and {@code GET /api/v1/me}
 * reports it as {@code welcomeSeen} (spec section 7).
 *
 * <p>Runs the shipped filter chain against the shipped migrations ({@code V009}, {@code V165}) on a real
 * PostgreSQL with row-level security, so the native update is proved under the tenant binding it runs with.
 */
@SpringBootTest(classes = IdentityTestApp.class)
@AutoConfigureMockMvc
class MeWelcomeIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    static void applySchema() throws Exception {
        IdentityTestSchema.apply();
    }

    @BeforeEach
    void seed() throws Exception {
        IdentityTestSchema.seedTenants();
        IdentityTestSchema.clearUserAccounts();
        IdentityTestSchema.seedMembership(USER_ADMIN_ACME, TENANT_ACME);
        IdentityTestSchema.seedMembership(USER_ADMIN_GLOBEX, TENANT_GLOBEX);
    }

    private static MockHttpServletRequestBuilder as(UUID sub, UUID tenant, MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(token -> token.subject(sub.toString())
                .claim("tenant_id", tenant.toString())
                .claim("email", "user@" + tenant + ".local")));
    }

    @Test
    @DisplayName("A new account has not seen the welcome page")
    void newAccountHasNotSeenIt() throws Exception {
        mockMvc.perform(as(USER_ADMIN_ACME, TENANT_ACME, get("/api/v1/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.welcomeSeen").value(false));

        assertThat(IdentityTestSchema.readColumn(TENANT_ACME, USER_ADMIN_ACME, "welcome_seen_at"))
                .isNull();
    }

    @Test
    @DisplayName("PUT stamps the time once; a second PUT is 204 and leaves it where it was")
    void putSetsTheTimestampOnceAndIsIdempotent() throws Exception {
        mockMvc.perform(as(USER_ADMIN_ACME, TENANT_ACME, put("/api/v1/me/welcome-seen")))
                .andExpect(status().isNoContent());
        Timestamp first = (Timestamp) IdentityTestSchema.readColumn(TENANT_ACME, USER_ADMIN_ACME, "welcome_seen_at");
        assertThat(first).isNotNull();
        assertThat(IdentityTestSchema.readColumn(TENANT_ACME, USER_ADMIN_ACME, "updated_by"))
                .isEqualTo(USER_ADMIN_ACME.toString());

        mockMvc.perform(as(USER_ADMIN_ACME, TENANT_ACME, put("/api/v1/me/welcome-seen")))
                .andExpect(status().isNoContent());

        assertThat(IdentityTestSchema.readColumn(TENANT_ACME, USER_ADMIN_ACME, "welcome_seen_at"))
                .isEqualTo(first);
        mockMvc.perform(as(USER_ADMIN_ACME, TENANT_ACME, get("/api/v1/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.welcomeSeen").value(true));
    }

    @Test
    @DisplayName("Dismissing in one tenant leaves every other account's page in place")
    void onlyTheCallersRowIsWritten() throws Exception {
        mockMvc.perform(as(USER_ADMIN_GLOBEX, TENANT_GLOBEX, get("/api/v1/me"))).andExpect(status().isOk());

        mockMvc.perform(as(USER_ADMIN_ACME, TENANT_ACME, put("/api/v1/me/welcome-seen")))
                .andExpect(status().isNoContent());

        assertThat(IdentityTestSchema.readColumn(TENANT_GLOBEX, USER_ADMIN_GLOBEX, "welcome_seen_at"))
                .isNull();
        mockMvc.perform(as(USER_ADMIN_GLOBEX, TENANT_GLOBEX, get("/api/v1/me")))
                .andExpect(jsonPath("$.welcomeSeen").value(false));
    }

    @Test
    @DisplayName("No token, no write: PUT /api/v1/me/welcome-seen is 401")
    void withoutATokenItIs401() throws Exception {
        mockMvc.perform(put("/api/v1/me/welcome-seen")).andExpect(status().isUnauthorized());
    }
}
