package com.infinevo.core.holiday;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response representation of a single holiday (W-17).
 */
public record HolidayResponse(
        UUID id, UUID calendarId, String name, LocalDate from, LocalDate to, boolean restricted, String description) {}
