package com.infinevo.hrms.survey;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SurveyRequest(
        String title,
        String description,
        SurveyCategory category,
        Boolean isAnonymous,
        SurveyTargetType targetType,
        UUID targetId,
        LocalDate startDate,
        LocalDate endDate,
        List<SurveyQuestionRequest> questions) {}
