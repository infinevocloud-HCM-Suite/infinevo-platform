package com.infinevo.hrms.survey;

import java.util.List;

public record SurveyQuestionRequest(
        String questionText, QuestionType questionType, List<String> options, Boolean isRequired, Short sortOrder) {}
