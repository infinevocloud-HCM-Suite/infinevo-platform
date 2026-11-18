package com.infinevo.payroll.compliance.form24q;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.payroll.form16.exception.DeductorNotSetException;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller unit tests for {@link Form24QController} (W-89.2, W-89.3).
 */
class Form24QControllerTest {

    private Form24QAnnexure1Service annexure1Service;
    private Form24QAnnexure2Service annexure2Service;
    private MockMvc mvc;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        annexure1Service = mock(Form24QAnnexure1Service.class);
        annexure2Service = mock(Form24QAnnexure2Service.class);
        Form24QController controller = new Form24QController(annexure1Service, annexure2Service);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(converter, new ByteArrayHttpMessageConverter())
                .build();
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/v1/payroll/compliance/24q/{fy}/{quarter}/annexure-1 returns 200 with preview")
    void previewAnnexure1_returnsOk() throws Exception {
        Annexure1PreviewResponse preview = new Annexure1PreviewResponse(
                "2026-2027",
                "Q1",
                "2027-2028",
                "BLRE12345F",
                "ABCDE1234F",
                "Company Ltd",
                1,
                new BigDecimal("50000.00"),
                1,
                new BigDecimal("75000.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                true,
                0,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList());

        when(annexure1Service.previewAnnexure1("2026-2027", "Q1")).thenReturn(preview);

        mvc.perform(get("/api/v1/payroll/compliance/24q/{fy}/{quarter}/annexure-1", "2026-2027", "Q1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.financialYear").value("2026-2027"))
                .andExpect(jsonPath("$.data.quarter").value("Q1"))
                .andExpect(jsonPath("$.data.isReconciled").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/payroll/compliance/24q/{fy}/{quarter}/annexure-1/export returns CSV attachment")
    void exportAnnexure1_csv_returnsAttachment() throws Exception {
        byte[] csvBytes = "Challan Serial No,BSR Code\r\n\"12345\",\"0210045\"\r\n".getBytes(StandardCharsets.UTF_8);
        when(annexure1Service.exportAnnexure1(eq("2026-2027"), eq("Q1"), eq("CSV")))
                .thenReturn(csvBytes);

        mvc.perform(get("/api/v1/payroll/compliance/24q/{fy}/{quarter}/annexure-1/export", "2026-2027", "Q1")
                        .param("format", "CSV"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string(
                                "Content-Disposition", "attachment; filename=\"24Q_2026-2027_Q1_Annexure1.csv\""))
                .andExpect(content().bytes(csvBytes));
    }

    @Test
    @DisplayName("GET /api/v1/payroll/compliance/24q/{fy}/{quarter}/annexure-1/export returns TXT attachment")
    void exportAnnexure1_txt_returnsAttachment() throws Exception {
        byte[] txtBytes = "FH^1^NSDL-24Q^\r\n".getBytes(StandardCharsets.UTF_8);
        when(annexure1Service.exportAnnexure1(eq("2026-2027"), eq("Q1"), eq("TXT")))
                .thenReturn(txtBytes);

        mvc.perform(get("/api/v1/payroll/compliance/24q/{fy}/{quarter}/annexure-1/export", "2026-2027", "Q1")
                        .param("format", "TXT"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(header().string(
                                "Content-Disposition", "attachment; filename=\"24Q_2026-2027_Q1_Annexure1.txt\""))
                .andExpect(content().bytes(txtBytes));
    }

    @Test
    @DisplayName("GET /api/v1/payroll/compliance/24q/{fy}/{quarter}/annexure-1 with validation failure returns 400")
    void previewAnnexure1_validationError_returns400() throws Exception {
        when(annexure1Service.previewAnnexure1("2026-2027", "Q9"))
                .thenThrow(new Form24QValidationException("Invalid quarter: Q9"));

        mvc.perform(get("/api/v1/payroll/compliance/24q/{fy}/{quarter}/annexure-1", "2026-2027", "Q9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Invalid quarter: Q9"));
    }

    @Test
    @DisplayName("GET /api/v1/payroll/compliance/24q/{fy}/{quarter}/annexure-1 with missing deductor returns 409")
    void previewAnnexure1_missingDeductor_returns409() throws Exception {
        when(annexure1Service.previewAnnexure1("2026-2027", "Q1"))
                .thenThrow(new DeductorNotSetException("Tax deductor details have not been configured"));

        mvc.perform(get("/api/v1/payroll/compliance/24q/{fy}/{quarter}/annexure-1", "2026-2027", "Q1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("GET /api/v1/payroll/compliance/24q/{fy}/annexure-2 returns 200 with preview")
    void previewAnnexure2_returnsOk() throws Exception {
        Annexure2PreviewResponse preview = new Annexure2PreviewResponse(
                "2026-2027",
                "2027-2028",
                "BLRE12345F",
                "ABCDE1234F",
                "Company Ltd",
                1,
                new BigDecimal("1200000.00"),
                new BigDecimal("897600.00"),
                new BigDecimal("98300.80"),
                new BigDecimal("98300.80"),
                BigDecimal.ZERO,
                Collections.emptyList());

        when(annexure2Service.previewAnnexure2("2026-2027")).thenReturn(preview);

        mvc.perform(get("/api/v1/payroll/compliance/24q/{fy}/annexure-2", "2026-2027"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.financialYear").value("2026-2027"))
                .andExpect(jsonPath("$.data.totalEmployees").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/payroll/compliance/24q/{fy}/{quarter}/annexure-2 with non-Q4 throws 400")
    void previewAnnexure2_nonQ4_returns400() throws Exception {
        mvc.perform(get("/api/v1/payroll/compliance/24q/{fy}/{quarter}/annexure-2", "2026-2027", "Q2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message")
                        .value("Annexure II is an annual statement applicable exclusively to Quarter 4 (Q4)"));
    }

    @Test
    @DisplayName("GET /api/v1/payroll/compliance/24q/{fy}/annexure-2/export returns CSV attachment")
    void exportAnnexure2_csv_returnsAttachment() throws Exception {
        byte[] csvBytes = "Sl No,Employee Code\r\n1,\"EMP001\"\r\n".getBytes(StandardCharsets.UTF_8);
        when(annexure2Service.exportAnnexure2(eq("2026-2027"), eq("CSV"))).thenReturn(csvBytes);

        mvc.perform(get("/api/v1/payroll/compliance/24q/{fy}/annexure-2/export", "2026-2027")
                        .param("format", "CSV"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string(
                                "Content-Disposition", "attachment; filename=\"24Q_2026-2027_Q4_Annexure2.csv\""))
                .andExpect(content().bytes(csvBytes));
    }
}
