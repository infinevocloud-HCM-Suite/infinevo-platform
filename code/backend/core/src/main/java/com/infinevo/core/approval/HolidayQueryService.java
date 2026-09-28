package com.infinevo.core.approval;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Seam for querying location-specific holidays (W-15.3, spec section 3 & section 4; full implementation in W-17).
 */
public interface HolidayQueryService {

    /**
     * Returns public/company holidays falling between {@code from} and {@code to} inclusive for the given work location.
     */
    default List<LocalDate> holidaysBetween(UUID workLocationId, LocalDate from, LocalDate to) {
        return Collections.emptyList();
    }
}
