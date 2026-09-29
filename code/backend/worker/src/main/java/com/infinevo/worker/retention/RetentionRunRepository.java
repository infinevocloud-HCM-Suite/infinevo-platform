package com.infinevo.worker.retention;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link RetentionRun} entities (W-22.2).
 */
@Repository
@Transactional(readOnly = true)
public interface RetentionRunRepository extends JpaRepository<RetentionRun, UUID> {

    List<RetentionRun> findByTenantIdOrderByStartedAtDesc(UUID tenantId);

    List<RetentionRun> findByTenantIdAndTargetTableOrderByStartedAtDesc(UUID tenantId, String targetTable);

    Optional<RetentionRun> findTopByTenantIdAndTargetTableAndCutoffDateAndStatusOrderByStartedAtDesc(
            UUID tenantId, String targetTable, LocalDate cutoffDate, String status);
}
