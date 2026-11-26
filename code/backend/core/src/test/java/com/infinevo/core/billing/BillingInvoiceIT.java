package com.infinevo.core.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.sql.SQLException;
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
 * W-94.1 integration tests for GST subscription invoice generation, pricing matrix, headcount, sequential
 * numbering, and tenant isolation under PostgreSQL RLS.
 */
@SpringBootTest(classes = {PermissionGuardTestApp.class, BillingInvoiceIT.ItConfig.class})
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class BillingInvoiceIT extends AbstractIntegrationTest {

    @TestConfiguration
    @Import({AuditIntegratorConfig.class, AuditWriter.class})
    static class ItConfig {}

    private static final UUID PLATFORM = PlatformTenant.DEFAULT_PLATFORM_TENANT_ID;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private UUID platformStaffSub;
    private UUID tenantA;
    private UUID tenantAAdminSub;
    private UUID tenantB;
    private UUID tenantBAdminSub;

    @BeforeEach
    void setUp() throws SQLException {
        platformStaffSub = UUID.randomUUID();
        UUID staffAccount = AuthzTestSchema.insertMember(
                PLATFORM, platformStaffSub, "billing-staff-" + platformStaffSub + "@infinevo.test");
        AuthzTestSchema.grant(PLATFORM, staffAccount, AuthzTestSchema.roleId(PLATFORM, "platform-admin"));

        tenantA = AuthzTestSchema.insertTenant("BillingAlpha " + UUID.randomUUID());
        provisionSubscription(tenantA, "STARTER", "STANDARD");
        tenantAAdminSub = UUID.randomUUID();
        UUID adminA = AuthzTestSchema.insertMember(tenantA, tenantAAdminSub, "admin-a-" + tenantA + "@alpha.test");
        AuthzTestSchema.grant(tenantA, adminA, AuthzTestSchema.roleId(tenantA, "tenant-admin"));

        tenantB = AuthzTestSchema.insertTenant("BillingBeta " + UUID.randomUUID());
        provisionSubscription(tenantB, "GROWTH", "PRIORITY");
        tenantBAdminSub = UUID.randomUUID();
        UUID adminB = AuthzTestSchema.insertMember(tenantB, tenantBAdminSub, "admin-b-" + tenantB + "@beta.test");
        AuthzTestSchema.grant(tenantB, adminB, AuthzTestSchema.roleId(tenantB, "tenant-admin"));
    }

    @Test
    @DisplayName("Starter monthly intrastate invoice enforces minimum 10 users and splits CGST + SGST 9%/9%")
    void starterMonthlyIntrastate_computesMinimumAndSplitsGst() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        AuthzTestSchema.insertEmployee(tenantA, "A1-" + tag, "Active");
        UUID suspended = AuthzTestSchema.insertEmployee(tenantA, "S1-" + tag, "Suspended");
        UUID terminated = AuthzTestSchema.insertEmployee(tenantA, "T1-" + tag, "Terminated");
        execute("UPDATE core.employee SET status = 'SUSPENDED' WHERE id = ?", suspended);
        execute("UPDATE core.employee SET status = 'TERMINATED' WHERE id = ?", terminated);

        String body = mvc.perform(as(PLATFORM, platformStaffSub, post("/api/v1/core/billing/invoices/generate"))
                        .content(
                                """
                                {
                                  "tenantId": "%s",
                                  "billingPeriodStart": "2026-11-01",
                                  "billingPeriodEnd": "2026-11-30",
                                  "billingFrequency": "MONTHLY",
                                  "invoiceDate": "2026-11-01",
                                  "customerGstin": "27AABCU9603R1ZM"
                                }
                                """
                                        .formatted(tenantA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planTier").value("STARTER"))
                .andExpect(jsonPath("$.activeHeadcount").value(2))
                .andExpect(jsonPath("$.subtotal").value(490.0))
                .andExpect(jsonPath("$.discountAmount").value(0.0))
                .andExpect(jsonPath("$.taxableAmount").value(490.0))
                .andExpect(jsonPath("$.cgstAmount").value(44.1))
                .andExpect(jsonPath("$.sgstAmount").value(44.1))
                .andExpect(jsonPath("$.igstAmount").value(0.0))
                .andExpect(jsonPath("$.totalTax").value(88.2))
                .andExpect(jsonPath("$.totalAmount").value(578.2))
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.sacCode").value("997331"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = json.readTree(body);
        assertThat(created.path("invoiceNumber").asText()).matches("^INF/2026-27/\\d{4}$");
        assertThat(created.path("items")).hasSize(1);

        // Tenant A can read its own invoice; Tenant B cannot read Tenant A's invoice.
        UUID invoiceId = UUID.fromString(created.path("id").asText());
        mvc.perform(as(tenantA, tenantAAdminSub, get("/api/v1/core/billing/invoices/{id}", invoiceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber")
                        .value(created.path("invoiceNumber").asText()));

        mvc.perform(as(tenantB, tenantBAdminSub, get("/api/v1/core/billing/invoices/{id}", invoiceId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Growth annual interstate invoice applies 15% discount, adds Priority support, and charges IGST 18%")
    void growthAnnualInterstate_appliesDiscountAndIgst() throws Exception {
        String body1 = mvc.perform(as(PLATFORM, platformStaffSub, post("/api/v1/core/billing/invoices/generate"))
                        .content(
                                """
                                {
                                  "tenantId": "%s",
                                  "billingPeriodStart": "2026-11-01",
                                  "billingPeriodEnd": "2027-10-31",
                                  "billingFrequency": "ANNUAL",
                                  "invoiceDate": "2026-11-15",
                                  "customerGstin": "29AABCU9603R1ZM"
                                }
                                """
                                        .formatted(tenantB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planTier").value("GROWTH"))
                // Min 25 users: 25 * 79 * 12 = 23700.00; 15% discount = 3555.00; Priority = 4999 * 12 = 59988.00
                // Subtotal = 83688.00; Taxable = 80133.00; IGST 18% = 14423.94; Total = 94556.94
                .andExpect(jsonPath("$.subtotal").value(83688.0))
                .andExpect(jsonPath("$.discountAmount").value(3555.0))
                .andExpect(jsonPath("$.taxableAmount").value(80133.0))
                .andExpect(jsonPath("$.cgstAmount").value(0.0))
                .andExpect(jsonPath("$.sgstAmount").value(0.0))
                .andExpect(jsonPath("$.igstAmount").value(14423.94))
                .andExpect(jsonPath("$.totalAmount").value(94556.94))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String body2 = mvc.perform(as(PLATFORM, platformStaffSub, post("/api/v1/core/billing/invoices/generate"))
                        .content(
                                """
                                {
                                  "tenantId": "%s",
                                  "billingPeriodStart": "2026-12-01",
                                  "billingPeriodEnd": "2026-12-31",
                                  "billingFrequency": "MONTHLY",
                                  "invoiceDate": "2026-12-01"
                                }
                                """
                                        .formatted(tenantA)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int seq1 = Integer.parseInt(
                json.readTree(body1).path("invoiceNumber").asText().split("/")[2]);
        int seq2 = Integer.parseInt(
                json.readTree(body2).path("invoiceNumber").asText().split("/")[2]);
        assertThat(seq2).isEqualTo(seq1 + 1);
    }

    @Test
    @DisplayName("Enterprise plan calculates ₹119 PEPM with minimum 100 users, and customer tenants cannot generate")
    void enterpriseAndPermissionGuards() throws Exception {
        UUID tenantC = AuthzTestSchema.insertTenant("BillingEnt " + UUID.randomUUID());
        provisionSubscription(tenantC, "ENTERPRISE", "STANDARD");

        mvc.perform(as(PLATFORM, platformStaffSub, post("/api/v1/core/billing/invoices/generate"))
                        .content(
                                """
                                {
                                  "tenantId": "%s",
                                  "billingPeriodStart": "2026-11-01",
                                  "billingPeriodEnd": "2026-11-30",
                                  "billingFrequency": "MONTHLY",
                                  "invoiceDate": "2026-11-01",
                                  "customerStateCode": "27"
                                }
                                """
                                        .formatted(tenantC)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(11900.0))
                .andExpect(jsonPath("$.cgstAmount").value(1071.0))
                .andExpect(jsonPath("$.sgstAmount").value(1071.0))
                .andExpect(jsonPath("$.totalAmount").value(14042.0));

        // Customer tenant admin is forbidden from generating invoices or querying another tenant's list.
        mvc.perform(as(tenantA, tenantAAdminSub, post("/api/v1/core/billing/invoices/generate"))
                        .content(
                                """
                                {
                                  "tenantId": "%s",
                                  "billingPeriodStart": "2026-11-01",
                                  "billingPeriodEnd": "2026-11-30"
                                }
                                """
                                        .formatted(tenantA)))
                .andExpect(status().isForbidden());

        mvc.perform(as(
                        tenantA,
                        tenantAAdminSub,
                        get("/api/v1/core/billing/invoices").param("tenantId", tenantB.toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Subscription without a plan returns 409 CONFLICT")
    void noPlanConfigured_conflict() throws Exception {
        UUID tenantNoPlan = AuthzTestSchema.insertTenant("NoPlan " + UUID.randomUUID());
        provisionSubscription(tenantNoPlan, null, null);

        mvc.perform(as(PLATFORM, platformStaffSub, post("/api/v1/core/billing/invoices/generate"))
                        .content(
                                """
                                {
                                  "tenantId": "%s",
                                  "billingPeriodStart": "2026-11-01",
                                  "billingPeriodEnd": "2026-11-30"
                                }
                                """
                                        .formatted(tenantNoPlan)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    private static void provisionSubscription(UUID tenantId, String plan, String supportPlan) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.subscription (id, tenant_id, status, started_on, plan, support_plan)"
                                + " VALUES (?, ?, 'ACTIVE', CURRENT_DATE, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setString(3, plan);
            ps.setString(4, supportPlan);
            ps.executeUpdate();
        }
    }

    private static void execute(String sql, UUID id) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }

    private static MockHttpServletRequestBuilder as(UUID tenantId, UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenantId.toString())));
    }
}
