package com.infinevo.core.payinput;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * W-19 §7 — {@code write}, {@code read} and {@code lock} are three codes, not one: a caller holding
 * only {@code core.pay_input.read} must be refused on the write and lock endpoints.
 *
 * <p>No system role holds exactly {@code read} alone ({@code V025}: only {@code tenant-admin} holds
 * all three), so the read-only subject here is granted a role built for this test
 * ({@code PayInputTestSchema.insertMemberWithActions}), not a seeded one.
 */
@SpringBootTest(classes = PayInputTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            PayInputTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class PayInputGuardIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private UUID tenant;
    private UUID admin;
    private UUID readOnly;
    private UUID employee;

    @BeforeEach
    void seed() throws SQLException {
        tenant = PayInputTestSchema.insertTenant("Guard " + UUID.randomUUID());
        employee = PayInputTestSchema.insertEmployee(tenant, "GUARD-" + UUID.randomUUID());
        admin = UUID.randomUUID();
        readOnly = UUID.randomUUID();
        PayInputTestSchema.insertMember(tenant, admin, "tenant-admin");
        PayInputTestSchema.insertMemberWithActions(tenant, readOnly, "core.pay_input.read");
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, UUID sub) {
        return builder.header("X-Tenant-ID", tenant.toString())
                .with(jwt().jwt(j -> j.subject(sub.toString()).claim("email", sub + "@payinput.test")));
    }

    @Test
    @DisplayName("GET /api/v1/pay-inputs: 200 for read-only and for tenant-admin")
    void getIsAllowedForReadAndForAdmin() throws Exception {
        mvc.perform(authed(get("/api/v1/pay-inputs").param("period", "2026-04"), readOnly))
                .andExpect(status().isOk());
        mvc.perform(authed(get("/api/v1/pay-inputs").param("period", "2026-04"), admin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/pay-inputs: 403 for read-only, 201 for tenant-admin")
    void postIsRefusedForReadOnly() throws Exception {
        String body =
                """
            {
              "employee_id": "%s",
              "period": "2026-04",
              "kind": "LOP_DAYS",
              "quantity": 1,
              "source_ref": "guard-1"
            }
            """
                        .formatted(employee);

        mvc.perform(authed(
                        post("/api/v1/pay-inputs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body),
                        readOnly))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message", containsString("core.pay_input.write")));

        mvc.perform(authed(
                        post("/api/v1/pay-inputs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body),
                        admin))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/v1/pay-inputs/periods/{period}/lock: 403 for read-only, 200 for tenant-admin")
    void lockIsRefusedForReadOnly() throws Exception {
        mvc.perform(authed(post("/api/v1/pay-inputs/periods/2026-04/lock"), readOnly))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message", containsString("core.pay_input.lock")));

        mvc.perform(authed(post("/api/v1/pay-inputs/periods/2026-05/lock"), admin))
                .andExpect(status().isOk());
    }
}
