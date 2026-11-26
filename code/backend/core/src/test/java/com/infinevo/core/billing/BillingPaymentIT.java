package com.infinevo.core.billing;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * W-94.1 integration tests for payment recording: partial payment -> full settlement to {@code PAID},
 * duplicate UTR rejection, settled-invoice guard, and tenant RLS isolation.
 */
@SpringBootTest(classes = {PermissionGuardTestApp.class, BillingPaymentIT.ItConfig.class})
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            AuthzTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class BillingPaymentIT extends AbstractIntegrationTest {

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
                PLATFORM, platformStaffSub, "pay-staff-" + platformStaffSub + "@infinevo.test");
        AuthzTestSchema.grant(PLATFORM, staffAccount, AuthzTestSchema.roleId(PLATFORM, "platform-admin"));

        tenantA = AuthzTestSchema.insertTenant("PayAlpha " + UUID.randomUUID());
        provisionSubscription(tenantA, "STARTER", "STANDARD");
        tenantAAdminSub = UUID.randomUUID();
        UUID adminA = AuthzTestSchema.insertMember(tenantA, tenantAAdminSub, "pay-admin-a-" + tenantA + "@alpha.test");
        AuthzTestSchema.grant(tenantA, adminA, AuthzTestSchema.roleId(tenantA, "tenant-admin"));

        tenantB = AuthzTestSchema.insertTenant("PayBeta " + UUID.randomUUID());
        provisionSubscription(tenantB, "STARTER", "STANDARD");
        tenantBAdminSub = UUID.randomUUID();
        UUID adminB = AuthzTestSchema.insertMember(tenantB, tenantBAdminSub, "pay-admin-b-" + tenantB + "@beta.test");
        AuthzTestSchema.grant(tenantB, adminB, AuthzTestSchema.roleId(tenantB, "tenant-admin"));
    }

    @Test
    @DisplayName("Partial payment moves invoice to PARTIALLY_PAID, full payment moves to PAID, duplicate UTR is 409")
    void partialThenFullPayment_andDuplicateUtrRejected() throws Exception {
        // Starter monthly = 490.00 + 18% GST = 578.20
        String invBody = mvc.perform(as(PLATFORM, platformStaffSub, post("/api/v1/core/billing/invoices/generate"))
                        .content(
                                """
                                {
                                  "tenantId": "%s",
                                  "billingPeriodStart": "2026-11-01",
                                  "billingPeriodEnd": "2026-11-30",
                                  "invoiceDate": "2026-11-01",
                                  "customerGstin": "27AABCU9603R1ZM"
                                }
                                """
                                        .formatted(tenantA)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID invoiceId = UUID.fromString(json.readTree(invBody).path("id").asText());
        String utr1 = "UTR-" + UUID.randomUUID().toString().substring(0, 12);

        // 1. Partial payment of 300.00 -> PARTIALLY_PAID
        mvc.perform(as(PLATFORM, platformStaffSub, post("/api/v1/core/billing/invoices/{id}/payments", invoiceId))
                        .content(
                                """
                                {
                                  "paymentDate": "2026-11-05",
                                  "amount": 300.00,
                                  "paymentMethod": "NEFT",
                                  "transactionReference": "%s",
                                  "notes": "First installment"
                                }
                                """
                                        .formatted(utr1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.transactionReference").value(utr1));

        mvc.perform(as(PLATFORM, platformStaffSub, get("/api/v1/core/billing/invoices/{id}", invoiceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.amountPaid").value(300.0));

        // 2. Duplicate UTR for the same tenant -> 409 CONFLICT
        mvc.perform(as(PLATFORM, platformStaffSub, post("/api/v1/core/billing/invoices/{id}/payments", invoiceId))
                        .content(
                                """
                                {
                                  "paymentDate": "2026-11-06",
                                  "amount": 278.20,
                                  "paymentMethod": "NEFT",
                                  "transactionReference": "%s"
                                }
                                """
                                        .formatted(utr1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        // 3. Remaining payment of 278.20 -> PAID
        String utr2 = "UTR-" + UUID.randomUUID().toString().substring(0, 12);
        mvc.perform(as(PLATFORM, platformStaffSub, post("/api/v1/core/billing/invoices/{id}/payments", invoiceId))
                        .content(
                                """
                                {
                                  "paymentDate": "2026-11-06",
                                  "amount": 278.20,
                                  "paymentMethod": "UPI",
                                  "transactionReference": "%s"
                                }
                                """
                                        .formatted(utr2)))
                .andExpect(status().isCreated());

        mvc.perform(as(PLATFORM, platformStaffSub, get("/api/v1/core/billing/invoices/{id}", invoiceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.amountPaid").value(578.2));

        // 4. Customer tenant A can list payments; tenant B gets 404; customer cannot record payments (403)
        mvc.perform(as(tenantA, tenantAAdminSub, get("/api/v1/core/billing/invoices/{id}/payments", invoiceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(as(tenantB, tenantBAdminSub, get("/api/v1/core/billing/invoices/{id}/payments", invoiceId)))
                .andExpect(status().isNotFound());

        mvc.perform(
                        as(tenantA, tenantAAdminSub, post("/api/v1/core/billing/invoices/{id}/payments", invoiceId))
                                .content(
                                        """
                                {
                                  "paymentDate": "2026-11-07",
                                  "amount": 10.00,
                                  "paymentMethod": "UPI",
                                  "transactionReference": "SELF-ATTEMPT"
                                }
                                """))
                .andExpect(status().isForbidden());

        // 5. Further payment on a PAID invoice -> 409 CONFLICT
        mvc.perform(
                        as(PLATFORM, platformStaffSub, post("/api/v1/core/billing/invoices/{id}/payments", invoiceId))
                                .content(
                                        """
                                {
                                  "paymentDate": "2026-11-07",
                                  "amount": 10.00,
                                  "paymentMethod": "UPI",
                                  "transactionReference": "UTR-EXTRA-1"
                                }
                                """))
                .andExpect(status().isConflict());
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

    private static MockHttpServletRequestBuilder as(UUID tenantId, UUID sub, MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(sub.toString()).claim("tenant_id", tenantId.toString())));
    }
}
