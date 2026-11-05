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
 * W-84.1 spec, Tests: {@code PUT /api/v1/tenants/{id}/subscription/plan} through the shipped filter chain, the real
 * permission check, the real {@code core.set_subscription_plan} (V188) and PostgreSQL with row-level security.
 *
 * <p>{@link ItConfig} adds the audit capture to {@code PermissionGuardTestApp}, as {@code ImpersonationItSupport}
 * does, so the audited write leaves the row the test reads back.
 */
@SpringBootTest(classes = {PermissionGuardTestApp.class, SubscriptionPlanIT.ItConfig.class})
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class SubscriptionPlanIT extends AbstractIntegrationTest {

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

    @BeforeEach
    void setUp() throws SQLException {
        staffSub = UUID.randomUUID();
        UUID staffAccount =
                AuthzTestSchema.insertMember(PLATFORM, staffSub, "plan-staff-" + staffSub + "@infinevo.test");
        AuthzTestSchema.grant(PLATFORM, staffAccount, AuthzTestSchema.roleId(PLATFORM, "platform-admin"));

        customer = AuthzTestSchema.insertTenant("PlanCo " + UUID.randomUUID());
        provisionSubscription(customer);

        customerAdminSub = UUID.randomUUID();
        UUID adminAccount = AuthzTestSchema.insertMember(customer, customerAdminSub, "admin@planco.test");
        AuthzTestSchema.grant(customer, adminAccount, AuthzTestSchema.roleId(customer, "tenant-admin"));
    }

    @Test
    @DisplayName("Platform staff set plan, support plan and limit: 200, the columns hold them, GET echoes them, "
            + "and the change is audited")
    void platformStaffSetPlan_writesColumnsAndAudits() throws Exception {
        mvc.perform(as(PLATFORM, staffSub, put("/api/v1/tenants/{id}/subscription/plan", customer))
                        .content("{\"plan\":\"GROWTH\",\"supportPlan\":\"PRIORITY\",\"employeeLimit\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("GROWTH"))
                .andExpect(jsonPath("$.supportPlan").value("PRIORITY"))
                .andExpect(jsonPath("$.employeeLimit").value(50));

        assertThat(storedPlan(customer)).containsExactly("GROWTH", "PRIORITY", "50");

        // The tenant reads its own subscription; the bound tenant is the only one GET answers for.
        mvc.perform(as(customer, customerAdminSub, get("/api/v1/tenants/{id}/subscription", customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("GROWTH"))
                .andExpect(jsonPath("$.supportPlan").value("PRIORITY"))
                .andExpect(jsonPath("$.employeeLimit").value(50));

        assertThat(auditedChangedColumns(subscriptionId(customer))).contains("employee_limit", "plan", "support_plan");
    }

    @Test
    @DisplayName("Only the limit sent, the rest null: plan and support plan are cleared, limit stored")
    void nullFieldsAreWrittenAsGiven() throws Exception {
        mvc.perform(as(PLATFORM, staffSub, put("/api/v1/tenants/{id}/subscription/plan", customer))
                        .content("{\"plan\":\"STARTER\",\"supportPlan\":\"STANDARD\",\"employeeLimit\":10}"))
                .andExpect(status().isOk());
        mvc.perform(as(PLATFORM, staffSub, put("/api/v1/tenants/{id}/subscription/plan", customer))
                        .content("{\"employeeLimit\":25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeLimit").value(25));

        assertThat(storedPlan(customer)).containsExactly(null, null, "25");
    }

    @Test
    @DisplayName("A customer tenant's administrator gets 403 and nothing is written")
    void customerTenant_forbidden() throws Exception {
        mvc.perform(as(customer, customerAdminSub, put("/api/v1/tenants/{id}/subscription/plan", customer))
                        .content("{\"employeeLimit\":1000}"))
                .andExpect(status().isForbidden());

        // A customer granted core.tenant.provision is still refused: the platform-tenant guard.
        UUID provisionerSub = UUID.randomUUID();
        UUID provisionerAccount = AuthzTestSchema.insertMember(customer, provisionerSub, "prov@planco.test");
        UUID role = AuthzTestSchema.insertRole(customer, "plan-provisioner", "Provisioner", "core.tenant.provision");
        AuthzTestSchema.grant(customer, provisionerAccount, role);
        mvc.perform(as(customer, provisionerSub, put("/api/v1/tenants/{id}/subscription/plan", customer))
                        .content("{\"employeeLimit\":1000}"))
                .andExpect(status().isForbidden());

        assertThat(storedPlan(customer)).containsExactly(null, null, null);
    }

    @Test
    @DisplayName("An employee limit of 0 answers 400 VALIDATION_FAILED")
    void zeroLimit_badRequest() throws Exception {
        mvc.perform(as(PLATFORM, staffSub, put("/api/v1/tenants/{id}/subscription/plan", customer))
                        .content("{\"employeeLimit\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(storedPlan(customer)).containsExactly(null, null, null);
    }

    @Test
    @DisplayName("The platform tenant's own plan cannot be set: 400")
    void platformTenant_refused() throws Exception {
        mvc.perform(as(PLATFORM, staffSub, put("/api/v1/tenants/{id}/subscription/plan", PLATFORM))
                        .content("{\"employeeLimit\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /tenants reports employee_count without terminated or soft-deleted employees, with suspended "
            + "ones, and the limit just set")
    void tenantList_reportsEmployeeCountAndLimit() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        AuthzTestSchema.insertEmployee(customer, "A-" + tag, "Active");
        UUID suspended = AuthzTestSchema.insertEmployee(customer, "S-" + tag, "Suspended");
        UUID terminated = AuthzTestSchema.insertEmployee(customer, "T-" + tag, "Terminated");
        UUID deleted = AuthzTestSchema.insertEmployee(customer, "D-" + tag, "Deleted");
        execute("UPDATE core.employee SET status = 'SUSPENDED' WHERE id = ?", suspended);
        execute("UPDATE core.employee SET status = 'TERMINATED' WHERE id = ?", terminated);
        execute("UPDATE core.employee SET is_deleted = true WHERE id = ?", deleted);

        mvc.perform(as(PLATFORM, staffSub, put("/api/v1/tenants/{id}/subscription/plan", customer))
                        .content("{\"plan\":\"GROWTH\",\"employeeLimit\":25}"))
                .andExpect(status().isOk());

        // The function itself, straight off the database.
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT employee_count, employee_limit, plan, support_plan FROM core.list_tenants()"
                                + " WHERE tenant_id = ?")) {
            ps.setObject(1, customer);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("employee_count")).isEqualTo(2L);
                assertThat(rs.getInt("employee_limit")).isEqualTo(25);
                assertThat(rs.getString("plan")).isEqualTo("GROWTH");
                assertThat(rs.getString("support_plan")).isNull();
            }
        }

        // And through the API, as the tenant list reads it.
        String body = mvc.perform(as(PLATFORM, staffSub, get("/api/v1/tenants")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode row = null;
        for (JsonNode candidate : json.readTree(body)) {
            if (customer.toString().equals(candidate.path("tenant_id").asText())) {
                row = candidate;
            }
        }
        assertThat(row).as("the customer is in the tenant list").isNotNull();
        assertThat(row.path("employee_count").asLong()).isEqualTo(2L);
        assertThat(row.path("employee_limit").asInt()).isEqualTo(25);
        assertThat(row.path("plan").asText()).isEqualTo("GROWTH");

        mvc.perform(as(PLATFORM, staffSub, get("/api/v1/tenants/{id}", customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee_count").value(2))
                .andExpect(jsonPath("$.employee_limit").value(25));
    }

    private void provisionSubscription(UUID tenantId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("INSERT INTO core.subscription (id, tenant_id, status, started_on)"
                                + " VALUES (?, ?, 'ACTIVE', CURRENT_DATE)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.executeUpdate();
        }
    }

    /** {@code [plan, support_plan, employee_limit]} as stored, read as the schema owner. */
    private static List<String> storedPlan(UUID tenantId) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT plan, support_plan, employee_limit::text FROM core.subscription WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return Arrays.asList(rs.getString(1), rs.getString(2), rs.getString(3));
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

    private static void execute(String sql, UUID id) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }

    private MockHttpServletRequestBuilder as(UUID tenantId, UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenantId.toString())));
    }
}
