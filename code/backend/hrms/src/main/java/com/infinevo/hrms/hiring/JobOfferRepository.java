package com.infinevo.hrms.hiring;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link JobOffer}.
 */
@Repository
public interface JobOfferRepository extends JpaRepository<JobOffer, UUID> {

    List<JobOffer> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<JobOffer> findAllByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, JobOfferStatus status);

    Optional<JobOffer> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<JobOffer> findByTenantIdAndApplicationId(UUID tenantId, UUID applicationId);
}
