package com.infinevo.payroll.taxdeclaration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationRequest;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationResponse;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowRequest;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowResponse;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotEditableException;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotFoundException;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller tests for W-32.1 tax declaration endpoints (D-7).
 * Verifies HTTP contracts, status codes (200, 400, 403, 404, 409), and authorization enforcement.
 */
class TaxDeclarationControllerTest {

    private TaxDeclarationService taxDeclarationService;
    private TaxDeclarationWindowService windowService;
    private PermissionService permissionService;
    private ObjectMapper objectMapper;

    private MockMvc myMvc;
    private MockMvc officerMvc;
    private MockMvc settingsMvc;

    private final String fy = "2026-2027";
    private final UUID employeeId = UUID.randomUUID();
    private final UUID declarationId = UUID.randomUUID();

    private final TaxDeclarationResponse sampleResponse = new TaxDeclarationResponse(
            declarationId,
            employeeId,
            fy,
            "NEW",
            DeclarationStatus.DRAFT,
            false,
            false,
            false,
            false,
            null,
            null,
            true,
            true,
            true,
            new java.math.BigDecimal("100000.0000"));

    private final TaxDeclarationWindowResponse sampleWindowResponse = new TaxDeclarationWindowResponse(
            UUID.randomUUID(),
            fy,
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 4, 30),
            false,
            "NEW",
            true,
            true,
            false,
            false,
            true,
            true);

    private final TaxDeclarationRequest sampleRequest = new TaxDeclarationRequest("NEW", false, false, false);

    private final TaxDeclarationWindowRequest sampleWindowRequest = new TaxDeclarationWindowRequest(
            LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), false, "NEW", true, true, false, false);

    @BeforeEach
    void setUp() {
        taxDeclarationService = mock(TaxDeclarationService.class);
        windowService = mock(TaxDeclarationWindowService.class);
        permissionService = mock(PermissionService.class);

        doAnswer(invocation -> {
                    String action = invocation.getArgument(0);
                    if (!permissionService.holds(action)) {
                        throw new PermissionDeniedException(action);
                    }
                    return null;
                })
                .when(permissionService)
                .require(any());

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        // Proxied controllers enforcing @RequiresActionAspect
        MyTaxDeclarationController myController =
                proxy(new MyTaxDeclarationController(taxDeclarationService), permissionService);
        TaxDeclarationController officerController =
                proxy(new TaxDeclarationController(taxDeclarationService), permissionService);
        TaxDeclarationSettingsController settingsController =
                proxy(new TaxDeclarationSettingsController(windowService), permissionService);

        myMvc = MockMvcBuilders.standaloneSetup(myController)
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(converter)
                .build();

        officerMvc = MockMvcBuilders.standaloneSetup(officerController)
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(converter)
                .build();

        settingsMvc = MockMvcBuilders.standaloneSetup(settingsController)
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target, PermissionService permService) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permService));
        return (T) factory.getProxy();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // MyTaxDeclarationController (Self-service)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/me/tax-declaration/{fy} returns 200 on success when user holds read_own")
    void testGetOwnSuccess() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.read_own")).willReturn(true);
        given(taxDeclarationService.readOwn(fy)).willReturn(sampleResponse);

        myMvc.perform(get("/api/v1/me/tax-declaration/{fy}", fy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.financial_year").value(fy))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                // Rent PAN rule on the header, under the two names the W-47.3 screen reads
                .andExpect(
                        jsonPath("$.data.pan_required_for_rent_over_threshold").value(true))
                .andExpect(jsonPath("$.data.rent_pan_threshold").value(100000.0));
    }

    @Test
    @DisplayName("PUT /api/v1/me/tax-declaration/{fy} returns 403 when user only holds read_own (not declare_own)")
    void testPutOwnRefusedWith403WhenDeclareOwnMissing() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.read_own")).willReturn(true);
        given(permissionService.holds("payroll.tax_declaration.declare_own")).willReturn(false);

        myMvc.perform(put("/api/v1/me/tax-declaration/{fy}", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("PUT /api/v1/me/tax-declaration/{fy} returns 200 when user holds declare_own")
    void testPutOwnSuccess() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.declare_own")).willReturn(true);
        given(taxDeclarationService.saveOwn(eq(fy), any())).willReturn(sampleResponse);

        myMvc.perform(put("/api/v1/me/tax-declaration/{fy}", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("PUT /api/v1/me/tax-declaration/{fy} returns 409 WINDOW_CLOSED when window is closed")
    void testPutOwnConflictWhenWindowClosed() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.declare_own")).willReturn(true);
        given(taxDeclarationService.saveOwn(eq(fy), any()))
                .willThrow(new DeclarationNotEditableException("WINDOW_CLOSED", "Tax declaration window is closed"));

        myMvc.perform(put("/api/v1/me/tax-declaration/{fy}", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WINDOW_CLOSED"));
    }

    @Test
    @DisplayName("PUT /api/v1/me/tax-declaration/{fy} returns 409 DECLARATION_LOCKED when declaration is locked")
    void testPutOwnConflictWhenLocked() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.declare_own")).willReturn(true);
        given(taxDeclarationService.saveOwn(eq(fy), any()))
                .willThrow(new DeclarationNotEditableException("DECLARATION_LOCKED", "Declaration is locked"));

        myMvc.perform(put("/api/v1/me/tax-declaration/{fy}", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DECLARATION_LOCKED"));
    }

    @Test
    @DisplayName("POST /api/v1/me/tax-declaration/{fy}/submit returns 200 on success")
    void testSubmitOwnSuccess() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.declare_own")).willReturn(true);
        given(taxDeclarationService.submitOwn(fy)).willReturn(sampleResponse);

        myMvc.perform(post("/api/v1/me/tax-declaration/{fy}/submit", fy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("POST /api/v1/me/tax-declaration/{fy}/reopen returns 200 on success")
    void testReopenOwnSuccess() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.declare_own")).willReturn(true);
        given(taxDeclarationService.reopenOwn(fy)).willReturn(sampleResponse);

        myMvc.perform(post("/api/v1/me/tax-declaration/{fy}/reopen", fy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("GET /api/v1/me/tax-declaration/{fy} returns 404 when declaration not found")
    void testGetOwnNotFound() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.read_own")).willReturn(true);
        given(taxDeclarationService.readOwn(fy)).willThrow(new DeclarationNotFoundException(declarationId));

        myMvc.perform(get("/api/v1/me/tax-declaration/{fy}", fy))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TaxDeclarationController (Officer)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Officer GET returns 200 when holding read action")
    void testOfficerGetSuccess() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.read")).willReturn(true);
        given(taxDeclarationService.read(employeeId, fy)).willReturn(sampleResponse);

        officerMvc
                .perform(get("/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}", employeeId, fy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("Officer PUT returns 403 when holding read only (missing manage)")
    void testOfficerPutRefusedWithoutManage() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.manage")).willReturn(false);

        officerMvc
                .perform(put("/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}", employeeId, fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Officer PUT returns 200 when holding manage action")
    void testOfficerPutSuccess() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.manage")).willReturn(true);
        given(taxDeclarationService.save(eq(employeeId), eq(fy), any())).willReturn(sampleResponse);

        officerMvc
                .perform(put("/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}", employeeId, fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("Officer POST submit, reopen, lock, unlock return 200 on success")
    void testOfficerLifecycleEndpointsSuccess() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.manage")).willReturn(true);
        given(taxDeclarationService.submit(employeeId, fy)).willReturn(sampleResponse);
        given(taxDeclarationService.reopen(employeeId, fy)).willReturn(sampleResponse);
        given(taxDeclarationService.lock(employeeId, fy)).willReturn(sampleResponse);
        given(taxDeclarationService.unlock(employeeId, fy)).willReturn(sampleResponse);

        officerMvc
                .perform(post("/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/submit", employeeId, fy))
                .andExpect(status().isOk());

        officerMvc
                .perform(post("/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/reopen", employeeId, fy))
                .andExpect(status().isOk());

        officerMvc
                .perform(post("/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/lock", employeeId, fy))
                .andExpect(status().isOk());

        officerMvc
                .perform(post("/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/unlock", employeeId, fy))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TaxDeclarationSettingsController (Window Settings)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Settings GET returns 200 on success")
    void testSettingsGetSuccess() throws Exception {
        given(permissionService.holds("payroll.settings.manage")).willReturn(true);
        given(windowService.get(fy)).willReturn(sampleWindowResponse);

        settingsMvc
                .perform(get("/api/v1/payroll/tax-declaration/settings/{fy}", fy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.financial_year").value(fy));
    }

    @Test
    @DisplayName("Settings PUT returns 200 on success")
    void testSettingsPutSuccess() throws Exception {
        given(permissionService.holds("payroll.settings.manage")).willReturn(true);
        given(windowService.upsert(eq(fy), any())).willReturn(sampleWindowResponse);

        settingsMvc
                .perform(put("/api/v1/payroll/tax-declaration/settings/{fy}", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleWindowRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("Settings PUT returns 400 when WindowValidationException is thrown")
    void testSettingsPutValidationFailure() throws Exception {
        given(permissionService.holds("payroll.settings.manage")).willReturn(true);
        given(windowService.upsert(eq(fy), any()))
                .willThrow(new WindowValidationException("openDate must be before or equal to closeDate"));

        settingsMvc
                .perform(put("/api/v1/payroll/tax-declaration/settings/{fy}", fy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleWindowRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
