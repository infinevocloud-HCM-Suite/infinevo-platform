package com.infinevo.payroll.taxdeclaration.summary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.payroll.taxdeclaration.DeclarationStatus;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotEditableException;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotFoundException;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.payroll.taxdeclaration.summary.dto.DeclaredTotals;
import com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryResponse;
import java.math.BigDecimal;
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

class TaxSummaryControllerTest {

    private TaxSummaryService taxSummaryService;
    private OtherIncomeService otherIncomeService;
    private MockMvc mySummaryMvc;
    private MockMvc officerSummaryMvc;
    private MockMvc myOtherIncomeMvc;
    private MockMvc officerOtherIncomeMvc;

    private final String fy = "2024-25";
    private final UUID employeeId = UUID.randomUUID();
    private final DeclaredTotals zeroTotals = new DeclaredTotals(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            Map.of(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    private final TaxSummaryResponse sampleSummary =
            new TaxSummaryResponse(fy, "NEW", DeclarationStatus.DRAFT, zeroTotals, null);

    @BeforeEach
    void setUp() {
        taxSummaryService = mock(TaxSummaryService.class);
        otherIncomeService = mock(OtherIncomeService.class);

        mySummaryMvc = MockMvcBuilders.standaloneSetup(new MyTaxSummaryController(taxSummaryService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
        officerSummaryMvc = MockMvcBuilders.standaloneSetup(new TaxSummaryController(taxSummaryService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
        myOtherIncomeMvc = MockMvcBuilders.standaloneSetup(new MyOtherIncomeController(otherIncomeService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
        officerOtherIncomeMvc = MockMvcBuilders.standaloneSetup(new OtherIncomeController(otherIncomeService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/me/tax-declaration/{fy}/summary returns 200 on success")
    void testGetOwnSummarySuccess() throws Exception {
        given(taxSummaryService.summaryOwn(fy)).willReturn(sampleSummary);

        mySummaryMvc
                .perform(get("/api/v1/me/tax-declaration/{fy}/summary", fy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("Officer GET summary returns 200 on success")
    void testOfficerGetSummarySuccess() throws Exception {
        given(taxSummaryService.summary(employeeId, fy)).willReturn(sampleSummary);

        officerSummaryMvc
                .perform(get("/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/summary", employeeId, fy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("GET /api/v1/me/tax-declaration/{fy}/other-income returns 200 on success")
    void testGetOwnOtherIncomeSuccess() throws Exception {
        given(otherIncomeService.readOwn(fy)).willReturn(List.of());

        myOtherIncomeMvc
                .perform(get("/api/v1/me/tax-declaration/{fy}/other-income", fy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("PUT /api/v1/me/tax-declaration/{fy}/other-income returns 200 on success")
    void testReplaceOtherIncomeOwnSuccess() throws Exception {
        given(otherIncomeService.replaceOwn(eq(fy), any())).willReturn(List.of());

        myOtherIncomeMvc
                .perform(put("/api/v1/me/tax-declaration/{fy}/other-income", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("Officer GET other-income returns 200 on success")
    void testOfficerGetOtherIncomeSuccess() throws Exception {
        given(otherIncomeService.read(employeeId, fy)).willReturn(List.of());

        officerOtherIncomeMvc
                .perform(
                        get("/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/other-income", employeeId, fy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("Officer PUT other-income returns 200 on success")
    void testOfficerReplaceOtherIncomeSuccess() throws Exception {
        given(otherIncomeService.replace(eq(employeeId), eq(fy), any())).willReturn(List.of());

        officerOtherIncomeMvc
                .perform(put("/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/other-income", employeeId, fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("Controller handles DeclarationNotFoundException with 404 NOT_FOUND")
    void testNotFoundHandling() throws Exception {
        given(taxSummaryService.summaryOwn(fy)).willThrow(new DeclarationNotFoundException(UUID.randomUUID()));

        mySummaryMvc
                .perform(get("/api/v1/me/tax-declaration/{fy}/summary", fy))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Controller handles DeclarationNotEditableException with 409 CONFLICT")
    void testConflictHandling() throws Exception {
        given(otherIncomeService.replaceOwn(eq(fy), any()))
                .willThrow(new DeclarationNotEditableException("DECLARATION_LOCKED", "Declaration is locked"));

        myOtherIncomeMvc
                .perform(put("/api/v1/me/tax-declaration/{fy}/other-income", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DECLARATION_LOCKED"));
    }

    @Test
    @DisplayName("Controller handles WindowValidationException with 400 BAD_REQUEST")
    void testValidationHandling() throws Exception {
        given(otherIncomeService.replaceOwn(eq(fy), any()))
                .willThrow(new WindowValidationException("Invalid other income item"));

        myOtherIncomeMvc
                .perform(put("/api/v1/me/tax-declaration/{fy}/other-income", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Controller handles IllegalArgumentException with 400 BAD_REQUEST")
    void testIllegalArgumentHandling() throws Exception {
        given(otherIncomeService.replaceOwn(eq(fy), any()))
                .willThrow(new IllegalArgumentException("Income amount cannot be negative"));

        myOtherIncomeMvc
                .perform(put("/api/v1/me/tax-declaration/{fy}/other-income", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
