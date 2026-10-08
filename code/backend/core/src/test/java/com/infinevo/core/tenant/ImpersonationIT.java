package com.infinevo.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.authz.AuthzTestSchema;
import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmploymentStatus;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * W-65.2 spec section 7, {@code ImpersonationIT}: staff opens a session as a customer user, and the next
 * requests are that user's — their menu, their permissions, and every write audited under both names.
 *
 * <p>Two users in one customer tenant, an employee and a tenant admin. The same {@code PUT} on an employee is
 * refused while acting as the first and succeeds while acting as the second, and the audit row it leaves names
 * the staff member and the user acted as.
 */
class ImpersonationIT extends ImpersonationItSupport {

    private UUID tenant;
    private UUID employeeRecord;
    private String adminEmail;
    private String employeeEmail;
    private Staff staff;

    @BeforeEach
    void setUp() throws SQLException {
        tenant = AuthzTestSchema.insertTenant("Globex Impersonation " + UUID.randomUUID());
        provisionSubscription(tenant, "HRMS", "PAYROLL");
        adminEmail = "admin@globex-full.local";
        employeeEmail = "emp@globex-full.local";
        UUID adminAccount = AuthzTestSchema.insertMember(tenant, UUID.randomUUID(), adminEmail);
        AuthzTestSchema.grant(tenant, adminAccount, AuthzTestSchema.roleId(tenant, "tenant-admin"));
        UUID employeeAccount = AuthzTestSchema.insertMember(tenant, UUID.randomUUID(), employeeEmail);
        AuthzTestSchema.grant(tenant, employeeAccount, AuthzTestSchema.roleId(tenant, "employee"));
        employeeRecord = AuthzTestSchema.insertEmployee(tenant, "G-001", "Gita");
        staff = newStaff("staff-" + UUID.randomUUID());
    }

    @Test
    @DisplayName(
            "Acting as the employee, the menu is the tenant's employee feed, not the admin's and not the platform's")
    void navigationIsTheTargetUsersFeed() throws Exception {
        UUID asEmployee = openSession(staff, tenant, Map.of("email", employeeEmail, "reason", "Ticket 4411"));
        UUID asAdmin = openSession(staff, tenant, Map.of("email", adminEmail, "reason", "Ticket 4411"));

        List<String> employeeFeed = navigationKeys(asEmployee);
        List<String> adminFeed = navigationKeys(asAdmin);

        assertThat(adminFeed).as("the admin sees more than the employee").containsAll(employeeFeed);
        assertThat(adminFeed).hasSizeGreaterThan(employeeFeed.size());
        assertThat(employeeFeed).doesNotContain("core.roles", "core.audit", "core.tenants");
        assertThat(adminFeed).doesNotContain("core.tenants");
    }

    @Test
    @DisplayName(
            "Acting as the employee, a PUT on an employee is 403 as it would be for them; as the admin it succeeds")
    void writeIsRefusedForTheEmployeeAndAllowedForTheAdmin() throws Exception {
        UUID asEmployee = openSession(staff, tenant, Map.of("email", employeeEmail, "reason", "Ticket 4412"));
        UUID asAdmin = openSession(staff, tenant, Map.of("email", adminEmail, "reason", "Ticket 4412"));

        mvc.perform(update(asEmployee, "Renamed By Employee")).andExpect(status().isForbidden());
        assertThat(latestAuditActor(tenant, "employee"))
                .as("a refused write leaves no audit row")
                .isEmpty();

        mvc.perform(update(asAdmin, "Renamed By Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Renamed By Admin"));

        assertThat(latestAuditActor(tenant, "employee")).first().isEqualTo(staff.email() + " as " + adminEmail);
        assertThat(latestAuditActor(tenant, "employee"))
                .as("and the actor id is the staff member's, never the user acted as")
                .last()
                .isEqualTo(staff.sub().toString());
    }

    @Test
    @DisplayName("A session lasts thirty minutes, as stored")
    void sessionLasts30Minutes() throws Exception {
        UUID session = openSession(staff, tenant, Map.of("email", employeeEmail, "reason", "Ticket 4413"));

        assertThat(sessionLifetimeMinutes(session)).isEqualTo(30);
    }

    private MockHttpServletRequestBuilder update(UUID session, String newFirstName) throws Exception {
        EmployeeRequest body = new EmployeeRequest(
                "G-001",
                newFirstName,
                null,
                "Globex",
                "FEMALE",
                LocalDate.of(2026, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                "gita@globex-full.local",
                null,
                false,
                null,
                null,
                null);
        return asStaff(staff, put("/api/v1/employees/{id}", employeeRecord))
                .header("X-Impersonation", session.toString())
                .content(json.writeValueAsString(body));
    }

    private List<String> navigationKeys(UUID session) throws Exception {
        String response = mvc.perform(asStaff(staff, get("/api/v1/navigation")).header("X-Impersonation", session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> keys = new ArrayList<>();
        collectKeys(json.readTree(response).path("items"), keys);
        return keys;
    }

    /** Every key in the feed, group and child alike (D-34 put roles and audit inside groups). */
    private static void collectKeys(JsonNode items, List<String> into) {
        items.forEach(item -> {
            into.add(item.path("key").asText());
            collectKeys(item.path("children"), into);
        });
    }
}
