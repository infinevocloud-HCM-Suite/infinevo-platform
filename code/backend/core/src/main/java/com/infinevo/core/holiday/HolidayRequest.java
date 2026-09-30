package com.infinevo.core.holiday;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Request payload for creating a holiday in a calendar (W-17).
 *
 * <p>The restricted-holiday flag travels as {@code isRestricted} on the wire, the name the
 * W-46.3b screen reads and the column name ({@code is_restricted}).
 */
public record HolidayRequest(
        String name,
        LocalDate from,
        LocalDate to,
        @JsonProperty("isRestricted") boolean restricted,
        String description) {}
