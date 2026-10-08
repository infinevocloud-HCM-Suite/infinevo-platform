package com.infinevo.payroll.statutory.settings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller unit tests verifying HTTP status codes, JSON structures and error handling (W-31.1).
 */
class StatutorySettingsControllerTest {

    private StatutorySettingsService settingsService;
    private MockMvc mvc;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        settingsService = mock(StatutorySettingsService.class);
        StatutorySettingsController controller = new StatutorySettingsController(settingsService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter())
                .build();
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/v1/payroll/settings/epf returns 200 with EPF settings")
    void getEpfSuccess() throws Exception {
        EpfSettingResponse response = EpfSettingResponse.defaultSettings(TENANT_ID);
        when(settingsService.epf(TENANT_ID)).thenReturn(response);

        mvc.perform(get("/api/v1/payroll/settings/epf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.data.isEnabled").value(false))
                .andExpect(jsonPath("$.data.source").value("DEFAULT"))
                .andExpect(jsonPath("$.data.employeeRate").value(12.0));
    }

    @Test
    @DisplayName("PUT /api/v1/payroll/settings/epf returns 200 with saved setting")
    void putEpfSuccess() throws Exception {
        EpfSettingResponse response = new EpfSettingResponse(
                UUID.randomUUID(),
                TENANT_ID,
                true,
                "REG-1234",
                null,
                DeductionCycle.MONTHLY,
                new BigDecimal("12.0000"),
                new BigDecimal("12.0000"),
                new BigDecimal("8.3300"),
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000"),
                new BigDecimal("15000.0000"),
                false,
                false,
                false,
                true,
                58,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                SettingSource.PERSISTED);

        when(settingsService.saveEpf(any())).thenReturn(response);

        mvc.perform(
                        put("/api/v1/payroll/settings/epf")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                            "isEnabled": true,
                            "registrationNumber": "REG-1234",
                            "employeeRate": 12.0000,
                            "employerRate": 12.0000,
                            "epsRate": 8.3300,
                            "edliRate": 0.5000,
                            "adminChargeRate": 0.5000,
                            "wageCeiling": 15000.0000
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.isEnabled").value(true))
                .andExpect(jsonPath("$.data.registrationNumber").value("REG-1234"))
                .andExpect(jsonPath("$.data.source").value("PERSISTED"));
    }

    @Test
    @DisplayName("GET /api/v1/payroll/settings/esi returns 200 with ESI settings")
    void getEsiSuccess() throws Exception {
        EsiSettingResponse response = EsiSettingResponse.defaultSettings(TENANT_ID);
        when(settingsService.esi(TENANT_ID)).thenReturn(response);

        mvc.perform(get("/api/v1/payroll/settings/esi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID.toString()))
                .andExpect(jsonPath("$.data.isEnabled").value(false))
                .andExpect(jsonPath("$.data.source").value("DEFAULT"))
                .andExpect(jsonPath("$.data.wageCeiling").value(21000.0));
    }

    @Test
    @DisplayName("PUT /api/v1/payroll/settings/esi returns 200 with saved setting")
    void putEsiSuccess() throws Exception {
        EsiSettingResponse response = new EsiSettingResponse(
                UUID.randomUUID(),
                TENANT_ID,
                true,
                "ESI-REG-1",
                null,
                DeductionCycle.MONTHLY,
                new BigDecimal("0.7500"),
                new BigDecimal("3.2500"),
                new BigDecimal("21000.0000"),
                false,
                false,
                SettingSource.PERSISTED);

        when(settingsService.saveEsi(any())).thenReturn(response);

        mvc.perform(
                        put("/api/v1/payroll/settings/esi")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                            "isEnabled": true,
                            "registrationNumber": "ESI-REG-1",
                            "employeeRate": 0.7500,
                            "employerRate": 3.2500,
                            "wageCeiling": 21000.0000
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.isEnabled").value(true))
                .andExpect(jsonPath("$.data.registrationNumber").value("ESI-REG-1"))
                .andExpect(jsonPath("$.data.source").value("PERSISTED"));
    }

    @Test
    @DisplayName("Validation failure returns 400 with fieldErrors in error envelope")
    void validationErrorReturns400() throws Exception {
        when(settingsService.saveEpf(any()))
                .thenThrow(new StatutorySettingsValidationException("employeeRate", "must be between 0 and 100"));

        mvc.perform(
                        put("/api/v1/payroll/settings/epf")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                        {
                            "employeeRate": 150.0000
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.employeeRate").value("must be between 0 and 100"));
    }
}
