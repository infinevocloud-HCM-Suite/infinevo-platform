package com.infinevo.hrms.hiring;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Candidate}.
 */
@Repository
public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    List<Candidate> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<Candidate> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Candidate> findByTenantIdAndEmail(UUID tenantId, String email);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);
}
