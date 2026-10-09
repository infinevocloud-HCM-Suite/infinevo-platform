package com.infinevo.core.invitation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link KeycloakProvisioningService} calling the Keycloak Admin REST API.
 *
 * <p><strong>Two URLs, as for the resource server.</strong> The realm name comes from the issuer
 * ({@code .../realms/{realm}}), but the issuer's host is the one stamped into tokens — pinned to what the
 * browser reaches — and need not be reachable from this process. {@code keycloak.admin.url}
 * ({@code KEYCLOAK_ADMIN_URL}) is where admin calls go; unset, the issuer's scheme and authority are used.
 *
 * <p><strong>A created user can log in.</strong> It is created with the {@code UPDATE_PASSWORD} required
 * action, and Keycloak is asked to email the set-password link. That email failing is logged and does not
 * fail the acceptance: the account exists and an administrator can resend the action from Keycloak.
 *
 * <p>The invitee's email address is never written to a log line or an exception message.
 */
@Service
public class KeycloakProvisioningServiceImpl implements KeycloakProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakProvisioningServiceImpl.class);

    static final String UPDATE_PASSWORD = "UPDATE_PASSWORD";

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String issuerUri;
    private final String adminUrl;
    private final String adminUsername;
    private final String adminPassword;

    public KeycloakProvisioningServiceImpl(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
            @Value("${keycloak.admin.url:}") String adminUrl,
            @Value("${keycloak.admin.username:admin}") String adminUsername,
            @Value("${keycloak.admin.password:}") String adminPassword) {
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.issuerUri = issuerUri == null ? "" : issuerUri.trim();
        this.adminUrl = adminUrl == null ? "" : adminUrl.trim();
        this.adminUsername = adminUsername == null || adminUsername.isBlank() ? "admin" : adminUsername.trim();
        this.adminPassword = adminPassword == null ? "" : adminPassword;
    }

    @Override
    public ProvisioningResult getOrCreateKeycloakUser(String email, String firstName, String lastName) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        String cleanEmail = email.toLowerCase().trim();
        Target target = target();
        try {
            String adminToken = fetchAdminToken(target);
            return provision(target, adminToken, cleanEmail, firstName, lastName);
        } catch (IOException e) {
            throw new KeycloakProvisioningException(
                    "Keycloak could not be reached: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeycloakProvisioningException("Interrupted while calling Keycloak", e);
        }
    }

    @Override
    public void deleteKeycloakUser(UUID keycloakUserId) {
        if (keycloakUserId == null) {
            return;
        }
        Target target = target();
        try {
            String adminToken = fetchAdminToken(target);
            HttpRequest deleteReq = HttpRequest.newBuilder()
                    .uri(URI.create(target.usersUrl() + "/" + keycloakUserId))
                    .header("Authorization", "Bearer " + adminToken)
                    .timeout(TIMEOUT)
                    .DELETE()
                    .build();
            HttpResponse<String> resp = httpClient.send(deleteReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2 && resp.statusCode() != 404) {
                throw new KeycloakProvisioningException(
                        "Keycloak refused to delete user " + keycloakUserId + ": HTTP " + resp.statusCode());
            }
            log.info("Compensating cleanup: deleted Keycloak user {} (HTTP {})", keycloakUserId, resp.statusCode());
        } catch (IOException e) {
            throw new KeycloakProvisioningException(
                    "Keycloak could not be reached: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeycloakProvisioningException("Interrupted while calling Keycloak", e);
        }
    }

    @Override
    public void setEnabled(UUID keycloakUserId, boolean enabled) {
        Objects.requireNonNull(keycloakUserId, "keycloakUserId must not be null");
        Target target = target();
        try {
            String adminToken = fetchAdminToken(target);
            HttpRequest putReq = HttpRequest.newBuilder()
                    .uri(URI.create(target.usersUrl() + "/" + keycloakUserId))
                    .header("Authorization", "Bearer " + adminToken)
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .PUT(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(Map.of("enabled", enabled))))
                    .build();
            HttpResponse<String> resp = httpClient.send(putReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) {
                log.warn("Keycloak holds no user {}; enabled={} not applied there", keycloakUserId, enabled);
                return;
            }
            if (resp.statusCode() / 100 != 2) {
                throw new KeycloakProvisioningException("Keycloak refused to set enabled=" + enabled + " on user "
                        + keycloakUserId + ": HTTP " + resp.statusCode());
            }
            log.info("Keycloak user {} enabled={}", keycloakUserId, enabled);
        } catch (IOException e) {
            throw new KeycloakProvisioningException(
                    "Keycloak could not be reached: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeycloakProvisioningException("Interrupted while calling Keycloak", e);
        }
    }

    private ProvisioningResult provision(
            Target target, String adminToken, String email, String firstName, String lastName)
            throws IOException, InterruptedException {
        // 1. Reuse an existing user (§13 decision 2)
        HttpRequest searchReq = HttpRequest.newBuilder()
                .uri(URI.create(target.usersUrl() + "?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                        + "&exact=true"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Accept", "application/json")
                .timeout(TIMEOUT)
                .GET()
                .build();
        UUID existing = firstUserId(httpClient.send(searchReq, HttpResponse.BodyHandlers.ofString()));
        if (existing != null) {
            return new ProvisioningResult(existing, false);
        }

        // 2. Create a user that must choose a password before it can sign in
        Map<String, Object> payload = Map.of(
                "username",
                email,
                "email",
                email,
                "firstName",
                firstName != null ? firstName : "",
                "lastName",
                lastName != null ? lastName : "",
                "enabled",
                true,
                "emailVerified",
                true,
                "requiredActions",
                List.of(UPDATE_PASSWORD));
        HttpRequest createReq = HttpRequest.newBuilder()
                .uri(URI.create(target.usersUrl()))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> createResp = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());
        if (createResp.statusCode() == 201) {
            String location = createResp.headers().firstValue("Location").orElse(null);
            if (location != null) {
                UUID created = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
                sendSetPasswordEmail(target, adminToken, created);
                return new ProvisioningResult(created, true);
            }
        }

        // 3. 409: created concurrently by another acceptance — reuse it
        if (createResp.statusCode() == 409) {
            UUID raced = firstUserId(httpClient.send(searchReq, HttpResponse.BodyHandlers.ofString()));
            if (raced != null) {
                return new ProvisioningResult(raced, false);
            }
        }

        // The body is not quoted: Keycloak echoes the submitted user, email included.
        throw new KeycloakProvisioningException("Keycloak refused to create the user: HTTP " + createResp.statusCode());
    }

    /**
     * Asks Keycloak to email the set-password link. Never throws: the user exists either way, and the
     * action can be resent from the Keycloak console.
     */
    private void sendSetPasswordEmail(Target target, String adminToken, UUID userId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(target.usersUrl() + "/" + userId + "/execute-actions-email"))
                    .header("Authorization", "Bearer " + adminToken)
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(List.of(UPDATE_PASSWORD))))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn(
                        "Keycloak did not send the set-password email for user {}: HTTP {}", userId, resp.statusCode());
            }
        } catch (IOException e) {
            log.warn(
                    "Keycloak did not send the set-password email for user {}: {}",
                    userId,
                    e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted asking Keycloak to send the set-password email for user {}", userId);
        }
    }

    private UUID firstUserId(HttpResponse<String> response) throws IOException {
        if (response.statusCode() != 200) {
            return null;
        }
        JsonNode array = objectMapper.readTree(response.body());
        if (array.isArray() && !array.isEmpty() && array.get(0).hasNonNull("id")) {
            return UUID.fromString(array.get(0).get("id").asText());
        }
        return null;
    }

    private String fetchAdminToken(Target target) throws IOException, InterruptedException {
        String form = "client_id=admin-cli&grant_type=password&username="
                + URLEncoder.encode(adminUsername, StandardCharsets.UTF_8)
                + "&password="
                + URLEncoder.encode(adminPassword, StandardCharsets.UTF_8);

        HttpRequest tokenReq = HttpRequest.newBuilder()
                .uri(URI.create(target.baseUrl() + "/realms/master/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> tokenResp = httpClient.send(tokenReq, HttpResponse.BodyHandlers.ofString());
        if (tokenResp.statusCode() != 200) {
            throw new KeycloakProvisioningException(
                    "Failed to obtain Keycloak admin token: HTTP " + tokenResp.statusCode());
        }
        JsonNode token = objectMapper.readTree(tokenResp.body()).get("access_token");
        if (token == null || token.asText().isBlank()) {
            throw new KeycloakProvisioningException("Keycloak admin token response carried no access_token");
        }
        return token.asText();
    }

    /** Where admin calls go and which realm they address. Refuses when either cannot be known. */
    Target target() {
        if (issuerUri.isEmpty()) {
            throw new KeycloakProvisioningException(
                    "Keycloak is not configured: spring.security.oauth2.resourceserver.jwt.issuer-uri is not set");
        }
        if (adminPassword.isBlank()) {
            throw new KeycloakProvisioningException(
                    "Keycloak admin password is not configured: set KEYCLOAK_ADMIN_PASSWORD");
        }
        URI issuer = URI.create(issuerUri);
        String path = issuer.getPath() == null ? "" : issuer.getPath();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String realm = path.substring(path.lastIndexOf('/') + 1);
        if (realm.isBlank()) {
            throw new KeycloakProvisioningException("Cannot read the realm from the issuer URI");
        }
        String base = adminUrl.isEmpty() ? issuer.getScheme() + "://" + issuer.getAuthority() : adminUrl;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return new Target(base, realm);
    }

    record Target(String baseUrl, String realm) {
        String usersUrl() {
            return baseUrl + "/admin/realms/" + realm + "/users";
        }
    }
}
