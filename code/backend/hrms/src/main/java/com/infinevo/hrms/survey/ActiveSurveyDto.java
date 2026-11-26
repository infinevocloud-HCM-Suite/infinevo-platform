package com.infinevo.hrms.survey;

import java.time.LocalDate;
import java.util.UUID;

public record ActiveSurveyDto(
        UUID id,
        String title,
        String description,
        SurveyCategory category,
        boolean isAnonymous,
        SurveyTargetType targetType,
        LocalDate startDate,
        LocalDate endDate,
        int questionCount,
        boolean completed) {}
