package com.infinevo.core.notification;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit test for {@link ReminderRuleController} (W-20.2 §7).
 *
 * <p>Validates that:
 * <ul>
 *   <li>an unknown audience is refused with {@code 400}
 *   <li>an anchor outside the enum is refused with {@code 400}
 *   <li>an event whose placeholders a reminder cannot supply is refused with {@code 400}
 *   <li>an anchor nothing in the runtime supplies a date for is refused with {@code 400}
 *   <li>valid rules succeed with {@code 201}, {@code 200}, and {@code 204}
 * </ul>
 */
class ReminderRuleControllerTest {

    private MockMvc mvc;
    private ReminderRuleRepository repository;
    private ReminderAudienceResolver audienceResolver;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(ReminderRuleRepository.class);
        audienceResolver = mock(ReminderAudienceResolver.class);
        when(audienceResolver.audience()).thenReturn("SUBJECT");
        ReminderRuleService service = new ReminderRuleServiceImpl(repository, List.of(audienceResolver));
        ReminderRuleController controller = new ReminderRuleController(service);

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(
                        new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(mapper))
                .build();
        TenantContext.set(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void unknownAudienceIsRefusedWith400() throws Exception {
        String body =
                """
            {
              "event": "TIMESHEET_REMINDER",
              "audience": "UNKNOWN_AUDIENCE",
              "anchor": "WEEKLY",
              "offset_days": 0,
              "day_of_week": 5,
              "send_at_local_time": "09:00:00"
            }
            """;

        mvc.perform(post("/api/v1/reminder-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.audience", containsString("Unknown audience")));
    }

    @Test
    void anchorOutsideEnumIsRefusedWith400() throws Exception {
        String body =
                """
            {
              "event": "TIMESHEET_REMINDER",
              "audience": "SUBJECT",
              "anchor": "NOT_AN_ANCHOR",
              "offset_days": 0,
              "day_of_week": 5,
              "send_at_local_time": "09:00:00"
            }
            """;

        mvc.perform(post("/api/v1/reminder-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void eventNeedingPlaceholdersAReminderCannotSupplyIsRefusedWith400() throws Exception {
        String body =
                """
            {
              "event": "PAYSLIP_READY",
              "audience": "SUBJECT",
              "anchor": "WEEKLY",
              "offset_days": 0,
              "day_of_week": 5,
              "send_at_local_time": "09:00:00"
            }
            """;

        mvc.perform(post("/api/v1/reminder-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.event", containsString("[link]")));
    }

    @Test
    void anchorWithNoDateSourceIsRefusedWith400() throws Exception {
        String body =
                """
            {
              "event": "POI_REMINDER",
              "audience": "SUBJECT",
              "anchor": "POI_DUE_DATE",
              "offset_days": 7,
              "send_at_local_time": "09:00:00"
            }
            """;

        mvc.perform(post("/api/v1/reminder-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.anchor", containsString("only WEEKLY rules can run")));
    }

    @Test
    void validPostCreatesRuleWith201() throws Exception {
        UUID ruleId = UUID.randomUUID();
        when(repository.save(any(ReminderRule.class))).thenAnswer(invocation -> {
            ReminderRule r = invocation.getArgument(0);
            return new ReminderRule(
                    r.getTenantId(),
                    r.getEvent(),
                    r.getAudience(),
                    r.getAnchor(),
                    r.getOffsetDays(),
                    r.getDayOfWeek(),
                    r.getSendAtLocalTime(),
                    r.getRepeatEveryDays(),
                    r.getMaxRepeats(),
                    "system") {
                @Override
                public UUID getId() {
                    return ruleId;
                }
            };
        });

        String body =
                """
            {
              "event": "TIMESHEET_REMINDER",
              "audience": "SUBJECT",
              "anchor": "WEEKLY",
              "offset_days": 0,
              "day_of_week": 5,
              "send_at_local_time": "09:00:00"
            }
            """;

        mvc.perform(post("/api/v1/reminder-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ruleId.toString()))
                .andExpect(jsonPath("$.event").value("TIMESHEET_REMINDER"))
                .andExpect(jsonPath("$.audience").value("SUBJECT"))
                .andExpect(jsonPath("$.anchor").value("WEEKLY"));
    }

    @Test
    void getListReturnsTenantRules() throws Exception {
        ReminderRule rule = new ReminderRule(
                tenantId,
                NotificationEvent.TIMESHEET_REMINDER,
                "SUBJECT",
                Anchor.WEEKLY,
                0,
                5,
                LocalTime.of(9, 0),
                null,
                null,
                "system");

        when(repository.findByTenantId(tenantId)).thenReturn(List.of(rule));

        mvc.perform(get("/api/v1/reminder-rules").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].event").value("TIMESHEET_REMINDER"))
                .andExpect(jsonPath("$[0].audience").value("SUBJECT"));
    }

    @Test
    void deleteSoftDeletesRuleWith204() throws Exception {
        UUID ruleId = UUID.randomUUID();
        ReminderRule rule = new ReminderRule(
                tenantId,
                NotificationEvent.TIMESHEET_REMINDER,
                "SUBJECT",
                Anchor.WEEKLY,
                0,
                5,
                LocalTime.of(9, 0),
                null,
                null,
                "system");

        when(repository.findByIdAndTenantId(ruleId, tenantId)).thenReturn(java.util.Optional.of(rule));

        mvc.perform(delete("/api/v1/reminder-rules/" + ruleId)).andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertFalse(rule.isActive());
    }
}
