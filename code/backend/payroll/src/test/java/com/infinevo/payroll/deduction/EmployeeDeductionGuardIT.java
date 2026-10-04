package com.infinevo.payroll.deduction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;

/**
 * W-35.2 §4 — the five mappings behind the real {@link RequiresActionAspect}: {@code read} alone cannot
 * enter or reverse; {@code manage} can; {@code read_own} reaches {@code /me}; the error mapping
 * ({@code 201}, {@code 400} naming the line, {@code 404}, {@code 409}, {@code 403} for a login with no
 * employee); and no edit mapping at all (§9). The service is mocked — the data paths are the other ITs'.
 */
class EmployeeDeductionGuardIT {

    private static final String READ = "payroll.employee_deduction.read";
    private static final String READ_OWN = "payroll.employee_deduction.read_own";
    private static final String MANAGE = "payroll.employee_deduction.manage";
    private static final String BATCH = "[{\"employee_id\":\"%s\",\"period\":\"2026-10\","
            + "\"deduction_type\":\"ADVANCE_RECOVERY\",\"amount\":1500,\"reason\":\"Advance\"}]";

    private EmployeeDeductionService service;
    private PermissionService permissionService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(EmployeeDeductionService.class);
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

        AspectJProxyFactory factory = new AspectJProxyFactory(new EmployeeDeductionController(service));
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permissionService));
        mvc = MockMvcBuilders.standaloneSetup((EmployeeDeductionController) factory.getProxy())
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    @Test
    @DisplayName("read alone: 403 on enter and reverse, and the service is never called; GET works")
    void readCannotManage() throws Exception {
        given(permissionService.holds(READ)).willReturn(true);
        UUID id = UUID.randomUUID();
        given(service.get(id)).willReturn(sample(id, DeductionState.POSTED));

        mvc.perform(post("/api/v1/payroll/employee-deductions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BATCH.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/payroll/employee-deductions/{id}", id).param("reason", "x"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payroll/employee-deductions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"));

        verify(service, never()).enter(any());
        verify(service, never()).reverse(any(), any());
    }

    @Test
    @DisplayName("manage: enter is 201 with the rows; reverse is 200 REVERSED, 409 a second time, 404 unknown")
    void manageEntersAndReverses() throws Exception {
        given(permissionService.holds(MANAGE)).willReturn(true);
        UUID id = UUID.randomUUID();
        UUID twice = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        given(service.enter(any()))
                .willReturn(EmployeeDeductionBatchResponse.of(List.of(sample(id, DeductionState.POSTED))));
        given(service.reverse(id, "entered twice")).willReturn(sample(id, DeductionState.REVERSED));
        given(service.reverse(eq(twice), anyString())).willThrow(new DeductionAlreadyReversedException(twice));
        given(service.reverse(eq(missing), anyString())).willThrow(new EmployeeDeductionNotFoundException(missing));

        mvc.perform(post("/api/v1/payroll/employee-deductions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BATCH.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.rows[0].posted_period").value("2026-10"));
        mvc.perform(delete("/api/v1/payroll/employee-deductions/{id}", id).param("reason", "entered twice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        mvc.perform(delete("/api/v1/payroll/employee-deductions/{id}", twice).param("reason", "x"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/payroll/employee-deductions/{id}", missing).param("reason", "x"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/payroll/employee-deductions/{id}", id)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A bad line is 400 naming it; a malformed body is 400")
    void validationIs400() throws Exception {
        given(permissionService.holds(MANAGE)).willReturn(true);
        given(service.enter(any()))
                .willThrow(new EmployeeDeductionValidationException(2, "amount must be greater than 0"));

        mvc.perform(post("/api/v1/payroll/employee-deductions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BATCH.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Line 2: amount must be greater than 0"))
                .andExpect(jsonPath("$.fieldErrors.line").value("2"));
        mvc.perform(post("/api/v1/payroll/employee-deductions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("read_own reaches /me; without it 403; a login with no employee is 403")
    void ownGuard() throws Exception {
        given(permissionService.holds(READ_OWN)).willReturn(false);
        mvc.perform(get("/api/v1/me/employee-deductions")).andExpect(status().isForbidden());

        given(permissionService.holds(READ_OWN)).willReturn(true);
        given(service.listOwn()).willReturn(List.of(sample(UUID.randomUUID(), DeductionState.POSTED)));
        mvc.perform(get("/api/v1/me/employee-deductions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].deduction_type").value("ADVANCE_RECOVERY"));

        given(service.listOwn()).willThrow(new AccessDeniedException("No employee profile linked"));
        mvc.perform(get("/api/v1/me/employee-deductions")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("No edit: PUT is not mapped, and the controller declares no PUT or PATCH handler")
    void noEdit() throws Exception {
        given(permissionService.holds(any())).willReturn(true);
        mvc.perform(put("/api/v1/payroll/employee-deductions/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        assertThat(Arrays.stream(EmployeeDeductionController.class.getDeclaredMethods())
                        .filter(m ->
                                m.isAnnotationPresent(PutMapping.class) || m.isAnnotationPresent(PatchMapping.class))
                        .map(Method::getName))
                .isEmpty();
    }

    private static EmployeeDeductionResponse sample(UUID id, DeductionState state) {
        boolean reversed = state == DeductionState.REVERSED;
        return new EmployeeDeductionResponse(
                id,
                UUID.randomUUID(),
                "First Last",
                "2026-10",
                DeductionType.ADVANCE_RECOVERY,
                new BigDecimal("1500.0000"),
                "Advance",
                null,
                null,
                state,
                UUID.randomUUID(),
                "2026-10",
                reversed ? UUID.randomUUID() : null,
                reversed ? Instant.now() : null,
                null,
                Instant.now(),
                "officer");
    }
}
