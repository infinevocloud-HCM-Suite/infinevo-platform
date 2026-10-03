package com.infinevo.hrms.attendance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit tests for {@link AttendancePreferenceController} HTTP status codes and error responses (W-40.1).
 */
class AttendancePreferenceControllerTest {

    private MockMvc mvc;
    private AttendancePreferenceService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        service = mock(AttendancePreferenceService.class);
        AttendancePreferenceController controller = new AttendancePreferenceController(service);

        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/hrms/attendance/preferences returns 200 with current preferences")
    void getPreferencesSucceeds() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(service.current()).thenReturn(AttendancePreferenceResponse.defaults(tenantId));

        mvc.perform(get("/api/v1/hrms/attendance/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.hoursCalculation").value("EVERY_SESSION"))
                .andExpect(jsonPath("$.fullDayMinimumHours").value(9.00))
                .andExpect(jsonPath("$.halfDayMinimumHours").value(4.50));

        verify(service).current();
    }

    @Test
    @DisplayName("PUT /api/v1/hrms/attendance/preferences returns 200 with saved response")
    void putPreferencesSucceeds() throws Exception {
        UUID tenantId = UUID.randomUUID();
        AttendancePreferenceRequest request = new AttendancePreferenceRequest(
                HoursCalculation.FIRST_IN_LAST_OUT, BigDecimal.valueOf(8.00), BigDecimal.valueOf(4.00), 7, 5, true);

        AttendancePreferenceResponse response = new AttendancePreferenceResponse(
                UUID.randomUUID(),
                tenantId,
                HoursCalculation.FIRST_IN_LAST_OUT,
                BigDecimal.valueOf(8.00),
                BigDecimal.valueOf(4.00),
                7,
                5,
                true,
                false,
                null,
                null);

        when(service.save(any(AttendancePreferenceRequest.class))).thenReturn(response);

        mvc.perform(put("/api/v1/hrms/attendance/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(false))
                .andExpect(jsonPath("$.hoursCalculation").value("FIRST_IN_LAST_OUT"))
                .andExpect(jsonPath("$.fullDayMinimumHours").value(8.00))
                .andExpect(jsonPath("$.halfDayMinimumHours").value(4.00))
                .andExpect(jsonPath("$.regularizationWindowDays").value(7))
                .andExpect(jsonPath("$.maxRegularizationsPerMonth").value(5))
                .andExpect(jsonPath("$.allowRegularizationWithoutSession").value(true));

        verify(service).save(any(AttendancePreferenceRequest.class));
    }

    @Test
    @DisplayName("Validation failure in service throws IllegalArgumentException and returns 400 VALIDATION_FAILED")
    void validationErrorReturns400() throws Exception {
        when(service.save(any()))
                .thenThrow(new IllegalArgumentException(
                        "halfDayMinimumHours must be strictly less than fullDayMinimumHours"));

        AttendancePreferenceRequest request = new AttendancePreferenceRequest(
                HoursCalculation.EVERY_SESSION, BigDecimal.valueOf(6.00), BigDecimal.valueOf(6.00), null, null, true);

        mvc.perform(put("/api/v1/hrms/attendance/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message")
                        .value("halfDayMinimumHours must be strictly less than fullDayMinimumHours"));
    }

    @Test
    @DisplayName("Malformed JSON body returns 400 VALIDATION_FAILED")
    void malformedBodyReturns400() throws Exception {
        mvc.perform(put("/api/v1/hrms/attendance/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message")
                        .value("The request body could not be read. Check the JSON is well formed."));
    }
}
