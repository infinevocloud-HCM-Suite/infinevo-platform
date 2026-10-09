package com.infinevo.core.holiday;

import com.fasterxml.jackson.databind.JsonNode;
import com.infinevo.core.template.TenantTemplateContributor;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The {@code holidays} section of a country template (W-73.9): one default calendar holding the national
 * holidays of the current and the next calendar year. Written only for a tenant with no calendar at all.
 *
 * <p>Payload: {@code {"calendar_name": "...", "years": {"2026": [{"date": "2026-01-26", "name": "..."}]}}}.
 */
@Component
public class HolidayTemplateContributor implements TenantTemplateContributor {

    public static final String SECTION = "holidays";

    private final HolidayCalendarRepository calendarRepository;
    private final HolidayRepository holidayRepository;

    public HolidayTemplateContributor(
            HolidayCalendarRepository calendarRepository, HolidayRepository holidayRepository) {
        this.calendarRepository = Objects.requireNonNull(calendarRepository, "calendarRepository must not be null");
        this.holidayRepository = Objects.requireNonNull(holidayRepository, "holidayRepository must not be null");
    }

    @Override
    public String section() {
        return SECTION;
    }

    @Override
    public boolean apply(UUID tenantId, JsonNode payload, LocalDate today) {
        if (calendarRepository.existsByTenantId(tenantId)) {
            return false;
        }
        List<JsonNode> entries = new ArrayList<>();
        JsonNode years = payload.path("years");
        for (int year : new int[] {today.getYear(), today.getYear() + 1}) {
            years.path(Integer.toString(year)).forEach(entries::add);
        }
        if (entries.isEmpty()) {
            return false;
        }

        HolidayCalendar calendar =
                new HolidayCalendar(tenantId, payload.path("calendar_name").asText("National holidays"), true);
        calendar.setCreatedBy(ACTOR);
        calendar.setUpdatedBy(ACTOR);
        calendar = calendarRepository.save(calendar);

        for (JsonNode entry : entries) {
            LocalDate date = LocalDate.parse(entry.path("date").asText());
            Holiday holiday =
                    new Holiday(tenantId, calendar.getId(), entry.path("name").asText(), date, date, false, null);
            holiday.setCreatedBy(ACTOR);
            holiday.setUpdatedBy(ACTOR);
            holidayRepository.save(holiday);
        }
        return true;
    }
}
