package com.infinevo.payroll.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.infinevo.core.lop.LopPolicyController;
import com.infinevo.core.lop.LopPolicyService;
import com.infinevo.core.lop.NoLopPolicyException;
import com.infinevo.core.lop.WorkingDayBasisCalculator;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * HTTP contract of the pay schedule endpoints (W-28 §4): every validation refusal is a {@code 400},
 * the wire keys are the spec's snake_case names, and a missing schedule is a {@code 409} — on this
 * controller and on {@code core}'s {@code /api/v1/lop-policy/basis}, which reads the work week
 * through {@link PayScheduleWorkingWeekSource}.
 */
class PayScheduleControllerTest {

    private final UUID tenantId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private PayScheduleRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository = mock(PayScheduleRepository.class);
        PayScheduleController controller =
                new PayScheduleController(new PayScheduleServiceImpl(repository), new PayPeriodServiceImpl(repository));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(jsonConverter())
                .build();
        TenantContext.set(tenantId);
    }

    /** ISO dates, as Spring Boot's auto-configured ObjectMapper writes them. */
    private static MappingJackson2HttpMessageConverter jsonConverter() {
        return new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── F-3: validation refusals, each 400 ───────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                // empty working_days
                "{\"working_days\":[],\"pay_day_rule\":\"LAST_WORKING_DAY\",\"input_cutoff_day\":25,"
                        + "\"first_period_start\":\"2026-01-01\"}",
                // duplicate working_days
                "{\"working_days\":[1,2,2],\"pay_day_rule\":\"LAST_WORKING_DAY\",\"input_cutoff_day\":25,"
                        + "\"first_period_start\":\"2026-01-01\"}",
                // SPECIFIC_DAY without pay_day_of_month
                "{\"working_days\":[1,2,3,4,5],\"pay_day_rule\":\"SPECIFIC_DAY\",\"input_cutoff_day\":25,"
                        + "\"first_period_start\":\"2026-01-01\"}",
                // pay_day_of_month with another rule
                "{\"working_days\":[1,2,3,4,5],\"pay_day_rule\":\"LAST_DAY_OF_PERIOD\",\"pay_day_of_month\":5,"
                        + "\"input_cutoff_day\":25,\"first_period_start\":\"2026-01-01\"}",
                // cut-off below 1
                "{\"working_days\":[1,2,3,4,5],\"pay_day_rule\":\"LAST_WORKING_DAY\",\"input_cutoff_day\":0,"
                        + "\"first_period_start\":\"2026-01-01\"}",
                // cut-off above 28
                "{\"working_days\":[1,2,3,4,5],\"pay_day_rule\":\"LAST_WORKING_DAY\",\"input_cutoff_day\":29,"
                        + "\"first_period_start\":\"2026-01-01\"}",
                // first_period_start not on the 1st
                "{\"working_days\":[1,2,3,4,5],\"pay_day_rule\":\"LAST_WORKING_DAY\",\"input_cutoff_day\":25,"
                        + "\"first_period_start\":\"2026-01-15\"}"
            })
    @DisplayName("PUT with an invalid schedule is 400 and writes nothing")
    void invalidSchedule_is400(String body) throws Exception {
        mvc.perform(put("/api/v1/payroll/pay-schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(repository, never()).save(any());
    }

    // ── F-8: one spelling per key ────────────────────────────────────────────

    @Test
    @DisplayName("PUT accepts the snake_case keys and the response echoes them, with no camelCase duplicates")
    void put_roundTripsSnakeCaseKeys() throws Exception {
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(repository.save(any(PaySchedule.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(put("/api/v1/payroll/pay-schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"working_days\":[1,2,3,4,5],\"pay_day_rule\":\"SPECIFIC_DAY\","
                                + "\"pay_day_of_month\":5,\"input_cutoff_day\":20,"
                                + "\"first_period_start\":\"2026-01-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.working_days.length()").value(5))
                .andExpect(jsonPath("$.pay_day_rule").value("SPECIFIC_DAY"))
                .andExpect(jsonPath("$.pay_day_of_month").value(5))
                .andExpect(jsonPath("$.input_cutoff_day").value(20))
                .andExpect(jsonPath("$.first_period_start").value("2026-01-01"))
                .andExpect(jsonPath("$.tenant_id").value(tenantId.toString()))
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.workingDays").doesNotExist())
                .andExpect(jsonPath("$.payDayRule").doesNotExist());
    }

    @Test
    @DisplayName("GET period serialises cutoff_date and pay_date once each")
    void period_usesSnakeCaseKeysOnly() throws Exception {
        when(repository.findByTenantId(tenantId))
                .thenReturn(Optional.of(new PaySchedule(
                        tenantId,
                        new Short[] {1, 2, 3, 4, 5},
                        PayDayRule.LAST_DAY_OF_PERIOD,
                        null,
                        (short) 25,
                        LocalDate.of(2026, 1, 1))));

        mvc.perform(get("/api/v1/payroll/pay-schedule/period").param("period", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start").value("2026-07-01"))
                .andExpect(jsonPath("$.end").value("2026-07-31"))
                .andExpect(jsonPath("$.cutoff_date").value("2026-07-25"))
                .andExpect(jsonPath("$.pay_date").value("2026-07-31"))
                .andExpect(jsonPath("$.cutoffDate").doesNotExist())
                .andExpect(jsonPath("$.payDate").doesNotExist());
    }

    @Test
    @DisplayName("GET period with no schedule is 409")
    void period_noSchedule_is409() throws Exception {
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/payroll/pay-schedule/period").param("period", "2026-07"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    // ── W-28 F-1: /basis sees a missing schedule as 409 ──────────────────────

    @Test
    @DisplayName("NoPayScheduleException is a NoLopPolicyException")
    void noPayScheduleException_isNoLopPolicyException() {
        org.assertj.core.api.Assertions.assertThat(new NoPayScheduleException("x"))
                .isInstanceOf(NoLopPolicyException.class);
    }

    @Test
    @DisplayName("GET /api/v1/lop-policy/basis for an ORG_DAYS tenant with no pay schedule is 409, not 500")
    void basis_noPaySchedule_is409() throws Exception {
        LopPolicyService policyService = mock(LopPolicyService.class);
        WorkingDayBasisCalculator calculator = mock(WorkingDayBasisCalculator.class);
        when(calculator.basisFor(any(YearMonth.class), isNull()))
                .thenThrow(new NoPayScheduleException("No pay schedule configured for tenant " + tenantId));
        MockMvc lopMvc = MockMvcBuilders.standaloneSetup(new LopPolicyController(policyService, calculator))
                .setMessageConverters(jsonConverter())
                .build();

        lopMvc.perform(get("/api/v1/lop-policy/basis").param("period", "2026-07"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("No pay schedule configured for tenant " + tenantId));
    }
}
