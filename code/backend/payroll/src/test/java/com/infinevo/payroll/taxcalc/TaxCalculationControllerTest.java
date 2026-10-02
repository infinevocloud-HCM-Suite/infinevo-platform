package com.infinevo.payroll.taxcalc;

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
import com.infinevo.payroll.taxcalc.exception.RegimeNotAvailableException;
import com.infinevo.payroll.taxcalc.exception.TaxRulesMissingException;
import com.infinevo.payroll.taxcalc.model.SlabLine;
import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotFoundException;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
 * Controller tests for W-33.1 tax calculation preview and compute endpoints.
 */
class TaxCalculationControllerTest {

    private TaxCalculationService taxCalculationService;
    private EmployeeService employeeService;
    private PermissionService permissionService;

    private MockMvc myMvc;
    private MockMvc officerMvc;

    private final UUID employeeId = UUID.randomUUID();
    private final String fyLabel = "2025-2026";
    private final FinancialYear fy = FinancialYear.of(2025, 2026);
    private EmployeeResponse sampleEmployee;

    private TaxComputation sampleComputation() {
        return new TaxComputation(
                TaxRegime.NEW,
                fyLabel,
                Money.of(1575000),
                Money.ZERO,
                Money.of(75000),
                Money.of(1575000),
                Money.of(1500000),
                Money.of(105000),
                Money.ZERO,
                Money.ZERO,
                Money.ZERO,
                Money.of(4200),
                Money.ZERO,
                Money.of(109200),
                List.of(new SlabLine(Money.ZERO, Money.of(400000), BigDecimal.ZERO, Money.of(400000), Money.ZERO)),
                List.of("12 months projected"));
    }

