package com.infinevo.core.holiday;

import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer read service answering holiday queries for work locations (W-17, 12-core-contracts.md §3).
 *
 * <p>The seam W-16 (leave engine), W-18 (loss-of-pay) and W-15.3 (approvals) plug into.
 * A location with no specific calendar assigned falls back to the tenant's default calendar.
 */
@Service
public class HolidayQueryService {

    private final HolidayCalendarRepository calendarRepository;
    private final HolidayRepository holidayRepository;
    private final HolidayCalendarLocationRepository locationRepository;

    public HolidayQueryService(
            HolidayCalendarRepository calendarRepository,
            HolidayRepository holidayRepository,
            HolidayCalendarLocationRepository locationRepository) {
        this.calendarRepository = Objects.requireNonNull(calendarRepository, "calendarRepository must not be null");
        this.holidayRepository = Objects.requireNonNull(holidayRepository, "holidayRepository must not be null");
        this.locationRepository = Objects.requireNonNull(locationRepository, "locationRepository must not be null");
    }

    /**
     * Answers whether the given date is a holiday for the work location.
     * Boundaries of holiday date ranges are inclusive.
     */
    @Transactional(readOnly = true)
    public boolean isHoliday(UUID locationId, LocalDate date) {
        if (date == null) {
            return false;
        }
        UUID tenantId = TenantContext.require();
        return resolveCalendarId(tenantId, locationId)
                .map(calendarId -> holidayRepository.isHoliday(tenantId, calendarId, date))
                .orElse(false);
    }

    /**
     * Returns all holidays overlapping the inclusive date range [from, to] for the work location.
     * Returns an empty list (never null) when there are none.
     */
    @Transactional(readOnly = true)
    public List<HolidayResponse> holidaysBetween(UUID locationId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        UUID tenantId = TenantContext.require();
        return resolveCalendarId(tenantId, locationId)
                .map(calendarId -> holidayRepository.findHolidaysBetween(tenantId, calendarId, from, to).stream()
                        .map(this::toResponse)
                        .toList())
                .orElse(List.of());
    }

    private Optional<UUID> resolveCalendarId(UUID tenantId, UUID locationId) {
        if (locationId != null) {
            Optional<HolidayCalendarLocation> loc =
                    locationRepository.findByTenantIdAndWorkLocationId(tenantId, locationId);
            if (loc.isPresent()) {
                return Optional.of(loc.get().getCalendarId());
            }
        }
        return calendarRepository.findByTenantIdAndIsDefaultTrue(tenantId).map(HolidayCalendar::getId);
    }

    private HolidayResponse toResponse(Holiday holiday) {
        return new HolidayResponse(
                holiday.getId(),
                holiday.getCalendarId(),
                holiday.getName(),
                holiday.getFromDate(),
                holiday.getToDate(),
                holiday.isRestricted(),
                holiday.getDescription());
    }
}
