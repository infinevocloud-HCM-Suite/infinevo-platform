package com.infinevo.payroll.payrun;

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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.payroll.schedule.NoPayScheduleException;
import com.infinevo.shared.authz.AuthzExceptionHandler;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresActionAspect;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
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
 * W-29.1 §7 — the controller behind the real {@link RequiresActionAspect}: {@code payroll.run.read}
 * alone gets {@code 403} on create, lock and cancel and never reaches the service; the error mapping
 * of §4 ({@code 201}, {@code 409}, {@code 400}). The service is mocked — the data paths are the other
 * ITs'. {@code TaxDeclarationControllerTest}'s shape.
 */
class PayRunGuardIT {

    private static final String READ = "payroll.run.read";
    private static final String EXECUTE = "payroll.run.execute";

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

        AspectJProxyFactory factory = new AspectJProxyFactory(new PayRunController(payRunService));
        factory.setProxyTargetClass(true);
        factory.addAspect(new RequiresActionAspect(permissionService));
        PayRunController controller = factory.getProxy();

        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AuthzExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("payroll.run.read alone: 403 on create, lock and cancel; the service is never called")
    void readOnlyCannotExecute() throws Exception {
        given(permissionService.holds(READ)).willReturn(true);
        given(permissionService.holds(EXECUTE)).willReturn(false);
        UUID id = UUID.randomUUID();

        mvc.perform(post("/api/v1/payroll/payruns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"2026-04\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/v1/payroll/payruns/{id}/lock", id)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/payroll/payruns/{id}/cancel", id)).andExpect(status().isForbidden());

        verify(payRunService, never()).create(any());
        verify(payRunService, never()).lock(any());
        verify(payRunService, never()).cancel(any());
    }

    @Test
    @DisplayName("payroll.run.read reaches GET; without it GET is 403")
    void readGuardsGet() throws Exception {
        UUID id = UUID.randomUUID();
        given(payRunService.get(id)).willReturn(sample(id));

        given(permissionService.holds(READ)).willReturn(false);
        mvc.perform(get("/api/v1/payroll/payruns/{id}", id)).andExpect(status().isForbidden());

        given(permissionService.holds(READ)).willReturn(true);
        mvc.perform(get("/api/v1/payroll/payruns/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.period").value("2026-04"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("payroll.run.execute: create is 201; a duplicate or missing schedule is 409; a bad period is 400")
    void executeCreatesAndMapsErrors() throws Exception {
        given(permissionService.holds(EXECUTE)).willReturn(true);
        given(payRunService.create(YearMonth.of(2026, 4))).willReturn(sample(UUID.randomUUID()));
        given(payRunService.create(YearMonth.of(2026, 5)))
                .willThrow(new DuplicatePayRunException(YearMonth.of(2026, 5)));
        given(payRunService.create(YearMonth.of(2026, 6))).willThrow(new NoPayScheduleException("no schedule"));

        mvc.perform(post("/api/v1/payroll/payruns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"2026-04\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.included_count").value(3));
        mvc.perform(post("/api/v1/payroll/payruns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"2026-05\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(post("/api/v1/payroll/payruns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"2026-06\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/payroll/payruns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"April 2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("An illegal transition is 409; an unknown run is 404")
    void transitionAndNotFound() throws Exception {
        given(permissionService.holds(EXECUTE)).willReturn(true);
        UUID locked = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        given(payRunService.lock(locked))
                .willThrow(new IllegalPayRunTransitionException(PayRunStatus.LOCKED, PayRunStatus.LOCKED));
        given(payRunService.cancel(missing)).willThrow(new PayRunNotFoundException(missing));

        mvc.perform(post("/api/v1/payroll/payruns/{id}/lock", locked)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/payroll/payruns/{id}/cancel", missing)).andExpect(status().isNotFound());
    }

    private static PayRunResponse sample(UUID id) {
        return new PayRunResponse(
                id,
                "2026-04",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 4, 25),
                LocalDate.of(2026, 4, 30),
                PayRunType.REGULAR,
                PayRunStatus.DRAFT,
                3,
                1,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
    }
}
