package com.infinevo.hrms.survey;

import java.util.UUID;

public interface SurveyAnalyticsService {

    SurveyResultsResponse getResults(UUID tenantId, UUID surveyId);
}
