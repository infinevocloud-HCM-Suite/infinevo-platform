package com.infinevo.hrms.hiring;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link JobApplication}.
 */
@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    List<JobApplication> findAllByTenantIdAndJobOpeningIdOrderByAppliedAtDesc(UUID tenantId, UUID jobOpeningId);

    List<JobApplication> findAllByTenantIdAndCandidateId(UUID tenantId, UUID candidateId);

    Optional<JobApplication> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<JobApplication> findByTenantIdAndJobOpeningIdAndCandidateId(
            UUID tenantId, UUID jobOpeningId, UUID candidateId);

    boolean existsByTenantIdAndJobOpeningIdAndCandidateId(UUID tenantId, UUID jobOpeningId, UUID candidateId);

    long countByTenantIdAndJobOpeningIdAndStage(UUID tenantId, UUID jobOpeningId, JobApplicationStage stage);

    long countByTenantIdAndJobOpeningId(UUID tenantId, UUID jobOpeningId);
}
