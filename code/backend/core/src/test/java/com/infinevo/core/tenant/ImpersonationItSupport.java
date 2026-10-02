package com.infinevo.core.tenant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.audit.AuditIntegratorConfig;
import com.infinevo.shared.audit.AuditWriter;
import com.infinevo.shared.cache.CacheService;
import com.infinevo.shared.impersonation.ImpersonationController;
import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Shared set-up for the platform-impersonation integration tests (W-65.2 spec section 7).
 *
 * <p>They run over HTTP through the shipped filter chain, the real permission check, the real
 * {@code core.open_impersonation} / {@code resolve_impersonation} / {@code close_impersonation} functions and
 * a real PostgreSQL with row-level security. Nothing about the session is mocked: a session is opened by the
 * staff member's own request, and what is then asserted is what the next request is allowed to do.
 *
 * <p>{@link ItConfig} adds to {@code PermissionGuardTestApp} the two things its context leaves out: the
 * impersonation controller (the applications find it by scanning {@code com.infinevo}) and the audit capture,
 * so an audited write made while impersonating leaves the row the tests read back.
 */
@SpringBootTest(classes = {PermissionGuardTestApp.class, ImpersonationItSupport.ItConfig.class})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = {PostgresTestContainerInitializer.class, AuthzTestSchema.Initializer.class})
abstract class ImpersonationItSupport extends AbstractIntegrationTest {

    @TestConfiguration
    @Import({ImpersonationController.class, AuditIntegratorConfig.class, AuditWriter.class})
    static class ItConfig {

        /** The cache the permission check reads through, in memory: see {@link InMemoryCacheService}. */
        @Bean
        @Primary
        CacheService inMemoryCacheService() {
            return new InMemoryCacheService();
        }
    }

    protected static final UUID PLATFORM = PlatformTenant.DEFAULT_PLATFORM_TENANT_ID;

    @Autowired
    protected MockMvc mvc;

    protected final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** A staff member: a member of the platform tenant holding {@code platform-admin}, hence the action. */
    protected record Staff(UUID sub, String email) {}

    protected Staff newStaff(String label) throws SQLException {
        UUID sub = UUID.randomUUID();
        String email = label + "@infinevo.local";
        UUID account = AuthzTestSchema.insertMember(PLATFORM, sub, email);
        AuthzTestSchema.grant(PLATFORM, account, AuthzTestSchema.roleId(PLATFORM, "platform-admin"));
        return new Staff(sub, email);
    }

    /** A request as the staff member, bound by their own token to the platform tenant. */
    protected MockHttpServletRequestBuilder asStaff(Staff staff, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(staff.sub().toString())
                        .claim("tenant_id", PLATFORM.toString())
                        .claim("email", staff.email())));
    }

    /** A request as a customer user, in their own tenant. */
    protected MockHttpServletRequestBuilder asCustomer(
            UUID tenantId, UUID sub, String email, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON).with(jwt().jwt(token -> token.subject(sub.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("email", email)));
    }

    /** Opens a session through the API, as the staff member, and returns its id. */
    protected UUID openSession(Staff staff, UUID tenantId, Map<String, Object> body) throws Exception {
        var result = mvc.perform(asStaff(staff, post("/api/v1/tenants/{id}/impersonations", tenantId))
                        .content(json.writeValueAsString(body)))
                .andReturn()
                .getResponse();
        if (result.getStatus() != 201) {
            throw new AssertionError(
                    "Opening the session answered " + result.getStatus() + ": " + result.getContentAsString());
        }
        String response = result.getContentAsString();
        return UUID.fromString(json.readTree(response).path("sessionId").asText());
    }

    /** The newest audit row for the tenant's entity table, as {@code [actor_label, actor_user_id]}. */
    protected List<String> latestAuditActor(UUID tenantId, String entityTable) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        SELECT actor_label, actor_user_id::text FROM core.audit_log
                         WHERE tenant_id = ? AND entity_table = ? AND operation = 'UPDATE'
                         ORDER BY occurred_at DESC LIMIT 1
                        """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, entityTable);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? List.of(rs.getString(1), String.valueOf(rs.getString(2))) : List.of();
            }
        }
    }

    protected void provisionSubscription(UUID tenantId, String... modules) throws SQLException {
        UUID subId = UUID.randomUUID();
        try (Connection conn = AuthzTestSchema.migrationConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.subscription (id, tenant_id, status, started_on) VALUES (?, ?, 'ACTIVE', CURRENT_DATE)")) {
                ps.setObject(1, subId);
                ps.setObject(2, tenantId);
                ps.executeUpdate();
            }
            for (String module : modules) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.subscription_module (id, tenant_id, subscription_id, module, granted_on) VALUES (?, ?, ?, ?, CURRENT_DATE)")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, tenantId);
                    ps.setObject(3, subId);
                    ps.setString(4, module);
                    ps.executeUpdate();
                }
            }
        }
    }

    /** Expires a session now, as the schema owner: the only way to age a row the application cannot update. */
    protected void expireSession(UUID sessionId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE core.impersonation_session SET expires_at = now() - interval '1 minute' WHERE id = ?")) {
            ps.setObject(1, sessionId);
            ps.executeUpdate();
        }
    }

    /** {@code expires_at - started_at} of a session, in minutes, as stored. */
    protected long sessionLifetimeMinutes(UUID sessionId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT extract(epoch FROM (expires_at - started_at)) / 60 FROM core.impersonation_session WHERE id = ?")) {
            ps.setObject(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return Math.round(rs.getDouble(1));
            }
        }
    }
}
