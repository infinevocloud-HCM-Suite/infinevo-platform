package com.infinevo.core.holiday;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link HolidayCalendar} (W-17).
 */
@Repository
public interface HolidayCalendarRepository extends JpaRepository<HolidayCalendar, UUID> {

    List<HolidayCalendar> findByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<HolidayCalendar> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<HolidayCalendar> findByTenantIdAndIsDefaultTrue(UUID tenantId);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(UUID tenantId, String name, UUID id);
}
