package com.infinevo.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * W-10 — the one filter chain: no token means 401, the health probe is let through, and an
 * application with no issuer configured does not start at all (spec section 7).
 *
 * <p>A Spring context rather than a bare object: a filter chain that is never assembled into a
 * servlet chain proves nothing about what a request meets. It is a small one — no database, no JPA,
 * no container — so it stays a Surefire test.
 */
@SpringBootTest(classes = ResourceServerConfigTest.TestApp.class)
@AutoConfigureMockMvc
class ResourceServerConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("A request with no token is refused with 401")
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A request with a token the issuer did not mint is refused with 401")
    void requestWithAnUnverifiableTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-token-this-issuer-signed"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("The health probe carries no token and is let through")
    void healthIsPermitted() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("No JwtDecoder means no startup — the chain refuses to be built, rather than permitting everyone")
    @SuppressWarnings("unchecked")
    void noJwtDecoderFailsFast() {
        ObjectProvider<JwtDecoder> noDecoder = mock(ObjectProvider.class);
        when(noDecoder.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> new ResourceServerConfig().apiSecurityFilterChain(null, noDecoder))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer-uri")
                .hasMessageContaining("KEYCLOAK_ISSUER_URI");
    }

    @Test
    @DisplayName("The permitted list is one path and its sub-paths, and grows only on purpose")
    void onlyHealthIsPublic() {
        assertThat(ResourceServerConfig.HEALTH_PATH).isEqualTo("/actuator/health");
        assertThat(ResourceServerConfig.HEALTH_SUBPATHS).isEqualTo("/actuator/health/**");
    }

    @Test
    @DisplayName("A management path that is not health still needs a token")
    void otherManagementPathsAreNotPublic() throws Exception {
        // The constants above only assert themselves — they would stay green if a third
        // permitAll were added on the next line. This makes the request instead, so the
        // boundary is proved by what the chain does rather than by what it is named.
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("The D-22 exception list is exactly the document download, and grows only on purpose")
    void publicApplicationEndpointsAreTheReviewedList() {
        assertThat(PublicEndpoints.PATHS).containsExactly("/api/v1/documents/download");
    }

    @Test
    @DisplayName("The document download path is reachable with no token - its signed link is the authorisation")
    void documentDownloadIsPermitted() throws Exception {
        mockMvc.perform(get(PublicEndpoints.DOCUMENT_DOWNLOAD)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Only that exact path: a sub-path and a sibling still need a token - no prefix grant")
    void documentDownloadIsNotAPrefix() throws Exception {
        mockMvc.perform(get(PublicEndpoints.DOCUMENT_DOWNLOAD + "/extra")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/documents/metadata")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A health sub-path is permitted, because that is what the orchestrator actually probes")
    void healthSubPathsArePermitted() throws Exception {
        // probes.enabled turns these on, and they are what the liveness and readiness probes
        // call — not the bare parent. A replica that is alive being restarted for failing its
        // probe is the failure this prevents.
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    /**
     * The smallest application that carries the chain: {@link ResourceServerConfig}, a decoder that
     * accepts nothing, and a stub health endpoint so "permitted" can be told apart from "no handler".
     */
    @SpringBootApplication(
            scanBasePackages = "com.infinevo.shared.security",
            exclude = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
            })
    static class TestApp {

        /** Stands in for Keycloak's JWKS. Rejecting every token is enough to prove the chain validates. */
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new BadJwtException("test decoder: this issuer signed nothing");
            };
        }

        @RestController
        static class HealthStub {

            @GetMapping("/actuator/health")
            String health() {
                return "{\"status\":\"UP\"}";
            }

            /**
             * The liveness and readiness probes Boot exposes when {@code probes.enabled} is on.
             * Stubbed here so a permitted sub-path can be told apart from one with no handler —
             * both would otherwise be a 404 and the test would prove nothing.
             */
            @GetMapping("/actuator/health/{probe}")
            String probe(@PathVariable String probe) {
                return "{\"status\":\"UP\",\"probe\":\"" + probe + "\"}";
            }

            /** Stands in for a management endpoint that must NOT be public. */
            @GetMapping("/actuator/prometheus")
            String prometheus() {
                return "# metrics";
            }

            /** Same, and a second one so the assertion is not about one lucky path. */
            @GetMapping("/actuator/env")
            String env() {
                return "{}";
            }

            /** Stands in for the document download, so "permitted" is told apart from "no handler". */
            @GetMapping(PublicEndpoints.DOCUMENT_DOWNLOAD)
            String download() {
                return "bytes";
            }

            /** Beneath and beside the download, and both must stay closed. */
            @GetMapping({PublicEndpoints.DOCUMENT_DOWNLOAD + "/extra", "/api/v1/documents/metadata"})
            String notPublic() {
                return "{}";
            }
        }
    }
}
