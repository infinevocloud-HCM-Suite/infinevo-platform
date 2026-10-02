package com.infinevo.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.core.authz.AuthzTestSchema;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The platform-only actions stay with Infinevo staff (review of W-65.2, decision 5).
 *
 * <p>{@code core.tenant.provision} lets its holder change any tenant's modules and status. Nothing used to stop a
 * customer's own role from carrying it, and the subscription endpoints checked the action and nothing else, so
 * such a user could reach into another tenant. Two layers now: the role service refuses the grant, and the
 * endpoints require the platform tenant whatever the user holds.
 *
 * <p>This test schema applies only some migrations (it lacks {@code V136}, the database's own refusal, which
 * {@code PlatformOnlyActionGuardIT} proves), so a customer role holding the action is built by writing the row
 * directly. That is deliberate: it shows the endpoint refusing a caller the database layer would also have stopped.
 */
class PlatformOnlyActionIT extends ImpersonationItSupport {

    private UUID victim;
    private UUID customer;
    private UUID customerSub;
    private String customerEmail;
    private UUID customerAccount;
    private Staff staff;

    @BeforeEach
    void setUp() throws SQLException {
        victim = AuthzTestSchema.insertTenant("Victim " + UUID.randomUUID());
        customer = AuthzTestSchema.insertTenant("Customer " + UUID.randomUUID());
        provisionSubscription(victim, "HRMS");
        provisionSubscription(customer, "HRMS");
        customerSub = UUID.randomUUID();
        customerEmail = "admin@customer.platformonly.test";
        customerAccount = AuthzTestSchema.insertMember(customer, customerSub, customerEmail);
        AuthzTestSchema.grant(customer, customerAccount, AuthzTestSchema.roleId(customer, "tenant-admin"));
        staff = newStaff("staff-" + UUID.randomUUID());
    }

    @Test
    @DisplayName("A customer user holding core.tenant.provision still cannot change another tenant's modules or status")
    void customerHoldingProvisionCannotReachAnotherTenant() throws Exception {
        UUID role = AuthzTestSchema.insertRole(customer, "custom-provisioner", "Provisioner");
        grantDirectly(customer, role, "core.tenant.provision");
        AuthzTestSchema.grant(customer, customerAccount, role);

        mvc.perform(asCustomer(
                                customer,
                                customerSub,
                                customerEmail,
                                put("/api/v1/tenants/{id}/subscription/modules", victim))
                        .content(json.writeValueAsString(Map.of("modules", List.of("HRMS", "PAYROLL")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(asCustomer(
                                customer,
                                customerSub,
                                customerEmail,
                                put("/api/v1/tenants/{id}/subscription/status", victim))
                        .content(json.writeValueAsString(Map.of("status", "SUSPENDED"))))
                .andExpect(status().isForbidden());
        mvc.perform(asCustomer(
                                customer,
                                customerSub,
                                customerEmail,
                                put("/api/v1/tenants/{id}/subscription/status", customer))
                        .content(json.writeValueAsString(Map.of("status", "CANCELLED"))))
                // not even its own tenant's
                .andExpect(status().isForbidden());

        assertThat(subscriptionStatus(victim)).as("the victim is untouched").isEqualTo("ACTIVE");
        assertThat(subscriptionModules(victim)).containsExactly("HRMS");
        assertThat(subscriptionStatus(customer)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Platform staff can still change a customer's modules and status")
    void platformStaffCan() throws Exception {
        mvc.perform(asStaff(staff, put("/api/v1/tenants/{id}/subscription/modules", victim))
                        .content(json.writeValueAsString(Map.of("modules", List.of("HRMS", "PAYROLL")))))
                .andExpect(status().isOk());
        mvc.perform(asStaff(staff, put("/api/v1/tenants/{id}/subscription/status", victim))
                        .content(json.writeValueAsString(Map.of("status", "SUSPENDED"))))
                .andExpect(status().isOk());

        assertThat(subscriptionModules(victim)).containsExactlyInAnyOrder("HRMS", "PAYROLL");
        assertThat(subscriptionStatus(victim)).isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName(
            "A customer's tenant admin cannot create or edit a role that carries a platform-only action: 400, nothing stored")
    void customerCannotGrantPlatformOnlyActions() throws Exception {
        for (String action : List.of("core.tenant.provision", "core.tenant.impersonate")) {
            mvc.perform(asCustomer(customer, customerSub, customerEmail, post("/api/v1/roles"))
                            .content(json.writeValueAsString(Map.of(
                                    "name", "Sneaky " + action, "actionCodes", List.of("core.role.read", action)))))
                    .andExpect(status().isBadRequest())
                    .andExpect(
                            jsonPath("$.fieldErrors.actionCodes").value(org.hamcrest.Matchers.containsString(action)));
        }

        UUID role = AuthzTestSchema.insertRole(customer, "editable", "Editable", "core.role.read");
        mvc.perform(asCustomer(customer, customerSub, customerEmail, put("/api/v1/roles/{id}", role))
                        .content(json.writeValueAsString(
                                Map.of("name", "Editable", "actionCodes", List.of("core.tenant.provision")))))
                .andExpect(status().isBadRequest());

        assertThat(AuthzTestSchema.actionsOfRole(role)).containsExactly("core.role.read");
        assertThat(roleCount(customer, "Sneaky core.tenant.provision")).isZero();
    }

    /** Writes the grant as the schema owner, which this test schema (no {@code V136}) lets through. */
    private static void grantDirectly(UUID tenant, UUID role, String action) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.role_action (tenant_id, role_id, action_code) VALUES (?, ?, ?)")) {
            ps.setObject(1, tenant);
            ps.setObject(2, role);
            ps.setString(3, action);
            ps.executeUpdate();
        }
    }

    private static String subscriptionStatus(UUID tenant) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT status FROM core.subscription WHERE tenant_id = ?")) {
            ps.setObject(1, tenant);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private static List<String> subscriptionModules(UUID tenant) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT module FROM core.subscription_module WHERE tenant_id = ? AND revoked_on IS NULL ORDER BY module")) {
            ps.setObject(1, tenant);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> modules = new java.util.ArrayList<>();
                while (rs.next()) {
                    modules.add(rs.getString(1));
                }
                return modules;
            }
        }
    }

    private static long roleCount(UUID tenant, String name) throws SQLException {
        try (Connection conn = AuthzTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core.role WHERE tenant_id = ? AND name = ?")) {
            ps.setObject(1, tenant);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
