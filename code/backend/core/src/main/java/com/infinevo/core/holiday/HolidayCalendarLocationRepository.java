package com.infinevo.core.holiday;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link HolidayCalendarLocation} (W-17).
 */
@Repository
public interface HolidayCalendarLocationRepository extends JpaRepository<HolidayCalendarLocation, UUID> {

    List<HolidayCalendarLocation> findByTenantIdAndCalendarId(UUID tenantId, UUID calendarId);

    Optional<HolidayCalendarLocation> findByTenantIdAndWorkLocationId(UUID tenantId, UUID workLocationId);

    void deleteByTenantIdAndCalendarId(UUID tenantId, UUID calendarId);

    boolean existsByTenantIdAndWorkLocationId(UUID tenantId, UUID workLocationId);

    boolean existsByTenantIdAndWorkLocationIdAndCalendarIdNot(UUID tenantId, UUID workLocationId, UUID calendarId);
}
