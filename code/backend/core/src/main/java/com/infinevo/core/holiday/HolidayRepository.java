package com.infinevo.core.holiday;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Holiday} (W-17).
 */
@Repository
public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    List<Holiday> findByTenantIdAndCalendarIdOrderByFromDateAsc(UUID tenantId, UUID calendarId);

    Optional<Holiday> findByTenantIdAndCalendarIdAndId(UUID tenantId, UUID calendarId, UUID id);

    Optional<Holiday> findByTenantIdAndId(UUID tenantId, UUID id);

    void deleteByTenantIdAndCalendarId(UUID tenantId, UUID calendarId);

    @Query("SELECT h FROM Holiday h WHERE h.tenantId = :tenantId AND h.calendarId = :calendarId "
            + "AND h.fromDate <= :to AND h.toDate >= :from ORDER BY h.fromDate ASC")
    List<Holiday> findHolidaysBetween(
            @Param("tenantId") UUID tenantId,
            @Param("calendarId") UUID calendarId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT COUNT(h) > 0 FROM Holiday h WHERE h.tenantId = :tenantId AND h.calendarId = :calendarId "
            + "AND :date >= h.fromDate AND :date <= h.toDate")
    boolean isHoliday(
            @Param("tenantId") UUID tenantId, @Param("calendarId") UUID calendarId, @Param("date") LocalDate date);
}
