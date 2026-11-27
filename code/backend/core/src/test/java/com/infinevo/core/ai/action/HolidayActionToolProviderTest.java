package com.infinevo.core.ai.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.ai.AiTool;
import com.infinevo.core.ai.AiToolResult;
import com.infinevo.core.holiday.HolidayCalendarResponse;
import com.infinevo.core.holiday.HolidayCalendarService;
import com.infinevo.core.holiday.HolidayRequest;
import com.infinevo.core.holiday.HolidayResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

/** W-114.28 step 4, Tests: {@code core.propose_holiday} and {@link HolidayAddExecutor}. */
class HolidayActionToolProviderTest {

    private static final LocalDate DIWALI = LocalDate.parse("2026-11-08");

    private final ObjectMapper json = new ObjectMapper();
    private final List<HolidayCalendarResponse> calendars = new ArrayList<>();
    private HolidayCalendarService service;
    private AdminStepsFixture f;
    private AiTool tool;
    private HolidayCalendarResponse india;

    @BeforeEach
    void setUp() {
        service = mock(HolidayCalendarService.class);
        when(service.listCalendars()).thenAnswer(inv -> List.copyOf(calendars));
        f = new AdminStepsFixture(List.of(new HolidayAddExecutor(service)));
        india = calendar("India 2026", true, List.of());
        calendars.add(india);
        calendars.add(calendar("Pune office", false, List.of()));
        tool = new HolidayActionToolProvider(
                        service::listCalendars, f.actions, TransactionOperations.withoutTransaction())
                .tools()
                .get(0);
    }

    @AfterEach
    void tearDown() {
        f.close();
    }

    private HolidayCalendarResponse calendar(String name, boolean isDefault, List<HolidayResponse> holidays) {
        return new HolidayCalendarResponse(
                UUID.randomUUID(), f.tenantId, name, isDefault, Set.of(), holidays, Instant.now(), Instant.now());
    }

    private AiToolResult run(String input) throws Exception {
        return tool.execute(f.ctx, json.readTree(input));
    }

    @Test
    @DisplayName("the tool needs core.holiday.manage, the Holidays screen's add action")
    void declaration() {
        assertThat(tool.name()).isEqualTo("core.propose_holiday");
        assertThat(tool.requiredAction()).isEqualTo("core.holiday.manage");
        assertThat(new HolidayAddExecutor(service).requiredAction()).isEqualTo("core.holiday.manage");
        assertThat(new HolidayAddExecutor(service).requiresEmployeeProfile()).isFalse();
    }

    @Test
    @DisplayName("no calendar named: the default one; a one-day holiday; the payload names the calendar, no id")
    void defaultCalendar() throws Exception {
        AiToolResult result = run("{\"name\":\"Diwali\",\"from\":\"2026-11-08\"}");

        assertThat(result.ok()).isTrue();
        assertThat(AdminStepsFixture.UUID_TEXT.matcher(result.data().toString()).find())
                .isFalse();
        assertThat(result.data().toString()).contains("India 2026").contains("Diwali");
        assertThat(f.stored.get(0).getPayload().toString())
                .isEqualTo("{\"calendar\":\"India 2026\",\"name\":\"Diwali\",\"from\":\"2026-11-08\","
                        + "\"to\":\"2026-11-08\",\"restricted\":false}");
        verify(service, never()).addHoliday(any(), any());
    }

    @Test
    @DisplayName("an unknown calendar lists the real ones; a holiday already there is refused")
    void refusals() throws Exception {
        AiToolResult unknown = run("{\"calendar\":\"Mumbai\",\"name\":\"Diwali\",\"from\":\"2026-11-08\"}");
        assertThat(unknown.error()).contains("Mumbai").contains("India 2026").contains("Pune office");

        calendars.set(0, calendar("India 2026", true, List.of(holiday("Diwali", DIWALI))));
        assertThat(run("{\"name\":\" diwali \",\"from\":\"2026-11-08\"}").error())
                .contains("already");

        assertThat(run("{\"name\":\"X\",\"from\":\"2026-11-08\",\"to\":\"2026-11-01\"}")
                        .ok())
                .isFalse();
        assertThat(f.stored).isEmpty();
    }

    @Test
    @DisplayName("no calendar at all: make one on the screen first")
    void noCalendars() throws Exception {
        calendars.clear();
        assertThat(run("{\"name\":\"Diwali\",\"from\":\"2026-11-08\"}").error())
                .isEqualTo(HolidayActionToolProvider.NO_CALENDARS);
    }

    @Test
    @DisplayName("Confirm finds the calendar by name again and calls addHoliday; gone or added in between is FAILED")
    void execute() throws Exception {
        assertThat(run("{\"calendar\":\"india 2026\",\"name\":\"Diwali\",\"from\":\"2026-11-08\",\"restricted\":true}")
                        .ok())
                .isTrue();
        var payload = f.stored.get(0).getPayload();
        HolidayAddExecutor exec = new HolidayAddExecutor(service);
        when(service.addHoliday(eq(india.id()), any())).thenReturn(holiday("Diwali", DIWALI));

        assertThat(exec.execute(f.ctx, payload).path("calendarId").asText())
                .isEqualTo(india.id().toString());
        ArgumentCaptor<HolidayRequest> sent = ArgumentCaptor.forClass(HolidayRequest.class);
        verify(service).addHoliday(eq(india.id()), sent.capture());
        assertThat(sent.getValue().restricted()).isTrue();
        assertThat(sent.getValue().from()).isEqualTo(DIWALI);

        calendars.set(0, calendar("India 2026", true, List.of(holiday("Diwali", DIWALI))));
        assertThatThrownBy(() -> exec.execute(f.ctx, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(HolidayAddExecutor.ALREADY);

        calendars.remove(0);
        assertThatThrownBy(() -> exec.execute(f.ctx, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(HolidayAddExecutor.NO_CALENDAR);
    }

    private HolidayResponse holiday(String name, LocalDate day) {
        return new HolidayResponse(UUID.randomUUID(), india == null ? null : india.id(), name, day, day, false, null);
    }
}
