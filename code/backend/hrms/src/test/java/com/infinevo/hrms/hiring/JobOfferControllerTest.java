package com.infinevo.hrms.hiring;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class JobOfferControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private MockMvc mvc;
    private JobOfferService jobOfferService;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);

        jobOfferService = mock(JobOfferService.class);
        JobOfferController controller = new JobOfferController(jobOfferService);

        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST /api/v1/hrms/offers/{id}/convert-to-employee returns 200 with converted employee")
    void convertToEmployee_returns200() throws Exception {
        UUID offerId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();

        ConvertToEmployeeRequest req = new ConvertToEmployeeRequest("EMP-2026-999", "anand@infinevo.com", "MALE");

        EmployeeResponse mockEmp = new EmployeeResponse(
                empId,
                TENANT_ID,
                "EMP-2026-999",
                "Anand",
                null,
                "Kumar",
                "MALE",
                LocalDate.of(2026, 12, 1),
                null,
                EmploymentStatus.ACTIVE,
                "anand@infinevo.com",
                null,
                true,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());

        when(jobOfferService.convertToEmployee(eq(TENANT_ID), eq(offerId), any(ConvertToEmployeeRequest.class), any()))
                .thenReturn(mockEmp);

        mvc.perform(post("/api/v1/hrms/offers/" + offerId + "/convert-to-employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(empId.toString()))
                .andExpect(jsonPath("$.data.employeeNumber").value("EMP-2026-999"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/v1/hrms/offers/{id}/convert-to-employee returns 400 when offer not accepted")
    void convertToEmployee_notAccepted_returns400() throws Exception {
        UUID offerId = UUID.randomUUID();
        ConvertToEmployeeRequest req = new ConvertToEmployeeRequest("EMP-2026-999", null, null);

        when(jobOfferService.convertToEmployee(eq(TENANT_ID), eq(offerId), any(ConvertToEmployeeRequest.class), any()))
                .thenThrow(new HiringValidationException("Only ACCEPTED offers can be converted to employee"));

        mvc.perform(post("/api/v1/hrms/offers/" + offerId + "/convert-to-employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Only ACCEPTED offers can be converted to employee"));
    }
}
