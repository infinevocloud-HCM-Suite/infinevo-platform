package com.infinevo.hrms.hiring;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Interview}.
 */
@Repository
public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    List<Interview> findAllByTenantIdAndApplicationIdOrderByScheduledAtAsc(UUID tenantId, UUID applicationId);

    List<Interview> findAllByTenantIdAndInterviewerIdOrderByScheduledAtDesc(UUID tenantId, UUID interviewerId);

    List<Interview> findAllByTenantIdOrderByScheduledAtDesc(UUID tenantId);

    Optional<Interview> findByIdAndTenantId(UUID id, UUID tenantId);
}
