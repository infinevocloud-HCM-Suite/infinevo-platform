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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * {@link KeycloakProvisioningServiceImpl} against a stub Keycloak admin API on a local port (W-24.2, B-2/B-3).
 *
 * <p>The issuer points at a port nothing listens on, so any call that went to the issuer's host instead of
 * {@code keycloak.admin.url} would fail the test.
 */
class KeycloakProvisioningServiceTest {

    private static final String UNREACHABLE_ISSUER = "http://127.0.0.1:1/realms/infinevo";
    private static final String EMAIL = "new.person@example.com";

    record Call(String method, String path, String query, String body) {}

    private HttpServer server;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final UUID createdId = UUID.randomUUID();
    private volatile String searchBody = "[]";
    private volatile int createStatus = 201;
    private volatile int actionsEmailStatus = 204;

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

        String users = "/admin/realms/infinevo/users";
        if (path.equals("/realms/master/protocol/openid-connect/token")) {
            respond(exchange, 200, "{\"access_token\":\"admin-token\"}");
        } else if (path.equals(users) && method.equals("GET")) {
            respond(exchange, 200, searchBody);
        } else if (path.equals(users) && method.equals("POST")) {
            if (createStatus == 201) {
                exchange.getResponseHeaders().add("Location", adminUrl() + users + "/" + createdId);
            }
            respond(exchange, createStatus, createStatus == 201 ? "" : "{\"error\":\"bad " + EMAIL + "\"}");
        } else if (path.startsWith(users + "/") && path.endsWith("/execute-actions-email") && method.equals("PUT")) {
            respond(exchange, actionsEmailStatus, "");
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
    @DisplayName("search, create with UPDATE_PASSWORD, then execute-actions-email — all on the admin URL")
    void createsUserThatMustSetAPassword() {
        KeycloakProvisioningService.ProvisioningResult result =
                service().getOrCreateKeycloakUser(EMAIL, "New", "Person");

        assertThat(result.keycloakUserId()).isEqualTo(createdId);
        assertThat(result.newlyCreated()).isTrue();
        assertThat(result.outcome()).isEqualTo(AcceptOutcome.SET_PASSWORD_EMAIL_SENT);
        assertThat(calls)
                .extracting(Call::method, Call::path)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("POST", "/realms/master/protocol/openid-connect/token"),
                        org.assertj.core.groups.Tuple.tuple("GET", "/admin/realms/infinevo/users"),
                        org.assertj.core.groups.Tuple.tuple("POST", "/admin/realms/infinevo/users"),
                        org.assertj.core.groups.Tuple.tuple(
                                "PUT", "/admin/realms/infinevo/users/" + createdId + "/execute-actions-email"));
        assertThat(calls.get(1).query()).contains("exact=true");
        assertThat(calls.get(2).body()).contains("\"requiredActions\":[\"UPDATE_PASSWORD\"]");
        assertThat(calls.get(3).body()).isEqualTo("[\"UPDATE_PASSWORD\"]");
    }

