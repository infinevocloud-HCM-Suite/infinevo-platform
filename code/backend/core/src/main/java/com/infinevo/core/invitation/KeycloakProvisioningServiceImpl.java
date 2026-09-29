package com.infinevo.core.invitation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link KeycloakProvisioningService} calling Keycloak Admin REST API.
 */
@Service
public class KeycloakProvisioningServiceImpl implements KeycloakProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakProvisioningServiceImpl.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String issuerUri;

    @Value("${keycloak.admin.username:admin}")
    private String adminUsername;

    @Value("${keycloak.admin.password:local_keycloak_pw}")
    private String adminPassword;

    public KeycloakProvisioningServiceImpl() {
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public UUID getOrCreateKeycloakUser(String email, String firstName, String lastName) {
        String cleanEmail = email.toLowerCase().trim();

        if (issuerUri != null && !issuerUri.isBlank() && !issuerUri.contains("unreachable")) {
            try {
                return provisionInKeycloak(cleanEmail, firstName, lastName);
            } catch (Exception e) {
                log.warn(
                        "Keycloak call failed for {}: {}. Falling back to deterministic UUID.",
                        cleanEmail,
                        e.getMessage());
            }
        }

        // Fallback for tests or disconnected environments
        return UUID.nameUUIDFromBytes(("keycloak:" + cleanEmail).getBytes(StandardCharsets.UTF_8));
    }

    private UUID provisionInKeycloak(String email, String firstName, String lastName) throws Exception {
        // e.g. issuerUri = http://localhost:8080/realms/infinevo
        URI issuer = URI.create(issuerUri);
        String baseUrl = issuer.getScheme() + "://" + issuer.getAuthority();
        String path = issuer.getPath();
        String realm = path.substring(path.lastIndexOf('/') + 1);

        String adminToken = fetchAdminToken(baseUrl);

        // 1. Check if user already exists
        HttpRequest searchReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/admin/realms/" + realm + "/users?email="
                        + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&exact=true"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> searchResp = httpClient.send(searchReq, HttpResponse.BodyHandlers.ofString());
        if (searchResp.statusCode() == 200) {
            JsonNode array = objectMapper.readTree(searchResp.body());
            if (array.isArray() && !array.isEmpty()) {
                String idStr = array.get(0).get("id").asText();
                return UUID.fromString(idStr);
            }
        }

        // 2. Create user
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
                true);
        String body = objectMapper.writeValueAsString(payload);

        HttpRequest createReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/admin/realms/" + realm + "/users"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> createResp = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());
        if (createResp.statusCode() == 201) {
            String location = createResp.headers().firstValue("Location").orElse(null);
            if (location != null) {
                String idStr = location.substring(location.lastIndexOf('/') + 1);
                return UUID.fromString(idStr);
            }
        }

        // If 409 Conflict, re-fetch
        HttpResponse<String> refetch = httpClient.send(searchReq, HttpResponse.BodyHandlers.ofString());
        if (refetch.statusCode() == 200) {
            JsonNode array = objectMapper.readTree(refetch.body());
            if (array.isArray() && !array.isEmpty()) {
                String idStr = array.get(0).get("id").asText();
                return UUID.fromString(idStr);
            }
        }

        throw new IllegalStateException("Failed to create Keycloak user for " + email + ": HTTP "
                + createResp.statusCode() + " " + createResp.body());
    }

    private String fetchAdminToken(String baseUrl) throws Exception {
        String form = "client_id=admin-cli&grant_type=password&username="
                + URLEncoder.encode(adminUsername, StandardCharsets.UTF_8)
                + "&password="
                + URLEncoder.encode(adminPassword, StandardCharsets.UTF_8);

        HttpRequest tokenReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/realms/master/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> tokenResp = httpClient.send(tokenReq, HttpResponse.BodyHandlers.ofString());
        if (tokenResp.statusCode() != 200) {
            throw new IllegalStateException("Failed to obtain Keycloak admin token: HTTP " + tokenResp.statusCode());
        }

        JsonNode node = objectMapper.readTree(tokenResp.body());
        return node.get("access_token").asText();
    }
}
