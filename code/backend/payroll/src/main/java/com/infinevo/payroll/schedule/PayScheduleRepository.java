package com.infinevo.payroll.schedule;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link PaySchedule} (W-28 §4).
 */
@Repository
public interface PayScheduleRepository extends JpaRepository<PaySchedule, UUID> {

    /**
     * Looks up the single pay schedule for the given tenant under row-level security.
     */
    Optional<PaySchedule> findByTenantId(UUID tenantId);
}
