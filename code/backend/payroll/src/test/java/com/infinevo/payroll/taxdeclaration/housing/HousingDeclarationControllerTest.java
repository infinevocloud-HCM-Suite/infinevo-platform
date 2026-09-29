package com.infinevo.payroll.taxdeclaration.housing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotEditableException;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotFoundException;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.payroll.taxdeclaration.housing.dto.HousingDeclarationResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HousingDeclarationControllerTest {

    private HousingDeclarationService housingService;
    private MockMvc myMvc;
    private MockMvc officerMvc;

    private final String fy = "2024-25";
    private final UUID employeeId = UUID.randomUUID();
    private final HousingDeclarationResponse sampleResponse =
            new HousingDeclarationResponse(List.of(), List.of(), List.of());

    @BeforeEach
    void setUp() {
        housingService = mock(HousingDeclarationService.class);
        myMvc = MockMvcBuilders.standaloneSetup(new MyHousingDeclarationController(housingService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
        officerMvc = MockMvcBuilders.standaloneSetup(new HousingDeclarationController(housingService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/me/tax-declaration/{fy}/housing returns 200 on success")
    void testGetOwnSuccess() throws Exception {
        given(housingService.readOwn(fy)).willReturn(sampleResponse);

        myMvc.perform(get("/api/v1/me/tax-declaration/{fy}/housing", fy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("PUT /api/v1/me/tax-declaration/{fy}/house-rent returns 200 on success")
    void testReplaceHouseRentOwnSuccess() throws Exception {
        given(housingService.replaceHouseRentOwn(eq(fy), any())).willReturn(sampleResponse);

        myMvc.perform(put("/api/v1/me/tax-declaration/{fy}/house-rent", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("PUT /api/v1/me/tax-declaration/{fy}/home-loan returns 200 on success")
    void testReplaceHomeLoanOwnSuccess() throws Exception {
        given(housingService.replaceHomeLoansOwn(eq(fy), any())).willReturn(sampleResponse);

        myMvc.perform(put("/api/v1/me/tax-declaration/{fy}/home-loan", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("PUT /api/v1/me/tax-declaration/{fy}/let-out-property returns 200 on success")
    void testReplaceLetOutPropertyOwnSuccess() throws Exception {
        given(housingService.replaceLetOutPropertiesOwn(eq(fy), any())).willReturn(sampleResponse);

        myMvc.perform(put("/api/v1/me/tax-declaration/{fy}/let-out-property", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("Officer GET housing returns 200 on success")
    void testOfficerGetSuccess() throws Exception {
        given(housingService.read(employeeId, fy)).willReturn(sampleResponse);

        officerMvc
                .perform(get("/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/housing", employeeId, fy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("Officer PUT house-rent returns 200 on success")
    void testOfficerReplaceHouseRentSuccess() throws Exception {
        given(housingService.replaceHouseRent(eq(employeeId), eq(fy), any())).willReturn(sampleResponse);

        officerMvc
                .perform(put("/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/house-rent", employeeId, fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("Controller handles DeclarationNotFoundException with 404 NOT_FOUND")
    void testNotFoundHandling() throws Exception {
        given(housingService.readOwn(fy)).willThrow(new DeclarationNotFoundException(UUID.randomUUID()));

        myMvc.perform(get("/api/v1/me/tax-declaration/{fy}/housing", fy))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Controller handles DeclarationNotEditableException with 409 CONFLICT")
    void testConflictHandling() throws Exception {
        given(housingService.replaceHouseRentOwn(eq(fy), any()))
                .willThrow(new DeclarationNotEditableException("DECLARATION_LOCKED", "Declaration is locked"));

        myMvc.perform(put("/api/v1/me/tax-declaration/{fy}/house-rent", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DECLARATION_LOCKED"));
    }

    @Test
    @DisplayName("Controller handles WindowValidationException with 400 BAD_REQUEST")
    void testValidationHandling() throws Exception {
        given(housingService.replaceHouseRentOwn(eq(fy), any()))
                .willThrow(new WindowValidationException("Invalid date range"));

        myMvc.perform(put("/api/v1/me/tax-declaration/{fy}/house-rent", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Controller handles IllegalArgumentException with 400 BAD_REQUEST")
    void testIllegalArgumentHandling() throws Exception {
        given(housingService.replaceHouseRentOwn(eq(fy), any()))
                .willThrow(new IllegalArgumentException("Rent amount cannot be negative"));

        myMvc.perform(put("/api/v1/me/tax-declaration/{fy}/house-rent", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
