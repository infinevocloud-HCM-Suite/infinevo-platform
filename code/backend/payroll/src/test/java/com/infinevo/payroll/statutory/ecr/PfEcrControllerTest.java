package com.infinevo.payroll.statutory.ecr;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

class PfEcrControllerTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PAYRUN_ID = UUID.randomUUID();

    private PfEcrService pfEcrService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        pfEcrService = mock(PfEcrService.class);
        PfEcrController controller = new PfEcrController(pfEcrService);
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
    @DisplayName("GET /preview returns 200 with ECR summary and rows in standard envelope")
    void getPreview_success() throws Exception {
        PfEcrPreviewResponse preview = new PfEcrPreviewResponse(
                PAYRUN_ID,
                "2026-10",
                "DLCPM1234567000",
                1,
                65000L,
                15000L,
                15000L,
                15000L,
                1800L,
                1250L,
                550L,
                75L,
                75L,
                3750L,
                List.of(),
                List.of(new PfEcrRowResponse(
                        "100123456789", "ASHA RAO", 65000L, 15000L, 15000L, 15000L, 1800L, 1250L, 550L, 0, 0L)));

        when(pfEcrService.getEcrPreview(PAYRUN_ID)).thenReturn(preview);

        mvc.perform(get("/api/v1/payroll/payruns/{id}/reports/pf-ecr/preview", PAYRUN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("PF ECR preview generated successfully"))
                .andExpect(jsonPath("$.data.establishmentCode").value("DLCPM1234567000"))
                .andExpect(jsonPath("$.data.totalMembers").value(1))
                .andExpect(jsonPath("$.data.rows[0].uan").value("100123456789"))
                .andExpect(jsonPath("$.data.rows[0].memberName").value("ASHA RAO"));
    }

    @Test
    @DisplayName("GET /export?format=TXT returns 200 with text file and attachment header")
    void exportTxt_success() throws Exception {
        String txtContent = "100123456789#~#ASHA RAO#~#65000#~#15000#~#15000#~#15000#~#1800#~#1250#~#550#~#0#~#0\r\n";
        PfEcrFile file = new PfEcrFile(
                "ECR_DLCPM1234567000_2026_10.txt",
                MediaType.parseMediaType("text/plain; charset=utf-8"),
                txtContent.getBytes(StandardCharsets.UTF_8));

        when(pfEcrService.exportEcrFile(eq(PAYRUN_ID), eq(PfEcrFormat.TXT))).thenReturn(file);

        mvc.perform(get("/api/v1/payroll/payruns/{id}/reports/pf-ecr/export?format=TXT", PAYRUN_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"ECR_DLCPM1234567000_2026_10.txt\""))
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(content().string(txtContent));
    }

    @Test
    @DisplayName("GET /export on off-cycle run returns 400 Bad Request")
    void export_offCycle_returns400() throws Exception {
        when(pfEcrService.exportEcrFile(eq(PAYRUN_ID), eq(PfEcrFormat.TXT)))
                .thenThrow(new IllegalArgumentException(
                        "Statutory ECR returns apply only to regular pay runs: " + PAYRUN_ID));

        mvc.perform(get("/api/v1/payroll/payruns/{id}/reports/pf-ecr/export?format=TXT", PAYRUN_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Statutory ECR returns apply only to regular pay runs: " + PAYRUN_ID));
    }

    @Test
    @DisplayName("GET /preview on non-existent pay run returns 404 Not Found")
    void preview_notFound_returns404() throws Exception {
        when(pfEcrService.getEcrPreview(PAYRUN_ID)).thenThrow(new PayRunNotFoundException(PAYRUN_ID));

        mvc.perform(get("/api/v1/payroll/payruns/{id}/reports/pf-ecr/preview", PAYRUN_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /preview on uncomputed pay run returns 409 Conflict")
    void preview_uncomputed_returns409() throws Exception {
        when(pfEcrService.getEcrPreview(PAYRUN_ID))
                .thenThrow(new PayFigureNotComputedException(PAYRUN_ID, PayRunStatus.DRAFT));

        mvc.perform(get("/api/v1/payroll/payruns/{id}/reports/pf-ecr/preview", PAYRUN_ID))
                .andExpect(status().isConflict());
    }
}
