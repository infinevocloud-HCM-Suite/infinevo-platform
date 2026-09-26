package com.infinevo.core.holiday;

import com.infinevo.core.org.WorkLocationRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing holiday calendars, holidays, and location assignments (W-17).
 */
@Service
public class HolidayCalendarService {

    private final HolidayCalendarRepository calendarRepository;
    private final HolidayRepository holidayRepository;
    private final HolidayCalendarLocationRepository locationRepository;
    private final WorkLocationRepository workLocationRepository;

    public HolidayCalendarService(
            HolidayCalendarRepository calendarRepository,
            HolidayRepository holidayRepository,
            HolidayCalendarLocationRepository locationRepository,
            WorkLocationRepository workLocationRepository) {
        this.calendarRepository = Objects.requireNonNull(calendarRepository, "calendarRepository must not be null");
        this.holidayRepository = Objects.requireNonNull(holidayRepository, "holidayRepository must not be null");
        this.locationRepository = Objects.requireNonNull(locationRepository, "locationRepository must not be null");
        this.workLocationRepository =
                Objects.requireNonNull(workLocationRepository, "workLocationRepository must not be null");
    }

    @Transactional
    public HolidayCalendarResponse createCalendar(HolidayCalendarRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateCalendarRequest(request);
        UUID tenantId = TenantContext.require();

        if (request.isDefault()) {
            calendarRepository.findByTenantIdAndIsDefaultTrue(tenantId).ifPresent(existing -> {
                throw new IllegalStateException("A default holiday calendar already exists for tenant: " + tenantId);
            });
        }

        HolidayCalendar calendar = new HolidayCalendar(tenantId, request.name().trim(), request.isDefault());
        calendar = calendarRepository.save(calendar);

        assignLocations(tenantId, calendar.getId(), request.workLocationIds());

        return toCalendarResponse(calendar);
    }

    @Transactional(readOnly = true)
    public List<HolidayCalendarResponse> listCalendars() {
        UUID tenantId = TenantContext.require();
        return calendarRepository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .map(this::toCalendarResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public HolidayCalendarResponse getCalendar(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        UUID tenantId = TenantContext.require();
        HolidayCalendar calendar = calendarRepository
                .findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Holiday calendar not found: " + id));
        return toCalendarResponse(calendar);
    }

    @Transactional
    public HolidayCalendarResponse updateCalendar(UUID id, HolidayCalendarRequest request) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(request, "request must not be null");
        validateCalendarRequest(request);
        UUID tenantId = TenantContext.require();

        HolidayCalendar calendar = calendarRepository
                .findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Holiday calendar not found: " + id));

        if (request.isDefault() && !calendar.isDefault()) {
            calendarRepository.findByTenantIdAndIsDefaultTrue(tenantId).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new IllegalStateException(
                            "A default holiday calendar already exists for tenant: " + tenantId);
                }
            });
        }

        calendar.setName(request.name().trim());
        calendar.setDefault(request.isDefault());
        calendar = calendarRepository.save(calendar);

        locationRepository.deleteByTenantIdAndCalendarId(tenantId, id);
        assignLocations(tenantId, calendar.getId(), request.workLocationIds());

        return toCalendarResponse(calendar);
    }

    @Transactional
    public HolidayResponse addHoliday(UUID calendarId, HolidayRequest request) {
        Objects.requireNonNull(calendarId, "calendarId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        validateHolidayRequest(request);
        UUID tenantId = TenantContext.require();

        calendarRepository
                .findByTenantIdAndId(tenantId, calendarId)
                .orElseThrow(() -> new IllegalArgumentException("Holiday calendar not found: " + calendarId));

        Holiday holiday = new Holiday(
                tenantId,
                calendarId,
                request.name().trim(),
                request.from(),
                request.to(),
                request.restricted(),
                request.description());
        holiday = holidayRepository.save(holiday);
        return toHolidayResponse(holiday);
    }

    private void validateCalendarRequest(HolidayCalendarRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Calendar name must not be blank");
        }
        if (request.name().trim().length() > 128) {
            throw new IllegalArgumentException("Calendar name must not exceed 128 characters");
        }
    }

    private void validateHolidayRequest(HolidayRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Holiday name must not be blank");
        }
        if (request.name().trim().length() > 128) {
            throw new IllegalArgumentException("Holiday name must not exceed 128 characters");
        }
        if (request.from() == null || request.to() == null) {
            throw new IllegalArgumentException("from and to dates must not be null");
        }
        if (request.to().isBefore(request.from())) {
            throw new IllegalArgumentException("to date must be on or after from date");
        }
        if (request.description() != null && request.description().length() > 500) {
            throw new IllegalArgumentException("Description must not exceed 500 characters");
        }
    }

    @Transactional
    public void deleteHoliday(UUID calendarId, UUID holidayId) {
        Objects.requireNonNull(calendarId, "calendarId must not be null");
        Objects.requireNonNull(holidayId, "holidayId must not be null");
        UUID tenantId = TenantContext.require();

        calendarRepository
                .findByTenantIdAndId(tenantId, calendarId)
                .orElseThrow(() -> new IllegalArgumentException("Holiday calendar not found: " + calendarId));

        Holiday holiday = holidayRepository
                .findByTenantIdAndCalendarIdAndId(tenantId, calendarId, holidayId)
                .orElseThrow(() -> new IllegalArgumentException("Holiday not found: " + holidayId));

        holidayRepository.delete(holiday);
    }

    private void assignLocations(UUID tenantId, UUID calendarId, Set<UUID> workLocationIds) {
        if (workLocationIds == null || workLocationIds.isEmpty()) {
            return;
        }

        for (UUID locationId : workLocationIds) {
            workLocationRepository
                    .findByIdAndTenantId(locationId, tenantId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Work location does not belong to tenant: " + locationId));

            if (locationRepository.existsByTenantIdAndWorkLocationIdAndCalendarIdNot(
                    tenantId, locationId, calendarId)) {
                throw new IllegalStateException("Work location already assigned to another calendar: " + locationId);
            }

            HolidayCalendarLocation link = new HolidayCalendarLocation(tenantId, calendarId, locationId);
            locationRepository.save(link);
        }
    }

    private HolidayCalendarResponse toCalendarResponse(HolidayCalendar calendar) {
        UUID tenantId = calendar.getTenantId();
        UUID calendarId = calendar.getId();

        Set<UUID> locationIds = locationRepository.findByTenantIdAndCalendarId(tenantId, calendarId).stream()
                .map(HolidayCalendarLocation::getWorkLocationId)
                .collect(Collectors.toSet());

        List<HolidayResponse> holidays =
                holidayRepository.findByTenantIdAndCalendarIdOrderByFromDateAsc(tenantId, calendarId).stream()
                        .map(this::toHolidayResponse)
                        .toList();

        return new HolidayCalendarResponse(
                calendar.getId(),
                calendar.getTenantId(),
                calendar.getName(),
                calendar.isDefault(),
                locationIds,
                holidays,
                calendar.getCreatedAt(),
                calendar.getUpdatedAt());
    }

    private HolidayResponse toHolidayResponse(Holiday holiday) {
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
