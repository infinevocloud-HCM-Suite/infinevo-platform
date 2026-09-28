package com.infinevo.core.approval;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Default fallback implementation of {@link HolidayQueryService} returning no holidays until W-17 ships.
 */
@Component
public class DefaultHolidayQueryService implements HolidayQueryService {

    @Override
    public List<LocalDate> holidaysBetween(UUID workLocationId, LocalDate from, LocalDate to) {
        return Collections.emptyList();
    }
}
