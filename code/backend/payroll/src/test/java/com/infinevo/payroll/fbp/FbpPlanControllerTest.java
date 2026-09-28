package com.infinevo.payroll.fbp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
 * Controller unit tests verifying HTTP contracts, JSON structures and status codes for FBP (W-27.1).
 */
class FbpPlanControllerTest {

    private FbpPlanService fbpPlanService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final UUID PLAN_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        fbpPlanService = mock(FbpPlanService.class);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(new FbpPlanController(fbpPlanService))
                .setMessageConverters(converter)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/payroll/fbp/plan returns 200 with plan JSON")
    void getPlanContract() throws Exception {
        FbpPlanResponse response = new FbpPlanResponse(
                PLAN_ID,
                true,
                true,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 15),
                false,
                null,
                true,
                true,
                List.of(5, 1));
        when(fbpPlanService.get()).thenReturn(response);

        mockMvc.perform(get("/api/v1/payroll/fbp/plan").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.id").value(PLAN_ID.toString()))
                .andExpect(jsonPath("$.isEnabled").value(true))
                .andExpect(jsonPath("$.windowOpensOn").value("2026-04-01"))
                .andExpect(jsonPath("$.windowClosesOn").value("2026-04-15"))
                .andExpect(jsonPath("$.reminderDaysBeforeClose[0]").value(5))
                .andExpect(jsonPath("$.reminderDaysBeforeClose[1]").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/payroll/fbp/plan returns 200 with upserted plan")
    void updatePlanContract() throws Exception {
        FbpPlanRequest request = new FbpPlanRequest(
                true, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 20), true, true, List.of(7, 2));

        FbpPlanResponse response = new FbpPlanResponse(
                PLAN_ID,
                true,
                true,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 20),
                false,
                null,
                true,
                true,
                List.of(7, 2));
        when(fbpPlanService.upsert(any(FbpPlanRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/payroll/fbp/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PLAN_ID.toString()))
                .andExpect(jsonPath("$.windowClosesOn").value("2026-04-20"))
                .andExpect(jsonPath("$.reminderDaysBeforeClose[0]").value(7))
                .andExpect(jsonPath("$.reminderDaysBeforeClose[1]").value(2));
    }

    @Test
    @DisplayName("POST /api/v1/payroll/fbp/plan/lock returns 200 with isLocked=true")
    void lockPlanContract() throws Exception {
        Instant now = Instant.now();
        FbpPlanResponse response = new FbpPlanResponse(
                PLAN_ID,
                true,
                true,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 20),
                true,
                now,
                true,
                true,
                List.of(5, 1));
        when(fbpPlanService.lock()).thenReturn(response);

        mockMvc.perform(post("/api/v1/payroll/fbp/plan/lock").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isLocked").value(true))
                .andExpect(jsonPath("$.lockedAt").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/payroll/fbp/plan/unlock returns 200 with isLocked=false")
    void unlockPlanContract() throws Exception {
        FbpPlanResponse response = new FbpPlanResponse(
                PLAN_ID,
                true,
                true,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 20),
                false,
                null,
                true,
                true,
                List.of(5, 1));
        when(fbpPlanService.unlock()).thenReturn(response);

        mockMvc.perform(post("/api/v1/payroll/fbp/plan/unlock").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isLocked").value(false))
                .andExpect(jsonPath("$.lockedAt").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/payroll/fbp/components returns 200 with components list")
    void getComponentsContract() throws Exception {
        UUID compId = UUID.randomUUID();
        List<FbpComponentResponse> components = List.of(
                new FbpComponentResponse("EARNING", compId, "FUEL", "Fuel Allowance", new BigDecimal("2400.0000")));
        when(fbpPlanService.components()).thenReturn(components);

        mockMvc.perform(get("/api/v1/payroll/fbp/components").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("EARNING"))
                .andExpect(jsonPath("$[0].code").value("FUEL"))
                .andExpect(jsonPath("$[0].name").value("Fuel Allowance"))
                .andExpect(jsonPath("$[0].maxLimit").value(2400.0));
    }

    @Test
    @DisplayName("PUT /api/v1/payroll/fbp/plan with invalid dates returns 400 and validation envelope")
    void validationErrorHandling() throws Exception {
        FbpPlanRequest request = new FbpPlanRequest(
                true, LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 10), true, true, List.of(5, 1));
        when(fbpPlanService.upsert(any(FbpPlanRequest.class)))
                .thenThrow(new FbpValidationException(
                        Map.of("windowClosesOn", "Window close date must be on or after window open date")));

        mockMvc.perform(put("/api/v1/payroll/fbp/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.windowClosesOn")
                        .value("Window close date must be on or after window open date"));
    }
}
