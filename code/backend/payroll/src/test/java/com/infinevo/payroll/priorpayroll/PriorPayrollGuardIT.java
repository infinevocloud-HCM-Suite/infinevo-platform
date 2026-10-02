package com.infinevo.payroll.priorpayroll;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * W-38.1 §7: Guard integration test for {@link PriorPayrollController}.
 * Covers:
 * - without payroll.run.execute, POST import and DELETE are 403
 * - without payroll.run.read, every GET is 403
 * - the template is text/csv with the 8 headers
 */
class PriorPayrollGuardIT {

    private static final String READ = "payroll.run.read";
    private static final String EXECUTE = "payroll.run.execute";

    private PriorPayrollImportService importService;
    private PriorPayrollService priorPayrollService;
    private PermissionService permissionService;
    private MockMvc mvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        importService = mock(PriorPayrollImportService.class);
        priorPayrollService = mock(PriorPayrollService.class);
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

        AspectJProxyFactory factory =
                new AspectJProxyFactory(new PriorPayrollController(importService, priorPayrollService));
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permissionService));
        PriorPayrollController controller = factory.getProxy();

        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AuthzExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(
                        new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/payroll/prior-payroll/template returns 403 without payroll.run.read")
    void templateWithoutReadIsForbidden() throws Exception {
        when(permissionService.holds(READ)).thenReturn(false);

        mvc.perform(get("/api/v1/payroll/prior-payroll/template")).andExpect(status().isForbidden());

        verify(importService, never()).template();
    }

    @Test
    @DisplayName("GET /api/v1/payroll/prior-payroll/template returns 200 text/csv with 8 headers")
    void templateWithReadSucceeds() throws Exception {
        when(permissionService.holds(READ)).thenReturn(true);
        String expectedHeader =
                "employee_number,period,gross_earnings,epf_employee,esi_employee,professional_tax,tds,net_pay\n";
        when(importService.template()).thenReturn(expectedHeader);

        mvc.perform(get("/api/v1/payroll/prior-payroll/template"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=prior-payroll-template.csv"))
                .andExpect(content().string(expectedHeader));
    }

    @Test
    @DisplayName("POST /api/v1/payroll/prior-payroll-imports returns 403 without payroll.run.execute")
    void importWithoutExecuteIsForbidden() throws Exception {
        when(permissionService.holds(EXECUTE)).thenReturn(false);

        PriorPayrollImportRequest request = new PriorPayrollImportRequest(UUID.randomUUID(), "2026-2027", false);

        mvc.perform(post("/api/v1/payroll/prior-payroll-imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(importService, never()).importFile(any(), any(), any(Boolean.class));
    }

    @Test
    @DisplayName("POST /api/v1/payroll/prior-payroll-imports succeeds with payroll.run.execute")
    void importWithExecuteSucceeds() throws Exception {
        when(permissionService.holds(EXECUTE)).thenReturn(true);
        UUID docId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        PriorPayrollImportRequest request = new PriorPayrollImportRequest(docId, "2026-2027", false);
        PriorPayrollImportResponse response = new PriorPayrollImportResponse(
                importId,
                PriorPayrollImportStatus.COMPLETED,
                false,
                "2026-2027",
                10,
                10,
                0,
                docId,
                null,
                Instant.now(),
                Instant.now());
        when(importService.importFile(docId, "2026-2027", false)).thenReturn(response);

        mvc.perform(post("/api/v1/payroll/prior-payroll-imports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/payroll/prior-payroll/{id} returns 403 without payroll.run.execute")
    void deleteWithoutExecuteIsForbidden() throws Exception {
        when(permissionService.holds(EXECUTE)).thenReturn(false);
        UUID id = UUID.randomUUID();

        mvc.perform(delete("/api/v1/payroll/prior-payroll/" + id)).andExpect(status().isForbidden());

        verify(priorPayrollService, never()).delete(any());
    }

    @Test
    @DisplayName("DELETE /api/v1/payroll/prior-payroll/{id} returns 204 with payroll.run.execute")
    void deleteWithExecuteSucceeds() throws Exception {
        when(permissionService.holds(EXECUTE)).thenReturn(true);
        UUID id = UUID.randomUUID();

        mvc.perform(delete("/api/v1/payroll/prior-payroll/" + id)).andExpect(status().isNoContent());

        verify(priorPayrollService).delete(id);
    }

    @Test
    @DisplayName("every GET endpoint returns 403 without payroll.run.read")
    void allGetEndpointsForbiddenWithoutRead() throws Exception {
        when(permissionService.holds(READ)).thenReturn(false);
        UUID id = UUID.randomUUID();

        mvc.perform(get("/api/v1/payroll/prior-payroll-imports/" + id)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payroll/prior-payroll-imports")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payroll/prior-payroll?fy=2026-2027")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payroll/prior-payroll/status?fy=2026-2027")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("every GET endpoint succeeds with payroll.run.read")
    void allGetEndpointsSucceedWithRead() throws Exception {
        when(permissionService.holds(READ)).thenReturn(true);
        UUID id = UUID.randomUUID();

        when(importService.getImport(id))
                .thenReturn(new PriorPayrollImportResponse(
                        id,
                        PriorPayrollImportStatus.COMPLETED,
                        false,
                        "2026-2027",
                        0,
                        0,
                        0,
                        UUID.randomUUID(),
                        null,
                        Instant.now(),
                        null));
        when(importService.listImports(any()))
                .thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 10), 0));
        when(priorPayrollService.months(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 10), 0));
        when(priorPayrollService.status("2026-2027"))
                .thenReturn(new PriorPayrollStatusResponse("2026-2027", null, List.of(), List.of(), false));

        mvc.perform(get("/api/v1/payroll/prior-payroll-imports/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/payroll/prior-payroll-imports")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/payroll/prior-payroll?fy=2026-2027")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/payroll/prior-payroll/status?fy=2026-2027")).andExpect(status().isOk());
    }
}
