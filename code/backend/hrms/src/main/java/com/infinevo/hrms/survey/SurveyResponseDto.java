package com.infinevo.hrms.survey;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SurveyResponseDto(
        UUID id,
        String title,
        String description,
        SurveyCategory category,
        boolean isAnonymous,
        SurveyTargetType targetType,
        UUID targetId,
        String targetName,
        LocalDate startDate,
        LocalDate endDate,
        SurveyStatus status,
        long totalResponses,
        List<SurveyQuestionResponse> questions,
        Instant createdAt,
        Instant updatedAt) {}
