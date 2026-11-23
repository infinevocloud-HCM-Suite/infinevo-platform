package com.infinevo.core.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.guard.PermissionGuardTestApp;
import com.infinevo.shared.audit.AuditIntegratorConfig;
import com.infinevo.shared.audit.AuditWriter;
import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-114.1 spec, Tests: {@code PUT /api/v1/tenants/{id}/subscription/ai} and {@code GET /api/v1/ai/status} through
 * the shipped filter chain, the real permission check, the real {@code core.set_subscription_ai} (V192) and
 * PostgreSQL with row-level security. Built like {@link SubscriptionPlanIT}.
 */
@SpringBootTest(classes = {PermissionGuardTestApp.class, SubscriptionAiIT.ItConfig.class})
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class SubscriptionAiIT extends AbstractIntegrationTest {

    @TestConfiguration
    @Import({AuditIntegratorConfig.class, AuditWriter.class})
    static class ItConfig {}

    private static final UUID PLATFORM = PlatformTenant.DEFAULT_PLATFORM_TENANT_ID;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private UUID staffSub;
    private UUID customer;
    private UUID customerAdminSub;
    private UUID customerEmployeeSub;
    private UUID otherEmployeeSub;
    private UUID other;

    @BeforeEach
    void setUp() throws SQLException {
        staffSub = UUID.randomUUID();
        UUID staffAccount = AuthzTestSchema.insertMember(PLATFORM, staffSub, "ai-staff-" + staffSub + "@infinevo.test");
        AuthzTestSchema.grant(PLATFORM, staffAccount, AuthzTestSchema.roleId(PLATFORM, "platform-admin"));

        customer = AuthzTestSchema.insertTenant("AiCo " + UUID.randomUUID());
        provisionSubscription(customer);
        customerAdminSub = UUID.randomUUID();
        UUID adminAccount = AuthzTestSchema.insertMember(customer, customerAdminSub, "admin@aico.test");
        AuthzTestSchema.grant(customer, adminAccount, AuthzTestSchema.roleId(customer, "tenant-admin"));
        customerEmployeeSub = UUID.randomUUID();
        UUID employeeAccount = AuthzTestSchema.insertMember(customer, customerEmployeeSub, "staff@aico.test");
        AuthzTestSchema.grant(customer, employeeAccount, AuthzTestSchema.roleId(customer, "employee"));

        other = AuthzTestSchema.insertTenant("OtherCo " + UUID.randomUUID());
        provisionSubscription(other);
        otherEmployeeSub = UUID.randomUUID();
        UUID otherAccount = AuthzTestSchema.insertMember(other, otherEmployeeSub, "staff@otherco.test");
        AuthzTestSchema.grant(other, otherAccount, AuthzTestSchema.roleId(other, "employee"));
    }

    @Test
    @DisplayName("Platform staff switch the helper on with cap 500: 200, both columns hold it, and the change is"
            + " audited")
    void platformStaffSwitchOn_writesBothColumns() throws Exception {
        assertThat(storedAi(customer)).as("off with no cap by default").containsExactly("false", null);

        mvc.perform(as(PLATFORM, staffSub, put("/api/v1/tenants/{id}/subscription/ai", customer))
                        .content("{\"enabled\":true,\"monthlyRequestCap\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiEnabled").value(true))
                .andExpect(jsonPath("$.aiMonthlyRequestCap").value(500));

        assertThat(storedAi(customer)).containsExactly("true", "500");
        assertThat(auditedChangedColumns(subscriptionId(customer))).contains("ai_enabled", "ai_monthly_request_cap");

        // Switching off keeps the cap that is sent.
        mvc.perform(as(PLATFORM, staffSub, put("/api/v1/tenants/{id}/subscription/ai", customer))
                        .content("{\"enabled\":false,\"monthlyRequestCap\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiEnabled").value(false));
        assertThat(storedAi(customer)).containsExactly("false", "500");
    }

    @Test
    @DisplayName("A customer tenant's administrator gets 403, even holding core.tenant.provision; nothing is written")
    void customerTenant_forbidden() throws Exception {
        mvc.perform(as(customer, customerAdminSub, put("/api/v1/tenants/{id}/subscription/ai", customer))
                        .content("{\"enabled\":true,\"monthlyRequestCap\":500}"))
                .andExpect(status().isForbidden());

        UUID provisionerSub = UUID.randomUUID();
        UUID provisionerAccount = AuthzTestSchema.insertMember(customer, provisionerSub, "prov@aico.test");
        UUID role = AuthzTestSchema.insertRole(customer, "ai-provisioner", "Provisioner", "core.tenant.provision");
        AuthzTestSchema.grant(customer, provisionerAccount, role);
        mvc.perform(as(customer, provisionerSub, put("/api/v1/tenants/{id}/subscription/ai", customer))
                        .content("{\"enabled\":true,\"monthlyRequestCap\":500}"))
                .andExpect(status().isForbidden());

        assertThat(storedAi(customer)).containsExactly("false", null);
    }

    @Test
    @DisplayName("On with no cap, and cap 0, answer 400 VALIDATION_FAILED; the platform tenant's own answers 400")
    void invalidBodies_badRequest() throws Exception {
        mvc.perform(as(PLATFORM, staffSub, put("/api/v1/tenants/{id}/subscription/ai", customer))
                        .content("{\"enabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(as(PLATFORM, staffSub, put("/api/v1/tenants/{id}/subscription/ai", customer))
                        .content("{\"enabled\":true,\"monthlyRequestCap\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(as(PLATFORM, staffSub, put("/api/v1/tenants/{id}/subscription/ai", PLATFORM))
                        .content("{\"enabled\":true,\"monthlyRequestCap\":5}"))
                .andExpect(status().isBadRequest());

        assertThat(storedAi(customer)).containsExactly("false", null);
    }

    @Test
    @DisplayName("GET /api/v1/ai/status echoes the switch and cap to the customer's employee; another customer's"
            + " employee sees enabled = false")
    void status_echoesSettings() throws Exception {
        mvc.perform(as(PLATFORM, staffSub, put("/api/v1/tenants/{id}/subscription/ai", customer))
                        .content("{\"enabled\":true,\"monthlyRequestCap\":500}"))
                .andExpect(status().isOk());
        String month = YearMonth.now(ZoneOffset.UTC).toString();

        mvc.perform(as(customer, customerEmployeeSub, get("/api/v1/ai/status")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.monthlyRequestCap").value(500))
                .andExpect(jsonPath("$.usedThisMonth").value(0))
                .andExpect(jsonPath("$.month").value(month));

        mvc.perform(as(other, otherEmployeeSub, get("/api/v1/ai/status")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.usedThisMonth").value(0));

        insertUsage(customer, 12);
        mvc.perform(as(customer, customerEmployeeSub, get("/api/v1/ai/status")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedThisMonth").value(12));
    }

    @Test
    @DisplayName("core.list_tenants() and GET /tenants, /tenants/{id} return ai_enabled, ai_monthly_request_cap and"
            + " ai_usage_this_month")
    void tenantList_reportsAiColumns() throws Exception {
        mvc.perform(as(PLATFORM, staffSub, put("/api/v1/tenants/{id}/subscription/ai", customer))
                        .content("{\"enabled\":true,\"monthlyRequestCap\":500}"))
                .andExpect(status().isOk());
        insertUsage(customer, 12);

        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT ai_enabled, ai_monthly_request_cap, ai_usage_this_month FROM core.list_tenants()"
                                + " WHERE tenant_id = ?")) {
            ps.setObject(1, customer);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("ai_enabled")).isTrue();
                assertThat(rs.getInt("ai_monthly_request_cap")).isEqualTo(500);
                assertThat(rs.getInt("ai_usage_this_month")).isEqualTo(12);
            }
        }

        String body = mvc.perform(as(PLATFORM, staffSub, get("/api/v1/tenants")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode row = null;
        JsonNode otherRow = null;
        for (JsonNode candidate : json.readTree(body)) {
            if (customer.toString().equals(candidate.path("tenant_id").asText())) {
                row = candidate;
            }
            if (other.toString().equals(candidate.path("tenant_id").asText())) {
                otherRow = candidate;
            }
        }
        assertThat(row).as("the customer is in the tenant list").isNotNull();
        assertThat(row.path("ai_enabled").asBoolean()).isTrue();
        assertThat(row.path("ai_monthly_request_cap").asInt()).isEqualTo(500);
        assertThat(row.path("ai_usage_this_month").asInt()).isEqualTo(12);
        assertThat(otherRow).as("the other customer is in the tenant list").isNotNull();
        assertThat(otherRow.path("ai_enabled").asBoolean()).isFalse();
        assertThat(otherRow.path("ai_monthly_request_cap").isNull()).isTrue();
        assertThat(otherRow.path("ai_usage_this_month").asInt()).isZero();

        mvc.perform(as(PLATFORM, staffSub, get("/api/v1/tenants/{id}", customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ai_enabled").value(true))
                .andExpect(jsonPath("$.ai_monthly_request_cap").value(500))
                .andExpect(jsonPath("$.ai_usage_this_month").value(12));
    }

    private static void provisionSubscription(UUID tenantId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("INSERT INTO core.subscription (id, tenant_id, status, started_on)"
                                + " VALUES (?, ?, 'ACTIVE', CURRENT_DATE)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        }
    }

    /** This UTC month's usage row with {@code count} requests, as the schema owner. */
    private static void insertUsage(UUID tenantId, int count) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.ai_usage (tenant_id, usage_month, request_count) VALUES (?, ?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1));
            ps.setInt(3, count);
            ps.executeUpdate();
        }
    }

    /** {@code [ai_enabled, ai_monthly_request_cap]} as stored, read as the schema owner. */
    private static List<String> storedAi(UUID tenantId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT ai_enabled::text, ai_monthly_request_cap::text"
                        + " FROM core.subscription WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return Arrays.asList(rs.getString(1), rs.getString(2));
            }
        }
    }

    private static UUID subscriptionId(UUID tenantId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT id FROM core.subscription WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    /** The {@code changed_columns} of the newest UPDATE audit row for the subscription. */
    private static List<String> auditedChangedColumns(UUID subscriptionId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        SELECT changed_columns FROM core.audit_log
                         WHERE entity_table = 'subscription' AND entity_id = ? AND operation = 'UPDATE'
                         ORDER BY occurred_at DESC LIMIT 1
                        """)) {
            ps.setString(1, subscriptionId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("an audit row for the subscription").isTrue();
                return Arrays.asList((String[]) rs.getArray(1).getArray());
            }
        }
    }

    private MockHttpServletRequestBuilder as(UUID tenantId, UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenantId.toString())));
    }
}
