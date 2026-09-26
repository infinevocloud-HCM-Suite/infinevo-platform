package com.infinevo.core.holiday;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Response representation of a holiday calendar (W-17).
 */
public record HolidayCalendarResponse(
        UUID id,
        UUID tenantId,
        String name,
        boolean isDefault,
        Set<UUID> workLocationIds,
        List<HolidayResponse> holidays,
        Instant createdAt,
        Instant updatedAt) {}
