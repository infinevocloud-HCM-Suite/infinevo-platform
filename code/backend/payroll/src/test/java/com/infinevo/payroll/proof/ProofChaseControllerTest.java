package com.infinevo.payroll.proof;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller unit tests for {@link ProofChaseController} (W-34.3 spec section 4).
 */
class ProofChaseControllerTest {

    private static final String FY = "2026-2027";
    private static final String BASE_URL = "/api/v1/payroll/proof-of-investment";

    private ProofChaseService proofChaseService;
    private PermissionService permissionService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        proofChaseService = mock(ProofChaseService.class);
        permissionService = mock(PermissionService.class);

        given(permissionService.holds(any())).willReturn(true);
        doAnswer(invocation -> {
                    String action = invocation.getArgument(0);
                    if (!permissionService.holds(action)) {
                        throw new PermissionDeniedException(action);
                    }
                    return null;
                })
                .when(permissionService)
                .require(any());

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        var converter = new MappingJackson2HttpMessageConverter(mapper);
        var controller = new ProofChaseController(proofChaseService);

        mvc = MockMvcBuilders.standaloneSetup(proxy(controller))
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permissionService));
        return (T) factory.getProxy();
    }

    @Test
    @DisplayName("GET /api/v1/payroll/proof-of-investment returns 200 with paged chase rows")
    void getChaseListReturnsPagedRows() throws Exception {
        UUID empId = UUID.randomUUID();
        ProofChaseRow row = new ProofChaseRow(
                empId, "EMP-001", "Alice Smith", "NEW", "NOT_STARTED", null, null, BigDecimal.ZERO, null);

        given(proofChaseService.list(eq(FY), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 25), 1));

        mvc.perform(get(BASE_URL).param("fy", FY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].employee_id").value(empId.toString()))
                .andExpect(jsonPath("$.data.content[0].number").value("EMP-001"))
                .andExpect(jsonPath("$.data.content[0].name").value("Alice Smith"))
                .andExpect(jsonPath("$.data.content[0].tax_regime").value("NEW"))
                .andExpect(jsonPath("$.data.content[0].proof_status").value("NOT_STARTED"));
    }

    @Test
    @DisplayName("GET /api/v1/payroll/proof-of-investment returns 400 when fy is missing")
    void getChaseListMissingFyReturnsBadRequest() throws Exception {
        mvc.perform(get(BASE_URL)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/payroll/proof-of-investment clamps page size to 100 and negative page to 0")
    void getChaseListClampsPaging() throws Exception {
        given(proofChaseService.list(eq(FY), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mvc.perform(get(BASE_URL).param("fy", FY).param("page", "-5").param("size", "500"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(proofChaseService).list(eq(FY), any(), any(), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(pageable.getPageNumber()).isEqualTo(0);
        org.assertj.core.api.Assertions.assertThat(pageable.getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("GET /api/v1/payroll/proof-of-investment returns 403 when caller lacks payroll.proof.read")
    void getChaseListRefusesWithoutPermission() throws Exception {
        given(permissionService.holds("payroll.proof.read")).willReturn(false);

        mvc.perform(get(BASE_URL).param("fy", FY)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/payroll/proof-of-investment/summary returns 200 with summary data")
    void getSummaryReturnsData() throws Exception {
        ProofChaseSummary summary = new ProofChaseSummary(10, 3, 5, 7, 1, LocalDate.of(2027, 1, 31), true);

        given(proofChaseService.summary(FY)).willReturn(summary);

        mvc.perform(get(BASE_URL + "/summary").param("fy", FY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.not_started").value(10))
                .andExpect(jsonPath("$.data.draft").value(3))
                .andExpect(jsonPath("$.data.submitted").value(5))
                .andExpect(jsonPath("$.data.approved").value(7))
                .andExpect(jsonPath("$.data.rejected").value(1))
                .andExpect(jsonPath("$.data.due_date").value("2027-01-31"))
                .andExpect(jsonPath("$.data.proof_open").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/payroll/proof-of-investment/summary returns 403 when caller lacks payroll.proof.read")
    void getSummaryRefusesWithoutPermission() throws Exception {
        given(permissionService.holds("payroll.proof.read")).willReturn(false);

        mvc.perform(get(BASE_URL + "/summary").param("fy", FY)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/payroll/proof-of-investment returns 400 when fy is invalid format")
    void getChaseListInvalidFyReturnsBadRequest() throws Exception {
        given(proofChaseService.list(eq("invalid-fy"), any(), any(), any()))
                .willThrow(
                        new IllegalArgumentException("Invalid financial year format: invalid-fy (expected YYYY-YYYY)"));

        mvc.perform(get(BASE_URL).param("fy", "invalid-fy"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET /api/v1/payroll/proof-of-investment/summary returns 400 when fy is invalid format")
    void getSummaryInvalidFyReturnsBadRequest() throws Exception {
        given(proofChaseService.summary(eq("invalid-fy")))
                .willThrow(
                        new IllegalArgumentException("Invalid financial year format: invalid-fy (expected YYYY-YYYY)"));

        mvc.perform(get(BASE_URL + "/summary").param("fy", "invalid-fy"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
