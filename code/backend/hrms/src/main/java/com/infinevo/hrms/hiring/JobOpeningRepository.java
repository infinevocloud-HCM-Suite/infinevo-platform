package com.infinevo.hrms.hiring;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link JobOpening}.
 */
@Repository
public interface JobOpeningRepository extends JpaRepository<JobOpening, UUID> {

    List<JobOpening> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<JobOpening> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<JobOpening> findByTenantIdAndOpeningCode(UUID tenantId, String openingCode);

    boolean existsByTenantIdAndOpeningCode(UUID tenantId, String openingCode);

    List<JobOpening> findAllByTenantIdAndStatus(UUID tenantId, JobOpeningStatus status);
}
