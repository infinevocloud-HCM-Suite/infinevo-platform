package com.infinevo.hrms.exit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.hrms.project.HrmsProjectTestSchema;
import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Integration test for W-103: resignation submission, LWD approval, 4-department clearances,
 * READY_FOR_FNF transition, and Payroll hand-off.
 */
@SpringBootTest(classes = HrmsTestApp.class)
@ContextConfiguration(initializers = HrmsProjectTestSchema.Initializer.class)
@EnabledIfDockerAvailable
class ExitProcessLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    private ExitService exitService;

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private MockMvc mvc;
    private UUID tenantId;
    private UUID employeeId;

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        HrmsTestApp.CURRENT_EMPLOYEE.remove();
        tenantId = HrmsProjectTestSchema.insertTenant("Exit Process Corp");
        employeeId = HrmsProjectTestSchema.insertEmployee(tenantId, "EMP-EXIT-01");

        EmployeeResponse emp = new EmployeeResponse(
                employeeId,
                tenantId,
                "EMP-EXIT-01",
                "Rohan",
                null,
                "Deshmukh",
                "MALE",
                LocalDate.of(2023, 3, 1),
                null,
                EmploymentStatus.ACTIVE,
                "rohan@example.com",
                null,
                true,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
        HrmsTestApp.CURRENT_EMPLOYEE.set(emp);

        mvc = MockMvcBuilders.standaloneSetup(new ExitController(exitService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @AfterEach
    void tearDown() throws SQLException {
        HrmsTestApp.CURRENT_EMPLOYEE.remove();
        TenantContext.clear();
        try (Connection conn = HrmsProjectTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM hrms.exit_request WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("Full exit lifecycle: submit -> approve LWD -> clear 4 depts -> READY_FOR_FNF -> hand off")
    void fullExitLifecycle() throws Exception {
        TenantContext.set(tenantId);

        String submitBody = mvc.perform(post("/api/v1/me/exit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new SubmitResignationRequest(
                                LocalDate.of(2026, 10, 1),
                                LocalDate.of(2026, 10, 31),
                                "RESIGNATION",
                                "PERSONAL",
                                "Relocating"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID exitId = UUID.fromString(mapper.readTree(submitBody).get("id").asText());

        // Approve LWD -> generates 4 departments (8 items)
        mvc.perform(post("/api/v1/hrms/exits/{id}/approve", exitId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new ApproveExitRequest(LocalDate.of(2026, 10, 31)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLEARANCE_IN_PROGRESS"))
                .andExpect(jsonPath("$.clearanceItems.length()").value(8));

        // Clear IT, FINANCE, ADMIN -> still CLEARANCE_IN_PROGRESS
        for (ClearanceDepartment dept : new ClearanceDepartment[] {
            ClearanceDepartment.IT, ClearanceDepartment.FINANCE, ClearanceDepartment.ADMIN
        }) {
            mvc.perform(put("/api/v1/hrms/exits/{id}/clearance/{dept}", exitId, dept)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(
                                    new UpdateClearanceRequest(ClearanceItemStatus.CLEARED, "Verified"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLEARANCE_IN_PROGRESS"));
        }

        // Clear HR -> moves to READY_FOR_FNF
        mvc.perform(put("/api/v1/hrms/exits/{id}/clearance/{dept}", exitId, ClearanceDepartment.HR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new UpdateClearanceRequest(ClearanceItemStatus.CLEARED, "KT complete"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_FNF"));

        // Hand off to Payroll -> transitions to SETTLED
        String handOffBody = mvc.perform(post("/api/v1/hrms/exits/{id}/hand-off-to-payroll", exitId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(employeeId.toString()))
                .andExpect(jsonPath("$.exitStatus").value("SETTLED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode handOffJson = mapper.readTree(handOffBody);
        assertThat(handOffJson.get("workingDaysInExitMonth").asInt()).isGreaterThan(0);

        mvc.perform(get("/api/v1/hrms/exits/{id}", exitId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));
    }
}
