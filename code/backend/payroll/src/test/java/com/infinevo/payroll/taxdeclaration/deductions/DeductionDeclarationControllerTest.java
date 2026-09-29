package com.infinevo.payroll.taxdeclaration.deductions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.payroll.taxdeclaration.deductions.dto.DeductionDeclarationResponse;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotEditableException;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotFoundException;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DeductionDeclarationControllerTest {

    private DeductionDeclarationService deductionService;
    private MockMvc myMvc;
    private MockMvc officerMvc;

    private final String fy = "2024-25";
    private final UUID employeeId = UUID.randomUUID();
    private final DeductionDeclarationResponse sampleResponse =
            new DeductionDeclarationResponse(List.of(), List.of(), List.of());

    @BeforeEach
    void setUp() {
        deductionService = mock(DeductionDeclarationService.class);
        myMvc = MockMvcBuilders.standaloneSetup(new MyDeductionDeclarationController(deductionService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
        officerMvc = MockMvcBuilders.standaloneSetup(new DeductionDeclarationController(deductionService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/me/tax-declaration/{fy}/deductions returns 200 on success")
    void testGetOwnDeductionsSuccess() throws Exception {
        given(deductionService.readOwn(fy)).willReturn(sampleResponse);

        myMvc.perform(get("/api/v1/me/tax-declaration/{fy}/deductions", fy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("GET /api/v1/me/tax-declaration/{fy}/section6a-items returns 200 on success")
    void testGetSection6AItemsSuccess() throws Exception {
        given(deductionService.getSection6AItemsOwn(fy)).willReturn(List.of());

        myMvc.perform(get("/api/v1/me/tax-declaration/{fy}/section6a-items", fy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("PUT /api/v1/me/tax-declaration/{fy}/section6a returns 200 on success")
    void testReplaceSection6AOwnSuccess() throws Exception {
        given(deductionService.replaceSection6AOwn(eq(fy), any())).willReturn(sampleResponse);

        myMvc.perform(put("/api/v1/me/tax-declaration/{fy}/section6a", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("PUT /api/v1/me/tax-declaration/{fy}/pre-tax-deductions returns 200 on success")
    void testReplacePreTaxDeductionsOwnSuccess() throws Exception {
        given(deductionService.replacePreTaxDeductionsOwn(eq(fy), any())).willReturn(sampleResponse);

        myMvc.perform(put("/api/v1/me/tax-declaration/{fy}/pre-tax-deductions", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("PUT /api/v1/me/tax-declaration/{fy}/previous-employment returns 200 on success")
    void testReplacePrevEmploymentOwnSuccess() throws Exception {
        given(deductionService.replacePrevEmploymentOwn(eq(fy), any())).willReturn(sampleResponse);

        myMvc.perform(put("/api/v1/me/tax-declaration/{fy}/previous-employment", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("Officer GET deductions returns 200 on success")
    void testOfficerGetSuccess() throws Exception {
        given(deductionService.read(employeeId, fy)).willReturn(sampleResponse);

        officerMvc
                .perform(get("/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/deductions", employeeId, fy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("Officer PUT section6a returns 200 on success")
    void testOfficerReplaceSection6ASuccess() throws Exception {
        given(deductionService.replaceSection6A(eq(employeeId), eq(fy), any())).willReturn(sampleResponse);

        officerMvc
                .perform(put("/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/section6a", employeeId, fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("Controller handles DeclarationNotFoundException with 404 NOT_FOUND")
    void testNotFoundHandling() throws Exception {
        given(deductionService.readOwn(fy)).willThrow(new DeclarationNotFoundException(UUID.randomUUID()));

        myMvc.perform(get("/api/v1/me/tax-declaration/{fy}/deductions", fy))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Controller handles DeclarationNotEditableException with 409 CONFLICT")
    void testConflictHandling() throws Exception {
        given(deductionService.replaceSection6AOwn(eq(fy), any()))
                .willThrow(new DeclarationNotEditableException("DECLARATION_LOCKED", "Declaration is locked"));

        myMvc.perform(put("/api/v1/me/tax-declaration/{fy}/section6a", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DECLARATION_LOCKED"));
    }

    @Test
    @DisplayName("Controller handles WindowValidationException with 400 BAD_REQUEST")
    void testValidationHandling() throws Exception {
        given(deductionService.replaceSection6AOwn(eq(fy), any()))
                .willThrow(new WindowValidationException("Invalid section 6A item"));

        myMvc.perform(put("/api/v1/me/tax-declaration/{fy}/section6a", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Controller handles IllegalArgumentException with 400 BAD_REQUEST")
    void testIllegalArgumentHandling() throws Exception {
        given(deductionService.replaceSection6AOwn(eq(fy), any()))
                .willThrow(new IllegalArgumentException("Item id cannot be null"));

        myMvc.perform(put("/api/v1/me/tax-declaration/{fy}/section6a", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
