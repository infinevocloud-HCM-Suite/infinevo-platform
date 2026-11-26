package com.infinevo.hrms.survey;

import java.util.List;
import java.util.UUID;

public record SurveyResultsResponse(
        UUID surveyId,
        String title,
        boolean isAnonymous,
        long totalEligible,
        long totalResponses,
        double participationRate,
        boolean resultsWithheld,
        String withholdingReason,
        List<QuestionResultDto> questionResults) {}
