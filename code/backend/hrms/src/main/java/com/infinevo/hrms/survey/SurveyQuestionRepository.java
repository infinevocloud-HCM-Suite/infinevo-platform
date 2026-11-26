package com.infinevo.hrms.survey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SurveyQuestionRepository extends JpaRepository<SurveyQuestion, UUID> {

    List<SurveyQuestion> findByTenantIdAndSurveyIdOrderBySortOrderAsc(UUID tenantId, UUID surveyId);

    Optional<SurveyQuestion> findByIdAndTenantId(UUID id, UUID tenantId);

    void deleteByTenantIdAndSurveyId(UUID tenantId, UUID surveyId);
}
