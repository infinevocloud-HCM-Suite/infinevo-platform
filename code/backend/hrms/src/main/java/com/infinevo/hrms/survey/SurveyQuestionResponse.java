package com.infinevo.hrms.survey;

import java.util.List;
import java.util.UUID;

public record SurveyQuestionResponse(
        UUID id,
        String questionText,
        QuestionType questionType,
        List<String> options,
        boolean isRequired,
        short sortOrder) {}
