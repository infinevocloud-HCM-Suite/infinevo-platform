package com.infinevo.payroll.statutory.pt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller unit tests for professional tax settings endpoints (W-31.2).
 */
class ProfessionalTaxControllerTest {

    private ProfessionalTaxService service;
    private MockMvc mvc;
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = mock(ProfessionalTaxService.class);
        ProfessionalTaxController controller = new ProfessionalTaxController(service);
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
    @DisplayName("GET /api/v1/payroll/settings/professional-tax returns 200 with all tenant states in envelope")
    void getAllStatesSuccess() throws Exception {
        PtStateResponse state = new PtStateResponse(
                "KA",
                "Karnataka",
                PtSource.REFERENCE,
                null,
                null,
                List.of(new PtSlabDto(BigDecimal.ZERO, null, new BigDecimal("200.0000"), false, null)));
        when(service.statesForTenant()).thenReturn(List.of(state));

        mvc.perform(get("/api/v1/payroll/settings/professional-tax"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.data[0].state_code").value("KA"))
                .andExpect(jsonPath("$.data[0].source").value("REFERENCE"));
    }

    @Test
    @DisplayName("GET /api/v1/payroll/settings/professional-tax/{stateCode} returns 200 for active state")
    void getStateSuccess() throws Exception {
        PtStateResponse state = new PtStateResponse(
                "KA",
                "Karnataka",
                PtSource.REFERENCE,
                null,
                null,
                List.of(new PtSlabDto(BigDecimal.ZERO, null, new BigDecimal("200.0000"), false, null)));
        when(service.getStateForTenant("KA")).thenReturn(state);

        mvc.perform(get("/api/v1/payroll/settings/professional-tax/KA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.state_code").value("KA"));
    }

    @Test
    @DisplayName(
            "GET /api/v1/payroll/settings/professional-tax/{stateCode} returns 404 for state without active work location")
    void getStateNotFound() throws Exception {
        when(service.getStateForTenant("MH"))
                .thenThrow(new PtNotFoundException("No active work location found in state MH"));

        mvc.perform(get("/api/v1/payroll/settings/professional-tax/MH"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No active work location found in state MH"));
    }

    @Test
    @DisplayName("PUT /api/v1/payroll/settings/professional-tax/{stateCode} returns 200 with source=OVERRIDE")
    void putOverrideSuccess() throws Exception {
        PtStateResponse response = new PtStateResponse(
                "KA",
                "Karnataka",
                PtSource.OVERRIDE,
                "REG-999",
                LocalDate.of(2024, 4, 1),
                List.of(new PtSlabDto(BigDecimal.ZERO, null, new BigDecimal("250.0000"), false, null)));
        when(service.setOverride(eq("KA"), any(PtOverrideRequest.class))).thenReturn(response);

        String json =
                """
                {
                    "registration_number": "REG-999",
                    "effective_from": "2024-04-01",
                    "slabs": [
                        {
                            "from_amount": 0,
                            "to_amount": null,
                            "amount": 250,
                            "is_female_exempt": false,
                            "deduction_months": null
                        }
                    ]
                }
                """;

        mvc.perform(put("/api/v1/payroll/settings/professional-tax/KA")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.source").value("OVERRIDE"))
                .andExpect(jsonPath("$.data.registration_number").value("REG-999"));
    }

    @Test
    @DisplayName("DELETE /api/v1/payroll/settings/professional-tax/{stateCode}/override returns 204 No Content")
    void deleteOverrideSuccess() throws Exception {
        doNothing().when(service).resetOverride("KA");

        mvc.perform(delete("/api/v1/payroll/settings/professional-tax/KA/override"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/v1/payroll/settings/professional-tax/{stateCode}/history returns 200 with history rows")
    void getHistorySuccess() throws Exception {
        PtHistoryResponse history = new PtHistoryResponse(
                UUID.randomUUID(),
                "KA",
                PtHistoryOperation.OVERRIDE_SET,
                List.of(),
                List.of(new PtSlabDto(BigDecimal.ZERO, null, new BigDecimal("250.0000"), false, null)),
                Instant.now(),
                UUID.randomUUID());
        when(service.getHistory("KA")).thenReturn(List.of(history));

        mvc.perform(get("/api/v1/payroll/settings/professional-tax/KA/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].operation").value("OVERRIDE_SET"));
    }
}
