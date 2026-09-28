package com.infinevo.payroll.salary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StatutoryProfileControllerTest {

    private MockMvc mockMvc;
    private EmployeeStatutoryProfileService service;
    private final ObjectMapper mapper = new ObjectMapper();

    private final UUID employeeId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        service = mock(EmployeeStatutoryProfileService.class);
        EmployeeStatutoryProfileController controller = new EmployeeStatutoryProfileController(service);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(mapper);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(converter)
                .build();
    }

    @Test
    @DisplayName("GET /statutory-profile returns 200 with statutory profile")
    void getProfile_returns200() throws Exception {
        StatutoryProfileResponse response = new StatutoryProfileResponse(
                UUID.randomUUID(),
                employeeId,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                "PF12345",
                "100012345678",
                null);

        when(service.get(employeeId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/payroll/employees/{id}/statutory-profile", employeeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleForPf").value(true))
                .andExpect(jsonPath("$.pfAccountNumber").value("PF12345"));
    }

    @Test
    @DisplayName("PUT /statutory-profile updates profile and returns 200")
    void updateProfile_returns200() throws Exception {
        StatutoryProfileRequest request =
                new StatutoryProfileRequest(true, true, true, true, true, false, false, "PF123", "UAN123", "ESI123");

        StatutoryProfileResponse response = new StatutoryProfileResponse(
                UUID.randomUUID(), employeeId, true, true, true, true, true, false, false, "PF123", "UAN123", "ESI123");

        when(service.upsert(eq(employeeId), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/payroll/employees/{id}/statutory-profile", employeeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleForEsi").value(true));
    }
}
