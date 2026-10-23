package com.infinevo.core.wfh;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link WfhImportLog} entities (W-74).
 */
@Repository
public interface WfhImportLogRepository extends JpaRepository<WfhImportLog, UUID> {

    List<WfhImportLog> findByTenantIdOrderByStartedAtDesc(UUID tenantId);

    Optional<WfhImportLog> findByTenantIdAndId(UUID tenantId, UUID id);
}
