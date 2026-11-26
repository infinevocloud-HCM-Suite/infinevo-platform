package com.infinevo.hrms.survey;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SurveyAnswerRepository extends JpaRepository<SurveyAnswer, UUID> {

    List<SurveyAnswer> findByTenantIdAndResponseId(UUID tenantId, UUID responseId);

    List<SurveyAnswer> findByTenantIdAndQuestionId(UUID tenantId, UUID questionId);

    List<SurveyAnswer> findByTenantIdAndQuestionIdIn(UUID tenantId, Collection<UUID> questionIds);
}
