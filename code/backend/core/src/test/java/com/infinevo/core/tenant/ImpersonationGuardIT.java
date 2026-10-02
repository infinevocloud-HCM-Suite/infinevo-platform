package com.infinevo.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.core.authz.AuthzTestSchema;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-65.2 spec section 7, {@code ImpersonationGuardIT}: who may open a session, and when a session stops being
 * good. Every refusal here is the real filter, the real permission check and the real database functions.
 */
class ImpersonationGuardIT extends ImpersonationItSupport {

    private static final String HEADER = "X-Impersonation";

    private UUID customer;
    private UUID customerAdminSub;
    private UUID customerUserAccount;
    private String customerEmail;
    private Staff staff;

    @BeforeEach
    void setUp() throws SQLException {
        customer = AuthzTestSchema.insertTenant("Acme Guard " + UUID.randomUUID());
        customerAdminSub = UUID.randomUUID();
        customerEmail = "admin@acme.impersonation.test";
        customerUserAccount = AuthzTestSchema.insertMember(customer, customerAdminSub, customerEmail);
        AuthzTestSchema.grant(customer, customerUserAccount, AuthzTestSchema.roleId(customer, "tenant-admin"));
        staff = newStaff("staff-" + UUID.randomUUID());
    }

    private Map<String, Object> asCustomerUser() {
        return Map.of("email", customerEmail, "reason", "Guard test");
    }

    @Test
    @DisplayName("A customer's tenant admin, who lacks core.tenant.impersonate, gets 403 on POST")
    void customerAdminCannotOpenASession() throws Exception {
        mvc.perform(asCustomer(
                                customer,
                                customerAdminSub,
                                customerEmail,
                                post("/api/v1/tenants/{id}/impersonations", customer))
                        .content(json.writeValueAsString(asCustomerUser())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName(
            "A customer role cannot carry the impersonate action at all: the grant is dropped, and POST and DELETE are 403")
    void customerRoleNeverHoldsTheAction() throws Exception {
        // V083's trigger drops the row, so the role ends up with no action rather than the platform-only one.
        UUID role =
                AuthzTestSchema.insertRole(customer, "custom-impersonator", "Impersonator", "core.tenant.impersonate");
        AuthzTestSchema.grant(customer, customerUserAccount, role);
        assertThat(AuthzTestSchema.actionsOfRole(role))
                .as("the grant never landed")
                .isEmpty();

        mvc.perform(asCustomer(
                                customer,
                                customerAdminSub,
                                customerEmail,
                                post("/api/v1/tenants/{id}/impersonations", customer))
                        .content(json.writeValueAsString(asCustomerUser())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(asCustomer(
                        customer,
                        customerAdminSub,
                        customerEmail,
                        delete("/api/v1/impersonations/{id}", UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("An expired session is 403 IMPERSONATION_INVALID")
    void expiredSessionIsRefused() throws Exception {
        UUID session = openSession(staff, customer, asCustomerUser());
        mvc.perform(asStaff(staff, get("/api/v1/navigation")).header(HEADER, session.toString()))
                .andExpect(status().isOk());

        expireSession(session);

        mvc.perform(asStaff(staff, get("/api/v1/navigation")).header(HEADER, session.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IMPERSONATION_INVALID"));
    }

    @Test
    @DisplayName("A session opened by staff A and used by staff B is 403")
    void anotherStaffMemberCannotUseTheSession() throws Exception {
        Staff other = newStaff("other-" + UUID.randomUUID());
        UUID session = openSession(staff, customer, asCustomerUser());

        mvc.perform(asStaff(other, get("/api/v1/navigation")).header(HEADER, session.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IMPERSONATION_INVALID"));
    }

    @Test
    @DisplayName("DELETE ends the session; using it afterwards is 403, and ending it twice is 404")
    void endedSessionCannotBeReused() throws Exception {
        UUID session = openSession(staff, customer, asCustomerUser());

        mvc.perform(asStaff(staff, delete("/api/v1/impersonations/{id}", session)))
                .andExpect(status().isNoContent());
        mvc.perform(asStaff(staff, get("/api/v1/navigation")).header(HEADER, session.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IMPERSONATION_INVALID"));
        mvc.perform(asStaff(staff, delete("/api/v1/impersonations/{id}", session)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Another staff member cannot end a session they did not open")
    void anotherStaffMemberCannotEndTheSession() throws Exception {
        Staff other = newStaff("other-" + UUID.randomUUID());
        UUID session = openSession(staff, customer, asCustomerUser());

        mvc.perform(asStaff(other, delete("/api/v1/impersonations/{id}", session)))
                .andExpect(status().isNotFound());
        mvc.perform(asStaff(staff, get("/api/v1/navigation")).header(HEADER, session.toString()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("X-Impersonation together with X-Tenant-Id is 400")
    void bothHeadersAreRefused() throws Exception {
        UUID session = openSession(staff, customer, asCustomerUser());

        mvc.perform(asStaff(staff, get("/api/v1/navigation"))
                        .header(HEADER, session.toString())
                        .header("X-Tenant-Id", customer.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Nobody opens a session on the platform tenant, a missing tenant, or without a reason")
    void badRequestsAreClearRefusals() throws Exception {
        mvc.perform(asStaff(staff, post("/api/v1/tenants/{id}/impersonations", PLATFORM))
                        .content(json.writeValueAsString(Map.of("email", customerEmail, "reason", "x"))))
                .andExpect(status().isBadRequest());
        mvc.perform(asStaff(staff, post("/api/v1/tenants/{id}/impersonations", UUID.randomUUID()))
                        .content(json.writeValueAsString(asCustomerUser())))
                .andExpect(status().isNotFound());
        mvc.perform(asStaff(staff, post("/api/v1/tenants/{id}/impersonations", customer))
                        .content(json.writeValueAsString(Map.of("email", customerEmail, "reason", "  "))))
                .andExpect(status().isBadRequest());
        mvc.perform(asStaff(staff, post("/api/v1/tenants/{id}/impersonations", customer))
                        .content(json.writeValueAsString(
                                Map.of("email", "nobody@acme.impersonation.test", "reason", "x"))))
                .andExpect(status().isNotFound());
    }
}
