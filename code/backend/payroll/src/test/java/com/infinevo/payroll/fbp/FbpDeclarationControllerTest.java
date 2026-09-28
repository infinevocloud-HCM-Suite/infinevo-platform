package com.infinevo.payroll.fbp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.payroll.salary.SalaryConflictException;
import com.infinevo.payroll.salary.SalaryValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller unit tests verifying HTTP contracts, JSON structures and status codes for FBP declarations (W-27.2).
 */
class FbpDeclarationControllerTest {

    private FbpDeclarationService fbpDeclarationService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final UUID CTC_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID COMP_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        fbpDeclarationService = mock(FbpDeclarationService.class);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(new FbpDeclarationController(fbpDeclarationService))
                .setMessageConverters(converter)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/me/fbp-declaration returns 200 with declaration JSON")
    void readOwnContract() throws Exception {
        FbpDeclarationResponse response = new FbpDeclarationResponse(
                CTC_ID,
                EMPLOYEE_ID,
                true,
                Instant.now(),
                "EMPLOYEE",
                new FbpSummaryResponse(
                        new BigDecimal("48000.00"), new BigDecimal("36000.00"), new BigDecimal("12000.00")),
                List.of(new FbpDeclarationLineResponse(
                        "EARNING",
                        COMP_ID,
                        "FUEL",
                        "Fuel Allowance",
                        new BigDecimal("48000.0000"),
                        new BigDecimal("36000.0000"),
                        new BigDecimal("3000.0000"))));

        when(fbpDeclarationService.readOwn()).thenReturn(response);

        mockMvc.perform(get("/api/v1/me/fbp-declaration").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ctcStructureId").value(CTC_ID.toString()))
                .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_ID.toString()))
                .andExpect(jsonPath("$.windowOpen").value(true))
                .andExpect(jsonPath("$.summary.poolAnnual").value(48000.00))
                .andExpect(jsonPath("$.summary.declaredAnnual").value(36000.00))
                .andExpect(jsonPath("$.summary.unallocatedAnnual").value(12000.00))
                .andExpect(jsonPath("$.lines[0].kind").value("EARNING"))
                .andExpect(jsonPath("$.lines[0].componentCode").value("FUEL"))
                .andExpect(jsonPath("$.lines[0].declaredAnnualAmount").value(36000.0));
    }

    @Test
    @DisplayName("PUT /api/v1/me/fbp-declaration returns 200 on successful submission")
    void declareOwnContract() throws Exception {
        FbpDeclarationRequest request = new FbpDeclarationRequest(
                List.of(new FbpDeclarationLineRequest("EARNING", COMP_ID, new BigDecimal("36000.0000"))));

        FbpDeclarationResponse response = new FbpDeclarationResponse(
                CTC_ID,
                EMPLOYEE_ID,
                true,
                Instant.now(),
                "EMPLOYEE",
                new FbpSummaryResponse(
                        new BigDecimal("48000.00"), new BigDecimal("36000.00"), new BigDecimal("12000.00")),
                List.of(new FbpDeclarationLineResponse(
                        "EARNING",
                        COMP_ID,
                        "FUEL",
                        "Fuel Allowance",
                        new BigDecimal("48000.0000"),
                        new BigDecimal("36000.0000"),
                        new BigDecimal("3000.0000"))));

        when(fbpDeclarationService.declareOwn(any(FbpDeclarationRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/me/fbp-declaration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declaredBy").value("EMPLOYEE"))
                .andExpect(jsonPath("$.lines[0].declaredAnnualAmount").value(36000.0));
    }

    @Test
    @DisplayName("PUT /api/v1/me/fbp-declaration when window is closed returns 409 WINDOW_CLOSED")
    void declareOwnWindowClosedReturns409() throws Exception {
        FbpDeclarationRequest request = new FbpDeclarationRequest(
                List.of(new FbpDeclarationLineRequest("EARNING", COMP_ID, new BigDecimal("36000.0000"))));

        when(fbpDeclarationService.declareOwn(any(FbpDeclarationRequest.class)))
                .thenThrow(new SalaryConflictException("WINDOW_CLOSED: FBP declaration window is closed"));

        mockMvc.perform(put("/api/v1/me/fbp-declaration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WINDOW_CLOSED"))
                .andExpect(jsonPath("$.message").value("WINDOW_CLOSED: FBP declaration window is closed"));
    }

    @Test
    @DisplayName("GET /api/v1/payroll/employees/{id}/fbp-declaration?asOf= returns 200")
    void officerReadContract() throws Exception {
        FbpDeclarationResponse response = new FbpDeclarationResponse(
                CTC_ID,
                EMPLOYEE_ID,
                false,
                Instant.now(),
                "OFFICER",
                new FbpSummaryResponse(new BigDecimal("48000.00"), new BigDecimal("48000.00"), BigDecimal.ZERO),
                List.of());

        when(fbpDeclarationService.read(any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/payroll/employees/" + EMPLOYEE_ID + "/fbp-declaration?asOf=2026-05-01")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declaredBy").value("OFFICER"))
                .andExpect(jsonPath("$.windowOpen").value(false));
    }

    @Test
    @DisplayName("PUT /api/v1/payroll/employees/{id}/fbp-declaration returns 200 for officer")
    void officerSetContract() throws Exception {
        FbpDeclarationRequest request = new FbpDeclarationRequest(
                List.of(new FbpDeclarationLineRequest("EARNING", COMP_ID, new BigDecimal("24000.0000"))));

        FbpDeclarationResponse response = new FbpDeclarationResponse(
                CTC_ID,
                EMPLOYEE_ID,
                false,
                Instant.now(),
                "OFFICER",
                new FbpSummaryResponse(
                        new BigDecimal("48000.00"), new BigDecimal("24000.00"), new BigDecimal("24000.00")),
                List.of());

        when(fbpDeclarationService.set(any(), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/payroll/employees/" + EMPLOYEE_ID + "/fbp-declaration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declaredBy").value("OFFICER"));
    }

    @Test
    @DisplayName("PUT /api/v1/me/fbp-declaration with invalid line returns 400 and validation envelope")
    void validationErrorReturns400() throws Exception {
        FbpDeclarationRequest request = new FbpDeclarationRequest(
                List.of(new FbpDeclarationLineRequest("EARNING", COMP_ID, new BigDecimal("-100.0000"))));

        when(fbpDeclarationService.declareOwn(any(FbpDeclarationRequest.class)))
                .thenThrow(new SalaryValidationException(Map.of("annualAmount", "Annual amount must be non-negative")));

        mockMvc.perform(put("/api/v1/me/fbp-declaration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.annualAmount").value("Annual amount must be non-negative"));
    }
}