    @BeforeEach
    void setUp() {
        taxCalculationService = mock(TaxCalculationService.class);
        employeeService = mock(EmployeeService.class);
        permissionService = mock(PermissionService.class);
        sampleEmployee = mock(EmployeeResponse.class);
        given(sampleEmployee.id()).willReturn(employeeId);

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

        MyTaxCalculationController myController =
                proxy(new MyTaxCalculationController(taxCalculationService, employeeService), permissionService);
        TaxCalculationController officerController =
                proxy(new TaxCalculationController(taxCalculationService), permissionService);

        myMvc = MockMvcBuilders.standaloneSetup(myController)
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(converter)
                .build();

        officerMvc = MockMvcBuilders.standaloneSetup(officerController)
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
    // MyTaxCalculationController (Self-Service)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/me/tax-declaration/{fy}/tax returns 200 with scale 2 Money preview")
    void getOwnReturnsPreview() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.read_own")).willReturn(true);
        given(employeeService.currentEmployee()).willReturn(Optional.of(sampleEmployee));
        given(taxCalculationService.compute(employeeId, fy, null)).willReturn(sampleComputation());

        myMvc.perform(get("/api/v1/me/tax-declaration/{fy}/tax", fyLabel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.regime").value("NEW"))
                .andExpect(jsonPath("$.data.financial_year").value("2025-2026"))
                .andExpect(jsonPath("$.data.income_from_salary").value(1575000.00))
                .andExpect(jsonPath("$.data.standard_deduction").value(75000.00))
                .andExpect(jsonPath("$.data.gross_total_income").value(1575000.00))
                .andExpect(jsonPath("$.data.taxable_income").value(1500000.00))
                .andExpect(jsonPath("$.data.tax_before_rebate").value(105000.00))
                .andExpect(jsonPath("$.data.cess").value(4200.00))
                .andExpect(jsonPath("$.data.annual_tax").value(109200.00))
                // Old-regime working fields are present and zero for NEW (W-33.2 spec § 4)
                .andExpect(jsonPath("$.data.exemption_under_section10").value(0.00))
                .andExpect(jsonPath("$.data.professional_tax").value(0.00))
                .andExpect(jsonPath("$.data.house_property.loss_cap_applied").value(false))
                .andExpect(jsonPath("$.data.chapter_via_lines").isArray())
                .andExpect(jsonPath("$.data.exemption_under_section6a").value(0.00));
    }

    @Test
    @DisplayName(
            "GET /api/v1/me/tax-declaration/{fy}/tax maps RegimeNotAvailableException (no calculator registered) to 409")
    void getOwnReturns409WhenNoCalculatorRegistered() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.read_own")).willReturn(true);
        given(employeeService.currentEmployee()).willReturn(Optional.of(sampleEmployee));
        given(taxCalculationService.compute(employeeId, fy, TaxRegime.OLD))
                .willThrow(new RegimeNotAvailableException(TaxRegime.OLD));

        myMvc.perform(get("/api/v1/me/tax-declaration/{fy}/tax", fyLabel).param("regime", "OLD"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGIME_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.fieldErrors.regime").value("OLD"));
    }

    @Test
    @DisplayName("GET /api/v1/me/tax-declaration/{fy}/tax returns 404 when declaration not found")
    void getOwnReturns404WhenNotFound() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.read_own")).willReturn(true);
        given(employeeService.currentEmployee()).willReturn(Optional.of(sampleEmployee));
        given(taxCalculationService.compute(employeeId, fy, null))
                .willThrow(new DeclarationNotFoundException(employeeId, fyLabel));

        myMvc.perform(get("/api/v1/me/tax-declaration/{fy}/tax", fyLabel))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/me/tax-declaration/{fy}/tax returns 422 when rules missing")
    void getOwnReturns422WhenRulesMissing() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.read_own")).willReturn(true);
        given(employeeService.currentEmployee()).willReturn(Optional.of(sampleEmployee));
        given(taxCalculationService.compute(employeeId, fy, null))
                .willThrow(new TaxRulesMissingException("standard_deduction_rule_master", fyLabel));

        myMvc.perform(get("/api/v1/me/tax-declaration/{fy}/tax", fyLabel))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TAX_RULES_MISSING"))
                .andExpect(jsonPath("$.fieldErrors.rule_table").value("standard_deduction_rule_master"));
    }

    @Test
    @DisplayName("POST /api/v1/me/tax-declaration/{fy}/tax/compute returns 200 with NEW and OLD regimes")
    void computeOwnReturnsComputations() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.declare_own")).willReturn(true);
        given(employeeService.currentEmployee()).willReturn(Optional.of(sampleEmployee));
        given(taxCalculationService.computeAndRecord(employeeId, fy))
                .willReturn(Map.of(TaxRegime.NEW, sampleComputation()));

        myMvc.perform(post("/api/v1/me/tax-declaration/{fy}/tax/compute", fyLabel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.NEW.annual_tax").value(109200.00))
                .andExpect(jsonPath("$.data.OLD").doesNotExist());
    }

    @Test
    @DisplayName("Self-service endpoints return 403 when permission missing")
    void selfServiceReturns403() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.read_own")).willReturn(false);
        given(permissionService.holds("payroll.tax_declaration.declare_own")).willReturn(false);

        myMvc.perform(get("/api/v1/me/tax-declaration/{fy}/tax", fyLabel)).andExpect(status().isForbidden());

        myMvc.perform(post("/api/v1/me/tax-declaration/{fy}/tax/compute", fyLabel))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // TaxCalculationController (Officer)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Officer GET /api/v1/payroll/employees/{id}/tax-declaration/{fy}/tax returns 200")
    void officerGetReturnsPreview() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.read")).willReturn(true);
        given(taxCalculationService.compute(employeeId, fy, null)).willReturn(sampleComputation());

        officerMvc
                .perform(get("/api/v1/payroll/employees/{id}/tax-declaration/{fy}/tax", employeeId, fyLabel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.annual_tax").value(109200.00));
    }

    @Test
    @DisplayName("Officer POST /api/v1/payroll/employees/{id}/tax-declaration/{fy}/tax/compute returns 200")
    void officerComputeReturns200() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.manage")).willReturn(true);
        given(taxCalculationService.computeAndRecord(employeeId, fy))
                .willReturn(Map.of(TaxRegime.NEW, sampleComputation()));

        officerMvc
                .perform(post("/api/v1/payroll/employees/{id}/tax-declaration/{fy}/tax/compute", employeeId, fyLabel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.NEW.annual_tax").value(109200.00));
    }

    @Test
    @DisplayName("Officer endpoints return 403 when permission missing")
    void officerReturns403() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.read")).willReturn(false);
        given(permissionService.holds("payroll.tax_declaration.manage")).willReturn(false);

        officerMvc
                .perform(get("/api/v1/payroll/employees/{id}/tax-declaration/{fy}/tax", employeeId, fyLabel))
                .andExpect(status().isForbidden());

        officerMvc
                .perform(post("/api/v1/payroll/employees/{id}/tax-declaration/{fy}/tax/compute", employeeId, fyLabel))
                .andExpect(status().isForbidden());
    }
}
