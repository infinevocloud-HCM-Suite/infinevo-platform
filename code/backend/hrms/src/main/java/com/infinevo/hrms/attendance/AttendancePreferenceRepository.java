package com.infinevo.hrms.attendance;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link AttendancePreference} (W-40.1).
 */
@Repository
public interface AttendancePreferenceRepository extends JpaRepository<AttendancePreference, UUID> {

    /**
     * Looks up the attendance preference record for a given tenant.
     *
     * @param tenantId the tenant id
     * @return the optional attendance preference record
     */
    Optional<AttendancePreference> findByTenantId(UUID tenantId);
}
