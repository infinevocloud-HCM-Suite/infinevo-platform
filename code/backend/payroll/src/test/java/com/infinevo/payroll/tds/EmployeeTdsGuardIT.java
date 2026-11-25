package com.infinevo.payroll.tds;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.tds.exception.EmployeeTdsConflictException;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
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
 * W-36.1 §4 — the four mappings behind the real {@link RequiresActionAspect}: {@code read} cannot
 * {@code PUT}; {@code verify} can, and the JSON carries the snake_case fields with {@code year_to_date}
 * and {@code remaining}; a missing figure is 400, a lost race 409, an unknown employee 404. The
 * service is mocked — the data paths are the other ITs'.
 */
class EmployeeTdsGuardIT {

    private static final String READ = "payroll.tax_declaration.read";
    private static final String VERIFY = "payroll.tax_declaration.verify";
    private static final String FY = "2026-2027";
    private static final UUID TENANT = UUID.randomUUID();
    private static final String BODY = "{\"regime\":\"NEW\",\"annual_gross\":1200000,"
            + "\"annual_taxable_income\":1000000,\"annual_tax\":120000,\"effective_from_period\":\"2026-04\"}";

    private EmployeeTdsService service;
    private EmployeeService employeeService;
    private PermissionService permissionService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(EmployeeTdsService.class);
        employeeService = mock(EmployeeService.class);
        permissionService = mock(PermissionService.class);
        // D-145: the controller asks the service for the spread; answer it as the service does for April 2026.
        given(service.spread(any(), any())).willAnswer(invocation -> {
            EmployeeTds record = invocation.getArgument(0);
            return TdsSpread.of(
                    record.getAnnualTax(),
                    invocation.getArgument(1),
                    YearMonth.parse(record.getEffectiveFromPeriod()),
                    YearMonth.of(2026, 4),
                    FinancialYear.parse(record.getFinancialYear()));
        });
        doAnswer(invocation -> {
                    String action = invocation.getArgument(0);
                    if (!permissionService.holds(action)) {
                        throw new PermissionDeniedException(action);
                    }
                    return null;
                })
                .when(permissionService)
                .require(any());

