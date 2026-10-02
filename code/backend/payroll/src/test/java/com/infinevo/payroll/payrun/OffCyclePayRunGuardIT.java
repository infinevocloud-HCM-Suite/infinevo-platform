package com.infinevo.payroll.payrun;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * W-30.2 §7 — the two off-cycle endpoints behind the real {@link RequiresActionAspect}:
 * {@code payroll.run.read} alone is {@code 403} on both; {@code payroll.run.execute} without
 * {@code core.pay_input.write} is {@code 403} on inputs; the §4 error mapping; and the list's
 * {@code runType} filter. The service is mocked — the data paths are the other ITs'.
 */
class OffCyclePayRunGuardIT {

    private static final String READ = "payroll.run.read";
    private static final String EXECUTE = "payroll.run.execute";
    private static final String PAY_INPUT_WRITE = "core.pay_input.write";
    private static final String INPUTS =
            "[{\"employee_id\":\"%s\",\"kind\":\"ONE_TIME_PAYOUT\"," + "\"amount\":10000,\"source_ref\":\"bonus-1\"}]";

    private PayRunService payRunService;
    private PermissionService permissionService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        payRunService = mock(PayRunService.class);
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

        mvc = MockMvcBuilders.standaloneSetup(
                        guarded(new OffCyclePayRunController(payRunService, permissionService)),
                        guarded(new PayRunController(payRunService)))
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)))
                .build();
    }

    @Test
    @DisplayName("payroll.run.read alone: 403 on create and on inputs; the service is never called")
    void readOnlyCannotExecute() throws Exception {
        given(permissionService.holds(READ)).willReturn(true);
        UUID id = UUID.randomUUID();

        mvc.perform(post("/api/v1/payroll/payruns/off-cycle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pay_date\":\"2026-04-15\",\"employee_ids\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/v1/payroll/payruns/{id}/inputs", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INPUTS.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());

        verify(payRunService, never()).createOffCycle(any(), any(), any());
        verify(payRunService, never()).addInputs(any(), any());
    }

    @Test
    @DisplayName("payroll.run.execute without core.pay_input.write: 403 on inputs")
    void inputsNeedPayInputWrite() throws Exception {
        given(permissionService.holds(EXECUTE)).willReturn(true);
        given(permissionService.holds(PAY_INPUT_WRITE)).willReturn(false);

        mvc.perform(post("/api/v1/payroll/payruns/{id}/inputs", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INPUTS.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verify(payRunService, never()).addInputs(any(), any());
    }

    @Test
    @DisplayName("Both actions: create and inputs are 201 with the body the service returns")
    void executeAndWriteSucceed() throws Exception {
        given(permissionService.holds(EXECUTE)).willReturn(true);
        given(permissionService.holds(PAY_INPUT_WRITE)).willReturn(true);
        UUID id = UUID.randomUUID();
        UUID employee = UUID.randomUUID();
        UUID payInput = UUID.randomUUID();
        given(payRunService.createOffCycle(LocalDate.of(2026, 4, 15), List.of(employee), "Bonus"))
                .willReturn(sample(id));
        given(payRunService.addInputs(
                        id,
                        List.of(new PayRunInputRequest(
                                employee, PayInputKind.ONE_TIME_PAYOUT, new BigDecimal("10000"), "bonus-1"))))
                .willReturn(List.of(PayRunInputResponse.recorded(employee, "bonus-1", payInput)));

        mvc.perform(post("/api/v1/payroll/payruns/off-cycle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pay_date\":\"2026-04-15\",\"employee_ids\":[\"" + employee
                                + "\"],\"notes\":\"Bonus\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.run_type").value("OFF_CYCLE"))
                .andExpect(jsonPath("$.data.notes").value("Bonus"));
        mvc.perform(post("/api/v1/payroll/payruns/{id}/inputs", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INPUTS.formatted(employee)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].result").value("RECORDED"))
                .andExpect(jsonPath("$.data[0].pay_input_id").value(payInput.toString()));
    }

    @Test
    @DisplayName(
            "Errors: 400 for a named employee not payable or a bad item, 409 for a wrong run, a lock or no schedule")
    void errorMapping() throws Exception {
        given(permissionService.holds(EXECUTE)).willReturn(true);
        given(permissionService.holds(PAY_INPUT_WRITE)).willReturn(true);
        UUID employee = UUID.randomUUID();
        UUID regular = UUID.randomUUID();
        UUID locked = UUID.randomUUID();
        UUID bad = UUID.randomUUID();
        given(payRunService.createOffCycle(any(), any(), any()))
                .willThrow(new EmployeeNotInRunException("No such employee in this tenant", List.of(employee)));
        given(payRunService.addInputs(eq(regular), any()))
                .willThrow(new NotAnOffCycleRunException(regular, PayRunType.REGULAR, PayRunStatus.DRAFT));
        given(payRunService.addInputs(eq(locked), any())).willThrow(new PayInputService.RunLockedException(locked));
        given(payRunService.addInputs(eq(bad), any()))
                .willThrow(new IllegalArgumentException("inputs[0].kind LOP_DAYS is not allowed"));

        mvc.perform(post("/api/v1/payroll/payruns/off-cycle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pay_date\":\"2026-04-15\",\"employee_ids\":[\"" + employee + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/payroll/payruns/{id}/inputs", regular)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INPUTS.formatted(employee)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/payroll/payruns/{id}/inputs", locked)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INPUTS.formatted(employee)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/payroll/payruns/{id}/inputs", bad)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INPUTS.formatted(employee)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/payroll/payruns/off-cycle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pay_date\":\"15 April\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /payruns?runType=OFF_CYCLE passes the filter to the service")
    void listFiltersByRunType() throws Exception {
        given(permissionService.holds(READ)).willReturn(true);
        Page<PayRunResponse> page = new PageImpl<>(List.of(sample(UUID.randomUUID())), PageRequest.of(0, 25), 1);
        given(payRunService.list(eq(null), eq(PayRunType.OFF_CYCLE), any())).willReturn(page);

        mvc.perform(get("/api/v1/payroll/payruns").param("runType", "OFF_CYCLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].run_type").value("OFF_CYCLE"));
    }

    private <T> T guarded(T controller) {
        AspectJProxyFactory factory = new AspectJProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permissionService));
        return factory.getProxy();
    }

    private static PayRunResponse sample(UUID id) {
        return new PayRunResponse(
                id,
                "2026-04",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 4, 25),
                LocalDate.of(2026, 4, 15),
                PayRunType.OFF_CYCLE,
                "Bonus",
                PayRunStatus.DRAFT,
                1,
                0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                0,
                0,
                Instant.now(),
                Instant.now());
    }
}
