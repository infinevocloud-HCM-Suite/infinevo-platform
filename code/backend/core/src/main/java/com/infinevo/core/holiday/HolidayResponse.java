package com.infinevo.core.holiday;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response representation of a single holiday (W-17).
 *
 * <p>The restricted-holiday flag is serialised as {@code isRestricted} (W-17 §3, W-46.3b).
 */
public record HolidayResponse(
        UUID id,
        UUID calendarId,
        String name,
        LocalDate from,
        LocalDate to,
        @JsonProperty("isRestricted") boolean restricted,
        String description) {}