        AspectJProxyFactory factory = new AspectJProxyFactory(new EmployeeTdsController(service, employeeService));
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permissionService));
        mvc = MockMvcBuilders.standaloneSetup((EmployeeTdsController) factory.getProxy())
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    @Test
    @DisplayName("read alone: 403 on PUT and the service is never called; GET returns the record")
    void readCannotRecord() throws Exception {
        given(permissionService.holds(READ)).willReturn(true);
        UUID employeeId = UUID.randomUUID();
        given(service.active(employeeId, FY)).willReturn(Optional.of(sample(employeeId)));
        given(service.yearToDate(employeeId, FY)).willReturn(new BigDecimal("20000.0000"));

        mvc.perform(put("/api/v1/payroll/employees/{id}/tds/{fy}", employeeId, FY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payroll/employees/{id}/tds/{fy}", employeeId, FY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.annual_tax").value(120000.0))
                .andExpect(jsonPath("$.data.year_to_date").value(20000.0))
                .andExpect(jsonPath("$.data.remaining").value(100000.0))
                .andExpect(jsonPath("$.data.deducted_so_far").value(20000.0))
                .andExpect(jsonPath("$.data.months_left").value(12))
                .andExpect(jsonPath("$.data.monthly_share").value(8333.33))
                .andExpect(jsonPath("$.data.effective_from_period").value("2026-04"));

        verify(service, never()).record(any(), any(), any());
    }

    @Test
    @DisplayName("verify: PUT is 200 with the record; a missing figure 400; a lost race 409; unknown 404")
    void verifyRecords() throws Exception {
        given(permissionService.holds(VERIFY)).willReturn(true);
        UUID employeeId = UUID.randomUUID();
        UUID raced = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        given(service.record(eq(employeeId), eq(FY), any())).willReturn(sample(employeeId));
        given(service.yearToDate(employeeId, FY)).willReturn(BigDecimal.ZERO.setScale(4));
        given(service.record(eq(raced), eq(FY), any())).willThrow(new EmployeeTdsConflictException(raced, FY));
        given(service.record(eq(missing), eq(FY), any())).willThrow(new EmployeeService.NotFoundException(missing));

        mvc.perform(put("/api/v1/payroll/employees/{id}/tds/{fy}", employeeId, FY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employee_id").value(employeeId.toString()))
                .andExpect(jsonPath("$.data.regime").value("NEW"))
                .andExpect(jsonPath("$.data.is_active").value(true))
                .andExpect(jsonPath("$.data.remaining").value(120000.0));
        mvc.perform(put("/api/v1/payroll/employees/{id}/tds/{fy}", employeeId, FY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"regime\":\"NEW\",\"annual_gross\":1200000,\"annual_taxable_income\":1000000}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/payroll/employees/{id}/tds/{fy}", raced, FY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/payroll/employees/{id}/tds/{fy}", missing, FY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("No grant at all: 403 on the officer read and on history")
    void noGrantIsForbidden() throws Exception {
        UUID employeeId = UUID.randomUUID();
        given(service.history(employeeId, FY)).willReturn(List.of());

        mvc.perform(get("/api/v1/payroll/employees/{id}/tds/{fy}", employeeId, FY))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payroll/employees/{id}/tds/{fy}/history", employeeId, FY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName(
            "D-151: read alone cannot apply or clear a suggestion; GET shows the suggestion beside the officer's figure")
    void suggestionNeedsVerify() throws Exception {
        given(permissionService.holds(READ)).willReturn(true);
        UUID employeeId = UUID.randomUUID();
        EmployeeTds record = sample(employeeId);
        record.suggest(
                TaxRegime.NEW,
                null,
                new BigDecimal("1300000.00"),
                new BigDecimal("1100000.00"),
                new BigDecimal("90000.00"),
                "SALARY_REVISION x",
                Instant.parse("2026-11-24T05:00:00Z"),
                "system");
        given(service.active(employeeId, FY)).willReturn(Optional.of(record));
        given(service.yearToDate(employeeId, FY)).willReturn(BigDecimal.ZERO);

        mvc.perform(post("/api/v1/payroll/employees/{id}/tds/{fy}/suggestion/apply", employeeId, FY))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/payroll/employees/{id}/tds/{fy}/suggestion", employeeId, FY))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payroll/employees/{id}/tds/{fy}", employeeId, FY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.annual_tax").value(120000.0))
                .andExpect(jsonPath("$.data.source").value("OFFICER"))
                .andExpect(jsonPath("$.data.suggestion.annual_tax").value(90000.0))
                .andExpect(jsonPath("$.data.suggestion.note").value("SALARY_REVISION x"));

        verify(service, never()).applySuggestion(any(), any());
        verify(service, never()).clearSuggestion(any(), any());
    }

    @Test
    @DisplayName("D-151: verify applies and clears a suggestion; no suggestion to apply is 404")
    void verifyAppliesAndClears() throws Exception {
        given(permissionService.holds(VERIFY)).willReturn(true);
        UUID employeeId = UUID.randomUUID();
        UUID none = UUID.randomUUID();
        given(service.applySuggestion(employeeId, FY)).willReturn(sample(employeeId));
        given(service.clearSuggestion(employeeId, FY)).willReturn(sample(employeeId));
        given(service.yearToDate(employeeId, FY)).willReturn(BigDecimal.ZERO);
        given(service.applySuggestion(none, FY))
                .willThrow(new com.infinevo.payroll.tds.exception.EmployeeTdsNotFoundException("no suggestion"));

        mvc.perform(post("/api/v1/payroll/employees/{id}/tds/{fy}/suggestion/apply", employeeId, FY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employee_id").value(employeeId.toString()));
        mvc.perform(delete("/api/v1/payroll/employees/{id}/tds/{fy}/suggestion", employeeId, FY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestion").doesNotExist());
        mvc.perform(post("/api/v1/payroll/employees/{id}/tds/{fy}/suggestion/apply", none, FY))
                .andExpect(status().isNotFound());
    }

    private static EmployeeTds sample(UUID employeeId) {
        return new EmployeeTds(
                TENANT,
                employeeId,
                FY,
                TaxRegime.NEW,
                TdsSource.OFFICER,
                null,
                new BigDecimal("1200000.0000"),
                new BigDecimal("1000000.0000"),
                new BigDecimal("120000.0000"),
                "2026-04",
                null,
                "officer",
                Instant.now());
    }
}
