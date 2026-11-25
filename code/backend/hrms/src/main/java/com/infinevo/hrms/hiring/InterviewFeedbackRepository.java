package com.infinevo.hrms.hiring;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link InterviewFeedback}.
 */
@Repository
public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, UUID> {

    List<InterviewFeedback> findAllByTenantIdAndInterviewIdOrderBySubmittedAtAsc(UUID tenantId, UUID interviewId);

    Optional<InterviewFeedback> findByTenantIdAndInterviewIdAndInterviewerId(
            UUID tenantId, UUID interviewId, UUID interviewerId);

    boolean existsByTenantIdAndInterviewIdAndInterviewerId(UUID tenantId, UUID interviewId, UUID interviewerId);
}
