package com.infinevo.core.approval;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Fallback {@link HolidayQueryService} returning no holidays.
 *
 * <p>Deliberately <strong>not</strong> a Spring bean: the real implementation is
 * {@code com.infinevo.core.holiday.ApprovalHolidayQueryAdapter} (W-17), the only bean of this type
 * in the application. {@link EscalationServiceImpl} constructs this fallback itself only when no
 * bean is present — a test context that does not scan {@code core.holiday}.
 */
public class DefaultHolidayQueryService implements HolidayQueryService {

    @Override
    public List<LocalDate> holidaysBetween(UUID workLocationId, LocalDate from, LocalDate to) {
        return Collections.emptyList();
    }
}
