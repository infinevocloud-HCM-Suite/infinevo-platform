package com.infinevo.payroll.taxcalc.recalc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller tests for W-33.3 recalculation and history audit endpoints.
 */
class TaxRecalculationControllerTest {

    private TaxRecalculationService recalculationService;
    private EmployeeService employeeService;
    private PermissionService permissionService;

    private MockMvc officerMvc;
    private MockMvc myMvc;

    private final UUID employeeId = UUID.randomUUID();
    private final UUID officerId = UUID.randomUUID();
    private final UUID declarationId = UUID.randomUUID();
    private final String fyLabel = "2025-2026";

    private TaxComputationRecord sampleRecord() {
        return new TaxComputationRecord(
                UUID.randomUUID(),
                employeeId,
                declarationId,
                fyLabel,
                "NEW",
                TaxTrigger.OFFICER,
                BigDecimal.valueOf(1200000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(75000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(1125000),
                BigDecimal.valueOf(90000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(3600),
                BigDecimal.ZERO,
                BigDecimal.valueOf(93600),
                "{\"taxableIncome\":1125000}",
                Instant.now(),
                officerId,
                "system");
    }

    private TaxComputationRecordResponse sampleResponse() {
        return new TaxComputationRecordResponse(
                UUID.randomUUID(),
                employeeId,
                declarationId,
                fyLabel,
                "NEW",
                TaxTrigger.OFFICER,
                BigDecimal.valueOf(1200000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(75000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(1125000),
                BigDecimal.valueOf(90000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(3600),
                BigDecimal.ZERO,
                BigDecimal.valueOf(93600),
                null,
                Instant.now(),
                officerId);
    }

    @BeforeEach
    void setUp() {
        recalculationService = mock(TaxRecalculationService.class);
        employeeService = mock(EmployeeService.class);
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

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);

        TaxRecalculationController officerController = proxy(
                new TaxRecalculationController(recalculationService, employeeService, objectMapper), permissionService);

        MyTaxHistoryController myController =
                proxy(new MyTaxHistoryController(recalculationService), permissionService);

        officerMvc = MockMvcBuilders.standaloneSetup(officerController)
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(converter)
                .build();

        myMvc = MockMvcBuilders.standaloneSetup(myController)
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    private <T> T proxy(T target, PermissionService permissionService) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new RequiresActionAspect(permissionService));
        return factory.getProxy();
    }

    @Test
    @DisplayName("Officer recalculate returns 200 OK when user holds payroll.tax_declaration.manage")
    void officerRecalculateSuccess() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.manage")).willReturn(true);
        EmployeeResponse officer = mock(EmployeeResponse.class);
        given(officer.id()).willReturn(officerId);
        given(employeeService.currentEmployee()).willReturn(Optional.of(officer));

        given(recalculationService.recalculate(employeeId, fyLabel, TaxTrigger.OFFICER, officerId))
                .willReturn(sampleRecord());

        officerMvc
                .perform(post(
                        "/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/tax/recalculate",
                        employeeId,
                        fyLabel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regime").value("NEW"))
                .andExpect(jsonPath("$.data.trigger").value("OFFICER"))
                .andExpect(jsonPath("$.data.annual_tax").value(93600));
    }

    @Test
    @DisplayName("Officer recalculate returns 403 Forbidden without payroll.tax_declaration.manage")
    void officerRecalculateForbidden() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.manage")).willReturn(false);

        officerMvc
                .perform(post(
                        "/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/tax/recalculate",
                        employeeId,
                        fyLabel))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Officer history returns 200 OK when user holds payroll.tax_declaration.read")
    void officerHistorySuccess() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.read")).willReturn(true);
        given(recalculationService.history(employeeId, fyLabel)).willReturn(List.of(sampleResponse()));

        officerMvc
                .perform(get(
                        "/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/tax/history", employeeId, fyLabel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].regime").value("NEW"))
                .andExpect(jsonPath("$.data[0].trigger").value("OFFICER"));
    }

    @Test
    @DisplayName("Officer history returns 403 Forbidden without payroll.tax_declaration.read")
    void officerHistoryForbidden() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.read")).willReturn(false);

        officerMvc
                .perform(get(
                        "/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/tax/history", employeeId, fyLabel))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("My tax history returns 200 OK when user holds payroll.tax_declaration.read_own")
    void myHistorySuccess() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.read_own")).willReturn(true);
        given(recalculationService.historyOwn(fyLabel)).willReturn(List.of(sampleResponse()));

        myMvc.perform(get("/api/v1/me/tax-declaration/{fy}/tax/history", fyLabel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].regime").value("NEW"))
                .andExpect(jsonPath("$.data[0].trigger").value("OFFICER"));
    }

    @Test
    @DisplayName("My tax history returns 403 Forbidden without payroll.tax_declaration.read_own")
    void myHistoryForbidden() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.read_own")).willReturn(false);

        myMvc.perform(get("/api/v1/me/tax-declaration/{fy}/tax/history", fyLabel))
                .andExpect(status().isForbidden());
    }
}
