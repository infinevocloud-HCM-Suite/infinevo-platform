package com.infinevo.payroll.taxcalc.recalc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** D-147: the officer's list of failed recalculations and the retry, with their actions. */
class TaxRecalcFailureControllerTest {

    private TaxRecalcFailureService failureService;
    private PermissionService permissionService;
    private EmployeeService employeeService;
    private MockMvc mvc;

    private final UUID failureId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        failureService = mock(TaxRecalcFailureService.class);
        employeeService = mock(EmployeeService.class);
        given(employeeService.currentEmployee()).willReturn(Optional.empty());
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
        AspectJProxyFactory factory =
                new AspectJProxyFactory(new TaxRecalcFailureController(failureService, employeeService));
        factory.addAspect(new RequiresActionAspect(permissionService));
        TaxRecalcFailureController controller = factory.getProxy();
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("GET lists open failures with payroll.tax_declaration.read")
    void listOpen() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.read")).willReturn(true);
        given(failureService.open("2026-2027"))
                .willReturn(List.of(new TaxRecalcFailureResponse(
                        failureId,
                        employeeId,
                        "2026-2027",
                        TaxTrigger.DECLARATION_SUBMITTED,
                        "boom",
                        1,
                        Instant.now(),
                        Instant.now())));

        mvc.perform(get("/api/v1/payroll/tax-recalc-failures").param("fy", "2026-2027"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(failureId.toString()))
                .andExpect(jsonPath("$.data[0].error_message").value("boom"))
                .andExpect(jsonPath("$.data[0].trigger").value("DECLARATION_SUBMITTED"));
    }

    @Test
    @DisplayName("D-151: each open failure carries the employee's name, read only for the listed ids")
    void listCarriesEmployeeName() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.read")).willReturn(true);
        given(failureService.open(null))
                .willReturn(List.of(new TaxRecalcFailureResponse(
                        failureId,
                        employeeId,
                        "2026-2027",
                        TaxTrigger.SALARY_REVISION,
                        "boom",
                        2,
                        Instant.now(),
                        Instant.now())));
        given(employeeService.displayNames(Set.of(employeeId))).willReturn(Map.of(employeeId, "Asha Rao"));

        mvc.perform(get("/api/v1/payroll/tax-recalc-failures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].employee_name").value("Asha Rao"))
                .andExpect(jsonPath("$.data[0].attempts").value(2));
    }

    @Test
    @DisplayName("Retry is refused without payroll.tax_declaration.verify")
    void retryNeedsVerify() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.verify")).willReturn(false);

        mvc.perform(post("/api/v1/payroll/tax-recalc-failures/{id}/retry", failureId))
                .andExpect(status().isForbidden());
        verify(failureService, never()).retry(any(), any());
    }

    @Test
    @DisplayName("Retry returns the new computation")
    void retrySucceeds() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.verify")).willReturn(true);
        given(failureService.retry(failureId, null)).willReturn(record());

        mvc.perform(post("/api/v1/payroll/tax-recalc-failures/{id}/retry", failureId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employee_id").value(employeeId.toString()))
                .andExpect(jsonPath("$.data.annual_tax").value(1000));
    }

    @Test
    @DisplayName("Retry of an unknown failure is 404; a retry that fails again is 422")
    void retryErrors() throws Exception {
        given(permissionService.holds("payroll.tax_declaration.verify")).willReturn(true);
        given(failureService.retry(failureId, null)).willThrow(new TaxRecalcFailureNotFoundException(failureId));
        mvc.perform(post("/api/v1/payroll/tax-recalc-failures/{id}/retry", failureId))
                .andExpect(status().isNotFound());

        UUID other = UUID.randomUUID();
        given(failureService.retry(other, null))
                .willThrow(new TaxRecalcRetryFailedException("Tax recalculation failed again: x", null));
        mvc.perform(post("/api/v1/payroll/tax-recalc-failures/{id}/retry", other))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TAX_RECALC_FAILED"));
    }

    private TaxComputationRecord record() {
        return new TaxComputationRecord(
                UUID.randomUUID(),
                employeeId,
                null,
                "2026-2027",
                "NEW",
                TaxTrigger.DECLARATION_SUBMITTED,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(1000),
                "{}",
                Instant.now(),
                null,
                "system");
    }
}
