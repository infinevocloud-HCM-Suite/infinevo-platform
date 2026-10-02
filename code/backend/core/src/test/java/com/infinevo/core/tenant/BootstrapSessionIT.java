package com.infinevo.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmploymentStatus;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-65.2 spec section 7, {@code BootstrapSessionIT}: a freshly provisioned tenant has no user yet, so staff open
 * a session with no target and act as its {@code tenant-admin} until the first user exists.
 */
class BootstrapSessionIT extends ImpersonationItSupport {

    private UUID fresh;
    private Staff staff;

    @BeforeEach
    void setUp() throws SQLException {
        // The way a tenant is provisioned: a row, and the trigger gives it its system roles. No user account.
        fresh = AuthzTestSchema.insertTenant("Fresh Tenant " + UUID.randomUUID());
        provisionSubscription(fresh, "HRMS", "PAYROLL");
        staff = newStaff("staff-" + UUID.randomUUID());
    }

    @Test
    @DisplayName(
            "A session with no target opens, and the menu is the tenant-admin feed built from tenant-admin's actions")
    void bootstrapSessionActsAsTenantAdmin() throws Exception {
        String opened = mvc.perform(asStaff(staff, post("/api/v1/tenants/{id}/impersonations", fresh))
                        .content(json.writeValueAsString(Map.of("reason", "Set up the first admin"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userAccountId").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID session = UUID.fromString(json.readTree(opened).path("sessionId").asText());
        Set<String> tenantAdminActions = AuthzTestSchema.actionsOfRole(AuthzTestSchema.roleId(fresh, "tenant-admin"));

        String feed = mvc.perform(
                        asStaff(staff, get("/api/v1/navigation")).header("X-Impersonation", session.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode actions = json.readTree(feed).path("actions");
        Set<String> served = new HashSet<>();
        actions.forEach(a -> served.add(a.asText()));
        assertThat(served).containsExactlyInAnyOrderElementsOf(tenantAdminActions);
        assertThat(json.readTree(feed).path("items")).isNotEmpty();
    }

    @Test
    @DisplayName("A write made in a bootstrap session is audited as 'staff as tenant-admin (bootstrap)'")
    void bootstrapWriteIsAuditedUnderBothNames() throws Exception {
        UUID employee = AuthzTestSchema.insertEmployee(fresh, "F-001", "First");
        UUID session = openSession(staff, fresh, Map.of("reason", "Set up the first admin"));
        EmployeeRequest body = new EmployeeRequest(
                "F-001",
                "Renamed",
                null,
                "Employee",
                "MALE",
                LocalDate.of(2026, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                "first@fresh.local",
                null,
                false,
                null,
                null,
                null);

        mvc.perform(asStaff(staff, put("/api/v1/employees/{id}", employee))
                        .header("X-Impersonation", session.toString())
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isOk());

        assertThat(latestAuditActor(fresh, "employee"))
                .first()
                .isEqualTo(staff.email() + " as tenant-admin (bootstrap)");
        assertThat(sessionLifetimeMinutes(session))
                .as("thirty minutes, as stored")
                .isEqualTo(30);
    }

    @Test
    @DisplayName("Once the tenant has a user account, a second session with no target is 400")
    void bootstrapIsRefusedOnceThereIsAUser() throws Exception {
        openSession(staff, fresh, Map.of("reason", "First, while the tenant is empty"));
        AuthzTestSchema.insertMember(fresh, UUID.randomUUID(), "first.admin@fresh.local");

        mvc.perform(asStaff(staff, post("/api/v1/tenants/{id}/impersonations", fresh))
                        .content(json.writeValueAsString(Map.of("reason", "Second, too late"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
