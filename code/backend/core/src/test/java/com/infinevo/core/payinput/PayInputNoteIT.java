package com.infinevo.core.payinput;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * D-140: the Loss of pay screen's free-text reason travels as {@code note}, not as the
 * {@code source_ref} idempotency key. A note is stored and read back with {@code created_by}, the
 * same note twice is not a duplicate, a reversal stores its own reason as its note, and a note over
 * 200 characters is a 400 rather than a column overflow.
 */
@SpringBootTest(classes = PayInputTestApp.class)
@AutoConfigureMockMvc
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            PayInputTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class PayInputNoteIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    private UUID tenant;
    private UUID admin;
    private UUID employee;

    @BeforeEach
    void seed() throws SQLException {
        tenant = PayInputTestSchema.insertTenant("Note " + UUID.randomUUID());
        employee = PayInputTestSchema.insertEmployee(tenant, "NOTE-" + UUID.randomUUID());
        admin = UUID.randomUUID();
        PayInputTestSchema.insertMember(tenant, admin, "tenant-admin");
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Tenant-ID", tenant.toString())
                .with(jwt().jwt(j -> j.subject(admin.toString()).claim("email", admin + "@payinput.test")));
    }

    private String body(String sourceRef, String note) throws Exception {
        return JSON.writeValueAsString(JSON.createObjectNode()
                .put("employee_id", employee.toString())
                .put("period", "2026-04")
                .put("kind", "LOP_DAYS")
                .put("quantity", 1)
                .put("source_ref", sourceRef)
                .put("note", note));
    }

    private JsonNode postEntry(String sourceRef, String note) throws Exception {
        String response = mvc.perform(authed(post("/api/v1/pay-inputs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(sourceRef, note))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JSON.readTree(response);
    }

    @Test
    @DisplayName("A row posted with a note reads back with it and with created_by")
    void noteAndCreatedByReadBack() throws Exception {
        JsonNode created = postEntry("manual-lop:" + UUID.randomUUID(), "Unpaid absence on 14 April");

        mvc.perform(authed(get("/api/v1/pay-inputs")
                        .param("employeeId", employee.toString())
                        .param("period", "2026-04")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].id").value(created.get("id").asText()))
                .andExpect(jsonPath("$.rows[0].note").value("Unpaid absence on 14 April"))
                .andExpect(jsonPath("$.rows[0].created_by").value(admin.toString()));
    }

    @Test
    @DisplayName("The same note on two entries is not a duplicate: only source_ref is the key")
    void sameNoteTwiceIsAccepted() throws Exception {
        postEntry("manual-lop:" + UUID.randomUUID(), "Absent");
        postEntry("manual-lop:" + UUID.randomUUID(), "Absent");
    }

    @Test
    @DisplayName("A reverse with a reason stores it as the reversal row's note, not the original's")
    void reverseReasonIsTheReversalNote() throws Exception {
        JsonNode original = postEntry("manual-lop:" + UUID.randomUUID(), "Entered for the wrong week");

        mvc.perform(authed(post("/api/v1/pay-inputs/" + original.get("id").asText() + "/reverse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Wrong employee\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reverses_id").value(original.get("id").asText()))
                .andExpect(jsonPath("$.note").value("Wrong employee"))
                .andExpect(jsonPath("$.created_by").value(admin.toString()));
    }

    @Test
    @DisplayName("A note over 200 characters is a 400, and nothing is written")
    void overlongNoteIsRefused() throws Exception {
        mvc.perform(authed(post("/api/v1/pay-inputs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("manual-lop:" + UUID.randomUUID(), "x".repeat(201)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(authed(get("/api/v1/pay-inputs")
                        .param("employeeId", employee.toString())
                        .param("period", "2026-04")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isEmpty());
    }
}
