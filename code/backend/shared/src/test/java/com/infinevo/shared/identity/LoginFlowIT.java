package com.infinevo.shared.identity;

import static com.infinevo.shared.identity.IdentityTestSchema.TENANT_GLOBEX;
import static com.infinevo.shared.identity.IdentityTestSchema.USER_ADMIN_GLOBEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * W-10 — a token minted by the dev realm reaches {@code /api/v1/me} with the right tenant bound
 * (spec section 7).
 *
 * <p>This is the end-to-end proof the ticket exists for, and it uses the real things: the realm
 * export the local stack imports ({@code infra/docker/keycloak/dev-realm.json}), a real Keycloak
 * container, the shipped migrations, a real PostgreSQL with row-level security, and the shipped
 * filter chain. Nothing is stubbed but the browser — the token is fetched with a direct access
 * grant rather than by following redirects, which is what spec section 9 asks for: assert on a
 * minted token, not on a login page.
 *
 * <p>One container for the whole class, started once and reused, for the same reason.
 *
 * <p>It inherits {@code @EnabledIfDockerAvailable} from {@link AbstractIntegrationTest}, so on a
 * machine without Docker it is reported as skipped rather than passing quietly — #117.
 */
@SpringBootTest(classes = IdentityTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {PostgresTestContainerInitializer.class, LoginFlowIT.KeycloakContainerInitializer.class})
class LoginFlowIT extends AbstractIntegrationTest {

    private static final String REALM = "infinevo";
    private static final String CLIENT_ID = "infinevo-web";
    private static final String USERNAME = "admin.globex";
    private static final String PASSWORD = "local_dev_pw";

    @Autowired
    private MockMvc mockMvc;

    private static String accessToken;

    @BeforeAll
    static void prepare() throws Exception {
        IdentityTestSchema.apply();
        IdentityTestSchema.seedTenants();
        IdentityTestSchema.clearUserAccounts();
        // The membership row W-24 will one day create by invitation. Without it the request is
        // answered 401 TENANT_NOT_BOUND - spec section 13, decision 2.
        IdentityTestSchema.seedMembership(USER_ADMIN_GLOBEX, TENANT_GLOBEX);
        accessToken = mintToken();
    }

    @Test
    @DisplayName("The realm puts the tenant UUID in the token, as a top-level tenant_id claim")
    void tokenCarriesTheTenantClaim() throws Exception {
        JsonNode claims = decodePayload(accessToken);

        assertThat(claims.path("sub").asText()).isEqualTo(USER_ADMIN_GLOBEX.toString());
        assertThat(claims.path("tenant_id").asText()).isEqualTo(TENANT_GLOBEX.toString());
        assertThat(claims.path("email").asText()).isEqualTo("admin@globex-full.local");
    }

    @Test
    @DisplayName("The minted token reaches /api/v1/me, and the answer names the user and the tenant")
    void tokenReachesTheProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ADMIN_GLOBEX.toString()))
                .andExpect(jsonPath("$.email").value("admin@globex-full.local"))
                .andExpect(jsonPath("$.firstName").value("Gita"))
                .andExpect(jsonPath("$.lastName").value("Globex"))
                .andExpect(jsonPath("$.tenantId").value(TENANT_GLOBEX.toString()));
    }

    @Test
    @DisplayName("The first request creates the profile row; the second writes nothing")
    void profileIsSyncedOnceAndNotRewritten() throws Exception {
        IdentityTestSchema.clearUserAccounts();

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        assertThat(IdentityTestSchema.countUserAccounts(TENANT_GLOBEX)).isEqualTo(1);
        Timestamp afterFirst =
                (Timestamp) IdentityTestSchema.readColumn(TENANT_GLOBEX, USER_ADMIN_GLOBEX, "updated_at");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        assertThat(IdentityTestSchema.countUserAccounts(TENANT_GLOBEX)).isEqualTo(1);
        // Unchanged claims write nothing at all, so updated_at does not move - spec section 7.
        assertThat(IdentityTestSchema.readColumn(TENANT_GLOBEX, USER_ADMIN_GLOBEX, "updated_at"))
                .isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("No token, no answer: /api/v1/me is 401")
    void withoutATokenTheEndpointIs401() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/api/v1/auth/login is 404 even with a valid token — the path is gone, not exempted")
    void localLoginPathNoLongerExists() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    /** A direct access grant against the running realm. The password flow is what the spec verifies with. */
    private static String mintToken() throws Exception {
        String form = "client_id=" + CLIENT_ID
                + "&username=" + URLEncoder.encode(USERNAME, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8)
                + "&grant_type=password";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(KeycloakContainerInitializer.issuerUri() + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Keycloak refused the direct access grant: " + response.statusCode() + " " + response.body());
        }
        return new ObjectMapper().readTree(response.body()).path("access_token").asText();
    }

    /** The token's claims, read the way the verification block in spec section 8 reads them. */
    private static JsonNode decodePayload(String token) throws Exception {
        String payload = token.split("\\.")[1];
        byte[] decoded = java.util.Base64.getUrlDecoder().decode(payload);
        return new ObjectMapper().readTree(new String(decoded, StandardCharsets.UTF_8));
    }

    /**
     * Starts Keycloak with the local realm export and points the resource server at it.
     *
     * <p>The issuer has to be settled before the Spring context refreshes, which is why this is an
     * initializer and not a {@code @DynamicPropertySource}: {@code ResourceServerConfig} refuses to
     * build a chain with no decoder, and the decoder is built from this property.
     *
     * <p>{@code start-dev} keeps its own in-memory database — the realm is imported at boot and
     * thrown away with the container, so the test cannot be affected by, or affect, anything else.
     */
    static class KeycloakContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        /** The same image the local stack runs — {@code infra/docker/compose.yml:102}. */
        private static final String IMAGE = "quay.io/keycloak/keycloak:25.0";

        private static final int HTTP_PORT = 8080;

        @SuppressWarnings("resource") // container lifecycle is managed by the JVM shutdown hook
        private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(IMAGE)
                .withEnv("KEYCLOAK_ADMIN", "admin")
                .withEnv("KEYCLOAK_ADMIN_PASSWORD", "local_keycloak_pw")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("keycloak/dev-realm.json"),
                        "/opt/keycloak/data/import/dev-realm.json")
                .withCommand("start-dev", "--import-realm")
                .withExposedPorts(HTTP_PORT)
                .waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                        .forPort(HTTP_PORT)
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(5)));

        private static volatile boolean started = false;

        static synchronized void startIfNeeded() {
            if (!started) {
                KEYCLOAK.start();
                started = true;
            }
        }

        /**
         * {@code http://localhost:<mapped port>/realms/infinevo}. Dev-mode Keycloak derives the
         * issuer from the request it answered, so asking on this address yields tokens whose
         * {@code iss} is this address — which is what the decoder will check.
         */
        static String issuerUri() {
            startIfNeeded();
            return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(HTTP_PORT) + "/realms/" + REALM;
        }

        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            // Both, and deliberately. The placeholder JWK set URI that lets every other
            // integration test start (PostgresTestContainerInitializer) has to be replaced, not
            // merely accompanied - Spring Boot builds the decoder from jwk-set-uri when it is
            // present. With issuer-uri set alongside it, Boot adds the issuer validator too, so
            // the token is checked for signature, expiry and issuer alike.
            TestPropertyValues.of(
                            "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuerUri(),
                            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + issuerUri()
                                    + "/protocol/openid-connect/certs")
                    .applyTo(ctx.getEnvironment());
        }
    }
}