    @Test
    @DisplayName("the set-password email failing is logged without the address and does not fail provisioning")
    void actionsEmailFailureIsOnlyAWarning() {
        actionsEmailStatus = 500;

        KeycloakProvisioningService.ProvisioningResult result = service().getOrCreateKeycloakUser(EMAIL, null, null);

        assertThat(result.newlyCreated()).isTrue();
        assertThat(result.outcome()).isEqualTo(AcceptOutcome.SET_PASSWORD_EMAIL_FAILED);
        assertThat(logs.list)
                .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("set-password email"));
        assertThat(logs.list)
                .noneSatisfy(event -> assertThat(event.getFormattedMessage()).contains(EMAIL));
    }

    @Test
    @DisplayName("an existing user is reused: no create, no email, not marked as created")
    void existingUserIsReused() {
        UUID existing = UUID.randomUUID();
        searchBody = "[{\"id\":\"" + existing + "\"}]";

        KeycloakProvisioningService.ProvisioningResult result = service().getOrCreateKeycloakUser(EMAIL, null, null);

        assertThat(result.keycloakUserId()).isEqualTo(existing);
        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.outcome()).isEqualTo(AcceptOutcome.EXISTING_ACCOUNT);
        assertThat(calls).extracting(Call::method).containsExactly("POST", "GET");
    }

    @Test
    @DisplayName("D-62: an existing user who never set a password is sent the set-password mail again")
    void existingUserAwaitingPasswordGetsTheMailAgain() {
        UUID existing = UUID.randomUUID();
        searchBody = "[{\"id\":\"" + existing + "\",\"requiredActions\":[\"UPDATE_PASSWORD\"]}]";

        KeycloakProvisioningService.ProvisioningResult result = service().getOrCreateKeycloakUser(EMAIL, null, null);

        assertThat(result.keycloakUserId()).isEqualTo(existing);
        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.outcome()).isEqualTo(AcceptOutcome.SET_PASSWORD_EMAIL_SENT);
        assertThat(calls)
                .extracting(Call::method, Call::path)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("POST", "/realms/master/protocol/openid-connect/token"),
                        org.assertj.core.groups.Tuple.tuple("GET", "/admin/realms/infinevo/users"),
                        org.assertj.core.groups.Tuple.tuple(
                                "PUT", "/admin/realms/infinevo/users/" + existing + "/execute-actions-email"));
    }

    @Test
    @DisplayName("D-62: a create that races another acceptance (409) reuses the user and sends the mail itself")
    void raceOnCreateSendsTheMail() {
        UUID raced = UUID.randomUUID();
        createStatus = 409;
        // The first search finds nothing; the search after the 409 finds the raced user, still awaiting a password.
        server.removeContext("/");
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/admin/realms/infinevo/users")
                    && exchange.getRequestMethod().equals("GET")) {
                long searches =
                        calls.stream().filter(c -> c.method().equals("GET")).count();
                searchBody =
                        searches == 0 ? "[]" : "[{\"id\":\"" + raced + "\",\"requiredActions\":[\"UPDATE_PASSWORD\"]}]";
            }
            handle(exchange);
        });

        KeycloakProvisioningService.ProvisioningResult result = service().getOrCreateKeycloakUser(EMAIL, null, null);

        assertThat(result.keycloakUserId()).isEqualTo(raced);
        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.outcome()).isEqualTo(AcceptOutcome.SET_PASSWORD_EMAIL_SENT);
        assertThat(calls)
                .extracting(Call::path)
                .contains("/admin/realms/infinevo/users/" + raced + "/execute-actions-email");
    }

    @Test
    @DisplayName("D-62: the set-password mail returns to the app — web client and the invitation link's origin")
    void setPasswordMailCarriesTheWayBack() {
        new KeycloakProvisioningServiceImpl(
                        UNREACHABLE_ISSUER,
                        adminUrl(),
                        "admin",
                        "admin-pw",
                        "infinevo-web",
                        "https://app.example/invitations/accept")
                .getOrCreateKeycloakUser(EMAIL, null, null);

        Call mail = calls.get(3);
        assertThat(mail.path()).endsWith("/execute-actions-email");
        assertThat(mail.query()).isEqualTo("client_id=infinevo-web&redirect_uri=https%3A%2F%2Fapp.example%2F");
    }

    @Test
    @DisplayName("D-62: with no invitation link the mail is sent bare rather than with a guessed address")
    void noInvitationLinkMeansNoRedirect() {
        service().getOrCreateKeycloakUser(EMAIL, null, null);

        assertThat(calls.get(3).query()).isNull();
        assertThat(KeycloakProvisioningServiceImpl.appRoot("not a url")).isEmpty();
        assertThat(KeycloakProvisioningServiceImpl.appRoot("http://localhost:5173/invitations/accept"))
                .isEqualTo("http://localhost:5173/");
    }

    @Test
    @DisplayName("a refused create fails without quoting the address Keycloak echoed back")
    void refusedCreateKeepsTheAddressOutOfTheMessage() {
        createStatus = 400;

        assertThatThrownBy(() -> service().getOrCreateKeycloakUser(EMAIL, null, null))
                .isInstanceOf(KeycloakProvisioningException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageNotContaining(EMAIL);
    }

    @Test
    @DisplayName("no issuer or no admin password: refused, never a made-up user id")
    void unconfiguredIsRefused() {
        assertThatThrownBy(() -> new KeycloakProvisioningServiceImpl("", adminUrl(), "admin", "pw")
                        .getOrCreateKeycloakUser(EMAIL, null, null))
                .isInstanceOf(KeycloakProvisioningException.class);
        assertThatThrownBy(() -> new KeycloakProvisioningServiceImpl(UNREACHABLE_ISSUER, adminUrl(), "admin", "")
                        .getOrCreateKeycloakUser(EMAIL, null, null))
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
