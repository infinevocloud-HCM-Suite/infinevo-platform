package com.infinevo.core.ai.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.holiday.HolidayCalendarResponse;
import com.infinevo.core.holiday.HolidayCalendarService;
import com.infinevo.core.holiday.HolidayRequest;
import com.infinevo.core.holiday.HolidayResponse;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Adds a holiday the AI helper proposed (W-114.28 step 4). Confirm calls {@link HolidayCalendarService#addHoliday},
 * what {@code POST /api/v1/holiday-calendars/{id}/holidays} calls ({@code holiday/HolidayCalendarController.java:66-70}),
 * so the screen's checks run again. The payload names the calendar, never its id: it is found again by name at
 * Confirm, and a calendar renamed or removed in between, or the same holiday added in between, ends {@code FAILED}.
 */
@Component
public class HolidayAddExecutor implements AiActionExecutor {

    public static final String ACTION_CODE = "core.holiday.add";
    static final String CALENDAR = "calendar";
    static final String NAME = "name";
    static final String FROM = "from";
    static final String TO = "to";
    static final String RESTRICTED = "restricted";

    static final String NO_CALENDAR =
            "That holiday calendar is no longer there. Ask the user to check the Holidays screen.";
    static final String ALREADY = "That holiday is already in the calendar.";

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final HolidayCalendarService calendars;

    public HolidayAddExecutor(HolidayCalendarService calendars) {
        this.calendars = Objects.requireNonNull(calendars, "calendars must not be null");
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    public String requiredAction() {
        return "core.holiday.manage";
    }

    /** Company set-up: an administrator need not be an employee. */
    @Override
    public boolean requiresEmployeeProfile() {
        return false;
    }

    @Override
    public AiActionDescription describe(AiToolContext ctx, JsonNode payload) {
        String name = OrgMasterCreateExecutor.text(payload, NAME);
        String from = OrgMasterCreateExecutor.text(payload, FROM);
        String to = OrgMasterCreateExecutor.text(payload, TO);
        boolean restricted = payload != null && payload.path(RESTRICTED).asBoolean(false);
        String calendar = OrgMasterCreateExecutor.text(payload, CALENDAR);
        String when = Objects.equals(from, to) ? from : from + " to " + to;
        return new AiActionDescription(
                "Add holiday " + name + " on " + when + " to " + calendar,
                List.of(
                        new AiActionField("Calendar", calendar),
                        new AiActionField("Holiday", name),
                        new AiActionField("From", from),
                        new AiActionField("To", to),
                        new AiActionField("Restricted", restricted ? "Yes" : "No")));
    }

    @Override
    public JsonNode execute(AiToolContext ctx, JsonNode payload) {
        String name = OrgMasterCreateExecutor.text(payload, NAME);
        LocalDate from = parse(OrgMasterCreateExecutor.text(payload, FROM));
        LocalDate to = parse(OrgMasterCreateExecutor.text(payload, TO));
        boolean restricted = payload.path(RESTRICTED).asBoolean(false);
        HolidayCalendarResponse calendar = find(
                        calendars.listCalendars(), OrgMasterCreateExecutor.text(payload, CALENDAR))
                .orElseThrow(() -> new IllegalStateException(NO_CALENDAR));
        if (alreadyThere(calendar, name, from)) {
            throw new IllegalStateException(ALREADY);
        }
        HolidayResponse added;
        try {
            added = calendars.addHoliday(calendar.id(), new HolidayRequest(name, from, to, restricted, null));
        } catch (HolidayCalendarService.HolidayCalendarNotFoundException e) {
            throw new IllegalStateException(NO_CALENDAR);
        }
        ObjectNode result = NODES.objectNode();
        result.put("calendarId", calendar.id().toString());
        result.put("holidayId", added.id().toString());
        return result;
    }

    /** The calendar of that name, any case. */
    static Optional<HolidayCalendarResponse> find(List<HolidayCalendarResponse> all, String name) {
        if (name == null) {
            return Optional.empty();
        }
        return all.stream()
                .filter(c -> c.name() != null && c.name().trim().equalsIgnoreCase(name.trim()))
                .findFirst();
    }

    /** A holiday of the same name starting the same day is already in the calendar. */
    static boolean alreadyThere(HolidayCalendarResponse calendar, String name, LocalDate from) {
        if (calendar.holidays() == null || name == null) {
            return false;
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        return calendar.holidays().stream()
                .anyMatch(h -> h.name() != null
                        && h.name().trim().toLowerCase(Locale.ROOT).equals(wanted)
                        && Objects.equals(h.from(), from));
    }

    private static LocalDate parse(String value) {
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("The date is not valid.");
        }
    }
}
