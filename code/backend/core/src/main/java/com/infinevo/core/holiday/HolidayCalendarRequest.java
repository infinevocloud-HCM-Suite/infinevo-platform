package com.infinevo.core.holiday;

import java.util.Set;
import java.util.UUID;

/**
 * Request payload for creating or updating a holiday calendar (W-17).
 */
public record HolidayCalendarRequest(String name, boolean isDefault, Set<UUID> workLocationIds) {}
