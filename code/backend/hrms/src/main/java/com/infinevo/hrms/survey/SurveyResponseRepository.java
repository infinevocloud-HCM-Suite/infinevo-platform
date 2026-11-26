package com.infinevo.hrms.survey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, UUID> {

    long countByTenantIdAndSurveyId(UUID tenantId, UUID surveyId);

    List<SurveyResponse> findByTenantIdAndSurveyId(UUID tenantId, UUID surveyId);

    boolean existsByTenantIdAndSurveyIdAndSubmissionToken(UUID tenantId, UUID surveyId, String submissionToken);

    Optional<SurveyResponse> findByTenantIdAndSurveyIdAndSubmissionToken(
            UUID tenantId, UUID surveyId, String submissionToken);
}
