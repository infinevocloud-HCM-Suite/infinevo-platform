package com.infinevo.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
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
 * W-73.2 spec §7: as platform staff, {@code GET /tenants/summary} counts the same customer tenants the tenant
 * list returns, and names the tenants whose administrator invitation is pending or expired - read across
 * row-level security through {@code core.list_waiting_admin_invitations()} (V168). A customer tenant's user
 * holding {@code core.tenant.provision} is refused, on the summary and on the resend.
 *
 * <p>The test database is shared with other tests, so counts are compared with the list, not with constants.
 */
@SpringBootTest(classes = PermissionGuardTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class TenantSummaryIT extends AbstractIntegrationTest {

    private static final UUID PLATFORM_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private UUID staffSub;
    private UUID suspendedTenant;
    private UUID pendingTenant;
    private UUID expiredTenant;
    private UUID acceptedTenant;
    private UUID supersededTenant;

    @BeforeEach
    void setUp() throws SQLException {
        staffSub = UUID.randomUUID();
        UUID staffAccount =
                AuthzTestSchema.insertMember(PLATFORM_TENANT, staffSub, "staff-" + staffSub + "@infinevo.test");
        AuthzTestSchema.grant(PLATFORM_TENANT, staffAccount, AuthzTestSchema.roleId(PLATFORM_TENANT, "platform-admin"));

        suspendedTenant = AuthzTestSchema.insertTenant("Summary Suspended " + UUID.randomUUID());
        subscription(suspendedTenant, "SUSPENDED");

        Instant now = Instant.now();
        pendingTenant = AuthzTestSchema.insertTenant("Summary Pending " + UUID.randomUUID());
        subscription(pendingTenant, "ACTIVE");
        adminInvitation(pendingTenant, "PENDING", now.plus(Duration.ofDays(5)), now, null);

        // Still PENDING in the table but past its expiry: reported EXPIRED.
        expiredTenant = AuthzTestSchema.insertTenant("Summary Expired " + UUID.randomUUID());
        subscription(expiredTenant, "ACTIVE");
        adminInvitation(expiredTenant, "PENDING", now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(8)), null);

        // An accepted administrator: nobody is waiting, even with a later pending invitation.
        acceptedTenant = AuthzTestSchema.insertTenant("Summary Accepted " + UUID.randomUUID());
        subscription(acceptedTenant, "ACTIVE");
        adminInvitation(acceptedTenant, "ACCEPTED", now.plus(Duration.ofDays(5)), now.minus(Duration.ofDays(2)), null);
        adminInvitation(acceptedTenant, "PENDING", now.plus(Duration.ofDays(6)), now, null);

        // Resent: the newest invitation is the one reported, the superseded one is ignored.
        supersededTenant = AuthzTestSchema.insertTenant("Summary Resent " + UUID.randomUUID());
        subscription(supersededTenant, "ACTIVE");
        UUID fresh = adminInvitation(supersededTenant, "PENDING", now.plus(Duration.ofDays(7)), now, null);
        adminInvitation(
                supersededTenant, "REVOKED", now.plus(Duration.ofDays(1)), now.minus(Duration.ofDays(6)), fresh);
    }

    @Test
    @DisplayName("staff: counts match the tenant list without the platform row; waiting admins are named")
    void summary_matchesTheListAndNamesWaitingAdmins() throws Exception {
        String listBody = mvc.perform(as(PLATFORM_TENANT, staffSub, get("/api/v1/tenants")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String summaryBody = mvc.perform(as(PLATFORM_TENANT, staffSub, get("/api/v1/tenants/summary")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<TenantOverview> customers = json.readValue(listBody, new TypeReference<List<TenantOverview>>() {}).stream()
                .filter(t -> !t.tenantId().equals(PLATFORM_TENANT))
                .toList();
        Map<String, Long> expectedByStatus = new HashMap<>();
        for (TenantOverview t : customers) {
            expectedByStatus.merge(t.status() == null ? "NONE" : t.status(), 1L, Long::sum);
        }

        JsonNode summary = json.readTree(summaryBody);
        assertThat(summary.path("total").asLong()).isEqualTo(customers.size());
        for (Map.Entry<String, Long> e : expectedByStatus.entrySet()) {
            assertThat(summary.path("byStatus").path(e.getKey()).asLong())
                    .as("byStatus.%s", e.getKey())
                    .isEqualTo(e.getValue());
        }
        assertThat(summary.path("byStatus").path("SUSPENDED").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(summary.path("createdLast30Days").asLong()).isGreaterThanOrEqualTo(5);

        List<String> recentIds = ids(summary.path("recent"), "id");
        assertThat(recentIds).hasSizeLessThanOrEqualTo(10).doesNotContain(PLATFORM_TENANT.toString());

        Map<String, JsonNode> waiting = new HashMap<>();
        summary.path("waitingForAdmin").forEach(w -> waiting.put(w.path("id").asText(), w));
        assertThat(waiting).doesNotContainKey(PLATFORM_TENANT.toString());
        assertThat(waiting).doesNotContainKey(acceptedTenant.toString());
        assertThat(waiting).doesNotContainKey(suspendedTenant.toString());
        assertThat(waiting.get(pendingTenant.toString())
                        .path("invitationStatus")
                        .asText())
                .isEqualTo("PENDING");
        assertThat(waiting.get(pendingTenant.toString()).path("adminEmail").asText())
                .isEqualTo("admin-" + pendingTenant + "@example.test");
        assertThat(waiting.get(pendingTenant.toString()).path("name").asText()).startsWith("Summary Pending ");
        assertThat(waiting.get(expiredTenant.toString())
                        .path("invitationStatus")
                        .asText())
                .isEqualTo("EXPIRED");
        assertThat(waiting.get(supersededTenant.toString())
                        .path("invitationStatus")
                        .asText())
                .as("the newest, not the superseded one")
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("a customer tenant's user holding core.tenant.provision gets 403 on summary and resend")
    void customerTenantWithProvision_isForbidden() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID account = AuthzTestSchema.insertMember(pendingTenant, sub, "provisioner-" + sub + "@acme.test");
        UUID role = AuthzTestSchema.insertRole(
                pendingTenant, "custom-provisioner-" + sub, "Provisioner", "core.tenant.provision");
        AuthzTestSchema.grant(pendingTenant, account, role);

        mvc.perform(as(pendingTenant, sub, get("/api/v1/tenants/summary")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(as(pendingTenant, sub, post("/api/v1/tenants/" + pendingTenant + "/admin-invitation/resend")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("V168 returns nothing unless the transaction is bound to the platform tenant")
    void waitingFunction_isPlatformOnlyInTheDatabase() throws SQLException {
        assertThat(waitingTenantsBoundTo(PLATFORM_TENANT)).contains(pendingTenant.toString());
        assertThat(waitingTenantsBoundTo(pendingTenant)).isEmpty();
        assertThat(waitingTenantsBoundTo(null)).as("unbound").isEmpty();
    }

    /** Calls the function as app_user, the transaction bound to {@code tenantId} (or to nothing). */
    private static List<String> waitingTenantsBoundTo(UUID tenantId) throws SQLException {
        try (Connection conn = AuthzTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try {
                if (tenantId != null) {
                    try (PreparedStatement ps =
                            conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
                        ps.setString(1, tenantId.toString());
                        ps.execute();
                    }
                }
                List<String> ids = new java.util.ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT tenant_id::text FROM core.list_waiting_admin_invitations()");
                        java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getString(1));
                    }
                }
                return ids;
            } finally {
                conn.rollback();
            }
        }
    }

    private static List<String> ids(JsonNode array, String field) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(n -> n.path(field).asText())
                .toList();
    }

    private static void subscription(UUID tenantId, String status) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("INSERT INTO core.subscription (id, tenant_id, status, started_on)"
                                + " VALUES (?, ?, ?, CURRENT_DATE) ON CONFLICT (tenant_id) DO NOTHING")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setString(3, status);
            ps.executeUpdate();
        }
    }

    /** A tenant-admin invitation written as the schema owner, past RLS. */
    private static UUID adminInvitation(
            UUID tenantId, String status, Instant expiresAt, Instant createdAt, UUID supersededBy) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection conn = AuthzTestSchema.migrationConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.user_invitation (id, tenant_id, email, token_hash, status, expires_at,"
                            + " superseded_by_id, invited_by_user_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, tenantId);
                ps.setString(3, "admin-" + tenantId + "@example.test");
                ps.setString(4, (id.toString().replace("-", "") + id.toString().replace("-", "")).substring(0, 64));
                ps.setString(5, status);
                ps.setTimestamp(6, Timestamp.from(expiresAt));
                ps.setObject(7, supersededBy);
                ps.setObject(8, UUID.randomUUID());
                ps.setTimestamp(9, Timestamp.from(createdAt));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.user_invitation_role (tenant_id, invitation_id, role_id) VALUES (?, ?, ?)")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, id);
                ps.setObject(3, AuthzTestSchema.roleId(tenantId, "tenant-admin"));
                ps.executeUpdate();
            }
        }
        return id;
    }

    private MockHttpServletRequestBuilder as(UUID tenantId, UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenantId.toString())));
    }
}
