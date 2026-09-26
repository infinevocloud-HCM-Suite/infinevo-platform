package com.infinevo.core.holiday;

import java.time.LocalDate;

/**
 * Request payload for creating a holiday in a calendar (W-17).
 */
public record HolidayRequest(String name, LocalDate from, LocalDate to, boolean restricted, String description) {}
