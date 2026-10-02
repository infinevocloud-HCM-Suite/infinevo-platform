package com.infinevo.core.leave;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link LeaveImportLog} (W-16.4b).
 */
@Repository
public interface LeaveImportLogRepository extends JpaRepository<LeaveImportLog, UUID> {

    Optional<LeaveImportLog> findByTenantIdAndId(UUID tenantId, UUID id);

    Page<LeaveImportLog> findByTenantIdOrderByStartedAtDesc(UUID tenantId, Pageable pageable);
}
