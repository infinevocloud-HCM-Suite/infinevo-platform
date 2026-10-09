package com.infinevo.core.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * {@link KeycloakProvisioningServiceImpl} against a stub Keycloak admin API on a local port (W-24.2, B-2/B-3;
 * D-88 for the password).
 *
 * <p>The issuer points at a port nothing listens on, so any call that went to the issuer's host instead of
 * {@code keycloak.admin.url} would fail the test.
 */
class KeycloakProvisioningServiceTest {

    private static final String UNREACHABLE_ISSUER = "http://127.0.0.1:1/realms/infinevo";
    private static final String EMAIL = "new.person@example.com";
    private static final String PASSWORD = "Str0ng-Passw0rd!";
    private static final String USERS = "/admin/realms/infinevo/users";
    private static final String TOKEN = "/realms/master/protocol/openid-connect/token";

    record Call(String method, String path, String query, String body) {}

    private HttpServer server;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final UUID createdId = UUID.randomUUID();
    private volatile String searchBody = "[]";
    private volatile int createStatus = 201;
    private volatile int resetStatus = 204;
    private volatile String resetBody = "";

    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();

        logger = (Logger) LoggerFactory.getLogger(KeycloakProvisioningServiceImpl.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void stop() {
        server.stop(0);
        logger.detachAppender(logs);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        calls.add(new Call(method, path, exchange.getRequestURI().getRawQuery(), body));

        if (path.equals(TOKEN)) {
            respond(exchange, 200, "{\"access_token\":\"admin-token\"}");
        } else if (path.equals(USERS) && method.equals("GET")) {
            respond(exchange, 200, searchBody);
        } else if (path.equals(USERS) && method.equals("POST")) {
            if (createStatus == 201) {
                exchange.getResponseHeaders().add("Location", adminUrl() + USERS + "/" + createdId);
            }
            respond(exchange, createStatus, createStatus == 201 ? "" : "{\"error\":\"bad " + EMAIL + "\"}");
        } else if (path.startsWith(USERS + "/") && path.endsWith("/reset-password") && method.equals("PUT")) {
            respond(exchange, resetStatus, resetBody);
        } else if (path.startsWith(USERS + "/") && (method.equals("PUT") || method.equals("DELETE"))) {
            respond(exchange, 204, "");
        } else {
            respond(exchange, 404, "");
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private String adminUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private KeycloakProvisioningServiceImpl service() {
        return new KeycloakProvisioningServiceImpl(UNREACHABLE_ISSUER, adminUrl(), "admin", "admin-pw");
    }

    @Test
    @DisplayName(
            "D-88: search, create with UPDATE_PASSWORD, set the password, clear the action — no mail, all on the admin URL")
    void createsUserAndSetsItsPassword() {
        KeycloakProvisioningService.ProvisioningResult result =
                service().getOrCreateKeycloakUser(EMAIL, "New", "Person", PASSWORD);

        assertThat(result.keycloakUserId()).isEqualTo(createdId);
        assertThat(result.newlyCreated()).isTrue();
        assertThat(result.outcome()).isEqualTo(AcceptOutcome.PASSWORD_SET);
        assertThat(calls)
                .extracting(Call::method, Call::path)
                .containsExactly(
                        Tuple.tuple("POST", TOKEN),
                        Tuple.tuple("GET", USERS),
                        Tuple.tuple("POST", USERS),
                        Tuple.tuple("PUT", USERS + "/" + createdId + "/reset-password"),
                        Tuple.tuple("PUT", USERS + "/" + createdId));
        assertThat(calls.get(1).query()).contains("exact=true");
        assertThat(calls.get(2).body()).contains("\"requiredActions\":[\"UPDATE_PASSWORD\"]");
        assertThat(calls.get(3).body())
                .contains("\"type\":\"password\"")
                .contains("\"value\":\"" + PASSWORD + "\"")
                .contains("\"temporary\":false");
        assertThat(calls.get(4).body()).isEqualTo("{\"requiredActions\":[]}");
        assertThat(calls).extracting(Call::path).noneMatch(p -> p.endsWith("/execute-actions-email"));
    }

    @Test
    @DisplayName(
            "D-88: a password Keycloak refuses is a PasswordPolicyException with the realm's wording, and the created user is deleted")
    void refusedPasswordDeletesTheCreatedUser() {
        resetStatus = 400;
        resetBody = "{\"error\":\"invalidPasswordMinLengthMessage\","
                + "\"error_description\":\"Invalid password: minimum length 10.\"}";

        assertThatThrownBy(() -> service().getOrCreateKeycloakUser(EMAIL, null, null, "short"))
                .isInstanceOf(PasswordPolicyException.class)
                .hasMessage("Invalid password: minimum length 10.");

        assertThat(calls)
                .extracting(Call::method, Call::path)
                .containsExactly(
                        Tuple.tuple("POST", TOKEN),
                        Tuple.tuple("GET", USERS),
                        Tuple.tuple("POST", USERS),
                        Tuple.tuple("PUT", USERS + "/" + createdId + "/reset-password"),
                        Tuple.tuple("DELETE", USERS + "/" + createdId));
        assertThat(logs.list)
                .noneSatisfy(event -> assertThat(event.getFormattedMessage()).contains("short"));
    }

    @Test
    @DisplayName("D-88: a refusal with no readable reason still gets a plain message, never the password")
    void refusalWithoutReasonGetsAPlainMessage() {
        KeycloakProvisioningServiceImpl service = service();
        assertThat(service.policyMessage("")).isEqualTo("Password does not meet the password policy");
        assertThat(service.policyMessage("not json")).isEqualTo("Password does not meet the password policy");
        assertThat(service.policyMessage("{\"errorMessage\":\"Password policy not met\"}"))
                .isEqualTo("Password policy not met");
    }

    @Test
    @DisplayName("an existing user with a password is reused: no create, no reset, not marked as created")
    void existingUserKeepsItsPassword() {
        UUID existing = UUID.randomUUID();
        searchBody = "[{\"id\":\"" + existing + "\"}]";

        KeycloakProvisioningService.ProvisioningResult result =
                service().getOrCreateKeycloakUser(EMAIL, null, null, PASSWORD);

        assertThat(result.keycloakUserId()).isEqualTo(existing);
        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.outcome()).isEqualTo(AcceptOutcome.EXISTING_ACCOUNT);
        assertThat(calls).extracting(Call::method).containsExactly("POST", "GET");
    }

    @Test
    @DisplayName("D-88: an existing user who never set a password is given this one; their other required actions stay")
    void existingUserAwaitingPasswordGetsThisOne() {
        UUID existing = UUID.randomUUID();
        searchBody = "[{\"id\":\"" + existing
                + "\",\"requiredActions\":[\"VERIFY_EMAIL\",\"UPDATE_PASSWORD\",\"CONFIGURE_TOTP\"]}]";

        KeycloakProvisioningService.ProvisioningResult result =
                service().getOrCreateKeycloakUser(EMAIL, null, null, PASSWORD);

        assertThat(result.keycloakUserId()).isEqualTo(existing);
        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.outcome()).isEqualTo(AcceptOutcome.PASSWORD_SET);
        assertThat(calls)
                .extracting(Call::method, Call::path)
                .containsExactly(
                        Tuple.tuple("POST", TOKEN),
                        Tuple.tuple("GET", USERS),
                        Tuple.tuple("PUT", USERS + "/" + existing + "/reset-password"),
                        Tuple.tuple("PUT", USERS + "/" + existing));
        assertThat(calls.get(3).body()).isEqualTo("{\"requiredActions\":[\"VERIFY_EMAIL\",\"CONFIGURE_TOTP\"]}");
    }

    @Test
    @DisplayName("D-88: a create that races another acceptance (409) reuses the user and sets the password itself")
    void raceOnCreateSetsThePassword() {
        UUID raced = UUID.randomUUID();
        createStatus = 409;
        // The first search finds nothing; the search after the 409 finds the raced user, still awaiting a password.
        server.removeContext("/");
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals(USERS) && exchange.getRequestMethod().equals("GET")) {
                long searches =
                        calls.stream().filter(c -> c.method().equals("GET")).count();
                searchBody =
                        searches == 0 ? "[]" : "[{\"id\":\"" + raced + "\",\"requiredActions\":[\"UPDATE_PASSWORD\"]}]";
            }
            handle(exchange);
        });

        KeycloakProvisioningService.ProvisioningResult result =
                service().getOrCreateKeycloakUser(EMAIL, null, null, PASSWORD);

        assertThat(result.keycloakUserId()).isEqualTo(raced);
        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.outcome()).isEqualTo(AcceptOutcome.PASSWORD_SET);
        assertThat(calls).extracting(Call::path).contains(USERS + "/" + raced + "/reset-password");
    }

    @Test
    @DisplayName("a refused create fails without quoting the address Keycloak echoed back")
    void refusedCreateKeepsTheAddressOutOfTheMessage() {
        createStatus = 400;

        assertThatThrownBy(() -> service().getOrCreateKeycloakUser(EMAIL, null, null, PASSWORD))
                .isInstanceOf(KeycloakProvisioningException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageNotContaining(EMAIL);
    }

    @Test
    @DisplayName("a blank password is refused before Keycloak is called")
    void blankPasswordIsRefused() {
        assertThatThrownBy(() -> service().getOrCreateKeycloakUser(EMAIL, null, null, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(calls).isEmpty();
    }

    @Test
    @DisplayName("no issuer or no admin password: refused, never a made-up user id")
    void unconfiguredIsRefused() {
        assertThatThrownBy(() -> new KeycloakProvisioningServiceImpl("", adminUrl(), "admin", "pw")
                        .getOrCreateKeycloakUser(EMAIL, null, null, PASSWORD))
                .isInstanceOf(KeycloakProvisioningException.class);
        assertThatThrownBy(() -> new KeycloakProvisioningServiceImpl(UNREACHABLE_ISSUER, adminUrl(), "admin", "")
                        .getOrCreateKeycloakUser(EMAIL, null, null, PASSWORD))
                .isInstanceOf(KeycloakProvisioningException.class)
                .hasMessageContaining("KEYCLOAK_ADMIN_PASSWORD");
        assertThat(calls).isEmpty();
    }

    @Test
    @DisplayName("with no admin URL, the issuer's scheme and host are used")
    void adminUrlFallsBackToIssuerHost() {
        KeycloakProvisioningServiceImpl.Target target = new KeycloakProvisioningServiceImpl(
                        "http://kc.example:8081/realms/infinevo", "", "admin", "pw")
                .target();
        assertThat(target.usersUrl()).isEqualTo("http://kc.example:8081/admin/realms/infinevo/users");
    }
}
