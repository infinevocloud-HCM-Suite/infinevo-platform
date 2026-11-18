package com.infinevo.payroll.statutory.guided;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.payroll.statutory.settings.DeductionCycle;
import com.infinevo.payroll.statutory.settings.EpfSettingResponse;
import com.infinevo.payroll.statutory.settings.EsiSettingResponse;
import com.infinevo.payroll.statutory.settings.SettingSource;
import com.infinevo.payroll.statutory.settings.StatutorySettingsValidationException;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GuidedStatutorySetupControllerTest {

    private GuidedStatutorySetupService setupService;
    private MockMvc mvc;
    private ObjectMapper objectMapper;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        setupService = mock(GuidedStatutorySetupService.class);
        GuidedStatutorySetupController controller = new GuidedStatutorySetupController(setupService);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/v1/payroll/settings/guided-setup returns 200 with readiness data")
    void getReadinessSuccess() throws Exception {
        GuidedStatutoryReadinessResponse response = new GuidedStatutoryReadinessResponse(
                false,
                new EpfReadiness(false, null, false, false),
                false,
                new EsiReadiness(false, null, false),
                List.of(new PtStateReadiness("MH", "Maharashtra", true, null, false)),
                StatutoryDefaults.standard());

        when(setupService.getReadiness()).thenReturn(response);

        mvc.perform(get("/api/v1/payroll/settings/guided-setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Statutory readiness retrieved successfully"))
                .andExpect(jsonPath("$.data.epfConfigured").value(false))
                .andExpect(jsonPath("$.data.operatingStates[0].stateCode").value("MH"))
                .andExpect(jsonPath("$.data.defaults.epfEmployeeRate").value(12.0));

        verify(setupService).getReadiness();
    }

    @Test
    @DisplayName("POST /api/v1/payroll/settings/guided-setup returns 200 with applied setup")
    void applySetupSuccess() throws Exception {
        EpfSettingResponse epfMock = new EpfSettingResponse(
                UUID.randomUUID(),
                TENANT_ID,
                true,
                "DLCPM1234567000",
                LocalDate.of(2026, 4, 1),
                DeductionCycle.MONTHLY,
                new BigDecimal("12.0000"),
                new BigDecimal("12.0000"),
                new BigDecimal("8.3300"),
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                new BigDecimal("15000.0000"),
                true,
                true,
                true,
                true,
                58,
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                SettingSource.PERSISTED);

        EsiSettingResponse esiMock = new EsiSettingResponse(
                UUID.randomUUID(),
                TENANT_ID,
                true,
                "31000123450000101",
                LocalDate.of(2026, 4, 1),
                DeductionCycle.MONTHLY,
                new BigDecimal("0.7500"),
                new BigDecimal("3.2500"),
                new BigDecimal("21000.0000"),
                true,
                true,
                SettingSource.PERSISTED);

        GuidedStatutorySetupResponse setupResponse = new GuidedStatutorySetupResponse(
                epfMock, esiMock, List.of(new PtStateResult("MH", "Maharashtra", "27123456789P", "OVERRIDE")), true);

        when(setupService.applySetup(any(GuidedStatutorySetupRequest.class))).thenReturn(setupResponse);

        GuidedStatutorySetupRequest request = new GuidedStatutorySetupRequest(
                new EpfSetupRequest(true, "DLCPM1234567000", LocalDate.of(2026, 4, 1), true, true, false, false),
                new EsiSetupRequest(true, "31000123450000101", LocalDate.of(2026, 4, 1), true),
                List.of(new PtStateSetupRequest("MH", "27123456789P", LocalDate.of(2026, 4, 1))));

        mvc.perform(post("/api/v1/payroll/settings/guided-setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("Guided statutory setup applied successfully"))
                .andExpect(jsonPath("$.data.allStatutoryConfigured").value(true))
                .andExpect(jsonPath("$.data.epf.registrationNumber").value("DLCPM1234567000"))
                .andExpect(jsonPath("$.data.ptStates[0].stateCode").value("MH"));

        verify(setupService).applySetup(any(GuidedStatutorySetupRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/payroll/settings/guided-setup returns 400 on validation failure")
    void applySetupValidationFailure() throws Exception {
        when(setupService.applySetup(any(GuidedStatutorySetupRequest.class)))
                .thenThrow(new StatutorySettingsValidationException(
                        "epf.registrationNumber", "Registration number is required when EPF is registered"));

        GuidedStatutorySetupRequest request = new GuidedStatutorySetupRequest(
                new EpfSetupRequest(true, "", null, true, true, false, false),
                new EsiSetupRequest(false, null, null, false),
                List.of());

        mvc.perform(post("/api/v1/payroll/settings/guided-setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors['epf.registrationNumber']")
                        .value("Registration number is required when EPF is registered"));
    }
}
