package com.infinevo.payroll.statutory.lwf;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.payroll.payrun.PayFigureNotComputedException;
import com.infinevo.payroll.payrun.PayRunNotFoundException;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LwfControllerTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PAYRUN_ID = UUID.randomUUID();

    private LwfService lwfService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        lwfService = mock(LwfService.class);
        LwfController controller = new LwfController(lwfService);

        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(
                        new ByteArrayHttpMessageConverter(), new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/v1/payroll/settings/lwf returns 200 with list of state settings")
    void listSettings_success() throws Exception {
        LwfStateSettingResponse mh = new LwfStateSettingResponse(
                "MH",
                "Maharashtra",
                true,
                true,
                "HALF_YEARLY_JUNE_DECEMBER",
                List.of(6, 12),
                "FLAT",
                new BigDecimal("25.00"),
                new BigDecimal("75.00"),
                null,
                null,
                null,
                null,
                "MH-LWF-123",
                2);

        when(lwfService.listSettings(TENANT_ID)).thenReturn(List.of(mh));

        mvc.perform(get("/api/v1/payroll/settings/lwf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].stateCode").value("MH"))
                .andExpect(jsonPath("$.data[0].stateName").value("Maharashtra"))
                .andExpect(jsonPath("$.data[0].deductionCycle").value("HALF_YEARLY_JUNE_DECEMBER"))
                .andExpect(jsonPath("$.data[0].registrationNumber").value("MH-LWF-123"))
                .andExpect(jsonPath("$.data[0].activeLocationsCount").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/payroll/settings/lwf/{stateCode} returns 200 with single state setting")
    void getStateSetting_success() throws Exception {
        LwfStateSettingResponse hr = new LwfStateSettingResponse(
                "HR",
                "Haryana",
                true,
                true,
                "MONTHLY",
                List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
                "PERCENTAGE_WITH_CAP",
                null,
                null,
                new BigDecimal("0.0020"),
                new BigDecimal("0.0040"),
                new BigDecimal("25.00"),
                new BigDecimal("50.00"),
                "HR-REG-999",
                1);

        when(lwfService.getStateSetting(TENANT_ID, "HR")).thenReturn(hr);

        mvc.perform(get("/api/v1/payroll/settings/lwf/HR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.stateCode").value("HR"))
                .andExpect(jsonPath("$.data.calcMode").value("PERCENTAGE_WITH_CAP"))
                .andExpect(jsonPath("$.data.employeeCap").value(25.0));
    }

    @Test
    @DisplayName("PUT /api/v1/payroll/settings/lwf/{stateCode} updates registration number and status")
    void updateSetting_success() throws Exception {
        LwfStateSettingResponse updated = new LwfStateSettingResponse(
                "MH",
                "Maharashtra",
                true,
                true,
                "HALF_YEARLY_JUNE_DECEMBER",
                List.of(6, 12),
                "FLAT",
                new BigDecimal("25.00"),
                new BigDecimal("75.00"),
                null,
                null,
                null,
                null,
                "MH-NEW-REG",
                2);

        when(lwfService.updateSetting(eq(TENANT_ID), eq("MH"), any(), any())).thenReturn(updated);

        mvc.perform(put("/api/v1/payroll/settings/lwf/MH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isEnabled\": true, \"registrationNumber\": \"MH-NEW-REG\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.registrationNumber").value("MH-NEW-REG"));
    }

    @Test
    @DisplayName("GET /export returns 200 with XLSX file attachment")
    void exportReturn_xlsx_success() throws Exception {
        byte[] fakeContent = "fake-xlsx-content".getBytes(StandardCharsets.UTF_8);
        when(lwfService.exportReturnFile(eq(TENANT_ID), eq(PAYRUN_ID), eq("XLSX")))
                .thenReturn(fakeContent);

        mvc.perform(get("/api/v1/payroll/payruns/{id}/statutory/lwf/export?format=XLSX", PAYRUN_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"lwf-return-" + PAYRUN_ID + ".xlsx\""))
                .andExpect(content().bytes(fakeContent));
    }

    @Test
    @DisplayName("GET /reports/lwf returns 200 with CSV file attachment")
    void exportReturn_csv_success() throws Exception {
        byte[] fakeCsv = "Employee Number,Employee Name\r\n".getBytes(StandardCharsets.UTF_8);
        when(lwfService.exportReturnFile(eq(TENANT_ID), eq(PAYRUN_ID), eq("CSV")))
                .thenReturn(fakeCsv);

        mvc.perform(get("/api/v1/payroll/payruns/{id}/reports/lwf?format=CSV", PAYRUN_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"lwf-return-" + PAYRUN_ID + ".csv\""))
                .andExpect(content().bytes(fakeCsv));
    }

    @Test
    @DisplayName("Off-cycle run export throws 400 Bad Request")
    void exportReturn_offCycle_returns400() throws Exception {
        when(lwfService.exportReturnFile(eq(TENANT_ID), eq(PAYRUN_ID), eq("XLSX")))
                .thenThrow(new IllegalArgumentException(
                        "LWF return statements apply only to regular pay runs: " + PAYRUN_ID));

        mvc.perform(get("/api/v1/payroll/payruns/{id}/statutory/lwf/export?format=XLSX", PAYRUN_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("LWF return statements apply only to regular pay runs: " + PAYRUN_ID));
    }

    @Test
    @DisplayName("Pay run not found throws 404 Not Found")
    void exportReturn_notFound_returns404() throws Exception {
        when(lwfService.exportReturnFile(eq(TENANT_ID), eq(PAYRUN_ID), eq("XLSX")))
                .thenThrow(new PayRunNotFoundException(PAYRUN_ID));

        mvc.perform(get("/api/v1/payroll/payruns/{id}/statutory/lwf/export?format=XLSX", PAYRUN_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Uncomputed pay run throws 409 Conflict")
    void exportReturn_uncomputed_returns409() throws Exception {
        when(lwfService.exportReturnFile(eq(TENANT_ID), eq(PAYRUN_ID), eq("XLSX")))
                .thenThrow(new PayFigureNotComputedException(PAYRUN_ID, PayRunStatus.DRAFT));

        mvc.perform(get("/api/v1/payroll/payruns/{id}/statutory/lwf/export?format=XLSX", PAYRUN_ID))
                .andExpect(status().isConflict());
    }
}
